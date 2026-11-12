# Néon 2.0 : le nouveau look de Capri Android

État au 12 novembre 2026 : **compilé sur GitHub, pas encore vu sur un téléphone.** Le style a été écrit sans écran pour le regarder : ce qui suit décrit ce que le code dessine, pas un rendu vérifié. Dis ce qui ne te plaît pas et ça se règle dans un seul fichier.

## Ce qui change

Son identité reste la même : fond presque noir, rose et cyan, police mono pour les noms et les valeurs. Ce qui devient plus actuel :

| Élément | Avant | Néon 2.0 |
|---|---|---|
| Fond des écrans | couleur unie | la même couleur avec deux lueurs douces, bordeaux en haut à gauche et bleu-vert en bas à droite |
| Sections des réglages | bandes plates | cartes arrondies légèrement transparentes, filet lumineux en haut |
| Bulles, champs, panneau | coins coupés | coins arrondis, bord en dégradé avec un halo, léger reflet de verre |
| Boutons | plaques à coins coupés | pilules avec ondulation au toucher et petit effet de ressort |
| Interrupteurs | paire OFF / ON | interrupteur qui glisse et s'allume |
| Choix ◁ ▷ | texte nu | dans une pastille arrondie |
| Champ en cours de saisie | inchangé | son bord s'allume en rose et cyan |
| Textes d'explication | police mono | police sans-serif du téléphone, plus lisible |
| Mouvement | aucun | les cartes montent en place à l'ouverture d'un écran, les nouvelles bulles glissent, les écrans s'ouvrent en fondu (Android 14 et plus) |
| Toucher | aucun | petite vibration sur les boutons, interrupteurs et choix (seulement si le retour tactile du téléphone est activé) |

## Revenir à l'ancien look

Réglages > BULLE > **Style** : « Néon 2.0 » ou « Néon classique ». Le thème clair, sombre ou système reste un réglage séparé, juste au-dessus.

## Où c'est dans le code

Tout est dans `app/src/main/kotlin/com/capri/assistant/ui/Neon.kt` :

- `Plate` (bulles, boutons, champs), `Band` (cartes des sections), `Backdrop` (fond), `NeonSwitch` (interrupteur) ;
- `Neon.plate`, `Neon.tick`, `Neon.springy` (ondulation, vibration, ressort) ;
- `Form` (titre, sections, notes, interrupteurs, choix, champs).

Les autres écrans et le panneau de la bulle utilisent ces éléments, donc ils changent tous ensemble. Le réglage est `prefs.look` (0 = Néon 2.0, 1 = classique).

## Limites

- Pas de vrai flou derrière le panneau : l'effet de verre est fait de transparence et de reflets, parce que le flou entre fenêtres n'est pas disponible sur tous les téléphones.
- Rien n'a été repris de l'application `com.capri.mobile` : son APK est compilé et ne contient pas de code lisible. Pour réutiliser ses écrans, il faut son projet source.
