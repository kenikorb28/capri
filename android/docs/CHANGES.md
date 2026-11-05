# Fichiers créés et modifiés

**CapriDesktop (ton projet 0.8.2) : aucun fichier modifié.** Tout est dans un projet séparé, `capri-android/`. `LICENSE.md` y est copié tel quel depuis CapriDesktop (obligation de la licence PolyForm Noncommercial).

## Fichiers créés

### Racine et build
| Fichier | Raison |
|---|---|
| `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties` | Projet Gradle à deux modules (`:core`, `:app`), AGP 8.13.2, Kotlin 2.2.21 |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/*` | Gradle 8.14.3 sans installation |
| `core/build.gradle.kts` | Moteur en Kotlin pur (JVM 17), testable sans téléphone |
| `app/build.gradle.kts`, `app/proguard-rules.pro` | App Android : minSdk 30, cible 36, arm64 + x86_64, ML Kit, signature release par variables d'environnement |
| `.github/workflows/android.yml` | Compiler l'APK debug sur GitHub si besoin |
| `README.md`, `NOTICE.md`, `LICENSE.md` | Présentation, mentions obligatoires, licence |

### CapriCore — `core/src/main/kotlin/com/capri/core/`
| Fichier | Raison |
|---|---|
| `Json.kt`, `Text.kt`, `Http.kt` | Bases sans dépendance : JSON, regex Unicode identiques au téléphone, HTTP annulable, clés jamais en http public |
| `Persona.kt` | Personnalité et règles de sécurité de Capri (verbatim), `personality.md` |
| `Providers.kt`, `Backends.kt` | Les 8 fournisseurs du PC, reconnaissance des clés, erreurs expliquées |
| `Brain.kt` | Capri Brain : phrases directes, boucle d'outils, « act », réponses dans sa voix ; 2.0 : modes, contexte, rappel « on en était où », plan, mode sans cerveau |
| `Tools.kt` | Outils, niveaux de risque, SÉCURITÉ, plugins (`Toolbox.register`) |
| `Intents.kt` | Phrases directes FR/EN (sans cerveau), et phrases des plugins (`Intents.route`) |
| `Memory.kt` | Mémoire temporaire, projet, permanente ; afficher, oublier, tout effacer |
| `Reminders.kt`, `Cleanup.kt` | Rappels/routines en 6 langues ; nettoyage des réponses |
| `Web.kt`, `Docs.kt`, `Sense.kt` | Recherche web avec sources ; lecture des documents ; tri des notifications et modes |
| `Wake.kt` | « Hey Capri » : MFCC + DTW, ta voix, sur le téléphone |
| `Sync.kt` | Chiffrement et client de Capri Sync |
| `core/src/test/.../CoreTest.kt`, `Core2Test.kt` | 38 tests |

### CapriAndroid — `app/src/main/`
| Fichier | Raison |
|---|---|
| `AndroidManifest.xml` | Permissions minimales expliquées, 27 composants |
| `res/…`, `assets/…` | Thèmes, icônes, police Share Tech Mono (OFL), sprites et voix de Capri, configurations (accessibilité, assistant, reconnaissance, réseau, raccourcis) |
| `CapriApp.kt`, `engine/Capri.kt` | Une seule Capri partagée par toutes les fenêtres ; LOCAL/HYBRIDE/CLOUD ; honnêteté hors ligne |
| `engine/PhoneHands.kt` | Les mains : chaque outil réalisé avec les API Android |
| `data/Prefs.kt` | Réglages, clés chiffrées par le Keystore |
| `system/Phone.kt`, `DeviceInfo.kt`, `Watches.kt`, `Bridge.kt`, `Alarms.kt` | Apps, liens, musique, média, réglages, lampe, Ne pas déranger, horloge, agenda, contacts ; état du téléphone ; surveillances et alertes ; sélecteur/caméra/presse-papiers ; rappels |
| `screen/CapriAccessibility.kt`, `ScreenTools.kt`, `PhoneAgent.kt` | Lire/toucher/capturer sur demande ; MediaProjection ; OCR ; « Fais-le pour moi » |
| `voice/Ears.kt`, `Mouth.kt`, `WakeListener.kt` | Écoute, voix (+ interruption), « Hey Capri » |
| `overlay/OverlayService.kt`, `BubbleView.kt`, `PanelView.kt`, `GamingHud.kt` | Bulle, panneau compact/complet avec le menu 2.0, HUD de jeu |
| `assist/Assist.kt` | Assistant par défaut, relais de reconnaissance, notifications, tuile, sélection de texte, partage |
| `sync/SyncHub.kt` | Capri Sync côté téléphone |
| `plugins/Plugins.kt` | Exemple de plugins (calculatrice, tirage au sort) |
| `ui/*.kt` | Accueil, Chat, Réglages, Capri Sécurité (+ guide TECNO), Mémoire, Capri Device, Outils et plugins, Hey Capri, éditeur, écoute |
| `app/src/test/.../CalcTest.kt` | Tests du plugin |

### Capri Sync — `pc-sync/`
| Fichier | Raison |
|---|---|
| `CapriSync.cs` | Programme Windows séparé (C# 5) : serveur Wi-Fi local chiffré, appairage, conversations, mémoire, projets, fichiers |
| `build-sync.ps1` | Compilation avec le `csc.exe` de Windows + auto-test |

### Documentation — `docs/`
`AUDIT.md`, `ARCHITECTURE.md`, `BUILD.md`, `INSTALL_TECNO_CL6K.md`, `PERMISSIONS.md`, `SYNC.md`, `STATUS.md`, `TEST_REPORT.md`, ce fichier.

## Modifiés lors de l'ajout du cahier des charges 2.0 (sur la base déjà créée)

| Fichier | Modification | Raison (section du cahier) |
|---|---|---|
| `core/Intents.kt` | « regarde ce que j'ai à l'écran », « regarde ça / cette page / ce code », « aide-moi avec ça », « descends / remonte la page », « appuie sur … », notifications par catégorie, « récupère les fichiers du PC », « attends », phrases des plugins | §7, 13, 23, 26, 32, 54 |
| `core/Tools.kt` | Supprimer un fichier : choix puis confirmation avec son nom ; action `pull_files` ; liste des plugins | §24, 32, 8 |
| `core/Memory.kt` | Apprentissage spontané désactivable ; « oublie cette information » | §27 |
| `core/Brain.kt` | Respecte ce réglage d'apprentissage ; sans cerveau, dit pourquoi un outil manque | §27, 45 |
| `core/Sense.kt` | Apps scolaires reconnues (Pronote, Classroom…) | §26 |
| `core/Wake.kt` | Comparaison centrée sur le début réel de la phrase (une commande juste après ne gêne plus) | §9, 11 |
| `core/Sync.kt` | IV fixe possible pour le vecteur de test partagé avec le PC | §32 |
| `engine/Capri.kt` | Pas d'internet → commandes simples + message clair ; « Oui ? » au réveil ; apprentissage selon réglage | §15, 12, 27 |
| `engine/PhoneHands.kt` | Taille d'image (VISION), ligne « 📤 envoyé à … », appui direct par nom, suppression nominative, infos d'urgence | §18, 44, 23, 24, 35 |
| `screen/PhoneAgent.kt` | Appui direct « appuie sur X » sans cerveau | §23 |
| `screen/ScreenTools.kt` | Message « je n'ai pas la permission d'accéder à ton écran » | §45 |
| `system/Watches.kt`, `overlay/OverlayService.kt` | Alertes batterie faible / stockage presque plein (au plus une fois par jour) | §31 |
| `overlay/GamingHud.kt` | Boutons 🎙 💬 📊 🔕 | §30 |
| `system/Bridge.kt` | Accès aux fichiers choisis conservé jusqu'à l'usage, puis rendu | §24 |
| `voice/WakeListener.kt` | Mesure du coût processeur réel sur le téléphone | §11 |
| `voice/Mouth.kt` | Flux plus léger en profil Économie | §48 |
| `sync/SyncHub.kt`, `pc-sync/CapriSync.cs` | Fichiers du PC vers le téléphone (bouton et glisser-déposer) | §32 |
| `data/Prefs.kt` | Réglages VISION, alertes, apprentissage, « Oui ? », infos d'urgence | §47 |
| `ui/SettingsActivity.kt`, `MemoryActivity.kt`, `PermissionsActivity.kt`, `Screens.kt`, `ToolsActivity.kt` (nouveau) | Section VISION, tableau 🟢🟡🔴, page Outils et plugins, interrupteurs | §33, 40, 47 |
| `AndroidManifest.xml` | `ToolsActivity` | §40 |
| `CapriApp.kt` | Installe les plugins | §8 |
