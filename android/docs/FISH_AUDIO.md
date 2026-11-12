# Fish Audio sur Capri Android

## Réglage

Réglages > VOIX > Sa voix : **Fish Audio**.

| Champ | Rôle |
|---|---|
| Clé | Ta clé API Fish Audio. Chiffrée par l'Android Keystore, jamais affichée en entier, jamais écrite dans les journaux. Les espaces collés par erreur autour de la clé sont retirés. |
| ID ou lien de voix | L'ID de la voix (32 caractères) ou son lien fish.audio. Vide : Fish Audio prend sa voix par défaut. |
| Modèle | `s2.1-pro-free` (offre gratuite, par défaut), `s2.1-pro`, `s2-pro`, `s1`, `drama-3`. Champ libre, pour les modèles à venir. |
| Vitesse | Le curseur commun (50 à 200 %) est envoyé à Fish Audio (`prosody.speed`, 0,5 à 2,0). |
| Volume | Appliqué au lecteur du téléphone. |
| Tester sa voix | Fait un vrai appel à Fish Audio. En cas d'échec, la raison s'affiche à chaque test. |

Chaque champ s'enregistre avec son bouton OK. La Hauteur ne concerne que la voix du téléphone.

## Ce qui part chez Fish Audio

`POST https://api.fish.audio/v1/tts`, avec les en-têtes `Authorization: Bearer <clé>` et `model`, et un corps JSON : `text`, `reference_id` (si une voix est choisie), `format: mp3`, `prosody.speed` (si la vitesse n'est pas 100 %), `mp3_bitrate: 64` (profil Économie seulement). Seuls les mots qu'elle dit sont envoyés.

La requête est construite par le moteur (`core/Fish.kt`), vérifiée contre la documentation officielle le 12 novembre 2026.

## Quand ça échoue

Elle dit quand même la phrase avec la voix du téléphone, et écrit pourquoi :

| Réponse | Ce qu'elle dit | Quoi faire |
|---|---|---|
| 401, 403 | Fish Audio a refusé la clé | Recopier la clé depuis fish.audio |
| 402 sur un modèle payant | Demande du crédit ; elle repasse seule sur `s2.1-pro-free` et le dit une fois | Ajouter du crédit, ou garder le modèle gratuit |
| 404 | Voix introuvable | Vérifier l'ID ou le lien |
| 429 | Fish Audio est occupé | Réessayer dans un instant |
| 5xx | Souci côté Fish Audio | Réessayer plus tard |
| Autre | Le message de Fish Audio, sans la clé | Selon le message |

Un nom de modèle inconnu de Fish Audio devient son modèle payant par défaut : une faute de frappe finit donc en 402, rattrapée de la même façon.

## État réel

- **Testé (JVM, `FishTest`, 5 tests)** : la requête envoyée par le vrai client HTTP à un faux serveur porte bien la clé, la voix, le modèle et la vitesse ; lecture des liens de voix ; bascule sur le modèle gratuit ; messages d'erreur ; la clé n'apparaît dans aucun message.
- **Écrit, à compiler** : le branchement dans l'app (`voice/Mouth.kt`, `ui/SettingsActivity.kt`). Pas de SDK Android dans l'environnement de développement : c'est la compilation GitHub Actions qui le valide.
- **Pas encore testé** : une vraie clé sur le TECNO CL6k.
- **Pas encore fait** : la lecture en streaming. Elle télécharge le MP3 complet avant de parler.
