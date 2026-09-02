package fr.niware.nonbuild.placement;

import java.util.ArrayList;
import java.util.List;

/**
 * Alloue des emplacements pour les arènes déployées dans le monde de production.
 *
 * Principe :
 * - chaque arène occupe une cellule = emprise de l'arène + marge configurable ;
 * - les cellules sont alignées sur les chunks (multiples de 16 blocs) pour des
 *   frontières propres et un coût de chunk minimal ;
 * - la recherche part de l'origine et spiralise vers l'extérieur, en sautant la
 *   zone carrée protégée autour du spawn (0,0) ;
 * - le premier emplacement libre (aucune intersection avec une cellule occupée)
 *   est retenu : le placement est compact, déterministe et sans chevauchement.
 */
public class PlotAllocator {

    private static final int CHUNK = 16;
    private static final int MAX_RING = 12500;

    private final int spawnProtectionRadius;
    private final int margin;
    private final List<Region2D> occupied = new ArrayList<>();

    public PlotAllocator(int spawnProtectionRadius, int margin) {
        this.spawnProtectionRadius = spawnProtectionRadius;
        this.margin = margin;
    }

    public void addOccupied(Region2D cell) {
        occupied.add(cell);
    }

    /**
     * Région occupée (marge incluse) par une arène posée sur la cellule cellMin.
     */
    public Region2D cellFor(int[] cellMin, int sizeX, int sizeZ) {
        return new Region2D(cellMin[0], cellMin[1],
                cellMin[0] + sizeX + 2 * margin - 1,
                cellMin[1] + sizeZ + 2 * margin - 1);
    }

    /**
     * Cherche une cellule libre pour une arène de taille sizeX * sizeZ.
     *
     * @return le coin minimum {x, z} de la cellule (aligné chunk), ou null si
     *         aucun emplacement n'a été trouvé dans la limite de recherche.
     */
    public int[] allocate(int sizeX, int sizeZ) {
        int cellWidth = sizeX + 2 * margin;
        int cellLength = sizeZ + 2 * margin;
        // Pas de la spirale en chunks : au moins l'emprise de la cellule, pour ne pas
        // re-tester des emplacements déjà couverts par la cellule précédente (gain
        // décisif avec les grandes marges, ex. 256 → cellules de ~45 chunks).
        int step = Math.max(1, (Math.max(cellWidth, cellLength) + CHUNK - 1) / CHUNK);

        for (int ring = 0; ring < MAX_RING; ring += step) {
            int[] found = searchRing(ring, cellWidth, cellLength, step);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private int[] searchRing(int ring, int cellWidth, int cellLength, int step) {
        if (ring == 0) {
            return checkCell(0, 0, cellWidth, cellLength);
        }
        for (int i = -ring; i < ring; i += step) {
            int[] result = checkCell(i * CHUNK, -ring * CHUNK, cellWidth, cellLength);
            if (result != null) return result;

            result = checkCell(ring * CHUNK, i * CHUNK, cellWidth, cellLength);
            if (result != null) return result;

            result = checkCell(-i * CHUNK, ring * CHUNK, cellWidth, cellLength);
            if (result != null) return result;

            result = checkCell(-ring * CHUNK, -i * CHUNK, cellWidth, cellLength);
            if (result != null) return result;
        }
        return null;
    }

    private int[] checkCell(int x, int z, int cellWidth, int cellLength) {
        Region2D cell = new Region2D(x, z, x + cellWidth - 1, z + cellLength - 1);
        if (intersectsProtectedZone(cell)) {
            return null;
        }
        for (Region2D other : occupied) {
            if (cell.intersects(other)) {
                return null;
            }
        }
        return new int[]{x, z};
    }

    private boolean intersectsProtectedZone(Region2D cell) {
        int r = spawnProtectionRadius;
        return cell.minX() <= r && cell.maxX() >= -r && cell.minZ() <= r && cell.maxZ() >= -r;
    }
}
