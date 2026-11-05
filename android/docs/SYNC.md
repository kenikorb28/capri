# CAPRI SYNC — ton PC et ton téléphone ensemble

**Capri Sync** est un petit programme Windows séparé (`pc-sync/`). Il ne modifie pas CapriDesktop : il lit et complète seulement, à la demande du téléphone, les fichiers que Capri garde dans `%LOCALAPPDATA%\Capri`.

## Mise en route

1. Sur le PC : `pc-sync\build-sync.ps1` (une fois), puis lance **CapriSync.exe**. Si Windows le demande, autorise-le sur les **réseaux privés** uniquement.
2. La fenêtre affiche l'adresse du PC (ex. `192.168.1.20`) et un **code d'appairage** (ex. `K7Q2-M9XA`), valable 5 minutes, une seule fois, 5 essais.
3. Sur le téléphone (même Wi-Fi) : Capri > Réglages > **SYNCHRO PC** → adresse, puis code → **Appairer**.

## Ce qu'on peut faire (en parlant à Capri ou depuis les réglages)

| Tu dis | Ce qui se passe |
|---|---|
| « Envoie cette conversation sur mon PC » | Les 20 derniers messages apparaissent dans Capri Sync (avec une bulle Windows) ; **Copier pour Capri** les met dans le presse-papiers pour continuer avec Capri sur le PC. Copie gardée dans `%LOCALAPPDATA%\CapriSync\handoffs`. |
| « Qu'est-ce que je faisais sur le PC ? » | Capri lit les derniers échanges de `chat.json` du PC (sans le modifier) et te résume où tu en étais. |
| « Synchronise ta mémoire » | `me.md` fusionné dans les deux sens (chaque ligne gardée une fois ; sauvegarde `me.md.capri-sync-backup` la première fois ; la note Obsidian choisie dans Capri PC est respectée), mémoires de projets (la plus récente gagne), lignes de discussion du jour ajoutées au journal du PC marquées « (téléphone) » pour que Capri PC s'en souvienne. |
| « Envoie un fichier sur mon PC » | Tu choisis un fichier (20 Mo max) → `Téléchargements\Capri Sync` sur le PC. |
| « Récupère les fichiers du PC » | Les fichiers glissés sur la fenêtre Capri Sync (ou ajoutés par **Vers le téléphone…**) arrivent dans `Téléchargements/Capri Sync` du téléphone. |

## Sécurité

- **Réseau local seulement** : le serveur (port 47917) refuse toute adresse qui n'est pas de ton réseau (192.168.x, 10.x, 172.16–31.x, link-local) ; le téléphone refuse d'appairer une adresse publique.
- **Appairage** : le code devient une clé par PBKDF2-HMAC-SHA256 (20 000 itérations, sel aléatoire par code) ; le PC répond avec une clé longue (256 bits) propre à ce téléphone, gardée chiffrée par DPAPI côté PC et par le Keystore Android côté téléphone.
- **Chaque message** est chiffré AES-256-CBC puis signé HMAC-SHA256 (chiffrer puis signer), avec un horodatage (±10 min) et un cache anti-rejeu de 20 minutes ; une réponse ne peut pas passer pour une requête.
- **Rien ne se synchronise tout seul** : seulement quand tu le demandes.
- « Oublier » un téléphone dans Capri Sync, ou « Oublier le PC » dans Capri, coupe le lien.

## Protocole (pour les curieux)

`GET /hello` → `{app, version, salt, name}` en clair. `POST /pair` et `POST /api` → corps = base64(iv16 ‖ AES-CBC ‖ HMAC32), AAD « capri-sync-v1 request » / « capri-sync-v1 reply ». Opérations : `ping`, `handoff`, `pull_chat`, `notes`, `projects`, `transcript`, `file`, `pull_files`. Le vecteur de test commun au téléphone (`Core2Test`) et au PC (`CapriSync.exe --selftest`) garantit que les deux chiffrent pareil.
