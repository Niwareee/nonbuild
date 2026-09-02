# AGENTS.md — NonBuild

## Rôle et vue d'ensemble

**NonBuild** est un plugin Paper (Java) qui gère le **pipeline des arènes** du serveur Minecraft nontia :

```
monde de build                          monde de production
┌─────────────────┐   /build save    ┌──────────────────────┐
│ le buildeur     │ ───────────────► │ N instances collées  │
│ construit, pose │  YAML + .schem   │ + deployments.yml    │
│ 5 points        │                  │  (lu par le plugin   │
└─────────────────┘   /deploy <n>    │   practice nongame)  │
                     ───────────────►└──────────────────────┘
```

1. Le buildeur construit dans le **monde de build** et déclare son arène avec 5 points (`/build addarena` → corners/spawns/centre → `/build save`).
2. `/build save` **capture les blocs** (budget par tick), écrit `arenas/<slug>.yml` + `schematics/<slug>.schem` (format Sponge v2 produit par un **codec NBT maison**).
3. `/deploy <arène> <n>` **alloue automatiquement** l'espace (spirale alignée chunks hors de la zone du spawn), colle physiquement les blocs dans le **monde de production** (budget par tick) et enregistre chaque instance dans `deployments.yml`.
4. `/deploy rebuild` **remet la production à neuf** : suppression du monde, recréation en void, collage du spawn (`spawn.schem`, format WorldEdit lu par le codec maison) en (0, 90, 0), puis redéploiement de toutes les instances qui étaient dans `deployments.yml`.
5. Le plugin **nongame** (practice) consomme `deployments.yml` pour placer ses jeux — contrat détaillé dans **`../nongame/AGENTS.md`** (section « Intégration des maps NonBuild »). Les deux docs doivent rester synchrones si le format évolue.

## Règles de projet NON NÉGOCIABLES (choix utilisateur validés)

Ces décisions viennent du propriétaire du projet ; ne pas les contourner sans lui redemander :

- **Zéro dépendance runtime** : pas de MySQL/HikariCP (supprimés en août 2026), pas de WorldEdit/FAWE (le codec NBT et le format .schem sont internes). Le plugin ne dépend que de l'API Paper fournie.
- **Stockage 100 % YAML** dans `plugins/NonBuild/`. Le contrat inter-plugin est le fichier, pas une API Java, pas de base de données.
- **Placement en spirale** autour de l'origine (cellules alignées chunks + marge) — l'utilisateur a explicitement validé ce choix par rapport à une grille d'axes fixes à 1000 blocs. Ne pas « simplifier » vers une grille fixe.
- **Y de collage = `placement.paste-y`** de la config ; le Y du build n'est PAS conservé au deploy.
- **Aucun préfixe** `[NonBuild]` dans les messages chat (`Msg` envoie du texte coloré brut).
- **Aucun système de confirmation** dans les commandes (`[confirm]` retirés partout sur demande : « les erreurs sont facilement rattrapables »). Les seules gardes tolérées sont les gardes de **cohérence** (ex. `/build delete` refuse tant que des instances sont déployées ; un deploy/remove est refusé pendant qu'un déploiement tourne).
- **Sémantiques de déploiement** : `/deploy <arène> <n>` = les n premières instances sont **mises à jour sur place** (même nom, même cellule si l'arène tient toujours), le manque est créé, le surplus est laissé intact. Jamais de refus ni de suppression implicite.
- **Messages côté utilisateur en français** ; identificateurs de code en anglais ; commentaires rares (uniquement le « pourquoi » non évident).

## Build, commandes, environnement

- Gradle (wrapper 9.3) + plugin `java` + `jacoco`. **Toolchain Java 25**.
- Dépendance cible : `io.papermc.paper:paper-api:26.2.build.+` en `compileOnly` (version dynamique suivant le dernier build stable 26.2 ; Paper est passé à la **numérotation année-drop** en 2026 : `26.2.build.NNN-stable`, plus de `-R0.1-SNAPSHOT`). `plugin.yml` : `api-version: '26.2'`.
- Dépendances de test : JUnit 5 (BOM 5.10.2), Mockito 5.23.0 (mock-maker inline par défaut : peut mocker `JavaPlugin`, méthodes `final`, etc.).

```bash
./gradlew build          # compile + 203 tests + jacocoTestReport + jar (le jar part DIRECTEMENT sur le serveur)
./gradlew test           # tests seuls (up-to-date si rien n'a changé — normal, Gradle met en cache)
./gradlew cleanTest test # forcer le relancement des tests
./gradlew jacocoTestReport
```

- Sortie du jar : `tasks.named('jar') { destinationDirectory = ... }` pointe vers **`/home/emeric/Documents/sftp/minecraft-server/plugins/`** (mont sftp du serveur distant). ⚠️ **Ne jamais laisser Gradle écraser le jar pendant que le serveur tourne** : Paper charge les classes à la demande ; un jar remplacé à chaud produit des `NoClassDefFoundError` sur les classes non encore instanciées (bug réel rencontré : `EditSession` au premier `/build addarena`). Toujours : arrêt du serveur → copie du jar → démarrage.
- Rapport de couverture : `build/reports/jacoco/test/html/index.html` (XML pour scripts). État actuel : **~94 % lignes / ~82 % branches / 100 % classes**.

## Architecture (packages sous `fr.niware.nonbuild`)

```
├── NonBuild.java            Point d'entrée : onEnable charge config + storages, branche
│                            /build et /deploy (executor + tab), enregistre SessionListener.
├── Settings.java            Lecture typée de config.yml (valeurs par défaut + plancher à 1000
│                            pour les budgets de blocs/tick).
├── Msg.java                 Envoi de messages chat §-colorés, SANS préfixe. 5 niveaux :
│                            info/ok/warn/error/raw.
├── command/
│   ├── BuildCommand.java    /build — TabExecutor. Gère la session d'édition (voir edit/).
│   ├── DeployCommand.java   /deploy — TabExecutor. Orchestration deploy/list/map/tp/remove/rebuild.
│   └── Args.java            Reparse les args en gérant les guillemets :
│                            ["\"nom","de","l'arène\"","3"] → ["nom de l'arène","3"].
├── edit/
│   ├── EditSession.java     Session en mémoire (non persistée) : 5 points + slug + displayName
│                            + monde + mode de jeu précédent. missingPoints() = checklist.
│   ├── SessionManager.java  Map UUID → session (1 session par joueur).
│   └── SessionListener.java onQuit : restaure le mode de jeu ; onJoin : le réapplique si la
│                            session est encore ouverte. Logique extraite dans handleQuit/
│                            handleJoin (package-private) POUR ÊTRE TESTABLE sans événements.
├── model/
│   ├── Arena.java           Définition d'une arène côté build : corners (blocs), center,
│                            spawn1/2 (Point précis), min/max normalisés, volume(), contains().
│   ├── DeployedInstance.java Une instance en prod : center/corners/spawns ABSOLUS + cell
│                            (emprise avec marge) + world + deployed-at (epoch ms).
│   └── Point.java           Record (x,y,z,yaw,pitch) + withOffset() + toLocation().
├── placement/
│   ├── PlotAllocator.java   Cœur du placement (voir section algo).
│   ├── Region2D.java        Rectangle XZ inclusif + intersects().
│   └── DeploymentMap.java   Carte ASCII épurée de /deploy map (classe PURE, testable, sans
│                            Bukkit) : contour ░ zone protégée, + spawn, bordure · = cellule
│                            (arène+marge), LETTRES = volume réellement collé (corner1/2,
│                            jamais invisible même arène sous-pixel), stats (surface collée,
│                            % d'emprise), échelle auto alignée 16.
├── schematic/
│   ├── Nbt.java             Codec NBT minimal maison (compound/list/primitives/tableaux,
│   │                        gzip). Représentation Java : Map<String,Object>, List<Object>.
│   ├── BlockEntityIO.java   Capture/application des block entities via l'API typée Paper :
│   │                        pancartes (texte des 2 faces), crânes (profil/skin, y compris
│   │                        nom seul non résolu), bannières (motifs), spawners, campfires,
│   │                        jukebox, conteneurs (items via ItemStack#serializeAsBytes).
│   │                        Entrées au format Sponge (Id + Pos + données) ; application
│   │                        tolérante (une entrée illisible n'interrompt jamais le collage).
│   └── SpongeSchematic.java Format schematic Sponge v2 (.schem), lisible/produit par
│                            WorldEdit : palette de BlockState + BlockData en varints,
│                            ordre index = (y*L + z)*W + x. `Offset` lu à la lecture
│                            (collage fidèle à //paste ; écrit [0,0,0] en interne).
│                            Block entities portées par la clé BlockEntities (capture
│                            maison + lecture des .schem WorldEdit v2/v3, legacy
│                            SkullOwner accepté). Pas de biomes.
├── storage/
│   ├── ArenaStorage.java    arenas/<slug>.yml + chemins des .schem + chargement au start.
│   │                        Statique : slugify() (NFD, accents retirés, [a-z0-9_-], 40 max).
│   └── DeploymentStorage.java deployments.yml ( Maps LinkedHashMap → ordre d'insertion =
                            getdown-1, -2, ... ) ; put/remove sauvegardent immédiatement.
├── world/
│   └── VoidChunkGenerator.java Générateur de monde vide pour /deploy rebuild : sous-classe
│                            nue de ChunkGenerator (les défauts Paper 26.2 ne génèrent rien :
│                            generateNoise no-op, tous les shouldGenerate* à false).
└── work/                    Tâches BukkitRunnable « budget par tick » (jamais un gros freeze) :
    ├── BlockCapture.java    Lecture monde → palette + indices → SpongeSchematic.
    ├── BlockPaster.java     Schematic → monde (Block#setBlockData(data, false), physique off).
    │                        Budget = poses RÉELLES par tick ; mode skipAir (réservé zones
    │                        fraîchement vides : spawn et arènes du rebuild) avec plafond
    │                        de balayage 1 M cases/tick pour ne jamais freeze.
    └── BlockEraser.java     Remplissage AIR d'une région (remove + nettoyage d'ancienne zone).
```

## Commandes publiques (plugin.yml + tab)

Permissions `nonbuild.build` et `nonbuild.deploy`, `default: op`. Console autorisée pour `/deploy` (sauf `tp`) ; les sous-commandes `/build` exigeant un joueur renvoient « réservée aux joueurs ».

| Commande                                                                 | Effet                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| ------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `/build addarena "Nom"`                                                  | Ouvre la session (slug = nom normalisé), passe en creative si `edit.set-creative`. Le joueur doit être dans le monde `worlds.build`.                                                                                                                                                                                                                                                                                                                                                        |
| `/build setcorner1`, `setcorner2`, `setspawn1`, `setspawn2`, `setcenter` | Posent le point à la **position du joueur**, remis **d'équerre** automatiquement : x/z au centre du bloc, yaw/pitch arrondis au multiple de 90° le plus proche (les points sont posés à la main, jamais parfaitement alignés). Ordre guidé : corner1 → corner2 → spawn1 → spawn2 → center → save.                                                                                                                                                                                           |
| `/build status` / `cancel`                                               | Checklist / abandon (restaure le mode de jeu).                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `/build save`                                                            | Valide (5 points, volume ≤ `limits.max-volume`, points dans le cuboïde) puis capture → `arenas/<slug>.yml` + `schematics/<slug>.schem`. Session refermée seulement si TOUT est écrit.                                                                                                                                                                                                                                                                                                       |
| `/build edit <arène>` / `info` / `list` / `delete <arène>`               | Recharge en édition / détails / liste / suppression (YAML+schematic ; refusée si des instances sont déployées).                                                                                                                                                                                                                                                                                                                                                                             |
| `/build tp <arène>`                                                      | Téléporte au **centre de l'arène dans le monde de build** (miroir de `/deploy tp` côté production) : précharge les 9 chunks autour (3×3) en async puis téléporte. Réservée aux joueurs.                                                                                                                                                                                                                                                                                                     |
| `/deploy "<arène>" <n>`                                                  | Déploiement/mise à jour (1..128). Voir sémantique + pipeline plus bas.                                                                                                                                                                                                                                                                                                                                                                                                                      |
| `/deploy list`                                                           | Instances enregistrées.                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| `/deploy map`                                                            | Carte ASCII + stats.                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| `/deploy tp <instance>`                                                  | Précharge **en async** les 9 chunks autour du centre (3×3) puis téléporte le joueur une fois chargés — évite la génération synchrone (freeze) et la chute dans le vide à la première arrivée. Si le joueur se déconnecte pendant le chargement, le tp est annulé ; un échec de chargement de chunk ne bloque pas le tp.                                                                                                                                                                     |
| `/deploy remove <instance>` ou `<arène>`                                 | Effacement physique des blocs (air) puis retrait du registre. Un nom d'arène = toutes ses instances. Sans confirmation.                                                                                                                                                                                                                                                                                                                                                                     |
| `/deploy rebuild` (ou `--rebuild`)                                       | Monde de production recréé à neuf : joueurs évacués vers le monde de build, unload + suppression du dossier, recréation **void** (seed 0, `VoidChunkGenerator`), spawn du monde en (0.5, 90, 0.5), collage de `spawn.schem` en (0, 90, 0), `deployments.yml` vidé puis toutes les instances qui y figuraient redéployées (comptes par arène conservés, numéros repartent de 1). Fonctionne aussi registre vide (monde + spawn) ou monde prod absent (mode récupération). Sans confirmation. |

## Config de référence (`config.yml`, `saveDefaultConfig`)

```yaml
worlds:
  build: 'build' # monde où les buildeurs construisent (validé à addarena)
  prod: 'world' # monde de collage (doit être chargé au deploy)
placement:
  spawn-protection-radius: 512 # demi-côté du carré interdit autour de (0,0)
  margin: 32 # marge PAR CELLULE : gap réel entre 2 arènes = 2×margin
  paste-y: 60 # Y absolu du coin bas de chaque collage
pasting:
  blocks-per-tick: 20000 # collage/effacement (plancher 1000)
  capture-blocks-per-tick: 50000 # capture au save (plancher 1000)
limits:
  max-volume: 4000000 # blocs (≈ 200×100×200)
edit:
  set-creative: true
```

⚠️ La config live du serveur (`plugins/NonBuild/config.yml`) peut diverger du fichier du repo : c'est le serveur qui fait foi (ex. marge actuellement à 256 → cellules énormes et arènes très espacées).

## Format des données (contrat avec nongame)

### `arenas/<slug>.yml` (définition côté build — interne à NonBuild, lisible pour debug)

```yaml
slug: getdown
display-name: 'Getdown'
world: build
saved-at: 1756576800000
corner1: { x: -32, y: -61, z: -26 } # blocs, normalisés min/max
corner2: { x: -25, y: -56, z: -17 }
center: { x: -27.5, y: -58.0, z: -21.5, yaw: 179.9, pitch: 4.7 }
spawn1: { x, y, z, yaw, pitch } # position précise du joueur au moment du set
spawn2: { x, y, z, yaw, pitch }
size: { x: 8, y: 6, z: 10 }
volume: 480
```

### `deployments.yml` (contrat public — **fiche d'identité de l'API inter-plugins**)

```yaml
instances:
  <slug>-<N>: # N = max(existant)+1 par arène → TROUS possibles après
    arena: <slug> # remove ; ne jamais supposer la continuité
    world: prod
    deployed-at: <epoch-ms> # bump à chaque re-déploiement (signal de fraîcheur)
    center: { x, y, z, yaw, pitch } # ancre absolue (double) ; spawns/center = positions
    spawn1: { x, y, z, yaw, pitch } # précises avec orientation du buildeur
    spawn2: { x, y, z, yaw, pitch }
    corner1: { x, y, z } # volume COLLÉ, blocs entiers inclusifs
    corner2: { x, y, z }
    cell: { min-x, min-z, max-x, max-z } # emprise (arène+marge), garantit zéro chevauchement
```

- Écriture par `YamlConfiguration.save()` : **non atomique**. Le consommateur (nongame) doit tolérer une lecture partielle (retry), ne jamais crasher sur une entrée invalide.
- Évolution du format : **additive uniquement**, à documenter ici **et** dans `../nongame/AGENTS.md`, avec tests des deux côtés.

### `schematics/<slug>.schem`

Sponge v2 gzip NBT : `Version=2`, `Width/Height/Length` (shorts), `Offset=[0,0,0]`, `BlockStatePalette` (id→état bloc), `BlockData` (varints, ordre x rapide → z → y), `BlockEntities` (optionnel, entrées `Id` + `Pos` + données, produites par `BlockEntityIO` à la capture et réappliquées au collage — pancartes, têtes, bannières, spawners, conteneurs…). Compatible WorldEdit en lecture. **Pas de biomes** (biome du monde de destination).

### `spawn.schem` (racine du dossier du plugin)

Spawn du monde de production pour `/deploy rebuild` : `.schem` **WorldEdit**, lu par le même codec maison — aucune dépendance ajoutée. Deux layouts supportés à la lecture : **Sponge v2** (clés à la racine, `BlockStatePalette` id→état) et **Sponge v3** (WorldEdit 2.15+ : tout est enveloppé dans un compound `Schematic`, palette `Blocks/Palette` état→id, `Blocks/Data` en varints identiques à v2). Les tags superflus (`Metadata`, `DataVersion`…) sont parsés et ignorés ; les `BlockEntities` (données imbriquées sous `Data` en v3, normalisées en forme plate) sont réappliquées au collage comme celles des arènes. `Offset` est respecté : le coin min est collé en `(0, 90, 0) + Offset`, soit la sémantique exacte d'un `//paste` debout en (0, 90, 0) (la position de copie du buildeur devient le spawn du monde). Le contrôle de hauteur pré-destruction utilise `90 + Offset.y`, pas `90` seul.

Concrètement (spawn réel du serveur) : 469×335×346 ≈ 54 M de cases pour 5,9 M de blocs pleins — lecture ~0,8 s (pics heap ~300 Mo), collage ~15-30 s grâce à skipAir (seuls les blocs pleins sont posés), emprise XZ entièrement dans le rayon protégé de 512. Les block entities (pancartes, têtes, coffres…) sont appliquées après les blocs, par lots de 1000/tick (`BlockPaster.applyBlockEntities`) ; celles non reconnues par `BlockEntityIO` tombent en état par défaut.

## Algorithme de placement (`PlotAllocator`)

- Cellule = `taille arène + 2×margin`, coin min **aligné multiple de 16** (chunks).
- Recherche en **spirale carrée** anneau par anneau (chunk coords, périmètre 8r cases), du centre vers l'extérieur ; premier emplacement libre retenu → placement **compact, déterministe, sans trou** ; limite `MAX_RING=12500` (≈ 200 000 blocs de rayon) → null si jamais d'espace (déclenche « Espace insuffisant »).
- Un candidat est rejeté s'il intersecte le carré protégé `[-r, r]²` du spawn **ou** une cellule déjà occupée.
- Les cellules occupées proviennent de `deployments.yml` (rejouable après restart) + réservations de la vague en cours.
- L'arène est collée à `cellMin + margin` ; si un re-déploiement change la région (paste-y différent, arène agrandie hors de sa cellule → nouvelle cellule), **l'ancienne zone est effacée avant le nouveau collage**.

## Pipeline d'exécution (threads)

- **Main thread** : lecture/écriture de blocs (capture, paster, eraser), toujours via `BukkitRunnable` à **budget de blocs par tick** — config l'ajuste, jamais de loop monolithique.
- **Async** : IO fichiers (écriture .schem, lecture .schem au deploy) via `runTaskAsynchronously`, puis retour `runTask` pour finir sur le main thread.
- Instances séquentielles : le paster de l'instance N finit → on persiste son entrée YAML → on enchaîne N+1. Un seul déploiement à la fois (flag `deploying`, garde sur deploy ET remove).
- **Rebuild** (`/deploy rebuild`) : validations sync (arènes du registre connues, fichiers présents, joueurs évacuables) → **préchargement async de TOUTES les schematics** (spawn + chaque arène ; une illisible = refus avant toute destruction) → [main] contrôle hauteur du spawn, évacuation des joueurs, `unloadWorld(save=false)` (`getWorldFolder()` capturé AVANT l'unload) → [async] suppression récursive du dossier (IO long jamais sur le main thread) → [main, garde `isTickingWorlds`] `createWorld` void (`WorldCreator` + `VoidChunkGenerator` + `generateStructures(false)` + seed 0), spawn du monde (0.5, 90, 0.5) → `configureProductionWorld` : le level.dat part déjà réglé (temps figé 6000, difficulté normale, `random_tick_speed` 0, pas de mobs naturels, de-risk weather/cycle, `mob_griefing` true… — clés String snake_case, `GameRules` étant inutilisable sous serveur mocké ; le `NonWorld` côté practice n'applique plus ces réglages) → collage du spawn [main, budget ticks] → registre vidé → chaque arène redéployée via le pipeline normal avec sa schematic préchargée, en séquentiel. L'enchaînement des arènes passe par un `Runnable onAllDone` nullable dans `executePlan`/`pasteInstance` (null = comportement classique). Toute erreur en cours laisse le système dans un état cohérent (le registre reflète exactement ce qui est collé).
- **Collage sans air** (`skipAir=true`, rebuild uniquement) : le monde vient d'être recréé void, les blocs d'air du schematic ne sont pas écrits et le budget `blocks-per-tick` ne compte que les poses → spawn réel (54,4 M de cases, 5,9 M pleines) collé en ~300 ticks ≈ 15-30 s au lieu de plusieurs minutes. Le `/deploy` classique garde la sémantique « tout le volume est écrasé, air compris » (piège n°3) : indispensable pour effacer les vestiges d'une version antérieure lors d'une mise à jour sur place.
- **Paper 26.2** : `unloadWorld`/`createWorld` lèvent `IllegalStateException` pendant le tick des mondes → garde `Bukkit.isTickingWorlds()` avec report d'1 tick dans `deleteAndRecreateWorld`.
- `Block#setBlockData(data, false)` (physics off) : l'overload `World#setBlockData(int,int,int,BlockData,boolean)` **a été retiré dans Paper 26.2** — ne pas le réutiliser.

## Infrastructures de test (`src/test/java`)

- **Philosophie (choix utilisateur)** : les tests vérifient le **moteur** — algorithmes, état, fichiers, interactions monde — **jamais la formulation des messages chat**. Un changement de wording ne doit jamais casser la suite ; une régression de comportement oui. En pratique : on assert des sessions créées/détruites, des fichiers écrits/absents, des `setBlockData` comptés, des tâches planifiées ou non (`pollTimerTask`), des modes de jeu, des positions de téléportation — pas des `contains("message")`.
- **203 tests**, ~94 % lignes / ~82 % branches / **100 % classes**. Non couvert volontairement : branches purement d'affichage (usage/erreurs textuelles, aide, checklist), gardes défensives inatteignables (« Espace insuffisant », `default` NBT, constructeur réel de `NonBuild`) et chemins d'échec critiques du rebuild (unload refusé à tort, suppression dossier impossible, `createWorld` null). Ne pas « remonter la couverture » avec des tests de wording.
- **`testutil/BukkitServerFixture`** : installe UNE fausse instance de `Server` dans le statique `Bukkit` (une seule fois par JVM, `Bukkit.setServer` est définitif) :
  - `runTask` / `runTaskAsynchronously` → **exécution inline** dans le thread test ;
  - `runTaskTimer` → le Runnable est **capturé (FIFO)** dans une file, récupéré par `pollTimerTask()` que le test fait avancer manuellement `run()` par `run()` (les tâches appelent `cancel()` en finissant : no-op sur le mock) ;
  - `createBlockData(String)` et `createBlockData(Material)` à stubber par test (le serveur mock est partagé → toujours re-stubber dans le `@BeforeEach`) ;
  - `getUnsafe()` stubbé (`getMainLevelName()` factice) : le constructeur `WorldCreator(name)` le consulte pour dériver la clé du monde ;
  - le mock serveur **cumule les invocations sur toute la JVM** : un `verify(server, never())` doit être précédé de `clearInvocations(Bukkit.getServer())` dans le `@BeforeEach` (pattern `DeployCommandTest`).
- **`testutil/TestServerBuildInfo`** + `src/test/resources/META-INF/services/io.papermc.paper.ServerBuildInfo` : sans ce provider ServiceLoader, `Bukkit.setServer` plante (`getVersionMessage` → `ServerBuildInfo.buildInfo()` → NoSuchElementException).
- Patterns : `@TempDir` pour le `getDataFolder()`, `mock(JavaPlugin.class)` pour les storages, `mock(Player.class)`/`mock(CommandSender.class)` avec `doAnswer` pour capturer les messages (assertions par `contains` sur le texte, **attention aux codes § intercalés** : préférer chercher « mise à jour » à « 3 instance(s) »), mock `NonBuild` pour les commandes (storages RÉELS en dossier temp).
- Les commandes saines en test : piloter `onCommand(...)` puis `BukkitServerFixture.pollTimerTask().run()` pour déclencher capture/paste/erase.
- **Gardes défensives inatteignables** (non couvertes volontairement, ~12 lignes) : « Espace insuffisant » des allocateurs, `default` de `writePayload` NBT, constructeur réel de `NonBuild`, branches `default` de switches. Ne pas tordre les tests pour elles.

## Pièges connus (déjà rencontrés ou structurels)

1. **Jar écrasé serveur en cours** → `NoClassDefFoundError` sur les classes chargées à la demande. Arrêt complet obligatoire après chaque build.
2. **Noms d'instance à trous** : `getdown-2` peut exister sans `-1` (remove). La factory nongame itère sur les clés, jamais sur la séquence.
3. **Le collage écrase tout le volume** (air compris) : le terrain naturel sous le cuboïde est perdu ; la marge évite les collisions entre arènes, pas les vestiges.
4. **`/deploy remove` laisse de l'air** (ne restaure pas le terrain d'origine).
5. Ne pas déployer/retraiter une map **pendant qu'une game l'occupe** (côté nongame) : consigne ops, pas de garde technique côté NonBuild (volontaire).
6. `contains()` d'Arena accepte la bordure haute `[max, max+1]` (point posé au bord d'un bloc).
7. Session d'édition **non persistée** : un restart serveur perd les points non sauvegardés (volontaire, simple).
8. `slugify` borne à 40 caractères et retire le tiret de troncature ; un nom entièrement non-alphanumérique → slug vide → refus.
9. `YamlConfiguration.loadConfiguration` avale les YAML malformés (retourne vide) : les fichiers corrompus sont ignorés au profit d'un warning.
10. **`/deploy rebuild` supprime physiquement le dossier du monde** : tout ce qui n'est pas recollé (spawn + instances du registre) est perdu. Les numéros d'instance repartent de 1 (registre vidé), et nongame ne relit `deployments.yml` qu'au démarrage → **restart serveur obligatoire après un rebuild**.
11. **`.schem` antérieurs au 31 août 2026 sans block entities** : la capture des block entities (têtes, pancartes…) a été ajoutée le 31 août 2026 ; les arènes sauvegardées avant ne contiendront jamais leurs têtes, quel que soit le jar. Re-sauvegarder l'arène (`/build edit` → `/build save`) puis `/deploy <arène> <n>` pour mettre à jour les instances.

## Recettes d'extension

- **Nouveau champ d'instance visible par nongame** : `model/DeployedInstance` + `DeploymentStorage` (save + parse) + tests `DeploymentStorageTest` + `ArenaManager`/`DeploymentReader` côté nongame + tableau du contrat ici ET dans `../nongame/AGENTS.md`. Toujours additif, tolérant à la lecture.
- **Nouveau sous-commande /deploy** : dispatch dans `onCommand`, gestion dans une méthode dédiée, `sendHelp`, `onTabComplete`, plugin.yml `usage`, tests de flux complet dans `DeployCommandTest`.
- **Nouveau type de placement** (herbe, clusters par mode…) : passer par `PlotAllocator` (pur) + ses tests d'abord, Brancher ensuite dans `startDeployment`.
- **Nouvelles données de debug** : préférer une ligne de plus dans `/deploy map` (`DeploymentMap`, pur + testé) plutôt qu'une nouvelle commande.
- **Avant tout push vers le serveur** : `./gradlew build` (tests + jacoco + jar sur le mont sftp) **serveur arrêté**, puis démarrer.

## Sources externes

- Documentation de la nouvelle versionisation Paper et les artefacts `paper-api` : https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/ (le projet suit le dernier build stable via `26.2.build.+`).
- Spécification Sponge schematic v2 (format BlockData varint + BlockStatePalette).
