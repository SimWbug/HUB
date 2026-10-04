# CleanGangHub 1.1.0 — Paper 26.3

Multimonde pour les maps de mini-jeux, carte du monde ouverte depuis un livre
(avec nuages qui se dissipent), points de TP posés sur la carte et menu des mini-jeux.

## Build
Prérequis : **JDK 25** (obligatoire pour Paper 26.x) et Maven.

    mvn package      ->  target/CleanGangHub-1.1.0.jar  (à mettre dans plugins/)

La dépendance `[26.3.build,)` récupère automatiquement le dernier build de paper-api 26.3.

## Mondes sur Paper 26.x
Les mondes ne sont plus à la racine du serveur mais dans
`world/dimensions/<namespace>/<clé>/`. Le plugin utilise la clé `cleangang:<id>`.

- Nouveau monde vide : `/cghub world create poulet void`
  -> world/dimensions/cleangang/poulet/
- Importer une map existante (dossier avec level.dat) : copie-la à la racine du
  serveur sous le nom de l'id (ex. `blockhunt/`), puis `/cghub world load blockhunt void`.
  Paper la convertit, et le plugin note sa clé dans config.yml (`key:`).
  Fais une sauvegarde avant : la conversion est irréversible.

## Démarrage rapide
1. Démarre le serveur, crée/importe tes mondes (voir ci-dessus).
2. Va à l'endroit voulu et pose un point : `/cghub point set spawn 22 BEACON`
3. Change le nom / la description dans points.yml puis `/cghub reload`.
4. Clic droit avec le livre (ou `/carte`) -> la carte s'ouvre derrière les nuages.
