# Capri — l'écosystème (Desktop + Android)

Une seule Capri, sur ton PC et sur ton téléphone, avec **la même mémoire**. Ce que tu lui dis sur Capri Desktop, elle le sait aussi sur Capri Android, et l'inverse.

| | Dossier | Quoi |
|---|---|---|
| 🖥️ **Capri Desktop** | [`desktop/`](desktop/) | La compagne de bureau Windows (C# 5, .NET Framework 4.8), avec **Capri Link** intégré (menu de Capri > *Capri Link…*). Port Mac/Linux dans `desktop/cross/`. |
| 📱 **Capri Android** | [`android/`](android/) | L'assistante Android (Kotlin) : bulle, « Hey Capri », vision, mains, modes, et la synchro **Capri Link**. |
| 🔗 **Le contrat** | [`contract/`](contract/) | Ce que les deux doivent faire pareil : protocole Capri Link v2, règles de la mémoire partagée, vecteurs de test. |
| 📚 **La doc** | [`docs/`](docs/) | Audit et plan, outils à installer, guide Capri Link. |
| 🔧 **Outils** | [`tools/`](tools/) | Tests du moteur Android sans SDK, génération du vecteur de test. |

Version unique de l'écosystème : **2.0.1** (fichier [`VERSION`](VERSION)), la même pour Desktop et Android.

**Télécharger Capri :** les deux boutons sont [en bas de cette page](#télécharger-capri).

## Démarrer

1. **Installer Capri** sur le PC et sur le téléphone : les deux boutons [en bas de cette page](#télécharger-capri).
2. **Relier le PC et le téléphone** : [docs/CAPRI-LINK.md](docs/CAPRI-LINK.md). Deux minutes : un code à taper, une fois.
3. **Publier une nouvelle version** quand le code a changé : [docs/PUBLIER.md](docs/PUBLIER.md). GitHub compile, teste et publie les deux applications.
4. Travailler sur le code (outils à installer, compilation de tous les jours) : [docs/OUTILS.md](docs/OUTILS.md).
5. Ce qui reste à construire, phase par phase : [docs/AUDIT-ECOSYSTEME.md](docs/AUDIT-ECOSYSTEME.md).

## Où en est l'écosystème

| Phase | État |
|---|---|
| P0 Audit, architecture, plan | ✅ [docs/AUDIT-ECOSYSTEME.md](docs/AUDIT-ECOSYSTEME.md) |
| P1 Dépôt unique, branches, version 2.0.0 | ✅ |
| P2 Contrat commun et vecteurs de test | ✅ [contract/](contract/) |
| P3 Capri Link + mémoire partagée | ✅ codé et testé des deux côtés par GitHub (voir ci-dessous) |
| P4 Rappels, projets et contexte enrichis sur les deux | à faire |
| P5 Update Engine (mises à jour depuis kenikorb28/capri) | en partie ✅ : versions publiées par GitHub ([docs/PUBLIER.md](docs/PUBLIER.md)), Capri Desktop se met à jour seule et vérifie la somme de l'installeur, APK toujours signé avec la même clé. À faire : la mise à jour proposée dans Capri Android elle-même |
| P6 et suivantes (parité des fonctions, Code/Husk Mode, Skills, plugins…) | à faire |

**À savoir, honnêtement :** chaque compilation sur GitHub lance le test automatique de Capri Desktop sous Windows (plus de mille vérifications), dont celles de Capri Link : même chiffrement que le téléphone (vecteur commun), échange de clés ECDH P-256, vrai appairage et vraie synchro en local. Le moteur Android a ses propres tests, dont un faux PC qui suit le contrat. Ce que ces tests ne remplacent pas : un essai entre ton vrai PC et ton vrai téléphone, sur ton réseau.

## Branches

| Branche | Rôle |
|---|---|
| `stable` | Les versions d'origine (Desktop 0.8.2, Android 2.0), intactes. Tags `desktop-0.8.2-origine`, `android-2.0-origine`. |
| `development` | L'écosystème en cours : c'est la branche à utiliser. |
| `android` | Suit `development` pour le travail centré sur Android. |
| `feature/*` | Une fonction en préparation, fusionnée dans `development` quand elle est prête. |

## Crédits et licence

Capri Desktop est le projet **Capri** de Lachy — https://github.com/lachydotmcg/capri-desktop — sous licence **PolyForm Noncommercial 1.0.0** (usage non commercial, avec ce crédit obligatoire). Les fichiers `LICENSE.md` de `desktop/` et `android/` s'appliquent. Fan work : Capri n'est ni approuvée ni soutenue par CD PROJEKT RED, les ayants droit de l'anime ou ses doubleuses.

## Télécharger Capri

<p align="center">
  <a href="https://github.com/kenikorb28/capri/releases/latest/download/Capri-Setup.exe"><img src="docs/images/telecharger-desktop.svg" alt="Télécharger Capri Desktop pour Windows" width="330"></a>
  &nbsp;&nbsp;
  <a href="https://github.com/kenikorb28/capri/releases/latest/download/Capri-Android.apk"><img src="docs/images/telecharger-android.svg" alt="Télécharger Capri Android (APK)" width="330"></a>
</p>

<p align="center"><sub>Toujours la dernière version publiée · <a href="https://github.com/kenikorb28/capri/releases/latest">notes de version et sommes de contrôle</a></sub></p>

- **Windows** : lance `Capri-Setup.exe`. Si Windows affiche « Windows a protégé votre ordinateur », clique sur *Informations complémentaires* puis *Exécuter quand même* : l'installeur n'a pas de certificat payant. Une Capri déjà installée est mise à jour et garde sa mémoire.
- **Android** (11 ou plus) : ouvre `Capri-Android.apk` sur le téléphone et autorise l'installation depuis cette source. Par-dessus une Capri signée avec la même clé, c'est une mise à jour qui garde sa mémoire.

Version personnelle, pour adultes : la personnalité de Capri y est adaptée à un usage adulte. La Capri d'origine de Lachy est sur https://github.com/lachydotmcg/capri-desktop.
