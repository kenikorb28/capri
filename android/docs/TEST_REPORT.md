# Rapport de tests — Capri Android 2.0

Date : 5 novembre 2026. Environnement : conteneur Linux, JDK 21, compilateur Kotlin 2.0.21 (celui fourni avec Gradle 8.14.3 ; le projet utilise Kotlin 2.2.21), JUnit 4.13.2, `android.jar` réel de l'API 36. **Pas de SDK Android complet, pas d'émulateur, pas de téléphone, pas de compilateur C#.** Seuls des résultats réellement obtenus figurent ci-dessous.

## 1. Moteur (`core/`) — 38 tests JUnit : **38 réussis, 0 échec**

Commande : compilation de `core/src/main` + `core/src/test`, puis `JUnitCore com.capri.core.CoreTest com.capri.core.Core2Test` → `OK (38 tests)` en ~2,5 s.

| Domaine | Tests |
|---|---|
| JSON, clés, regex Unicode identiques au téléphone | jsonRoundTrip, keysAreToldApart, unicodeBoundariesMatchOnTheJvmLikeOnAndroid |
| Rappels FR/EN, routines | timesInFrenchAndEnglish, reminderAsksAreRouted, remindersSaveCancelAndRing |
| Phrases directes (1.0 et 2.0, dont la phrase du critère final) | plainAsksGoStraightToHerHands, capri2PhrasesGoStraightToHerTools, writtenCallsAreRead |
| Nettoyage, personnalité et règles de sécurité | repliesAreCleanedLikeTheDesktop, personalityKeepsHerSafetyLines |
| Mémoire : notes, souvenirs, rappel, projets, « ce que tu sais », tout effacer, apprentissage sur accord, « oublie cette information » | notesMemoriesAndRecall, projectsKeepTheirDecisionsAndTheLastTask, sheSaysWhatSheRemembersAndWipesItAll, sheOnlyLearnsOnHerOwnWhenAllowedAndForgetsTheLastNote |
| Sécurité et confirmations | securityAsksFirstAndRunsOnYes, securityHoldsAPlainAskUntilTheySayYes, highRiskAlwaysAsksEvenWhenEverythingIsOn, deletingAFileAsksWithItsNameThenRunsOnYes |
| Cerveau de bout en bout contre un faux serveur HTTP local (requêtes réelles) | aStrongBrainWorksTheJobOutWithItsTools, aSmallBrainSaysActAndItsHandsPick, claudeGetsItsOwnRequestShape, ollamaSpeaksItsOwnApi, aPluginToolJoinsHerKit, onEnEtaitOuBringsBackTheChatAndTheProject, modesChangeHowSheAnswersNeverHerSafety, withNoBrainSheStillDoesPlainThingsAndSaysSoForTheRest |
| Fournisseurs, clés jamais en http public | modelListsAreSortedTheDesktopWay, keysNeverGoOverPlainHttpToTheInternet |
| Web, documents, notifications | webResultsAreReadWithTheirSources, documentsAreReadLikeOnTheDesktop, notificationsAreSortedAndCodesStayHidden |
| Capri Sync : PBKDF2 identique à celui du JDK, AES+HMAC, modification et rejeu refusés, appairage et appels contre un faux PC HTTP, fusion des notes | syncLockIsStandardPbkdf2AndCatchesChanges, phonePairsWithThePcAndTalksOnlyWithItsKey, syncMergesNotesAndTagsTheOtherSidesLines |
| Hey Capri | heyCapriWakesOnTheirPhraseAndNotOnOthers, heyCapriTakesThatCantBeUsedAreRefused, heyCapriCostsLittleProcessor |

**Hey Capri, chiffres mesurés** (voix *synthétisée* par un générateur de formants, pas une vraie voix) : seuil appris 1,383 ; ta phrase 0,975 (réveil, une seule fois) ; « OK Google » 4,782 (pas de réveil) ; pièce silencieuse : aucun réveil ; « Hey Capri » suivi d'une commande sans pause : réveil. Coût : 0,12 à 0,20 s de calcul pour 59 s d'audio, soit **0,2–0,4 % d'un cœur** (Xeon 2,8 GHz). Sur le Helio G99, attends-toi à quelques fois plus ; le chiffre réel s'affiche dans Capri Device. Ces tests prouvent la mécanique, **pas** la fiabilité avec ta vraie voix et ta pièce.

## 2. App (`app/`) — tests JVM du plugin : **3 réussis, 0 échec**

`CalcTest` : sumsAreExact, badInputSaysSo, pluginPhrasesRouteWithoutTouchingTheCore (une phrase de plugin passe avant « lance… » sans modifier le cœur).

## 3. App — vérification de compilation : **0 erreur**

Tout `app/src/main/kotlin` (29 fichiers, ~5 500 lignes) compilé avec Kotlin 2.0.21 contre le vrai `android.jar` API 36 et le moteur. Pour `R`, `BuildConfig` et ML Kit, des talons ont été générés : `R` **à partir des vrais fichiers de `res/`**, donc une ressource manquante aurait été une erreur. Résultat : classes produites, seulement des avertissements de dépréciation (`Locale(String,String)`, `SOFT_INPUT_ADJUST_RESIZE`, `isChecked`).

Ce que cette vérification **ne couvre pas** : aapt2 (liaison des ressources), fusion du manifeste, dex, R8, signature, et tout comportement à l'exécution. C'est le rôle de la compilation Gradle chez toi et des tests sur le téléphone.

## 4. Manifeste et XML

- Les 27 composants du manifeste (application, activités, services, récepteurs, fournisseur) existent en classes compilées, chacun avec la bonne classe mère (vérifié avec `javap`) ; les classes citées dans les XML (assistant, reconnaissance, accessibilité, raccourcis) aussi.
- Tous les fichiers XML (`AndroidManifest.xml`, `res/**`) sont bien formés.

## 5. Capri Sync (C#) — **non compilé ici**

Aucun compilateur C# dans cet environnement. Vérifications faites : accolades/parenthèses équilibrées, aucune syntaxe C# 6+ (interpolation, `?.`, `nameof`…). Le protocole a été testé côté téléphone contre un faux PC écrit d'après lui. **À faire chez toi** : `pc-sync\build-sync.ps1`, qui compile et lance `--selftest` (même vecteur de chiffrement que le téléphone).

## 6. Tests à faire sur ton TECNO CL6k

Coche au fur et à mesure ; note ce qui coince et envoie-moi Capri Device > Journal d'erreurs (copié sans clés).

| # | Test | Comment | Attendu |
|---|---|---|---|
| 1 | Installation | BUILD.md | L'app s'installe, icône Capri |
| 2 | Première ouverture | ouvrir l'app | liste POUR COMMENCER |
| 3 | Permissions | Capri Sécurité | chaque bouton ouvre la bonne page ; tableau 🟢🟡🔴 juste |
| 4 | Superposition | Lancer la bulle | bulle visible par-dessus les apps, notification « Capri est là » |
| 5 | Bouton flottant | glisser, toucher, appui long, double-toucher | se colle au bord ; panneau ; écoute ; analyse de l'écran |
| 6 | Microphone | appui long sur la bulle | « J'écoute… », la phrase s'affiche |
| 7 | TTS | Réglages > VOIX > Tester | elle parle en français |
| 8 | Hotword | enregistrer, puis « Hey Capri » | bip puis écoute ; peu de faux réveils |
| 9 | Assistant Android | choisir Capri comme assistant, appui long Accueil | elle écoute ; « regarde mon écran » utilise la capture d'Android |
| 10 | Vision | « regarde mon écran » | description juste ; ligne 📤 si cerveau cloud |
| 11 | OCR | modèle qui ne voit pas, « lis l'écran » | texte lu sur le téléphone |
| 12 | Caméra | « prends une photo et dis-moi ce que c'est » | l'app Photo s'ouvre, puis réponse |
| 13 | Clipboard | copier un texte, Presse-papiers > Traduire | traduction |
| 14 | Notifications | « résume mes notifications importantes » | tri par catégories, codes masqués |
| 15 | Accessibilité | « descends la page », « appuie sur … » | défilement, appui |
| 16 | Ouverture d'applications | « ouvre YouTube », « lance Free Fire » | l'app s'ouvre |
| 17 | Mémoire | « souviens-toi que… », « qu'est-ce que tu as mémorisé sur moi ? », « oublie cette information » | conforme |
| 18 | Plugins | « combien font 15 % de 2000 » | 300, même hors ligne |
| 19 | Mode hors ligne | mode avion, « allume la lampe », puis une question | lampe OK ; message « pas de connexion » clair |
| 20 | Mode cloud | clé NVIDIA/Gemini | réponses, outils |
| 21 | PC ↔ Android | SYNC.md | appairage, conversation, mémoire, fichiers |
| 22 | Batterie | une journée en NORMAL avec Hey Capri | consommation acceptable (Paramètres > Batterie) |
| 23 | RAM | Capri Device | pas de « mémoire presque pleine » à cause de Capri |
| 24 | Rotation écran | tourner pendant le panneau | pas de crash |
| 25 | Mise en veille | écran éteint 10 min | Hey Capri en pause (sauf option), bulle toujours là |
| 26 | Redémarrage du téléphone | redémarrer | rappels gardés ; bulle revient si activé ; micro après ouverture de l'app |
| 27 | Fermeture forcée | forcer l'arrêt puis rouvrir | chat de la semaine retrouvé |
| 28 | Perte Internet | couper le Wi-Fi/données | message honnête, commandes simples OK |
| 29 | Reconnexion Internet | rallumer | tout revient sans redémarrer |
| + | Critère final | Hey Capri → « regarde ce que j'ai à l'écran et explique-moi ce que je dois faire » → « fais-le » → « oui » → plus tard « on en était où ? » | enchaînement complet |
