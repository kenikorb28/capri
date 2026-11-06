# Capri Link v2 — protocole

Capri Link relie Capri Desktop (le PC, **serveur**) et Capri Android (le téléphone, **client**) sur le réseau de l'utilisateur : son Wi-Fi, un câble, ou Tailscale. Rien ne passe par internet ni par un serveur tiers.

Implémentations : `desktop/app/Link.cs` (PC) et `android/core/src/main/kotlin/com/capri/core/link/Link.kt` (téléphone). Vecteur de test : `vectors/link-v2-crypto.json`.

## 1. Transport

- **TCP 47917**, HTTP/1.1, une requête par connexion (`Connection: close`). Les corps des POST ont un `Content-Length` (pas de *chunked* : réponse 411).
- Le PC n'écoute que si **un téléphone est appairé** ou si **un code d'appairage est affiché**. La première fois, le pare-feu Windows demande l'autorisation : n'autoriser que les **réseaux privés**.
- Le PC ne répond qu'aux adresses de réseau privé : `10/8`, `172.16/12`, `192.168/16`, `169.254/16`, `100.64/10` (Tailscale), la boucle locale, et en IPv6 `fc00::/7` et le lien local. Toute autre adresse reçoit 403.
- Tailles maximales : 16 Ko pour `/pair`, 32 Mo pour `/api`, 15 Mo par fichier transféré. Au plus 8 connexions simultanées.
- Le PC et le téléphone doivent avoir leur horloge à l'heure à ±10 minutes près.

## 2. Découverte (UDP 47917)

Le téléphone envoie l'octet-texte ASCII `CAPRI-LINK?` en diffusion (adresse de diffusion du sous-réseau et `255.255.255.255`) et écoute 1,5 s. Chaque PC répond, à l'adresse d'où vient la question et seulement si elle est privée :

```json
{"app":"Capri Link","link":2,"id":"pc-3f9a…","name":"DESKTOP-CAPRI","pairing":true,"port":47917}
```

La découverte est un confort : taper l'adresse IP du PC (affichée dans sa fenêtre Capri Link) marche aussi. Si le PC change d'adresse, le téléphone le retrouve par son `id`.

## 3. `GET /hello`

```json
{"app":"Capri Link","link":2,"version":"2.0.0","name":"DESKTOP-CAPRI","id":"pc-3f9a…","pairing":true,
 "salt":"<16 octets en hex>","pub":"<clé publique du PC, base64>"}
```

`salt` et `pub` n'existent que pendant qu'un code est affiché.

## 4. Appairage

Sur le PC : menu de Capri > **Capri Link…** > **Ajouter un téléphone**. Le PC crée :

- un **code** de 8 caractères tirés uniformément parmi `ABCDEFGHJKMNPQRSTUVWXYZ23456789` (31 signes, sans 0/O/1/I/L), affiché `XXXX-XXXX` ;
- un **sel** de 16 octets aléatoires et une **paire de clés ECDH P-256** neuve.

Le code vaut **5 minutes**, **5 essais faux**, et **un seul téléphone**.

Calcul (identique des deux côtés) :

```
code normalisé  = majuscules, seulement A-Z et 0-9         ("k7q2-m9xa" → "K7Q2M9XA")
P, Q            = clés publiques du PC et du téléphone, forme non compressée (0x04 ‖ X ‖ Y, 65 octets)
S = SHA-256( X de l'ECDH(P, Q) )                             (32 octets)
C = PBKDF2-HMAC-SHA256(code normalisé, sel, 20 000 itérations, 32 octets)
K = HMAC-SHA256(S, "capri-link-v2 pair" ‖ C ‖ P ‖ Q)         clé d'appairage
L = HMAC-SHA256(K, "capri-link-v2 long-term")                 clé de la liaison, gardée par les deux
```

Échange :

1. Le téléphone lit `GET /hello` (`pub` = P, `salt`), crée sa paire de clés (Q), calcule K.
2. `POST /pair` avec `{"pub":"<Q en base64>","box":"<scellé>"}`, où la boîte est `{"op":"pair","ts":<ms>,"id":"<id du téléphone>","name":"<modèle>"}` scellée avec K et l'étiquette `capri-link-v2 pair request`.
3. Le PC ouvre la boîte avec son propre K. Code faux, boîte modifiée, `ts` hors des ±10 min ou `id` invalide (`^[A-Za-z0-9_.:-]{1,80}$`) : **403** et un essai de moins. Bon code : il garde `{id, nom, L}`, ferme le code et répond **200** avec `{"ok":true,"ts":…,"id":"<id du PC>","name":"<nom du PC>"}` scellé avec K et l'étiquette `capri-link-v2 pair reply`.
4. Le téléphone ouvre la réponse avec K et garde `L`, l'id et le nom du PC, et son adresse.

Pourquoi c'est sûr : quelqu'un qui écoute le Wi-Fi voit P, Q, le sel et les boîtes, mais pas S (secret ECDH) ; il ne peut donc pas essayer les codes hors ligne. Quelqu'un qui s'interpose et remplace les clés doit deviner le code en direct : 5 essais sur 31⁸ ≈ 8,5 × 10¹¹ possibilités.

## 5. Messages scellés

```
enc = HMAC-SHA256(clé, "capri-enc")      mac = HMAC-SHA256(clé, "capri-mac")
iv  = 16 octets aléatoires à chaque message
ct  = AES-256-CBC-PKCS7(enc, iv, UTF-8 du JSON)
tag = HMAC-SHA256(mac, étiquette ‖ iv ‖ ct)
scellé = base64(iv ‖ ct ‖ tag)
```

À l'ouverture, `tag` est vérifié **en temps constant avant** tout déchiffrement. Chaque sens a son étiquette (`capri-link-v2 request`, `capri-link-v2 reply`, `… pair request`, `… pair reply`) : une réponse ne peut pas passer pour une requête.

## 6. `POST /api`

Corps : une requête scellée avec L et l'étiquette `capri-link-v2 request` :

```json
{"op":"pull","ts":1730000000000,"id":"<id du téléphone>","n":"<nonce hex>","data":{…}}
```

- Aucune clé connue n'ouvre le message : **401** (le PC a oublié ce téléphone, il faut le réappairer).
- `ts` hors des ±10 min, `id` différent du téléphone de cette clé, ou `n` déjà vu (gardé 25 min) : réponse `{"ok":false,"error":"replayed or stale"}`.
- Sinon : `{"ok":true,"ts":…,"data":{…}}` ou `{"ok":false,"ts":…,"error":"…"}`, scellé avec L et l'étiquette `capri-link-v2 reply`. Le téléphone vérifie aussi que `ts` de la réponse est frais.

## 7. Opérations

| op | data envoyée | data reçue |
|---|---|---|
| `ping` | `{}` | `{name, version, rev}` |
| `pull` | `{days}` (1 à 31, 3 par défaut, 14 à la première synchro) | `{rev, notes, memories, projects, personality, journal, context}` (voir memory-v2.md) |
| `push` | `{expect, notes?, memories?, projects?, personality?, journal?, wipe?, context?}` | `{conflicts, rev}` |
| `wait` | `{rev, seconds}` (25 s au plus) | `{rev, changed}` : répond dès qu'un fichier partagé change sur le PC |
| `handoff` | `{name?, at?, app?, turns:[{role,text}]}` | `{shown:true}` : « continue sur le PC » |
| `file` | `{name, data}` (base64, 15 Mo max) | `{saved}` : rangé dans Téléchargements\Capri Link, jamais ouvert ni lancé |
| `pull_files` | `{}` | `{files:[{name,data}], left}` : fichiers mis en attente sur le PC |
| `forget` | `{}` | `{}` : le PC oublie ce téléphone |

`rev` est opaque : il change dès qu'un fichier partagé du PC change (notes, souvenirs, personnalité, journal du jour, projets).

`push` est **tout ou rien** : si un seul fichier envoyé a changé sur le PC depuis le `pull` (son SHA-256 ne correspond plus à `expect`), rien n'est écrit et `conflicts` liste `notes`, `memories`, `project:<nom>` ou `personality`. Le téléphone refait alors pull, fusion, push (3 essais).

## 8. Clés et révocation

- **PC** : les clés L des téléphones sont dans `%LOCALAPPDATA%\Capri\link\phones.dat`, chiffré par DPAPI pour l'utilisateur Windows (illisible depuis une autre session ou un autre PC). `pc-id.txt` n'est pas secret.
- **Téléphone** : L est chiffrée par l'Android Keystore (comme ses clés d'API).
- Aucune clé, aucun code n'est écrit dans un journal. Les clés d'API de Capri **ne voyagent jamais** par Capri Link.
- **Révoquer** : « Oublier » dans la fenêtre Capri Link du PC, ou « Oublier ce PC » sur le téléphone (qui prévient le PC). Ensuite toute requête reçoit 401.

## 9. Ce que Capri Link ne fait pas (encore)

Pas de relais par internet (prévu plus tard, chiffré de bout en bout), pas de synchro des rappels ni des réglages, pas de mise à jour des applications (ce sera l'Update Engine, signé, depuis le dépôt GitHub de l'utilisateur).
