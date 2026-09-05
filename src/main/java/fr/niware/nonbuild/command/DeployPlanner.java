package fr.niware.nonbuild.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

import fr.niware.nonbuild.Msg;
import fr.niware.nonbuild.NonBuild;
import fr.niware.nonbuild.model.Arena;
import fr.niware.nonbuild.model.DeployedInstance;
import fr.niware.nonbuild.model.Point;
import fr.niware.nonbuild.placement.PlotAllocator;
import fr.niware.nonbuild.placement.Region2D;
import fr.niware.nonbuild.schematic.SpongeSchematic;
import fr.niware.nonbuild.work.BlockPaster;
import fr.niware.nonbuild.work.ChunkPreloader;

/**
 * Planification et exécution du déploiement d'instances d'arènes.
 * Calcule les emplacements (via PlotAllocator), construit les plans,
 * puis exécute le collage séquentiel avec préchargement de chunks.
 */
class DeployPlanner {

    private final NonBuild plugin;
    private final DeployCommand deployCommand;

    DeployPlanner(NonBuild plugin, DeployCommand deployCommand) {
        this.plugin = plugin;
        this.deployCommand = deployCommand;
    }

    /**
     * Une action de déploiement : mise à jour sur place d'une instance
     * existante (existing != null) ou création d'une nouvelle.
     */
    static record Plan(String instanceName, DeployedInstance existing, Point targetCenter,
                       Point center, Point spawn1, Point spawn2,
                       int[] corner1, int[] corner2, int[] cellMin, int[] cellMax,
                       boolean clearBefore) {
    }

    /**
     * Lance le déploiement : valide les dimensions, alloue les cellules,
     * construit les plans puis exécute le collage séquentiel.
     */
    void startDeployment(CommandSender sender, Arena arena, SpongeSchematic schematic,
                         World prodWorld, int count, long startedAt, boolean skipAir, Runnable onAllDone) {
        if (deployCommand.isDeploying()) {
            return;
        }
        deployCommand.setDeploying(true);

        int width = schematic.getWidth();
        int height = schematic.getHeight();
        int length = schematic.getLength();
        int pasteY = plugin.getSettings().pasteY();

        if (pasteY < prodWorld.getMinHeight() || pasteY + height - 1 > prodWorld.getMaxHeight() - 1) {
            Msg.error(sender, "Hauteur incompatible : paste-y=" + pasteY + " (arène collée de Y " + pasteY
                    + " à " + (pasteY + height - 1) + ") dépasse les limites du monde " + prodWorld.getName()
                    + ". Réglez placement.paste-y dans config.yml.");
            deployCommand.setDeploying(false);
            return;
        }

        if (width != arena.sizeX() || height != arena.sizeY() || length != arena.sizeZ()) {
            Msg.warn(sender, "Dimensions de la schematic différentes des corners enregistrés, la schematic fait foi.");
        }

        List<DeployedInstance> existing = plugin.getDeployments().byArena(arena.getSlug());

        PlotAllocator allocator = new PlotAllocator(
                plugin.getSettings().spawnProtectionRadius(), plugin.getSettings().margin());
        for (DeployedInstance instance : plugin.getDeployments().all()) {
            allocator.addOccupied(new Region2D(
                    instance.getCellMinXZ()[0], instance.getCellMinXZ()[1],
                    instance.getCellMaxXZ()[0], instance.getCellMaxXZ()[1]));
        }

        List<Plan> plans = new ArrayList<>();

        // 1) Mise à jour sur place des 'count' premières instances existantes
        int toUpdate = Math.min(count, existing.size());
        for (int i = 0; i < toUpdate; i++) {
            DeployedInstance instance = existing.get(i);
            int cellWidth = instance.getCellMaxXZ()[0] - instance.getCellMinXZ()[0] + 1;
            int cellLength = instance.getCellMaxXZ()[1] - instance.getCellMinXZ()[1] + 1;
            int[] cellMin = instance.getCellMinXZ();

            if (width + 2 * plugin.getSettings().margin() > cellWidth || length + 2 * plugin.getSettings().margin() > cellLength) {
                cellMin = allocator.allocate(width, length);
                if (cellMin == null) {
                    Msg.error(sender, "Espace insuffisant pour déplacer l'instance " + instance.getName()
                            + " (l'arène est plus grande que son ancien emplacement).");
                    deployCommand.setDeploying(false);
                    return;
                }
                allocator.addOccupied(allocator.cellFor(cellMin, width, length));
            }
            plans.add(buildPlan(allocator, instance.getName(), instance, cellMin, arena, schematic, pasteY));
        }

        // 2) Création des instances manquantes
        int baseIndex = plugin.getDeployments().nextIndex(arena.getSlug());
        for (int i = 0; i < count - toUpdate; i++) {
            int[] cellMin = allocator.allocate(width, length);
            if (cellMin == null) {
                Msg.error(sender, "Espace insuffisant dans le monde de production pour " + count + " instance(s).");
                deployCommand.setDeploying(false);
                return;
            }
            allocator.addOccupied(allocator.cellFor(cellMin, width, length));
            plans.add(buildPlan(allocator, arena.getSlug() + "-" + (baseIndex + i), null, cellMin, arena, schematic, pasteY));
        }

        int updated = toUpdate;
        int created = plans.size() - updated;
        int untouched = existing.size() - updated;
        if (created == 0) {
            Msg.ok(sender, "Mise à jour de <white>" + updated + "<green> instance(s) de <white>" + arena.getDisplayName() + "<green> sur leurs emplacements.");
        } else if (updated == 0) {
            Msg.ok(sender, created + " emplacement(s) réservé(s) pour <white>" + arena.getDisplayName() + "<green> :");
        } else {
            Msg.ok(sender, "Mise à jour de <white>" + updated + "<green> instance(s) et création de <white>" + created
                    + "<green> pour <white>" + arena.getDisplayName() + "<green>.");
        }
        if (untouched > 0) {
            Msg.info(sender, untouched + " instance(s) existante(s) restent inchangée(s).");
        }
        for (Plan plan : plans) {
            Msg.raw(sender, "  <gray>• <yellow>" + plan.instanceName() + " <dark_gray>→ <white>centre "
                    + plan.center().x() + ", " + plan.center().y() + ", " + plan.center().z());
        }
        Msg.info(sender, "Collage de <yellow>" + (long) width * height * length * plans.size() + "<gray> blocs, "
                + plugin.getSettings().blocksPerTick() + " blocs/tick...");

        executePlan(sender, arena, schematic, prodWorld, plans, 0, startedAt, skipAir, onAllDone);
    }

    Plan buildPlan(PlotAllocator allocator, String instanceName, DeployedInstance existing, int[] cellMin,
                   Arena arena, SpongeSchematic schematic, int pasteY) {
        int margin = plugin.getSettings().margin();
        // Ancrage sur le centre : le centre de l'arène est au milieu de la cellule
        double targetCenterX = cellMin[0] + margin + schematic.getWidth() / 2.0;
        double targetCenterY = pasteY + schematic.getHeight() / 2.0;
        double targetCenterZ = cellMin[1] + margin + schematic.getLength() / 2.0;

        // Offset entre le centre du schematic et le centre cible
        double dx = targetCenterX - arena.getCenter().x();
        double dy = targetCenterY - arena.getCenter().y();
        double dz = targetCenterZ - arena.getCenter().z();

        // Corner1 et corner2 dérivés du centre
        int[] corner1 = {(int) Math.floor(targetCenterX - schematic.getWidth() / 2.0),
                (int) Math.floor(targetCenterY - schematic.getHeight() / 2.0),
                (int) Math.floor(targetCenterZ - schematic.getLength() / 2.0)};
        int[] corner2 = {(int) Math.ceil(targetCenterX + schematic.getWidth() / 2.0) - 1,
                (int) Math.ceil(targetCenterY + schematic.getHeight() / 2.0) - 1,
                (int) Math.ceil(targetCenterZ + schematic.getLength() / 2.0) - 1};

        boolean clearBefore = existing != null
                && (!Arrays.equals(existing.getCorner1(), corner1) || !Arrays.equals(existing.getCorner2(), corner2));

        Region2D cell = allocator.cellFor(cellMin, schematic.getWidth(), schematic.getLength());
        return new Plan(instanceName, existing,
                new Point(targetCenterX, targetCenterY, targetCenterZ, 0f, 0f),
                arena.getCenter().withOffset(dx, dy, dz),
                arena.getSpawn1().withOffset(dx, dy, dz),
                arena.getSpawn2().withOffset(dx, dy, dz),
                corner1, corner2,
                new int[]{cell.minX(), cell.minZ()}, new int[]{cell.maxX(), cell.maxZ()},
                clearBefore);
    }

    void executePlan(CommandSender sender, Arena arena, SpongeSchematic schematic,
                     World prodWorld, List<Plan> plans, int index, long startedAt,
                     boolean skipAir, Runnable onAllDone) {
        Plan plan = plans.get(index);

        Runnable paste = () -> pasteInstance(sender, arena, schematic, prodWorld, plans, index, startedAt, skipAir, onAllDone);

        if (plan.clearBefore()) {
            Msg.info(sender, "L'emplacement de <yellow>" + plan.instanceName() + "<gray> a changé, effacement de l'ancienne zone...");
            fr.niware.nonbuild.work.BlockEraser eraser = new fr.niware.nonbuild.work.BlockEraser(prodWorld,
                    plan.existing().getCorner1(), plan.existing().getCorner2(),
                    plugin.getSettings().blocksPerTick(),
                    percent -> Msg.info(sender, "Effacement de l'ancienne zone de <yellow>" + plan.instanceName() + " <gray>: " + percent + "%"),
                    paste,
                    message -> {
                        deployCommand.setDeploying(false);
                        Msg.error(sender, "Erreur pendant l'effacement de l'ancienne zone de " + plan.instanceName() + " : " + message);
                        plugin.getLogger().severe("Erreur d'effacement " + plan.instanceName() + " : " + message);
                    });
            scheduleAfterPreload(prodWorld,
                    plan.existing().getCorner1()[0], plan.existing().getCorner2()[0],
                    plan.existing().getCorner1()[2], plan.existing().getCorner2()[2], eraser);
        } else {
            paste.run();
        }
    }

    void pasteInstance(CommandSender sender, Arena arena, SpongeSchematic schematic,
                       World prodWorld, List<Plan> plans, int index, long startedAt,
                       boolean skipAir, Runnable onAllDone) {
        Plan plan = plans.get(index);
        deployCommand.addPastingInstance(plan.instanceName());
        // BlockPaster colle depuis le corner1 (min), dérivé du centre
        int pasteMinX = (int) Math.floor(plan.targetCenter().x() - schematic.getWidth() / 2.0);
        int pasteMinY = (int) Math.floor(plan.targetCenter().y() - schematic.getHeight() / 2.0);
        int pasteMinZ = (int) Math.floor(plan.targetCenter().z() - schematic.getLength() / 2.0);
        BlockPaster paster = new BlockPaster(prodWorld,
                pasteMinX, pasteMinY, pasteMinZ,
                schematic,
                plugin.getSettings().blocksPerTick(),
                skipAir,
                percent -> Msg.info(sender, "Collage de <yellow>" + plan.instanceName() + " <gray>: " + percent + "%"),
                () -> {
                    deployCommand.removePastingInstance(plan.instanceName());
                    DeployedInstance instance = new DeployedInstance(
                            plan.instanceName(), arena.getSlug(), prodWorld.getName(),
                            plan.center(), plan.corner1(), plan.corner2(),
                            plan.spawn1(), plan.spawn2(),
                            plan.cellMin(), plan.cellMax(),
                            System.currentTimeMillis());
                    plugin.getDeployments().put(instance);
                    if (plan.existing() != null) {
                        Msg.ok(sender, "Instance <white>" + plan.instanceName() + " <green>mise à jour sur place.");
                    } else {
                        Msg.ok(sender, "Instance <white>" + plan.instanceName() + " <green>déployée et enregistrée dans deployments.yml.");
                    }

                    if (index + 1 < plans.size()) {
                        executePlan(sender, arena, schematic, prodWorld, plans, index + 1, startedAt, skipAir, onAllDone);
                    } else {
                        double seconds = (System.currentTimeMillis() - startedAt) / 1000.0;
                        Msg.ok(sender, "Déploiement terminé : " + plans.size() + " instance(s) de <white>"
                                + arena.getDisplayName() + "<green> en " + String.format(java.util.Locale.ROOT, "%.1f", seconds) + " s.");
                        if (onAllDone != null) {
                            onAllDone.run();
                        } else {
                            deployCommand.setDeploying(false);
                        }
                    }
                },
                message -> {
                    deployCommand.removePastingInstance(plan.instanceName());
                    deployCommand.setDeploying(false);
                    Msg.error(sender, "Erreur pendant le collage de " + plan.instanceName() + " : " + message);
                    plugin.getLogger().severe("Erreur de collage " + plan.instanceName() + " : " + message);
                });
        scheduleAfterPreload(prodWorld, pasteMinX,
                pasteMinX + schematic.getWidth() - 1,
                pasteMinZ, pasteMinZ + schematic.getLength() - 1, paster);
    }

    /**
     * Planifie la tâche de blocs une fois les chunks de la région préchargés :
     * sans cela le premier getBlockAt d'un chunk froid déclenche une génération
     * synchrone sur le fil principal (freeze).
     */
    void scheduleAfterPreload(World world, int minX, int maxX, int minZ, int maxZ,
                              BukkitRunnable task) {
        ChunkPreloader.preload(plugin, world, minX, maxX, minZ, maxZ,
                () -> task.runTaskTimer(plugin, 1L, 1L));
    }
}
