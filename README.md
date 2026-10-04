# CleanGangHub

Plugin Paper : multimonde pour les maps de mini-jeux, carte du monde ouverte depuis un livre
(avec nuages qui se dissipent), points de TP posés sur la carte et menu des mini-jeux.

## Build
    mvn package      ->  target/CleanGangHub-1.0.0.jar  (à mettre dans plugins/)
Adapte `paper.version` dans pom.xml à la version de ton serveur.

## Démarrage rapide
1. Copie le dossier de chaque map (poulet, blockhunt, flip7...) à côté de `world`.
2. Démarre le serveur : les mondes listés dans config.yml sont chargés automatiquement.
   (ou `/cghub world load <nom> void`, ou `/cghub world create <nom> void` pour un monde vide)
3. Va à l'endroit voulu et pose un point : `/cghub point set spawn 22 BEACON`
4. Change le nom / la description dans points.yml puis `/cghub reload`.
5. Clic droit avec le livre (ou `/carte`) -> la carte s'ouvre derrière les nuages.
