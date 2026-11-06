# Architecture de Capri Android 2.0

## Les quatre parties

```
CapriDesktop   ← ton projet 0.8.2, inchangé (Windows, C#)
CapriCore      ← core/   : le moteur en Kotlin pur (JVM), sans aucune classe Android, testé sur PC
CapriAndroid   ← app/    : l'app Android (bulle, voix, écran, mains, réglages)
Capri Link     ← le serveur est dans Capri Desktop (desktop/app/Link.cs) ; pc-sync/ (Capri Sync v1) n'est plus utilisé
```

Pourquoi Kotlin natif plutôt que .NET MAUI : le code PC mélange C# 5, WinForms et Win32 ; MAUI ajouterait 30 à 40 Mo de runtime Mono, une chaîne de compilation plus lourde, et ne réutiliserait presque rien des parties Windows. Le moteur (cerveau, mémoire, sécurité, rappels, nettoyage) a été porté ligne à ligne en Kotlin et se teste sans téléphone.

## Modules du moteur (`core/`)

```
            ┌──────────── Brain (ask) ────────────┐
 texte/voix │ phrases directes → Intents          │
 ─────────► │ cerveau fort → workOut (12 tours)   │──► Hands (interface) ──► PhoneHands (app)
            │ petit cerveau → « act » → reach     │
            │ résultats → finish (sa propre voix) │
            └──┬──────────┬──────────┬───────────┘
               │          │          │
          Persona     Memory     Security/Toolbox (Risk LOW/MEDIUM/HIGH)
          (persona,   (me.md,    Reminders, Web, Docs, NotificationSense,
           sécurité)   memories,  Modes, Wake (Hey Capri), Sync (chiffrement)
                       projets)
```

- **Brain** : un tour de conversation. Sans cerveau branché, les commandes simples passent quand même (« ouvre WhatsApp », « pause », « lampe »), et tout le reste répond honnêtement « mon cerveau est hors ligne ».
- **Toolbox** : 39 outils intégrés, plus les plugins. Un plugin = un `ToolSpec` (nom, description, schéma des paramètres, validation `required`, interrupteur SÉCURITÉ `toggle`, groupe, besoin d'internet, niveau de risque) passé à `Toolbox.register`, ses mains passées à `PhoneHands.plug(nom) { args -> … }`, et, s'il veut, des phrases directes via `Intents.route` (elles marchent même hors ligne). Exemple livré : `app/…/plugins/Plugins.kt` (calculatrice exacte et tirage au sort), sans aucune modification du cœur. La page **Outils et plugins** liste chaque outil, son risque, son interrupteur et pourquoi il est indisponible.
- **Security** : préréglages du PC + niveaux de risque. **HIGH demande toujours** (envoyer, payer, supprimer, effacer la mémoire, appeler le contact d'urgence), quel que soit le préréglage.
- **Memory** : trois couches — temporaire (le chat en cours, 8/16/32 messages, et « ce qu'elle a vu »), projet (`projects/<nom>.md` : le projet, architecture, décisions, dernière tâche, notes), permanente (`me.md`, `memories.md`, journaux du jour). « Qu'est-ce que tu as mémorisé sur moi ? », oublier une note, tout effacer.
- **Wake** : MFCC 13 coefficients (16 kHz, 25 ms / 10 ms, FFT 512, 26 bandes mel), détection de parole par plancher de bruit adaptatif, DTW à fin ouverte contre les 3 prises de l'utilisateur.
- **Sync** : PBKDF2-HMAC-SHA256 (20 000 itérations) du code d'appairage, puis AES-256-CBC + HMAC-SHA256 (chiffrer puis signer), horodatage contre le rejeu.

## L'app (`app/`)

```
CapriApp ─► Capri (engine/Capri.kt) : un seul cerveau, une seule mémoire, partagés par toutes les fenêtres
  ├─ PhoneHands   : chaque outil → intents, accessibilité, notifications, SAF, caméra…
  ├─ Mouth        : voix du téléphone / ElevenLabs / Fish Audio / serveur OpenAI ; BargeIn (interruption)
  ├─ Ears         : SpeechRecognizer (téléphone ou sur l'appareil) / Whisper sur le PC
  ├─ WakeListener : « Hey Capri » dans le service au premier plan (type micro)
  ├─ NoticeBox    : notifications du jour, en mémoire uniquement
  ├─ Watches      : batterie, Wi-Fi, notification de quelqu'un
  └─ SyncHub      : Capri Link côté téléphone (core/link : client, fusion, MemorySync ; sync/LinkJob : toutes les 15 min)

overlay/  OverlayService (FGS specialUse|microphone) ─ BubbleView ─ PanelView (menu 2.0) ─ GamingHud
screen/   CapriAccessibility (lire, toucher, capturer) ─ Capture (MediaProjection) ─ PhoneAgent (« Fais-le »)
assist/   VoiceInteractionService + Session (assistant par défaut) ─ RecognitionService (relais)
          ─ NotificationWatcher ─ Tuile ─ « Expliquer avec Capri » (sélection de texte) ─ Partage
ui/       Accueil, Chat, Réglages, Capri Sécurité (+ guide TECNO), Mémoire, Capri Device, Hey Capri
system/   Phone.kt (apps, liens, musique, média, réglages, lampe, NPD, horloge, agenda, contacts),
          DeviceInfo, Watches, Bridge (sélecteur, caméra, presse-papiers, permissions), Alarms
```

## Confidentialité par construction

- Rien n'est lu, écouté ou capturé en arrière-plan : l'écran seulement sur demande (bulle cachée pendant la capture), le micro seulement pour parler ou pour « Hey Capri » (avec le point micro d'Android), les notifications gardées un jour en mémoire vive.
- Quand une capture, une photo ou un fichier part vers un cerveau, une ligne « 📤 … envoyé(e) à … » le dit dans le chat.
- Les codes à usage unique des notifications sont masqués avant d'aller au cerveau ; les champs mot de passe ne sont jamais lus ni tapés.
- Les clés API sont chiffrées par le Keystore Android, jamais écrites en clair ni dans le journal (`Redact`), jamais envoyées en http vers une adresse publique.

## Le parcours « Hey Capri → analyse → Fais-le → on en était où ? »

1. `WakeListener` entend sa voix → `Capri.wakeHeard()` → bip → `Ears.listen()`.
2. « Regarde mon écran » → `Intents` → `look_at_screen` → `PhoneHands.look` cache la bulle, capture (accessibilité ou MediaProjection), le cerveau décrit l'image (ou OCR), `Brain.finish` répond avec sa voix, à voix haute.
3. « Fais-le » → le cerveau (avec l'historique) appelle `use_phone` → `PhoneAgent` avance étape par étape ; un bouton « Envoyer / Payer / Supprimer » déclenche `Security.hold` → elle demande, le panneau montre **OUI, FAIS-LE / NON** → « oui » relance la tâche confirmée.
4. Plus tard, « on en était où ? » → `recap` : derniers échanges, mémoire du projet, rappels en attente.

## Règles Android 16 prises en compte

- Bords à bords imposé : chaque écran se décale avec les `WindowInsets`.
- Retour prédictif : `OnBackInvokedCallback` (le panneau et le chat), plus de `onBackPressed`.
- Services au premier plan typés : `specialUse` (bulle), `microphone` (seulement lancé depuis l'app visible, la notification ou l'assistant), `mediaProjection` (une image puis arrêt).
- Micro en arrière-plan interdit : sinon elle écoute via un écran transparent (`ListenActivity`).
- MediaProjection : accord à chaque capture, jeton à usage unique, callback enregistré avant l'affichage virtuel.
- Paramètres restreints (Android 15+) pour une app installée par APK : expliqués dans Capri Sécurité.
- Lancement d'activités depuis l'arrière-plan : permis par la permission de superposition accordée.
