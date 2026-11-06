# Contrat commun Capri Desktop ↔ Capri Android

Ce dossier décrit ce que les deux applications doivent faire **exactement pareil** pour que Capri soit une seule et même Capri, avec une seule mémoire, sur le PC et sur le téléphone.

| Fichier | Contenu |
|---|---|
| [link-v2.md](link-v2.md) | Le protocole Capri Link v2 : découverte, appairage, chiffrement, opérations. |
| [memory-v2.md](memory-v2.md) | Les fichiers de mémoire partagés et les règles de fusion. |
| [vectors/link-v2-crypto.json](vectors/link-v2-crypto.json) | Vecteur de test : mêmes clés et même message chiffré attendus des deux côtés. |

## Qui implémente quoi

| | Capri Desktop (Windows, C# 5 / .NET Framework 4.8) | Capri Android (Kotlin) |
|---|---|---|
| Serveur Capri Link | `desktop/app/Link.cs` | — |
| Client, fusion, synchro | — | `android/core/src/main/kotlin/com/capri/core/link/` |
| Intégration dans l'app | `Link.cs` (partie `Companion`), `Brain.cs` | `android/app/.../sync/SyncHub.kt`, `LinkJob.kt` |
| Tests | smoke test Windows : `Link.Checks` (vecteur + appairage et synchro réels en local) | `LinkTest.kt` (vecteur + un faux PC qui suit ce contrat) |

Le téléphone fait **toutes** les fusions ; le PC sert ses fichiers et n'accepte les fichiers fusionnés que s'ils n'ont pas changé entre-temps. Ainsi une seule implémentation de la fusion existe, et elle est testée.

## Règles de modification

1. Toute modification du protocole ou des règles de fusion se fait **ici d'abord**, puis dans les deux applications, dans le même commit ou la même branche.
2. Une modification incompatible change le numéro de version (`"link": 2` → `3`) et garde l'ancienne version lisible tant qu'un appareil peut encore l'utiliser.
3. Le vecteur se régénère avec `python3 tools/make_vectors.py` (aucune dépendance) ; les deux suites de tests le relisent.

## Lancer les tests

- Moteur Android (sans SDK Android) : `bash tools/test-core.sh android build/core` depuis la racine, ou `./gradlew :core:test` dans `android/`.
- Capri Desktop : `desktop/ci/build-windows.ps1 -SmokeTest` sur Windows, ou le workflow GitHub **Capri Desktop**.
