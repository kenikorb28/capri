# Publier une version de Capri

Une **version publiée** (une *Release* GitHub) contient les deux fichiers que les boutons de la page d'accueil téléchargent :

| Fichier | Quoi |
|---|---|
| `Capri-Setup.exe` | Capri Desktop pour Windows |
| `Capri-Android.apk` | Capri Android (Android 11 ou plus) |
| `SHA256SUMS.txt` | Les sommes de contrôle des deux |

Les boutons pointent vers `releases/latest/download/…` : ils donnent donc toujours la dernière version publiée, sans rien changer dans le README.

## Ce que fait le workflow *Capri Release*

1. Il vérifie que le numéro de version est **le même partout** (`VERSION`, Desktop, port Mac/Linux, Android) et que cette version **n'est pas déjà publiée**.
2. Il compile Capri Desktop sous Windows et lance tout son test automatique, Capri Link compris.
3. Il lance les tests de Capri Android, compile l'APK et vérifie qu'il est **signé avec ta clé**.
4. Si tout est vert, il crée la Release avec les trois fichiers et les notes de version (la partie du `CHANGELOG.md` qui porte ce numéro).

Si une étape échoue, rien n'est publié.

## Publier

Chaque version publiée doit avoir un **nouveau numéro**. Capri Desktop n'installe une mise à jour que si son numéro est plus grand que le sien, et Android refuse un APK plus ancien que l'appli installée.

1. Changer le numéro partout d'un coup, depuis le dossier `capri` :
   ```
   python tools/set_version.py 2.0.2
   ```
2. Ajouter en haut de `CHANGELOG.md` une partie `## 2.0.2 — date` qui dit ce qui change. Elle devient les notes de version.
3. Envoyer ces changements sur `development`, puis poser le tag :
   ```
   git tag v2.0.2
   git push origin v2.0.2
   ```
   Le tag lance le workflow. On peut aussi le lancer à la main : *Actions* > *Capri Release* > *Run workflow*.
4. Compter une dizaine de minutes. La version apparaît dans *Releases* et les boutons la donnent aussitôt.

Tu peux aussi le demander à Claude : « publie la version suivante ».

## La clé de signature Android (une seule fois)

Android n'installe un APK **par-dessus** une appli que s'il porte la **même signature**. Les machines de GitHub fabriquent une clé jetable à chaque compilation : un APK signé ainsi ne pourrait jamais mettre à jour la Capri de ton téléphone. Il faudrait la désinstaller à chaque fois, et sa mémoire avec.

La solution : GitHub signe avec **la clé de ton PC**, celle qu'Android Studio utilise déjà (`C:\Users\<toi>\.android\debug.keystore`). Les APK de GitHub et ceux de ton PC deviennent alors interchangeables.

Pour la donner à GitHub, sous forme de *secret* (chiffré, illisible ensuite, jamais donné aux contributions venues d'un autre dépôt) :

1. Dans PowerShell, cette ligne copie la clé dans le presse-papiers et ouvre la bonne page de GitHub :
   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\.android\debug.keystore")) | Set-Clipboard; Start-Process "https://github.com/kenikorb28/capri/settings/secrets/actions/new"
   ```
2. Sur la page : dans **Name**, écris `CAPRI_DEBUG_KEYSTORE_B64`. Dans **Secret**, colle avec **Ctrl + V**. Clique sur **Add secret**.

C'est tout. À partir de là, la publication et la compilation de tous les jours signent avec ta clé.

- Empreinte SHA-256 de cette clé (publique, c'est elle qu'un téléphone compare) : `97:E3:52:16:87:6F:21:1A:9E:F3:B1:48:96:F8:B8:E2:8A:8A:C0:62:A4:84:A8:06:25:34:2F:57:F4:AE:32:74`. Chaque version publiée affiche l'empreinte de son APK dans ses notes : elle doit rester la même.
- **Garde une copie de `debug.keystore`** ailleurs que sur ton PC (clé USB, par exemple). Si ce fichier est perdu, plus aucun APK ne pourra mettre à jour une Capri déjà installée. Il n'est pas dans le dépôt et ne doit jamais y être.
- Si ton téléphone a une Capri venue d'une **autre** clé (un ancien APK téléchargé depuis les *Artifacts* de GitHub), Android annoncera un conflit de paquet. Dans ce cas, une seule fois : fais une sauvegarde chiffrée dans les réglages de Capri, désinstalle-la, installe la version publiée, puis restaure.

## Les mises à jour, côté utilisateur

- **Capri Desktop** regarde toute seule la dernière version publiée de `kenikorb28/capri`. Si elle est plus récente, elle télécharge l'installeur, vérifie sa somme dans `SHA256SUMS.txt`, puis propose *Restart to update*. On peut aussi demander : clic droit sur Capri > *Help* > *Check for updates*.
- **Capri Android** ne propose pas encore la mise à jour elle-même : on passe par le bouton de la page d'accueil. C'est la suite prévue de la phase P5.
