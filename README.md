# Capri — l'écosystème (Desktop + Android)

Une seule Capri, sur ton PC et sur ton téléphone, avec **la même mémoire**. Ce que tu lui dis sur Capri Desktop, elle le sait aussi sur Capri Android, et l'inverse.

| | Dossier | Quoi |
|---|---|---|
| 🖥️ **Capri Desktop** | [`desktop/`](desktop/) | La compagne de bureau Windows (C# 5, .NET Framework 4.8), avec **Capri Link** intégré (menu de Capri > *Capri Link…*). Port Mac/Linux dans `desktop/cross/`. |
| 📱 **Capri Android** | [`android/`](android/) | L'assistante Android (Kotlin) : bulle, « Hey Capri », vision, mains, modes, et la synchro **Capri Link**. |
| 🔗 **Le contrat** | [`contract/`](contract/) | Ce que les deux doivent faire pareil : protocole Capri Link v2, règles de la mémoire partagée, vecteurs de test. |
| 📚 **La doc** | [`docs/`](docs/) | Audit et plan, outils à installer, guide Capri Link. |
| 🔧 **Outils** | [`tools/`](tools/) | Tests du moteur Android sans SDK, génération du vecteur de test. |

Version unique de l'écosystème : **2.0.0** (fichier [`VERSION`](VERSION)), la même pour Desktop et Android.

## Démarrer

1. **Installer les outils et mettre le code sur ton GitHub** : [docs/OUTILS.md](docs/OUTILS.md). GitHub compile Capri Desktop et Capri Android pour toi.
2. **Relier le PC et le téléphone** : [docs/CAPRI-LINK.md](docs/CAPRI-LINK.md). Deux minutes : un code à taper, une fois.
3. Ce qui reste à construire, phase par phase : [docs/AUDIT-ECOSYSTEME.md](docs/AUDIT-ECOSYSTEME.md).

## Où en est l'écosystème

| Phase | État |
|---|---|
| P0 Audit, architecture, plan | ✅ [docs/AUDIT-ECOSYSTEME.md](docs/AUDIT-ECOSYSTEME.md) |
| P1 Dépôt unique, branches, version 2.0.0 | ✅ |
| P2 Contrat commun et vecteurs de test | ✅ [contract/](contract/) |
| P3 Capri Link + mémoire partagée | ✅ codé et testé côté téléphone ; côté PC à confirmer par la première compilation Windows (voir ci-dessous) |
| P4 Rappels, projets et contexte enrichis sur les deux | à faire |
| P5 Update Engine (mises à jour signées depuis kenikorb28/capri) | à faire — le canal de mise à jour de Desktop pointe déjà vers ton dépôt |
| P6 et suivantes (parité des fonctions, Code/Husk Mode, Skills, plugins…) | à faire |

**À savoir, honnêtement :** l'environnement où ce code a été écrit n'a pas de compilateur Windows. Le côté Android de Capri Link est compilé et testé (53 tests du moteur, dont un faux PC qui suit le contrat). Le côté PC (`desktop/app/Link.cs`) a été relu et vérifié ligne par ligne, mais **sa première vraie compilation se fera sur GitHub** : le workflow *Capri Desktop* compile tout et lance le test automatique de Capri, qui contient un vrai appairage et une vraie synchro en local. Si quelque chose y échoue, le journal dit exactement quoi.

## Branches

| Branche | Rôle |
|---|---|
| `stable` | Les versions d'origine (Desktop 0.8.2, Android 2.0), intactes. Tags `desktop-0.8.2-origine`, `android-2.0-origine`. |
| `development` | L'écosystème en cours : c'est la branche à utiliser. |
| `android` | Suit `development` pour le travail centré sur Android. |
| `feature/*` | Une fonction en préparation (ici `feature/capri-link`, fusionnée dans `development`). |

## Crédits et licence

Capri Desktop est le projet **Capri** de Lachy — https://github.com/lachydotmcg/capri-desktop — sous licence **PolyForm Noncommercial 1.0.0** (usage non commercial, avec ce crédit obligatoire). Les fichiers `LICENSE.md` de `desktop/` et `android/` s'appliquent. Fan work : Capri n'est ni approuvée ni soutenue par CD PROJEKT RED, les ayants droit de l'anime ou ses doubleuses.
