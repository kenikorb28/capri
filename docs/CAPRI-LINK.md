# Capri Link — une seule Capri sur ton PC et ton téléphone

Capri Link relie **Capri Desktop 2.0** (ton PC Windows) et **Capri Android 2.0** (ton téléphone). Ce n'est pas deux Capri qui se parlent : c'est **la même Capri**, avec **la même mémoire**. Dis-lui sur le PC que tu aimes le thé, elle le sait sur le téléphone ; demande-lui d'oublier quelque chose sur le téléphone, elle l'oublie aussi sur le PC.

## Ce qui est partagé

| Partagé | Pas partagé (chaque appareil garde le sien) |
|---|---|
| Ses **notes** sur toi (`me.md`, y compris ta note Obsidian si tu en as choisi une) | Tes **clés d'API** (elles ne voyagent jamais) |
| Ses **souvenirs** (`memories.md`) | Les **réglages** (voix, cerveau, modes, apparence) |
| Tes **projets** | Les **rappels** : ils sonnent sur l'appareil où tu les as créés (leur partage viendra plus tard) |
| Sa **personnalité**, si tu l'as écrite | La conversation affichée à l'écran |
| Les **journaux** de vos discussions, marqués « (phone) » ou « (PC) » | |
| Ce que tu faisais avec elle sur l'autre appareil, pendant 6 heures | |

Le détail technique est dans [contract/memory-v2.md](../contract/memory-v2.md) et [contract/link-v2.md](../contract/link-v2.md).

## Les relier (une seule fois, 2 minutes)

Il te faut Capri Desktop 2.0 sur le PC et Capri Android 2.0 sur le téléphone ([OUTILS.md](OUTILS.md)), sur **le même Wi-Fi**, ou tous les deux sur Tailscale.

1. **Sur le PC** : clic droit sur Capri (ou sur son icône près de l'horloge) > **Capri Link…** > **Ajouter un téléphone**. Un code s'affiche, par exemple `K7Q2-M9XA`. Il vaut 5 minutes, pour un seul téléphone.
2. Si Windows demande l'accès au réseau : coche **Réseaux privés**, puis *Autoriser l'accès*.
3. **Sur le téléphone** : Capri > **Réglages** > **CAPRI LINK**.
   - *Adresse du PC* : laisse vide, elle trouve le PC toute seule sur le Wi-Fi. Sinon tape l'adresse que la fenêtre Capri Link affiche sur le PC (« Ce PC : 192.168.x.x »), ou son adresse Tailscale `100.x.y.z`.
   - *Code d'appairage* : tape le code (minuscules ou tiret, peu importe), puis **Associer**.
4. Le téléphone affiche « Appairé avec … : Capri a maintenant la même mémoire sur les deux », et la fenêtre du PC montre le téléphone dans la liste.

La toute première synchro **réunit** les deux mémoires : rien n'est perdu, chaque note n'apparaît qu'une fois.

## Au quotidien

Tu n'as rien à faire : elle se synchronise toute seule.

- **Quelques secondes** après chaque chose qu'elle note et après chaque réponse.
- **Juste avant de te répondre**, si la dernière synchro a plus d'une minute (une seconde et demie d'attente au plus).
- **En direct** tant que sa bulle est ouverte sur le téléphone : dès que tu lui parles sur le PC, le téléphone le sait en une ou deux secondes. Pas en mode ÉCONOMIE, pour la batterie.
- **Environ toutes les 15 minutes**, même bulle fermée, grâce au planificateur d'Android.

Ce que tu peux lui dire :

| Tu dis (sur le téléphone) | Ce qu'elle fait |
|---|---|
| « Continue sur le PC » / « Envoie cette conversation sur mon PC » | Le PC reçoit vos derniers messages : dis ensuite à Capri sur le PC « on continue ? ». |
| « Qu'est-ce que je faisais sur le PC ? » | Elle dit avec quelle application et de quoi vous parliez sur le PC. |
| « Continue ce que je faisais sur le PC » | Elle reprend là où vous en étiez sur le PC (elle le sait déjà, c'est dans sa mémoire). |
| « Synchronise ta mémoire » | Une synchro tout de suite, avec le résumé de ce qui a changé. |
| « Envoie un fichier sur le PC » | Tu choisis un fichier (15 Mo max) : il arrive dans `Téléchargements\Capri Link` sur le PC. |
| « Récupère les fichiers du PC » | Les fichiers que tu as mis dans *Envoyer au téléphone…* (fenêtre Capri Link du PC, ou glissés dessus) arrivent dans `Téléchargements/Capri Link` sur le téléphone. |

Sur le PC aussi, Capri sait ce que tu faisais avec elle sur le téléphone : « on reprend ce que je faisais sur mon téléphone ? ».

Pour couper la synchro automatique : *Réglages* > *CAPRI LINK* > **Synchroniser toute seule**. Elle se synchronise alors seulement quand tu le demandes.

## Sécurité et vie privée

- Tout passe **chiffré** (AES-256 + HMAC-SHA256), avec une clé créée à l'appairage par un échange ECDH et ton code. Quelqu'un qui espionne le Wi-Fi ne peut ni lire ni deviner le code.
- **Uniquement sur ton réseau** : le PC ne répond qu'aux adresses de réseau privé (ton Wi-Fi, Tailscale). Rien ne passe par internet ni par un serveur.
- Le PC n'écoute le réseau **que si un téléphone est appairé** ou qu'un code est affiché.
- Les clés sont protégées par Windows (DPAPI) sur le PC et par le Keystore d'Android sur le téléphone. Aucune clé n'est jamais écrite dans un journal.
- Un fichier reçu n'est **jamais ouvert ni lancé** tout seul.
- Avant de changer un fichier de sa mémoire sur le PC, Capri Link garde la version du matin dans `%LOCALAPPDATA%\Capri\link\backups\` pendant 3 jours (effacée par « oublie tout »).

## Oublier, révoquer

- **« Oublie que … »** sur un appareil : la note disparaît des deux.
- **« Oublie tout »** sur le téléphone (avec ton « oui ») : ses notes, souvenirs et journaux sont effacés des deux appareils.
- **Délier** : sur le téléphone, *Réglages* > *CAPRI LINK* > **Oublier ce PC** (le PC est prévenu) ; ou sur le PC, fenêtre Capri Link > choisis le téléphone > **Oublier**. Ensuite cet appareil ne peut plus rien demander. La mémoire déjà partagée reste sur les deux.

## Si ça ne marche pas

| Ce que tu vois | Ce qu'il faut faire |
|---|---|
| « Aucun PC avec un code affiché n'a été trouvé sur ce Wi-Fi » | Vérifie que le code est affiché sur le PC (il vaut 5 min). Sinon tape l'adresse « Ce PC : … » de la fenêtre Capri Link dans *Adresse du PC*. |
| « Code incorrect ou expiré » | Le code a plus de 5 minutes ou une faute de frappe : clique sur *Nouveau code* sur le PC. Après 5 erreurs le code est annulé. |
| « Ton PC n'a pas répondu … Capri est bien ouverte dessus ? » | Capri Desktop doit tourner sur le PC. Vérifie le pare-feu et le profil **Privé** du Wi-Fi ([OUTILS.md](OUTILS.md), étape 7). |
| « Le port 47917 est déjà pris » (fenêtre du PC) | L'ancien programme *Capri Sync* (v1) tourne encore : ferme-le, puis *Ajouter un téléphone*. |
| « L'horloge du PC et celle du téléphone sont trop différentes » | Mets les deux à l'heure automatique (écart maximum : 10 minutes). |
| « Ton PC ne reconnaît plus ce téléphone. Refais l'appairage. » | Le PC a oublié ce téléphone (ou sa liste a été effacée) : *Oublier ce PC* sur le téléphone, puis refais l'appairage. |
| Ça marche à la maison mais pas dehors | Normal sans Tailscale. Avec Tailscale, appaire avec l'adresse `100.x.y.z` du PC ([OUTILS.md](OUTILS.md), étape 8). |

L'état et le dernier problème rencontré s'affichent dans *Réglages* > *CAPRI LINK* (téléphone) et dans le journal de la fenêtre Capri Link (PC).

## Vérifier que tout marche (10 minutes)

Coche chaque ligne la première fois. Si l'une échoue, note ce que tu vois et envoie-le-moi.

| # | Fais ceci | Résultat attendu |
|---|---|---|
| 1 | Appaire le téléphone (ci-dessus). | Le téléphone apparaît dans la fenêtre Capri Link du PC, « vu à l'instant ». |
| 2 | Sur le PC, dis à Capri : « retiens que mon jeu préféré est Free Fire ». Bulle ouverte sur le téléphone, attends 5 s puis demande-lui : « c'est quoi mon jeu préféré ? » | « Free Fire ». La ligne est aussi dans sa mémoire sur le téléphone (page **Mémoire** de l'app). |
| 3 | Sur le téléphone : « retiens que j'aime le thé ». Ouvre `%LOCALAPPDATA%\Capri\me.md` sur le PC après 10 s. | « Aime le thé » y est. |
| 4 | Sur le téléphone : « oublie que j'aime le thé ». | La ligne disparaît aussi du `me.md` du PC. |
| 5 | Parle quelques minutes à Capri sur le PC, puis demande au téléphone : « qu'est-ce que je faisais sur le PC ? » | Elle cite l'application et le sujet. |
| 6 | Sur le téléphone : « continue sur le PC », puis sur le PC : « on continue ? » | Capri sur le PC reprend votre conversation. |
| 7 | Éteins le Wi-Fi du téléphone, parle-lui, rallume le Wi-Fi. | Elle répond normalement hors ligne du PC ; tout se synchronise au retour. |
| 8 | Ferme Capri Desktop, parle au téléphone, rouvre Capri Desktop. | Rien n'est perdu : ce que tu as dit au téléphone arrive sur le PC à la synchro suivante. |
| 9 | Fenêtre Capri Link > *Envoyer au téléphone…* > un petit fichier ; puis au téléphone : « récupère les fichiers du PC ». | Le fichier est dans `Téléchargements/Capri Link` du téléphone. |
| 10 | Au téléphone : « envoie un fichier sur le PC » et choisis une photo. | Elle est dans `Téléchargements\Capri Link` sur le PC. |
| 11 | Sur le PC, *Ajouter un téléphone* et tape 5 fois un mauvais code sur le téléphone. | Le code est annulé (« Mauvais code 5 fois »). |
| 12 | Sur le PC, *Oublier* le téléphone, puis au téléphone : « synchronise ta mémoire ». | « Ton PC ne reconnaît plus ce téléphone. Refais l'appairage. » Réappaire-le : la mémoire est intacte des deux côtés. |
