# Audit de CapriDesktop → Capri Android 2.0

Projet analysé : l'archive fournie, **capri-desktop-0.8.2** (le cahier des charges parle de 0.7.10 ; c'est bien la 0.8.2 qui était dans l'archive). Dossier `app/` : 61 fichiers C# (~120 000 lignes, dont `Capri.cs` ~40 700). Projet unique (WinForms, C# 5, compilé par `csc.exe` via `ci/`), sans NuGet. **CapriDesktop n'a pas été modifié.**

Dépendances et moteurs identifiés : .NET Framework 4.x (WinForms, Win32 P/Invoke, DXGI pour la capture), DataContractJsonSerializer pour `settings.json`/`chat.json`, sherpa-onnx (Parakeet TDT 0.6B v2/v3) pour les oreilles locales, Kokoro-82M et Qwen3-TTS (qwentts.cpp) pour les voix locales, ElevenLabs / Fish Audio / serveurs OpenAI pour les voix cloud, VB-CABLE pour la voix dans le micro, Discord Rich Presence. Données dans `%LOCALAPPDATA%\Capri` : `settings.json` (clés chiffrées DPAPI), `chat.json`, `me.md`, `memories.md`, `transcripts/AAAA-MM-JJ.md`, `reminders.json`, `routines.json`, `check-ins.md`, `games/`, sous-dossiers de modèles téléchargés (voix, oreilles) par son propre téléchargeur.

## A. Fonctionnalités existantes

- **Cerveau** : 8 fournisseurs (Ollama, serveur OpenAI-compatible, NVIDIA, Gemini, OpenRouter, OpenAI, DeepSeek, Claude) ; reconnaissance des clés ; bascule automatique NVIDIA ; boucle d'outils (WorkOut, 12 tours) ; « act » + Reach pour les petits modèles ; nettoyage des réponses ; relances ; personnalité éditable (`personality.md`) avec règles de sécurité fixes.
- **Mémoire** : notes `me.md` (ou note Obsidian), `memories.md` repliée par le cerveau, journaux du jour, rappel BM25, « ce qu'elle a vu », faire connaissance.
- **Outils (Hands)** : fenêtres, souris, clavier, icônes du bureau, apps, sites (et surnoms de sites), recherche, musique (Spotify/YouTube), média et volume, rappels, minuteurs, routines, fichiers (txt/code/docx/xlsx/pptx/odt), regarder l'écran, lire l'écran (OCR), jeux joués à la souris, écoute de l'audio du PC, dessin, fenêtres arrangées.
- **SÉCURITÉ** : préréglages Lockdown/Guarded/Trusted/Blackwall, « demander d'abord », oui/non.
- **Voix et oreilles** : ElevenLabs, Fish Audio, serveurs OpenAI (Kokoro-FastAPI…), Kokoro et Qwen3-TTS locaux, Parakeet local, reconnaissance Windows, « Hey Capri », chat vocal, appels Discord, voix dans le micro (VB-CABLE).
- **Jeux** : HUD, réactions en direct, notes de jeu, clips vidéo, regarder ensemble.
- **Interface** : la compagne animée sur le bureau (sprites de son artiste), Mainframe (réglages), notifications maison.
- **Divers** : échecs, faux écran bleu et faux clones (gags), Discord Presence, démarrage avec Windows, mises à jour.

## B. Fonctionnalités réutilisables sur Android (portées dans `core/`)

Cerveau et boucle d'outils · fournisseurs et sens des erreurs · personnalité et règles de sécurité (verbatim) · mémoire (me.md, memories.md, journaux, rappel BM25) · rappels et routines en 6 langues · SÉCURITÉ · nettoyage des réponses · lecture des documents · recherche web · formats de fichiers (me.md, transcripts) pour la synchro · sprites et police.

## C. Fonctionnalités dépendantes de Windows (remplacées)

| Windows | Remplacé par |
|---|---|
| Souris, clavier, fenêtres, icônes (`Computer.cs`, `Arrange.cs`, `Icons.cs`) | Intents Android + service d'accessibilité (`PhoneAgent`) |
| Capture DXGI, OCR Windows (`Frames.cs`, `Ocr.cs`) | `takeScreenshot` d'accessibilité / MediaProjection + ML Kit |
| Reconnaissance Windows, Parakeet (`Ears.cs`) | `SpeechRecognizer` (téléphone ou sur l'appareil), Whisper sur le PC |
| Kokoro/Qwen3-TTS locaux (300 Mo+) | Synthèse vocale du téléphone ; voix cloud identiques au PC |
| Hey Capri Windows | Moteur maison MFCC + DTW (`core/Wake.kt`) |
| VB-CABLE, appels Discord, mixeur | Sans équivalent sur Android (non porté) |
| HUD de jeu, réactions, clips | HUD adapté (batterie, chaleur, RAM, heure) ; réactions/clips non portés |
| Startup Windows, Discord Presence, mises à jour | `BootReceiver` ; le reste est sans objet |
| Gags (écran bleu, clones), échecs | Non portés (hors du cahier des charges) |

## D. Fonctionnalités Android possibles

Légende : **D** directement · **P** avec permission · **A** avec AccessibilityService · **V** avec VoiceInteractionService · **API** avec une API Android/externe · **~** partiellement · **✗** impossible avec les permissions normales.

| Fonction | Moyen | Cat. | Dans le projet |
|---|---|---|---|
| Bulle + panneau par-dessus les apps | Superposition + service au premier plan `specialUse` | P | Oui |
| Parler / écouter en français | SpeechRecognizer, ou sur l'appareil ; Whisper | P | Oui |
| « Hey Capri » | Moteur maison dans le service `microphone` | P | Oui (écran allumé par défaut) |
| Hotword système écran éteint | `AlwaysOnHotwordDetector` réservé aux apps privilégiées | ✗ | Moteur maison à la place |
| Assistant par défaut (appui long) | Rôle ASSISTANT | V | Oui |
| Comprendre l'écran | `takeScreenshot` ou MediaProjection + vision/OCR | A / P | Oui |
| Écrans protégés (`FLAG_SECURE`) | Android refuse | ✗ | Dit honnêtement |
| Ouvrir apps, liens, réglages, Wi-Fi, Bluetooth, musique, réveils, agenda | Intents | D | Oui |
| Appeler, SMS, e-mail, partager | Intents (dernier appui par l'utilisateur) | D | Oui |
| Retour, Accueil, défiler, appuyer sur un bouton | `performGlobalAction`, nœuds, gestes | A | Oui |
| « Fais-le pour moi » | Accessibilité pas à pas, confirmé pour le sensible | A | Oui |
| Lampe, volume, média | CameraManager, AudioManager | D | Oui |
| Ne pas déranger | NotificationManager + accès accordé | P | Oui |
| Ce qui joue | MediaSessionManager via l'accès aux notifications | P | Oui |
| Notifications intelligentes | NotificationListenerService | P | Oui |
| Presse-papiers | Lecture seulement avec le focus (Android 10+) | ~ | Oui (panneau ou écran transparent) |
| Caméra | App Photo du téléphone (`ACTION_IMAGE_CAPTURE`) | D | Oui |
| Fichiers | Sélecteur système (SAF) | D | Oui |
| Batterie, chaleur, RAM, stockage, réseau, casque | BatteryManager, PowerManager, ActivityManager, StatFs… | D | Oui |
| Charge CPU, FPS des jeux | Non lisibles par une app | ✗ | Non affichés, dit honnêtement |
| Synchro PC | Réseau local + Capri Sync | API | Oui |
| IA sur le téléphone | Choisi : pas pour l'instant | — | Non |
| Root, contournements | — | — | Jamais |

## E. Architecture proposée (et réalisée)

Voir [ARCHITECTURE.md](ARCHITECTURE.md) : **CapriCore** (`core/`, Kotlin pur testé sur PC) → **CapriAndroid** (`app/`) ; **Capri Sync** (`pc-sync/`) à côté de **CapriDesktop** inchangé. Kotlin natif plutôt que .NET MAUI : le code PC est lié à WinForms/Win32, MAUI ajouterait ~30–40 Mo de runtime et ne réutiliserait presque rien des parties Windows ; le moteur, lui, a été porté et se teste sans téléphone.

## F. Plan de migration suivi

| Phase | Contenu | État |
|---|---|---|
| 1 Audit | ce document | fait |
| 2 Capri Core | cerveau, mémoire, sécurité, outils, rappels, web, docs, notifications, modes, Hey Capri, chiffrement de synchro | fait, 38 tests JVM |
| 3 Client Android | projet Gradle, manifeste, services | fait, vérifié à la compilation |
| 4 Interface | bulle, panneau compact/complet, écrans | fait |
| 5 Voix | micro, STT, TTS, conversation, interruption | fait |
| 6 Assistant système | VoiceInteractionService, session, reconnaissance relayée | fait |
| 7 Vision | capture autorisée, OCR, image, caméra | fait |
| 8 Outils | Android, apps, web, fichiers, presse-papiers, notifications, plugins | fait |
| 9 Mémoire | temporaire, projet, permanente | fait |
| 10 Modes | Étude, Dev, Jeu, Device | fait |
| 11 Synchro | Capri Sync | fait (côté PC non compilé ici) |
| 12–13 Optimisation, sécurité | profils, risques, centre de sécurité | fait |
| 14 Tests réels | sur ton TECNO CL6k | **à faire par toi** (plan dans TEST_REPORT.md) |
