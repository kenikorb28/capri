# Mémoire partagée v2 — fichiers et règles de fusion

Capri est **une seule Capri** : ce qu'on lui dit sur le PC, elle le sait sur le téléphone, et l'inverse. Sa mémoire est faite de fichiers texte (Markdown) que l'utilisateur peut lire et corriger lui-même. Capri Link les garde identiques sur les deux appareils.

Implémentation de la fusion : `android/core/src/main/kotlin/com/capri/core/link/Merge.kt` et `MemorySync.kt` (testés par `LinkTest.kt`). Le PC ne fusionne rien : il donne ses fichiers et reprend les fichiers fusionnés (voir link-v2.md, `pull` et `push`).

## 1. Les fichiers partagés

| Fichier | Contenu | PC (`%LOCALAPPDATA%\Capri\`) | Téléphone (`files/capri/` de l'app) |
|---|---|---|---|
| `me.md` | Ses **notes** sur l'utilisateur (« - Aime le thé »). | `me.md`, ou la note Obsidian choisie dans ses réglages | `me.md` |
| `memories.md` | Ses **souvenirs**, un paragraphe par jour, résumés à partir des discussions. | `memories.md` | `memories.md` |
| `projects/*.md` | Les **projets** suivis (objectif, étapes, dernière tâche). | `projects\` | `projects/` |
| `personality.md` | Sa **personnalité**, si l'utilisateur l'a écrite. | `personality.md` | `personality.md` |
| `transcripts/AAAA-MM-JJ.md` | Les **journaux** de discussion du jour. | `transcripts\` | `transcripts/` |

Ne voyagent **pas** : les clés d'API, les réglages de chaque appareil (voix, cerveau, modes), l'historique de discussion en cours (`chat.json`), les rappels (ils sonnent sur l'appareil où ils ont été créés ; leur synchro viendra plus tard).

## 2. Le principe : fusion à trois voies

Pour chaque PC appairé, le téléphone garde la **base** : l'état des fichiers juste après leur dernière synchro réussie (`files/capri/link/base-<id du PC>/`). À chaque synchro il compare **base**, **téléphone** et **PC** :

- une ligne présente dans la base mais effacée d'un côté **disparaît** des deux (oublier marche dans les deux sens) ;
- une ligne ajoutée d'un côté **apparaît** des deux ;
- avant la toute première synchro (pas de base), on garde **l'union** des deux côtés, chaque ligne une seule fois.

Ensuite le téléphone écrit le résultat chez lui et l'envoie au PC avec le SHA-256 de ce qu'il avait lu (`expect`). Si le PC a écrit quelque chose entre-temps, il refuse tout (`conflicts`) et le téléphone recommence avec le nouveau fichier : **aucune écriture n'écrase une autre**. De son côté, Capri Desktop ne réécrit pas `memories.md` quand il a changé pendant qu'elle le résumait : elle réessaie plus tard.

## 3. Règles par fichier

**`me.md` (notes)** — Une note par ligne. Deux lignes qui ne diffèrent que par la casse ou les espaces sont la même note. Les lignes qui commencent par `#` ou `>` sont l'en-tête. Pour les étiquettes à valeur unique (`- Name:`, `- Pronouns:`, `- Into:`), une seule valeur reste : celle qui a changé, ou celle de l'appareil le plus récent si les deux ont changé. Le résultat garde **la mise en page du fichier du PC** (une note Obsidian garde ses titres et ses paragraphes) : les notes oubliées en sont retirées, les nouvelles ajoutées à la fin. Sans fichier sur le PC, le fichier du téléphone part tel quel.

**`memories.md` (souvenirs)** — Une section par jour (`## Tuesday 3 March 2026` et `## Mardi 3 mars 2026` sont le même jour), chaque puce fusionnée à trois voies dans sa section. Ordre : « Earlier » d'abord, puis les jours dans l'ordre. Une journée vide disparaît.

**`projects/<nom>.md`** — Fusion section par section, dans l'ordre du téléphone. Sous « Dernière tâche » ne reste que la ligne la plus récente. Un projet supprimé d'un côté (sans changement de l'autre) est supprimé des deux. Noms de fichiers acceptés : lettres, chiffres, `_`, espace, `.`, `-`, 80 caractères au plus, terminés par `.md`.

**`personality.md`** — Gardé **entier** : le côté qui l'a changé gagne ; si les deux l'ont changé, le plus récent gagne et l'autre version est gardée à côté (`personality.other-device.md`). Il voyage **dans les mots du PC** : ses deux phrases sur l'endroit où elle vit (« inside the user's computer » / « inside the user's phone ») sont réécrites pour chaque appareil. Sa personnalité **par défaut, non modifiée**, compte comme « pas de fichier » : elle ne remplace jamais une personnalité écrite sur l'autre appareil. Un retour au défaut d'un côté remet le défaut des deux ; ce qui était écrit est gardé dans `personality.backup.md`.

**`transcripts/AAAA-MM-JJ.md` (journaux)** — Ils ne font que grandir. Chaque appareil envoie **ses propres lignes** (`**14:02 You:** …`, `**14:02 Capri:** …`) des derniers jours ; l'autre les ajoute **une seule fois**, marquées de leur origine : `**14:02 You (phone):** …` sur le PC, `**14:02 You (PC):** …` sur le téléphone. Une ligne déjà marquée ne repart jamais. Ces lignes ne sont **pas** résumées une seconde fois en souvenirs : chaque appareil résume les siennes dans `memories.md`, qui est fusionné. Si l'utilisateur a coupé « Se souvenir de nos discussions » sur un appareil, aucune ligne n'y entre ni n'en sort.

## 4. « Oublie tout »

Quand l'utilisateur dit « oublie tout » sur le téléphone, ses notes, souvenirs et journaux y sont effacés et la synchro suivante envoie la date de l'effacement (`wipe`). Le PC efface alors ses journaux jusqu'à ce moment (les lignes dites après restent), les lignes en attente de résumé, ses sauvegardes et ce que le téléphone faisait ; ses notes et souvenirs se vident par la fusion elle-même. Chaque effacement ne compte qu'une fois.

## 5. Ce que chaque appareil sait de l'autre

À chaque synchro, chacun envoie un court **contexte** : nom de l'appareil, heure de la dernière discussion, application au premier plan (si la discussion date de moins de 15 minutes), et les 6 derniers messages. Pendant 6 heures, Capri le reçoit dans son prompt (« On their PC, 3 min ago, they were with you in "Visual Studio Code"… You are the same Capri on their PC and their phone, with the same memory »), ce qui permet « continue ce que je faisais sur le PC ». Le PC voit aussi la liste des projets et la dernière tâche du plus récent.

## 6. Sauvegardes

Avant que Capri Link change un fichier du PC, la première version du jour est copiée dans `%LOCALAPPDATA%\Capri\link\backups\AAAA-MM-JJ\` (gardée 3 jours, effacée par « oublie tout »). Une lecture qui échoue (fichier ouvert ailleurs) arrête la synchro au lieu de passer pour un fichier vide : une panne ne peut pas faire oublier quoi que ce soit.

## 7. Quand la synchro a lieu

Sur le téléphone, automatiquement (réglable dans Réglages > CAPRI LINK) :

- quelques secondes après chaque chose qu'elle note et après chaque réponse ;
- juste avant de répondre, si la dernière synchro a plus d'une minute (1,5 s d'attente au plus) ;
- en continu tant que sa bulle est ouverte (sauf en mode ÉCONOMIE) : le PC prévient dès qu'un fichier change, le téléphone suit en une ou deux secondes ;
- environ toutes les 15 minutes via le planificateur d'Android, même bulle fermée ;
- au démarrage de l'app, et quand l'utilisateur le demande (« synchronise ta mémoire »).
