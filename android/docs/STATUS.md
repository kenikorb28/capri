# État des fonctionnalités — Capri Android 2.0

Règle suivie : une fonction n'est dite « terminée » que si elle est **implémentée et testée**. Ici, le test sur ton TECNO CL6k n'a pas pu être fait (pas de téléphone ni de SDK Android dans cet environnement), donc :

- **T** = implémenté et **testé** automatiquement (le vrai code exécuté, sur la JVM) ;
- **C** = implémenté, **compilé** contre l'API Android 36, **pas encore exécuté sur un téléphone** ;
- **N** = écrit, **non compilé ici** (le C# de Capri Desktop ; le workflow GitHub *Capri Desktop* le compile et le teste) ;
- **~** = partiel ou dépend du téléphone ; **✗** = impossible ou volontairement non fait.

Rien de ce tableau n'est un bouton factice : chaque ligne C a son code réel dans `app/`.

## Cerveau, contexte, outils

| Fonction | État | Où |
|---|---|---|
| Capri Brain : comprendre, choisir les outils, enchaîner (12 tours), répondre avec sa voix | T | `core/Brain.kt` |
| Petits modèles : « act » puis choix des outils | T | `Brain.reach` |
| Modes LOCAL / HYBRIDE / CLOUD, bascule HYBRIDE vers le cloud si le PC ne répond pas (avant tout outil) | T (cerveau) / C (bascule) | `engine/Capri.kt` |
| Sans cerveau ou sans internet : commandes simples quand même, et message honnête pour le reste | T / C | `Brain.ask`, `Capri.say` |
| Contexte : batterie, réseau, app au premier plan, casque, mode, heure | C | `system/DeviceInfo.situation` |
| Système d'outils extensible (nom, description, paramètres, validation, permission, risque, confirmation) | T | `core/Tools.kt` |
| Plugins sans toucher au cœur (+ phrases directes) : calculatrice, tirage au sort | T | `plugins/Plugins.kt` |
| Page Outils et plugins (risque, interrupteur, disponibilité) | C | `ui/ToolsActivity.kt` |
| Planification en étapes (+ mémoire du projet) | T | `Brain.plan` |
| Conversation continue (références au message précédent) | T | historique 8/16/32 |

## Voix

| Fonction | État |
|---|---|
| Écoute : reconnaissance du téléphone, sur l'appareil (hors ligne si pack téléchargé), Whisper sur le PC | C |
| Synthèse : voix du téléphone, ElevenLabs, Fish Audio, serveur OpenAI (Kokoro…) ; vitesse, hauteur, volume, langue ; repli sur la voix du téléphone | C |
| Conversation vocale : elle réécoute après sa réponse ; deux silences l'arrêtent | C |
| Interruption : « attends », « chut », « tais-toi » | T (phrases) / C |
| Interruption en parlant par-dessus elle (annulation d'écho) | ~ C — dépend de l'annuleur d'écho du TECNO ; mieux avec un casque |
| « Hey Capri » avec ta voix, sur le téléphone, configurable, désactivable, coupé en Économie | T (signaux synthétiques) / C |
| « Hey Capri » puis la demande dans le même souffle (« Hey Capri ouvre YouTube ») | ~ — dis « Hey Capri », attends le bip, puis parle |
| Hey Capri écran éteint | ~ — option, plus de batterie ; jamais en Économie |
| Coût de Hey Capri | mesuré : 0,2–0,4 % d'un cœur sur la machine de test ; le vrai chiffre du téléphone s'affiche dans Capri Device |

## Assistant système et interface

| Fonction | État |
|---|---|
| Bulle flottante : déplaçable, aimantée au bord, taille, opacité, animée (sprites de Capri), masquable, atténuée sur le plein écran | C |
| Panneau compact / complet avec Parler, Analyser, Presse-papiers, Rechercher, Étudier, Actions ; OUI/NON quand elle demande | C |
| Bulle de parole quand le panneau est fermé | C |
| Assistant par défaut (appui long Accueil/Marche) avec texte et capture de l'écran si tu l'autorises dans Android | C |
| Relais de reconnaissance (la saisie vocale des autres apps continue de marcher) | C |
| Tuile des réglages rapides, raccourcis, « Expliquer avec Capri » (sélection de texte), Partager vers Capri | C |
| Thème sombre / clair / système, bords à bords, retour prédictif Android 16 | C |

## Vision

| Fonction | État |
|---|---|
| « Regarde mon écran » : capture (accessibilité ou question MediaProjection), bulle cachée pendant la capture, description par un cerveau qui voit | C |
| OCR sur le téléphone (ML Kit, hors ligne) quand le cerveau ne voit pas, pour les PDF et les photos | C |
| Écrans protégés (banque, `FLAG_SECURE`) | ✗ par Android — elle le dit |
| Apps d'argent : elle demande avant de lire l'écran | C |
| Caméra via ton app Photo (sans permission CAMERA), photo remise droite | C |
| Ligne « 📤 … envoyé(e) à … » quand une image ou un fichier part vers un cerveau | C |
| Taille des images envoyées (960 / 1280 / 1600 px) | C |

## Contrôle d'Android

| Fonction | État |
|---|---|
| Ouvrir une app par son nom (et surnoms : insta, ff, wa…), un site, une recherche, un réglage ou panneau (Wi-Fi, Bluetooth…) | T (phrases) / C |
| Musique : Spotify / YouTube Music jouent depuis une recherche ; YouTube ouvre la recherche | C |
| Média et volume, « qu'est-ce qui joue » (avec l'accès aux notifications) | C |
| Lampe, Ne pas déranger | C |
| Réveils, minuteurs (horloge du téléphone), agenda prérempli | C |
| Appel, SMS, e-mail, partage, itinéraire prêts — tu fais le dernier appui | C |
| Retour, Accueil, notifications, verrouiller, capture, défiler (« descends la page ») | T (phrases) / C |
| « Appuie sur Suivant » : appui direct par le nom, sans cerveau ; Envoyer/Payer/Supprimer demandent | T (phrases) / C |
| « Fais-le pour moi » : tâche en plusieurs étapes choisies par le cerveau, avis + bouton Arrêter, jamais de mot de passe, jamais les écrans de permissions | C |
| « Supprime ce fichier » → tu choisis le fichier → « Tu veux bien supprimer « test.zip » ? » → oui | T (flux) / C |

## Presse-papiers, notifications, fichiers

| Fonction | État |
|---|---|
| Presse-papiers intelligent : Traduire, Résumer, Corriger, Expliquer, Répondre ; copier une réponse | C (lecture possible seulement quand Capri a le focus : règle Android 10+) |
| Notifications : tri École / Messages / Applications / Important / Promotions, résumé, filtre, lecture à voix haute, codes masqués, rien d'envoyé tout seul | T (tri, résumé) / C (écoute) |
| Fichiers : txt/code/docx/pptx/xlsx/odt lus, PDF (6 premières pages par OCR), images ; enregistrer un texte ; supprimer avec confirmation nominative | T (lecture) / C |

## Mémoire

| Fonction | État |
|---|---|
| Temporaire (chat 8/16/32 messages, ce qu'elle a vu), projet (le projet, architecture, décisions, dernière tâche, notes), permanente (me.md, memories.md, journaux) | T |
| « Qu'est-ce que tu as mémorisé sur moi ? », « oublie cette information », « oublie tout » (confirmé) | T |
| Mémoire permanente seulement avec ton accord : l'apprentissage spontané est désactivé par défaut | T |
| « On en était où ? » : derniers échanges, projet en cours, rappels | T |
| Page Mémoire : voir, modifier, oublier une note, un projet, tout | C |

## Modes, appareil, tâches

| Fonction | État |
|---|---|
| CAPRI STUDY (débutant / normal / avancé, fiches, flashcards, quiz, exercices guidés) | T (invites) / C |
| CAPRI DEV (code, logs, erreurs, Git, fichiers) | T (invites) / C |
| CAPRI GAMING : réponses d'une ligne, HUD (batterie, chaleur, RAM, heure) avec 🎙 💬 📊 🔕, aucune triche | C — **pas de FPS** (illisible par une app sur Android) |
| CAPRI DEVICE : batterie, température, chaleur, RAM, stockage, réseau, casque ; alertes batterie faible / stockage presque plein (au plus une fois par jour) | C — charge CPU illisible par une app, dit honnêtement |
| Profils PERFORMANCE / NORMAL / ÉCONOMIE | C |
| Rappels et routines (6 langues), surveillances (batterie, Wi-Fi, notification de quelqu'un) | T (rappels) / C |
| Recherche web avec sources, lecture de pages | T (analyse) / C |

## Sécurité, urgence, synchro

| Fonction | État |
|---|---|
| Niveaux de risque ; ÉLEVÉ toujours confirmé, MOYEN selon tes interrupteurs | T |
| Capri Sécurité : tableau 🟢🟡🔴, chaque porte expliquée et coupable, guide « Optimiser Capri sur TECNO » | C |
| Clés API chiffrées (Keystore), jamais en clair ni dans les journaux, jamais en http vers internet | T (Redact, safeForKey) / C |
| CAPRI URGENCE (désactivée par défaut) : appeler, envoyer sa position par SMS, infos importantes ; toujours confirmé | C |
| Capri Link côté téléphone : appairage ECDH + code, fusion à trois voies de toute la mémoire, synchro automatique (après chaque changement, avant de répondre, en direct bulle ouverte, toutes les 15 min), passage de conversation, fichiers dans les deux sens | T (moteur : vecteur partagé, fusion, un faux PC qui suit le contrat) / app (`sync/`) : vérifiée contre une imitation de l'API Android ici, vraie compilation par le workflow *Capri Android* |
| Capri Link côté PC (dans Capri Desktop 2.0, `desktop/app/Link.cs`) | N — le test automatique de Capri Desktop contient le vecteur partagé et un vrai appairage + une vraie synchro en local |
| Capri Sync v1 (`pc-sync/CapriSync.exe`) | remplacé par Capri Link, plus utilisé |

## Non faits ou impossibles

- **IA dans le téléphone** : choisi « pas pour l'instant ».
- **Voix Kokoro / Qwen3 et oreilles Parakeet dans le téléphone** : trop lourdes pour l'instant ; Kokoro reste possible via ton PC (serveur OpenAI).
- **Appels Discord, VB-CABLE, mixeur, clips et réactions de jeu, gags** : spécifiques à Windows ou hors du cahier des charges.
- **Hotword système écran éteint, FPS, charge CPU, capture des écrans protégés** : interdits ou illisibles pour une app Android normale.
- **APK** : à compiler chez toi (BUILD.md) — rien n'a été exécuté sur un vrai téléphone.
