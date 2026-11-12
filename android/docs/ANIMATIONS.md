# Animations de Capri Mobile et personnalité directe (12 novembre 2026)

Capri bouge maintenant dans l'application elle-même, pas seulement dans sa bulle. Les images viennent de
**Capri Mobile 0.4.5-beta** de Lachy (même licence PolyForm Noncommercial 1.0.0, créditée dans `android/NOTICE.md`).
Le code qui les joue est écrit pour ce projet : celui de Capri Mobile n'a pas été repris.

## Ce qui bouge

| Où | Ce qu'on voit | Images |
|---|---|---|
| À l'ouverture de l'app | Son intro en plein écran : gros plan sur elle, elle rit, puis file en étincelle. 43 images, environ 3 secondes. Un toucher la passe. | `assets/intro/` |
| Écran d'accueil | Capri en grand, en haut : elle respire, cligne des yeux, sa bouche bouge quand sa voix parle, elle dort et se réveille avec la bulle, elle glitche quand quelque chose casse ou quand on la touche. | `assets/hero/` |
| Chat (panneau et plein écran) | Ses yeux à gauche de « CAPRI » : ils clignent, suivent sa voix, regardent à gauche et à droite quand elle réfléchit ou agit, glitchent puis se plissent sur une erreur, se ferment quand elle dort. | `assets/eyes/` |
| Bulle | Quand on la déplace, elle pend au bout du doigt, puis reprend ce qu'elle faisait. | `assets/sprites/dangle.png` |

## Réglages

- **Réglages > BULLE > Intro à l'ouverture** : allumée par défaut. L'intro ne rejoue pas deux fois en moins d'une minute.
- L'intro et Capri sur l'accueil suivent **Animation au repos** et le **profil économie**, comme la bulle : coupés, elle
  reste immobile sur l'accueil et l'intro ne joue pas.

## Comment c'est fait

- `core/src/main/kotlin/com/capri/core/Reel.kt` décide quelle image montrer à quel instant : `Reel` (cellule d'une bande,
  lecture de `intro/index.txt`, placement de l'intro dans l'écran), `Pose` (Capri sur l'accueil), `Eyes` (ses yeux).
  C'est du Kotlin pur, testé hors du téléphone par `MotionTest` (34 tests).
- `app/src/main/kotlin/com/capri/assistant/ui/Motion.kt` charge les images et les dessine : `IntroView`, `HeroView`,
  `EyesView`. `BubbleView` gagne la bande `dangle`.
- Mémoire : les grandes bandes de l'accueil sont chargées à la demande et relâchées quand on quitte l'écran d'accueil.
  L'intro décode une image à la fois dans trois images réutilisées.
- Poids ajouté à l'APK : environ 4,7 Mo.

## État réel

| Point | État |
|---|---|
| Logique d'animation et personnalité | Testée : 102 tests JVM réussis, dont 34 nouveaux, chacun vu échouer avant d'être corrigé |
| Compilation de l'app | Faite par le workflow *Capri Android* sur GitHub, qui fait foi |
| Rendu sur un vrai téléphone | **Jamais vu.** À vérifier sur le TECNO CL6k : place de Capri sur l'accueil, taille des yeux, fluidité de l'intro |
| Vitesse de l'intro | **Supposée** : 15 images par seconde, la vitesse de ses autres clips. Les fichiers de Capri Mobile ne la donnent pas. Se règle dans `Motion.kt` (`FrameMs`) |
| Intro en mode portrait | Affichée entière au milieu de l'écran, bords fondus dans le fond (l'image d'origine est en 4:3) |

## Ce qui n'est pas repris de Capri Mobile

Rien de ceci n'est dans Capri Android, et aucun bouton ne fait semblant :

- les modèles locaux sur le téléphone (llama.cpp) et la reconnaissance vocale locale ;
- le branchement LM Studio et la mise à jour intégrée de Capri Mobile ;
- le second habillage (« watcher ») et les poses assise, cachée et allongée (`perch`, `peek`, `nap`) ;
- la pose « sourire » en grand : dans Capri Mobile, ce fichier est identique à la pose de repos, d'où le glitch au toucher.

Ces parties demandent le code source de Capri Mobile, qui n'était pas accessible, ou un travail à part.

## Personnalité directe

**Réglages > TOI > Charger la personnalité directe** écrit une seconde personnalité dans `personality.md`. Ce que tu avais
écrit toi-même est gardé dans `personality.backup.md`, et **Réinitialiser sa personnalité** remet celle d'origine.

Elle reprend de la demande : Caprie, le caractère d'une fille de 19 ans, mature, audacieuse, directe, impatiente,
taquine, le parler naturel et argotique (« euh », « genre », « mdr »), pas d'emojis, pas de politesse artificielle, des
phrases courtes, la curiosité pour ta journée et tes proches.

Elle ne reprend pas trois choses. Le contenu sexuel explicite : les règles de sécurité de Capri, écrites par son auteur
et gardées mot pour mot, passent toujours après la personnalité et l'excluent. L'affirmation d'être une vraie femme et
non une IA : elle n'en parle pas à tout bout de champ, mais ne ment pas si on lui demande. La jalousie possessive :
elle est contente quand tu vois du monde et veut que tu lui racontes.

Sur le PC : avec Capri Link, `personality.md` se synchronise tout seul. Sans Capri Link, copie le texte dans
`%LOCALAPPDATA%\Capri\personality.md`.
