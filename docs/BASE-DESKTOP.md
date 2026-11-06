# Capri Desktop et la version de Lachy

Capri Desktop vient du projet de Lachy, [lachydotmcg/capri-desktop](https://github.com/lachydotmcg/capri-desktop) (licence PolyForm Noncommercial 1.0.0 : usage non commercial, crédit conservé). Le dossier `desktop/` est sa version, plus ce que l'écosystème y ajoute.

## Version de base

- **Aujourd'hui : v0.8.4** (commit `eeee07e`), fusionnée le 6 novembre 2026, avec ses 240 commits depuis la 0.8.2. On y trouve notamment les couleurs du Mainframe, les mods (dont les skill mods), le mode Husk, des messages d'erreur plus clairs, l'AppImage Linux et les fenêtres qui tiennent dans leur écran.
- **Avant : v0.8.2.** C'était exactement l'archive `capri-desktop-0.8.2.zip` (arbre identique, vérifié). La branche `stable` et le tag `desktop-0.8.2-origine` la gardent intacte.

## Ce que l'écosystème change dans `desktop/`

Tout le reste est identique à la version de Lachy.

- `app/Link.cs` : Capri Link (nouveau fichier).
- `app/Brain.cs` : verrous partagés avec Capri Link, ce que tu faisais sur le téléphone dans son prompt, heure de la dernière ligne.
- `app/Capri.cs` : démarrage et arrêt de Capri Link, menu *Capri Link…* et son test, vérifications de Capri Link dans le test automatique, canal de mise à jour `kenikorb28/capri`.
- `ci/build-windows.ps1` : `Link` dans la liste des fichiers.
- Version **2.0.0** : `app/AssemblyInfo.cs`, `cross/Capri.Cross.csproj`.
- Workflows : déplacés à la racine (`.github/workflows/capri-desktop.yml` et `capri-desktop-mac-linux.yml`), parce que GitHub ne lit que ceux-là. Ils sont adaptés au sous-dossier `desktop/` et reprennent les étapes de Lachy (écran 1080p, marqueur du paquet, installation et désinstallation silencieuses, AppImage).

## Laissé de côté exprès

- `quest/` : *Capri Room*, le port pour Meta Quest. C'est un projet Unity séparé (environ 270 fichiers lourds) qui ne touche ni le PC ni Android. Il reste disponible chez Lachy.
- Cinq images du README (`call.gif`, `intros.gif`, `mods.png`, `mac-desktop.png`, `mac-mainframe.png`, 8,6 Mo au total). L'application ne s'en sert pas, et `desktop/README.md` les affiche depuis le dépôt de Lachy.

## Prendre sa version suivante

Exemple avec une 0.8.5. Depuis le dossier qui contient `capri` :

```bash
git clone https://github.com/lachydotmcg/capri-desktop.git lachy      # une seule fois
cd lachy && git fetch --tags
git diff --binary v0.8.4 v0.8.5 -- . ':(exclude)quest' ':(exclude).github' > ../lachy.patch
cd ../capri && git apply -3 --directory=desktop ../lachy.patch
```

Ensuite, il faut résoudre les conflits en gardant la version 2.0.x et les ajouts listés plus haut. Il faut aussi reporter les changements de ses workflows dans `.github/workflows/`, et laisser GitHub tout compiler et tester. Claude peut le faire pour toi : c'est ce qui a été fait pour la 0.8.4.
