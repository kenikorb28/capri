# CAPRI ANDROID 2.0

Capri, ta compagne de CapriDesktop, devenue une assistante qui vit **par-dessus Android** : une bulle animée au-dessus de tes apps, « Hey Capri » avec ta propre voix, l'écran compris quand tu le demandes, des mains pour agir (avec ta confirmation pour tout ce qui est sensible), une mémoire en trois couches, les modes Étude / Dev / Jeu, et Capri Sync pour continuer avec ton PC. Pensée pour le **TECNO CL6k** (Helio G99, Android 16, HiOS).

> Fan work: Capri is an unofficial fan project and is not approved or endorsed by CD PROJEKT RED, the anime's rights holders or its voice actors.

## Structure

```
capri-android/
├── core/      CapriCore : le moteur en Kotlin pur (cerveau, mémoire, sécurité, outils, Hey Capri, chiffrement) — 38 tests
├── app/       CapriAndroid : l'app (bulle, voix, vision, mains, assistant système, réglages)
├── pc-sync/   Capri Sync : petit programme Windows (C# 5) pour la synchro Wi-Fi — CapriDesktop reste intact
└── docs/      audit, architecture, compilation, installation, permissions, synchro, état, tests, changements
```

## Démarrer

1. **Compiler** l'APK avec Android Studio : [docs/BUILD.md](docs/BUILD.md).
2. **Installer et régler** sur le TECNO : [docs/INSTALL_TECNO_CL6K.md](docs/INSTALL_TECNO_CL6K.md).
3. **Brancher un cerveau** : une clé gratuite NVIDIA ou Gemini (CLOUD), Ollama sur ton PC (LOCAL), ou les deux (HYBRIDE).
4. Facultatif : **Capri Sync** sur le PC : [docs/SYNC.md](docs/SYNC.md).

## Ce qu'elle sait faire

Dis « Hey Capri », attends le bip, puis par exemple :

- « Regarde ce que j'ai à l'écran et explique-moi ce que je dois faire » → « Fais-le » → (si c'est sensible) « oui » → plus tard « On en était où ? »
- « Ouvre YouTube », « monte le son », « allume la lampe », « mets un réveil à 6 h 30 », « rappelle-moi demain de travailler sur Capri »
- « Résume mes notifications importantes », « traduis ce que j'ai copié », « prends une photo et dis-moi ce que c'est »
- « Passe en mode étude débutant », « mode dev », « mode jeu »
- « Qu'est-ce que tu as mémorisé sur moi ? », « oublie cette information »
- « Envoie cette conversation sur mon PC », « récupère les fichiers du PC »
- « Combien font 15 % de 2000 ? » (plugin, marche hors ligne)

L'état exact de chaque fonction, sans exagération : [docs/STATUS.md](docs/STATUS.md). Les résultats réels des tests : [docs/TEST_REPORT.md](docs/TEST_REPORT.md).

## Ses principes

Elle ne regarde, n'écoute et n'agit que quand tu le demandes ; chaque intégration se coupe dans **Capri Sécurité** ; envoyer, payer, supprimer ou effacer sa mémoire demande toujours ton « oui » ; jamais de mot de passe lu ou tapé ; jamais de root ni de contournement d'Android ; tes clés API restent chiffrées sur le téléphone ; elle dit honnêtement quand internet, une permission ou Android l'empêchent de faire quelque chose. Détails : [docs/PERMISSIONS.md](docs/PERMISSIONS.md).

## Licence et crédits

Capri appartient au projet [Capri](https://github.com/lachydotmcg/capri-desktop) (PolyForm Noncommercial 1.0.0) : usage non commercial seulement. Son art et ses animations sont de son artiste, qui préfère rester anonyme. Voir [LICENSE.md](LICENSE.md) et [NOTICE.md](NOTICE.md).
