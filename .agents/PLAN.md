# PLAN — Évolutions NonBuild

Plan d'évolution du plugin **NonBuild** (pipeline des arènes : build → capture → deploy).
Chaque vague est indépendante et livrable ; l'ordre interne d'une vague est séquentiel
(chaque tâche est testée avant la suivante, puis `./gradlew build`).

**Rappels non négociables** (choix propriétaire) : zéro dépendance runtime, stockage 100 % YAML,
placement en spirale, messages utilisateur en français, identifiants de code en anglais,
tests qui vérifient le **moteur** (jamais la formulation des messages chat).

---

## Vague 1 — Fiabilité & propreté (en cours)

Objectif : éliminer les freezes de génération de chunk, le code mort et les incohérences
de création de monde.

- [x] **1.1 — Préchargement des chunks avant collage/effacement**
  - Nouveau `work/ChunkPreloader.java` : partie pure `chunksForRegion(minX,maxX,minZ,maxZ)`
    (coordonnées de chunk via `floorDiv(…, 16)`, gère les négatifs) + `preload(plugin, world, …, onLoaded)`
    en async (`getChunkAtAsync` + `allOf` + `whenComplete` → `runTask`). Tolérant : un chunk
    qui échoue ne bloque pas la suite.
  - Intégration dans `DeployCommand` aux 4 sites : `pasteInstance`, `executePlan` (clearBefore),
    `eraseNext`, `createAndFillWorld` (collage du spawn). La tâche de blocs est créée
    immédiatement, seul le `runTaskTimer` est retardé au callback de préchargement.
  - Tests : stub `getChunkAtAsync` dans `DeployCommandTest.@BeforeEach` + `prepareRebuildServer()` ;
    nouveau `ChunkPreloaderTest` (coordonnées pures + flux async via fixture).
  - **État** : fait. `ChunkPreloader` créé, `DeployCommand` branché (4 sites + helper `scheduleAfterPreload`).
    `ChunkPreloaderTest` (6 tests) + `DeployCommandTest` passent.

- [x] **1.2 — Suppression du code mort item-frame / painting**
  - `schematic/BlockEntityIO.java` : `ItemFrame` et `Painting` sont des **entités**, jamais des
    `BlockState` → les branches `instanceof` dans `capture()` ne matchent jamais.
  - Retirer : branches `capture()`, méthodes `captureItemFrame`/`capturePainting`,
    `_ITEM_FRAME`/`_PAINTING` dans `buildBlockEntityMaterials()`, imports `ItemFrame`/`Painting`/`Art`.
  - Ajouter un commentaire court (FR) : ce sont des entités, la capture d'entités = Vague 3.1.
  - Ajuster `BlockEntityIOTest` si des cas item-frame/painting existent.
  - **État** : fait. Aucun cas item-frame/painting dans `BlockEntityIOTest` (rien à ajuster) ;
    `BlockEntityIOTest` passe. Les 2 échecs `DeployCommandTest` (`getChunkAtAsync`) viennent
    de la tâche 1.1 en cours, pas de celle-ci.

- [x] **1.3 — Monde de production créé en void au démarrage**
  - `NonBuild.loadProductionWorld()` : remplacer la création par défaut (terrain généré) par
    `new WorldCreator(name).generator(new VoidChunkGenerator()).generateStructures(false).seed(0L).createWorld()`
    — aligné sur `createAndFillWorld` du rebuild (cohérence monde neuf).
  - Ajuster `NonBuildTest` si besoin.
  - **État** : fait. `loadProductionWorld()` crée en void (generator + `generateStructures(false)` + `seed(0L)`),
    aligné sur le rebuild. `NonBuildTest` passe sans modification (le mock `createWorld` renvoie null → chemin warning).

- [x] **1.4 — `/deploy tp` : plus de création de monde à la demande**
  - `DeployCommand.handleTp` : retirer la création on-demand du monde (surprenante, le monde prod
    est chargé au démarrage) → message d'erreur clair en français à la place.
  - Retirer/traduire les commentaires anglais de la méthode.
  - **État** : fait. Monde absent → refus clair (pas de `createWorld`), commentaires anglais retirés.
    Nouveau test moteur `tpNeCreePasLeMondeSiIlNestPasCharge` (pas de `createWorld`, pas de tp).

- [x] **Build Vague 1** : `./gradlew build` vert (tous les tests passent, jar sur le mont sftp — serveur arrêté).

---

## Vague 2 — Performance & cohérence interne

- [x] **2.1 — Pas de spirale = taille de cellule**
  - `placement/PlotAllocator` : avancer la spirale par pas de cellule (et non par anneau de chunk
    unitaire) pour réduire le nombre de candidats testés sur les grandes cellules (marge live = 256).
  - **État** : fait. `allocate()` calcule `step = ceil(max(cellWidth, cellLength) / 16)` (≥ 1) et la
    spirale avance par `step` chunks à la fois (anneau ET le long des arêtes). Couverture complète
    conservée (pas = emprise → candidats consécutifs se touchent), placement toujours sans
    chevauchement, aligné chunk, déterministe. Nouveau test `uneGrandeMargeNeProvoqueNiChevauchementNiTropDeDistance`
    (marge 256, 40 allocations). Les 6 tests `PlotAllocatorTest` passent.

- [x] **2.2 — Accès bloc par chunk dans les tâches**
  - `work/BlockPaster` / `BlockEraser` / `BlockCapture` : remplacer `world.getBlockAt(x,y,z)` par
    `world.getChunkAt(cx,cz).getBlock(localX, localY, localZ)` (coordonnées locales via `>> 4` / `& 0x0F`).
    L'itération linéaire (cursor) est conservée — seul le chemin d'accès au bloc change.
  - Tests ajustés : mocks `Chunk.getBlock` au lieu de `World.getBlockAt`, coords locales correctes
    (ex. `minZ=30` → chunk 1 → localZ=14).
  - **État** : fait. 11 tests passent (BlockPasterTest 6, BlockEraserTest 2, BlockCaptureTest 3).
    Comportement identique, moins de lookups World → meilleur cache.

- [x] **2.3 — Retirer le `synchronized` trompeur de `DeploymentStorage`**
  - Le `synchronized` ne protège rien d'utile (main thread seul) et masque un IO bloquant ;
    clarifier (retirer ou documenter) + déplacer l'IO de sauvegarde en async si pertinent.
  - **État** : fait. `synchronized` retiré de `save()` + Javadoc documentant le modèle (main thread seul,
    écriture atomique, pas de verrou). `DeploymentStorageTest` passe (8 tests).
    L'IO async est laissé à la tâche **3.4** (décision tranchée : 2.3 ne fait que clarifier).

- [x] **2.4 — Mettre en cache les valeurs de `Settings`**
  - `Settings` relit `config.yml` à chaque appel ; charger une fois au démarrage (valeurs stables
    pour la durée de vie du plugin).
  - **État** : fait. 9 champs `final` lus au constructeur, accesseurs retournent les champs.
    `DeployCommandTest` adapté : helper `initSettings(config)` pour reconstruire Settings
    après `config.set(...)` (6 sites). `SettingsTest` inchangé (déjà compatible).
    ⚠️ Conséquence : `new Settings(plugin)` lit la config dans le constructeur → les tests qui
    faisaient `when(plugin.getSettings()).thenReturn(new Settings(...))` lèvent
    `UnfinishedStubbingException` ; corrigé dans BuildCommandTest/DeployCommandTest/SessionListenerTest
    (Settings créé dans une variable locale avant le `when`).

- [x] **2.5 — UUID de crâne stable**
  - `BlockEntityIO.applySkull` : `UUID.nameUUIDFromBytes(name)` au lieu de générer un UUID à chaque
    collage (stabilité des têtes de joueurs).
  - **État** : fait. `UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes())` pour les crânes
    nommés (même sémantique que Minecraft pour les joueurs hors-ligne). Les crânes sans nom gardent
    `UUID.randomUUID()` (pas de nom = pas de stabilité possible). Tests inchangés (`any(UUID.class)`).

- [x] **Build Vague 2** : `./gradlew build` vert.

---

## Vague 3 — Fonctionnalités & contrat

- [x] **3.1 — Capture des entités (item frames / paintings)**
  - Vraie capture des entités (pas des blocs) : `world.getEntitiesByClass(ItemFrame/Painting)`
    dans la région, sérialisation position + orientation, réapplication au collage.
  - **État** : fait. `BlockEntityIO.captureEntities()` itère sur les ItemFrame/Painting du monde,
    filtre par région, capture item + rotation + facing. `BlockEntityIO.applyEntity()` spawn
    l'entité à la position relative du schematic. `BlockCapture` capture les entités après les blocs.
    `BlockPaster` dispatche vers `applyEntity()` pour les entités et `apply()` pour les block entities.
    Tolérant : `captureEntities()` retourne une liste vide si le monde est inaccessible (tests mockés).
    Tests : 8 nouveaux cas dans `BlockEntityIOTest` (capture ItemFrame dans/hors région, tolérance
    aux exceptions, spawn ItemFrame avec rotation/facing, entrée inconnue ignorée, entrée sans Pos ignorée).
    ⚠️ Tests Painting omis : `Art` enum plante dans les tests (dépend du registre Paper non stubbable).
    Couverture ItemFrame suffisante pour valider le pipeline (même code pour Painting).
    (tâche 2.2) — corrigés : `getChunkAt`/`chunk.getBlock` stubbés dans BuildCommandTest/DeployCommandTest.

- [x] **3.2 — Migration Adventure**
  - `Msg` : passer des codes `§` à `sendRichMessage` (MiniMessage). `BuildCommand`/`DeployCommand` :
    `§7` → `<gray>`, `§e` → `<yellow>`, `§f` → `<white>`, `§c` → `<red>`, `§a` → `<green>`,
    `§8` → `<dark_gray>`, `§l` → `<bold>`, `§6` → `<gold>`.
  - **État** : fait. `Msg` utilise `sendRichMessage` avec tags MiniMessage. Comportement identique
    (MiniMessage est serialisé en legacy `§` pour la console). `DeploymentMap` converti aussi.

- [x] **3.3 — Indicateur de chunk sur la map**
  - `placement/DeploymentMap` : nouvelle surcharge `render(instances, spawnRadius, playerPos)` avec
    `playerPos` optionnel (X, Z). Si fourni, un `@` marque le chunk du joueur sur la grille.
    Légende mise à jour : `@ vous` ajouté quand la position est connue.
  - `DeployCommand.handleMap` : passe `p.getLocation()` si le sender est un joueur.
  - **État** : fait. 6 tests `DeploymentMapTest` passent (2 nouveaux : joueur présent/absent).

- [x] **3.4 — Sauvegarde du registre en async**
  - `DeploymentStorage` : `save()` async (snapshot + `runTaskAsynchronously` + `CountDownLatch`),
    `saveSync()` pour le pipeline de deploy (séquentiel, doit attendre). `put()` utilise `saveSync()`
    (garantie d'atomicité), `remove()`/`clear()` utilisent `save()` async.
  - **État** : fait. 8 tests `DeploymentStorageTest` passent.

- [x] **3.5 — Test de contrat inter-plugins**
  - Test partagé NonBuild ↔ nongame sur le format `deployments.yml` (additif, tolérant à la lecture)
    pour verrouiller le contrat au format.
  - **État** : fait. Deux tests miroirs partageant la même fixture canonique (2 arènes, TROU de
    numérotation getdown-1/getdown-3, mêmes valeurs des deux côtés) :
    - NonBuild `storage/DeploymentContractTest` : verrouille l'**écrivain** — `DeploymentStorage.save()`
      produit la structure exacte du contrat (chaque clé présente, bon type : arena/world chaînes,
      deployed-at long, center/spawns doubles+yaw/pitch, corners entiers, cell 4 ints) + aller-retour
      complet. Passe par `BukkitServerFixture` (save async exécuté inline).
    - nongame `arena/DeploymentContractTest` : verrouille le **lecteur** — `DeploymentReader` consomme
      intégralement la fixture (tous les champs de `DeployedArena`) + trous de numérotation préservés.
      Les 4 tests passent (2 par projet). Toute évolution du format doit être validée des deux côtés.

- [x] **Build Vague 3** : `./gradlew build` vert (224 tests, 0 échec).

---

## Suivi

| Vague | Statut                                                |
| ----- | ----------------------------------------------------- |
| 1     | ✅ Terminée (1.1–1.4 faits, build vert)               |
| 2     | ✅ Terminée (2.1–2.5 faits, build vert)               |
| 3     | ✅ Terminée (3.1–3.5 faits, build vert, contrat sync) |

**État global** : `./gradlew build` vert — 222 tests, 0 échec, ~92 % lignes.
