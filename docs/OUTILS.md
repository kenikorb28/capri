# Les outils à installer, pas à pas

**En bref :** il te faut ton compte GitHub (**kenikorb28**) et **Git pour Windows**. C'est tout. GitHub compile Capri Desktop (l'EXE) et Capri Android (l'APK) sur ses propres machines. Android Studio reste possible si tu préfères compiler toi-même l'APK.

---

## 1. Installer Git pour Windows (5 min)

1. Va sur **https://git-scm.com/download/win** et télécharge **Git for Windows (64-bit)**.
2. Installe-le en laissant **toutes les options par défaut** (bouton *Next* jusqu'au bout).
3. Ouvre **PowerShell** (touche Windows, tape « PowerShell ») et vérifie :
   ```powershell
   git --version
   ```
   Tu dois voir quelque chose comme `git version 2.5x`.
4. Dis à Git qui signe tes changements. Prends l'adresse **noreply** que GitHub te donne, pour que ton e-mail ne soit jamais public (sur GitHub : photo en haut à droite > *Settings* > *Emails* > coche *Keep my email addresses private* et copie l'adresse `…@users.noreply.github.com`) :
   ```powershell
   git config --global user.name "kenikorb28"
   git config --global user.email "COLLE-ICI-TON-ADRESSE@users.noreply.github.com"
   ```

## 2. Créer ton dépôt sur GitHub (2 min)

1. Va sur **https://github.com/new**.
2. *Repository name* : `capri`. Choisis **Private** (conseillé : le code reste à toi).
3. Ne coche **rien** d'autre (pas de README, pas de .gitignore, pas de licence) puis *Create repository*.

## 3. Envoyer le code sur GitHub (5 min)

1. Ouvre PowerShell **normalement** (touche Windows, tape « PowerShell », un simple clic, **pas** « Exécuter en tant qu'administrateur »). L'invite doit montrer `PS C:\Users\<toi>>`, pas `C:\WINDOWS\System32`.
2. Colle ce bloc. Il trouve `capri-ecosysteme-2.0.0.zip` (dans Téléchargements, Documents ou le Bureau), le décompresse dans tes **vrais** Documents (même s'ils sont dans OneDrive) et entre dans le dossier `capri`. La décompression prend une à deux minutes.
   ```powershell
   $docs = [Environment]::GetFolderPath('MyDocuments')
   $zip = Get-ChildItem "$HOME\Downloads", $docs, ([Environment]::GetFolderPath('Desktop')) -Filter "capri-ecosysteme*.zip" -ErrorAction SilentlyContinue | Select-Object -First 1
   if ($zip) { Expand-Archive -LiteralPath $zip.FullName -DestinationPath $docs -Force; Set-Location (Join-Path $docs 'capri'); git config core.filemode false; git config core.ignorecase true; git status } else { "Zip introuvable : cherche où il a été enregistré." }
   ```
   Tu dois voir `On branch development` et `nothing to commit, working tree clean`. Le dossier `capri` contient un dossier `.git` : c'est tout l'historique, ne le supprime pas.
3. Vérifie que **https://github.com/kenikorb28/capri** existe (étape 2), puis, toujours dans le dossier `capri` :
   ```powershell
   git remote add origin https://github.com/kenikorb28/capri.git
   git push -u origin development
   git push origin stable android feature/capri-link
   git push origin --tags
   ```
4. Au premier `push`, une fenêtre **« Connect to GitHub »** s'ouvre : choisis *Sign in with your browser* et accepte. Git s'en souviendra.

`development` est la branche principale de l'écosystème (la première envoyée, donc celle que GitHub affiche par défaut). `stable` garde les versions d'origine intactes.

## 4. Laisser GitHub compiler

1. Sur la page de ton dépôt, onglet **Actions**. Deux workflows démarrent tout seuls à chaque push qui touche leur partie (`desktop/` ou `android/`, `contract/`, `VERSION`, ou leur propre fichier de workflow) :
   - **Capri Desktop** : compile `Capri.exe` et `Capri-Setup.exe` sur Windows et lance tout le test automatique de Capri (Capri Link compris). Compte 15 à 25 minutes.
   - **Capri Android** : lance les tests du moteur et compile l'APK. Compte environ 10 minutes.

   **Dépôt tout neuf :** le tout premier push ne les démarre pas toujours. L'onglet affiche alors *No workflows found* et *There are no workflow runs yet*. C'est normal : un workflow n'apparaît qu'après son premier lancement, qui se fera au push suivant touchant ses fichiers (par exemple ta première mise à jour, voir plus bas). Ensuite, tu peux aussi les relancer à la main : *Actions* > le workflow > *Run workflow*.
2. Quand une exécution est verte ✅, clique dessus : en bas, section **Artifacts**, télécharge :
   - `capri-desktop-windows` → un zip avec `Capri-Setup.exe` et `Capri.exe` ;
   - `capri-android-debug-apk` (ou `capri-android-release-apk` si tu as fait l'étape 6.3).
3. Si une exécution est rouge ❌ : ouvre-la, clique sur **⋯** en haut à droite > **Download log archive**, et envoie-moi le zip (à défaut, une capture d'écran de l'étape en rouge). Pour Desktop, télécharge aussi l'artefact `smoke-windows` : il contient le rapport complet du test (`smoke-test.txt` et éventuellement `failure.txt`). C'est probable à la toute première compilation de Capri Link côté PC (voir README) : envoie-moi ça et je corrige.

**Minutes gratuites :** ton dépôt est public depuis novembre 2026, et les Actions sont gratuites et sans limite de minutes sur un dépôt public. S'il redevenait privé : 2 000 minutes gratuites par mois, une minute Windows comptée comme deux, une minute Mac comme dix (c'est pour ça que le test Mac ne tourne que si tu coches *Aussi le test Mac*). Un dépôt public, c'est aussi du code que tout le monde peut lire : aucune clé ni mot de passe ne doit s'y trouver.

**Pour installer ou mettre à jour Capri, pas besoin des *Artifacts* :** les versions publiées se téléchargent avec les deux boutons de la page d'accueil. Publier une version : [PUBLIER.md](PUBLIER.md). Les *Artifacts* restent utiles pour essayer une compilation qui n'est pas encore publiée.

### Recevoir une mise à jour préparée par Claude (fichier `.bundle`)

*Depuis que le dépôt est relié à Claude, il envoie ses changements directement sur GitHub. Cette méthode reste un secours. Le dossier est maintenant `C:\Users\<toi>\capri`, hors de OneDrive : adapte la première ligne du bloc.*

Quand je t'envoie un fichier comme `capri-maj-1.bundle`, enregistre-le dans Téléchargements, puis colle ce bloc dans PowerShell (pas en administrateur), en remplaçant le nom du fichier si besoin :
```powershell
Set-Location (Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'capri')
$b = Get-ChildItem "$HOME\Downloads", ([Environment]::GetFolderPath('Desktop')), ([Environment]::GetFolderPath('MyDocuments')) -Filter "capri-maj-1.bundle" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($b) { git switch development; git pull $b.FullName development; git push } else { "Fichier introuvable : regarde où il a été enregistré." }
```
`git pull` doit afficher *Fast-forward*, puis `git push` envoie la mise à jour : les compilations concernées démarrent toutes seules dans *Actions*.

## 5. Installer Capri Desktop 2.0 sur le PC

1. **Sauvegarde d'abord sa mémoire** : copie le dossier `%LOCALAPPDATA%\Capri` (colle ce chemin dans la barre d'adresse de l'Explorateur) vers un autre endroit. L'installation n'y touche pas, mais une sauvegarde ne coûte rien.
2. Dézippe `capri-desktop-windows` et lance **`Capri-Setup.exe`**.
3. Windows peut afficher « Windows a protégé votre ordinateur » : l'app n'est pas signée avec un certificat payant. Clique sur *Informations complémentaires* puis *Exécuter quand même*.
4. Capri démarre avec sa mémoire habituelle (`me.md`, `memories.md`, ses journaux), plus le nouveau menu **Capri Link…**.

Les mises à jour de Capri Desktop viennent maintenant de **ton** dépôt (`kenikorb28/capri`, onglet *Releases*), plus du projet d'origine. Tant que tu n'as publié aucune « Release », elle ne propose rien. Les mises à jour signées et automatiques pour les deux appareils viendront avec l'Update Engine (phase P5).

## 6. Installer Capri Android 2.0 sur le téléphone

Android n'installe une nouvelle version **par-dessus** l'ancienne que si les deux sont signées avec **la même clé**.

### 6.1 Si tu as installé Capri Android en le compilant avec Android Studio sur ton PC

Recompile de la même façon, sur le même PC : la clé est la même, la nouvelle version remplace l'ancienne et **garde sa mémoire**.

1. Android Studio > *File* > *Open* > choisis le dossier **`capri\android`** (celui qui contient `settings.gradle.kts`).
2. Laisse la *Gradle Sync* finir (refuse toute proposition de passer au plugin Android 9.x).
3. *Build* > *Build App Bundle(s) / APK(s)* > *Build APK(s)*, puis installe `android\app\build\outputs\apk\debug\app-debug.apk` (bouton ▶ *Run* avec le téléphone branché, ou copie l'APK sur le téléphone).

Détails et problèmes fréquents : [android/docs/BUILD.md](../android/docs/BUILD.md).

### 6.2 Si tu prends l'APK compilé par GitHub

Il est signé avec une autre clé : Android refusera de l'installer par-dessus (« Application non installée »). Il faut d'abord désinstaller l'ancienne Capri Android, et ce qu'elle seule savait serait perdu. Pour ne rien perdre :

1. installe d'abord la nouvelle version par la méthode 6.1 ;
2. relie-la au PC (guide [CAPRI-LINK.md](CAPRI-LINK.md)) et laisse-la se synchroniser : sa mémoire est alors aussi sur le PC ;
3. ensuite seulement, désinstalle-la et installe l'APK de GitHub, puis refais l'appairage : Capri Link lui rend toute sa mémoire depuis le PC.

### 6.3 Conseillé : ta propre clé de signature (une seule fois, 5 min)

Avec ta clé, chaque APK compilé par GitHub s'installe par-dessus le précédent, sans rien désinstaller. Il en faudra une de toute façon pour les mises à jour automatiques (P5).

1. Crée la clé avec l'outil `keytool` fourni par Android Studio (PowerShell) :
   ```powershell
   & "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore $HOME\capri-release.jks -alias capri -keyalg RSA -keysize 4096 -validity 10000
   ```
   Il demande un mot de passe (deux fois) puis des questions sur ton nom ou ta ville : tu peux appuyer sur Entrée à chaque question, puis répondre `oui`.
   Sans Android Studio, `keytool` vient avec n'importe quel Java 17+ (par exemple Temurin : https://adoptium.net).
2. **Garde `capri-release.jks` et son mot de passe en lieu sûr** (une copie sur une clé USB ou ton cloud). Sans ce fichier, impossible de mettre l'app à jour sans la désinstaller. Ne le mets jamais dans le dossier `capri` (le dépôt l'ignore de toute façon).
3. Copie la clé sous forme de texte :
   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\capri-release.jks")) | Set-Clipboard
   ```
4. Sur GitHub : ton dépôt > *Settings* > *Secrets and variables* > *Actions* > *New repository secret*, et crée ces quatre secrets :

   | Nom | Valeur |
   |---|---|
   | `CAPRI_KEYSTORE_B64` | colle le texte copié à l'étape 3 |
   | `CAPRI_KEYSTORE_PASSWORD` | ton mot de passe |
   | `CAPRI_KEY_ALIAS` | `capri` |
   | `CAPRI_KEY_PASSWORD` | le même mot de passe |

5. Relance *Actions* > *Capri Android* > *Run workflow*. Tu obtiens l'artefact `capri-android-release-apk`, signé avec ta clé.

Le passage d'une version signée avec une autre clé à celle-ci demande **une** désinstallation, une seule fois (suis 6.2 pour ne rien perdre). Ensuite tout s'installe par-dessus.

## 7. Le pare-feu Windows et ton Wi-Fi

- La première fois que tu cliques sur **Ajouter un téléphone** dans Capri Link, Windows demande si Capri peut communiquer sur le réseau : coche **Réseaux privés** seulement, puis *Autoriser l'accès*.
- Ton Wi-Fi doit être en profil **Privé** : *Paramètres* > *Réseau et Internet* > *Wi-Fi* > ton réseau > *Type de profil réseau* > **Privé**.
- Si tu as cliqué sur *Annuler* par erreur : *Panneau de configuration* > *Système et sécurité* > *Pare-feu Windows Defender* > *Autoriser une application ou une fonctionnalité…* > *Modifier les paramètres* > coche **Privé** sur la ligne de Capri.

## 8. Facultatif : Tailscale, pour les relier hors de la maison

Avec Tailscale, ton téléphone joint ton PC même en 4G ou sur un autre Wi-Fi, toujours sans passer par un serveur de Capri.

1. Installe Tailscale sur le PC (https://tailscale.com/download) et sur le téléphone (Play Store), connectés **au même compte**.
2. Sur le PC, l'icône Tailscale indique l'adresse du PC, du type `100.x.y.z`.
3. Pour l'appairage, tape **cette adresse** dans *Réglages* > *CAPRI LINK* > *Adresse du PC* : elle marche à la maison comme dehors. Si le téléphone est déjà appairé avec l'adresse du Wi-Fi : *Oublier ce PC*, puis refais l'appairage avec l'adresse Tailscale.
4. Si ça ne se connecte pas par Tailscale, mets aussi le réseau Tailscale de Windows en profil **Privé** (même chemin qu'à l'étape 7).
