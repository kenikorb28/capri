# Changements de l'écosystème Capri

## 2.0.0 — en cours sur `development` (6 novembre 2026)

### Écosystème
- Un seul dépôt pour **Capri Desktop** (`desktop/`, depuis la 0.8.2) et **Capri Android** (`android/`, depuis la 2.0), avec le contrat commun (`contract/`), la doc (`docs/`) et les outils (`tools/`).
- **Version unique 2.0.0** pour les deux (`VERSION`, `desktop/app/AssemblyInfo.cs`, `desktop/cross/Capri.Cross.csproj`, `android/app/build.gradle.kts` avec versionCode 20000).
- Branches `stable` (originaux intacts, tags `desktop-0.8.2-origine` et `android-2.0-origine`), `development`, `android`, `feature/capri-link`.
- Workflows GitHub **déplacés à la racine** (`.github/workflows/`). Raison technique : GitHub n'exécute que les workflows à la racine d'un dépôt, ceux de `desktop/.github` et `android/.github` ne tournaient plus. *Capri Desktop* (Windows, Linux ; Mac seulement si tu coches *Aussi le test Mac* en le lançant à la main, pour économiser les minutes), *Capri Desktop Mac and Linux* (branche `mac-linux`), *Capri Android* (tests, APK debug, APK release signé si ta clé est dans les secrets ; téléchargements de Gradle gardés d'une fois sur l'autre).
- *Capri Android* : `android-actions/setup-android@v4` avec seulement `platform-tools`. Raison technique : depuis mi-septembre 2026, les machines de GitHub ne connaissent plus l'ancien paquet `tools` que la v3 demande par défaut, et le workflow échouait en quelques secondes avant même de compiler.
- Premier lancement des workflows : sur un dépôt tout neuf, le premier push ne les démarre pas toujours (*No workflows found*) ; le push suivant qui touche leurs fichiers les lance (docs/OUTILS.md, section 4).
- Docs : audit et plan [docs/AUDIT-ECOSYSTEME.md](docs/AUDIT-ECOSYSTEME.md), outils [docs/OUTILS.md](docs/OUTILS.md), guide [docs/CAPRI-LINK.md](docs/CAPRI-LINK.md).

### Capri Link v2 (nouveau) — la même Capri, la même mémoire
- Appairage par code de 8 caractères (5 min, 5 essais, un téléphone) mélangé à un échange **ECDH P-256** : écouter le Wi-Fi ne permet pas de deviner le code.
- Messages chiffrés AES-256-CBC + HMAC-SHA256, étiquette par sens, horodatage ±10 min, nonce à usage unique ; réseau privé uniquement (Wi-Fi, Tailscale) ; découverte UDP.
- Mémoire partagée : notes, souvenirs, projets, personnalité, journaux. Fusion à trois voies (oublier marche dans les deux sens), écriture refusée si le fichier a changé entre-temps, « oublie tout » propagé, contexte de l'autre appareil dans le prompt pendant 6 h.
- Contrat et vecteur de test commun : `contract/link-v2.md`, `contract/memory-v2.md`, `contract/vectors/link-v2-crypto.json`.

### Capri Desktop
- `app/Link.cs` : le serveur Capri Link, la fenêtre **Capri Link…** (téléphones, code, fichiers, journal), clés protégées par DPAPI, sauvegarde du jour avant chaque modification, vérifications dans le test automatique (vecteur + vrai appairage et vraie synchro en local).
- `Brain.cs` : verrous partagés avec Capri Link, ce que tu faisais sur le téléphone (et tes projets) dans son prompt, `memories.md` jamais réécrit s'il a changé pendant qu'elle le résumait, heure de la dernière ligne.
- `Capri.cs` : Capri Link démarre si un téléphone est appairé, menu *Capri Link…*, arrêt propre. Le test du menu court du clic droit attend maintenant cette ligne entre *Hologram* et *Help*.
- Test automatique, première compilation sur GitHub : 597 vérifications réussies, dont les trois de Capri Link (vecteur commun, ECDH P-256 sous Windows, vrai appairage et vraie synchro en local). Deux vérifications de mise en page (réglages à 150 % et 200 %) échouaient pour une raison technique : la machine Windows de GitHub a un écran virtuel de 1024×768, et Windows Forms limite une fenêtre à environ 1036 px de large, alors que les réglages en demandent 1140 à 150 % et 1520 à 200 %. Quand l'écran coupe ainsi la fenêtre (`Mainframe.CutByScreen`), ces deux vérifications ne contrôlent plus ce qui dépasse du bord, le disent dans une ligne LIMIT, et vérifient toujours les chevauchements et le texte. Sur un vrai PC assez grand, rien ne change.
- Canal de mise à jour : **ton dépôt `kenikorb28/capri`** au lieu du projet d'origine (décision n° 6), pour qu'une version d'origine ne remplace jamais l'écosystème. Les packs de voix restent téléchargés depuis le projet d'origine.

### Capri Android
- `sync/SyncHub.kt` réécrit pour Capri Link v2 : synchro automatique (après chaque changement et chaque réponse, avant de répondre si elle date, en direct bulle ouverte sauf ÉCONOMIE), PC retrouvé sur le Wi-Fi s'il change d'adresse, passage de conversation, fichiers dans les deux sens (15 Mo max).
- `sync/LinkJob.kt` : une synchro environ toutes les 15 minutes par le planificateur d'Android, sans nouvelle permission.
- Réglages : section **CAPRI LINK** (recherche du PC, code, synchro automatique, tester, oublier).
- Moteur : les notes gardent la mise en page du fichier du PC (une note Obsidian garde sa forme) ; `personality.md` voyage dans les mots du PC et un défaut intact ne remplace jamais une personnalité écrite ; `Brain.lastChatAt`. 53 tests.
- L'ancien appairage *Capri Sync v1* est effacé à la mise à jour. Raison technique : son chiffrement d'appairage (code seul) n'est pas celui de Capri Link (ECDH + code) ; un appairage suffit.

### Remplacé, gardé pour mémoire
- `android/pc-sync/` (Capri Sync v1, programme Windows séparé) : remplacé par Capri Link intégré à Capri Desktop, plus compilé ni lancé (voir son README).
