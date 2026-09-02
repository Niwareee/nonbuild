## Contexte

Les têtes des arènes ne survivent pas au déploiement. Diagnostic : la capture des block entities (dont les crânes) existe déjà (`BlockEntityIO.java`, 31 août 23:08) et est dans le jar déployé, mais l'arène `dirt` a été sauvegardée le 30 août avec l'ancien code — son `.schem` ne contient aucune entrée `BlockEntities`. La cause principale est opérationnelle (re-sauvegarde nécessaire), pas un bug.

## Étapes

1. **Durcissement `captureSkull`** (`BlockEntityIO.java:155`) : ne plus retourner `null` quand le profil a un nom mais pas de propriété `textures` (profil non résolu) — capturer le nom seul ; `applySkull` recrée déjà le profil depuis le nom et le serveur résout la skin. Tests associés dans `BlockEntityIOTest`.

2. **Mise à jour d'AGENTS.md** (sections schematic et pièges) : remplacer « Les block entities NE SONT PAS capturées » / « Pas de block entities » par la réalité — capture via `BlockEntityIO` (pancartes, crânes, bannières, spawners, conteneurs, campfires, jukebox), clé `BlockEntities` du `.schem`, limitation restante : biomes absents. Vérifier/mettre à jour `../nongame/AGENTS.md` si la même mention y figure.

3. **Tests** : `./gradlew cleanTest test` — la suite existante doit passer, nouveaux tests verts.

4. **Build & déploiement** : `./gradlew build` jar vers le mont sftp, serveur arrêté (règle du projet).

5. **Côté ops (information à l'utilisateur)** : après déploiement du nouveau jar, re-sauvegarder l'arène (`/build edit dirt` → `/build save`) puis `/deploy "dirt" <n>` pour que les têtes soient capturées et recollées — les `.schem` existants antérieurs au 31 août ne contiendront jamais les têtes.