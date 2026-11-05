# Installer et régler Capri sur le TECNO CL6k (HiOS, Android 16)

Compte 10 minutes la première fois. Tout se règle depuis Capri ; rien ne demande le root ni de couper les protections d'Android.

## 1. Installer

Voir [BUILD.md](BUILD.md). Installée par câble (adb), Capri échappe aux « paramètres restreints » ; installée depuis un fichier APK, fais l'étape 4.

## 2. Première ouverture

L'écran d'accueil montre une liste **POUR COMMENCER** :

1. **Branche un cerveau** (Réglages > CERVEAU) :
   - **CLOUD** : colle une clé gratuite NVIDIA (`nvapi-…`, sur build.nvidia.com) ou Gemini (`AIza…`, sur aistudio.google.com). Le modèle se choisit tout seul ; « Tester le cerveau » vérifie.
   - **LOCAL** : l'adresse de ton PC avec Ollama, par ex. `192.168.1.20:11434`, puis le modèle. Sur le PC, Ollama doit écouter le réseau : variable d'environnement `OLLAMA_HOST=0.0.0.0`, puis redémarre Ollama et autorise-le sur les réseaux privés du pare-feu.
   - **HYBRIDE** : les deux ; ton PC d'abord, le cloud quand il ne répond pas.
2. **Autorise la bulle** (affichage par-dessus les autres apps).
3. **Autorise le micro.**
4. Touche **Lancer la bulle** depuis l'app : lancée ainsi, la bulle a le droit d'utiliser le micro (règle d'Android).

## 3. Les pouvoirs facultatifs (Capri Sécurité)

| Pouvoir | Pour quoi | Où l'activer |
|---|---|---|
| Mains sur l'écran (accessibilité) | lire l'écran, Retour/Accueil, « appuie sur… », « fais-le pour moi », captures sans question à chaque fois | Paramètres > Accessibilité > Applis installées > Capri |
| Lire les notifications | « résume mes notifications », surveillances | Accès aux notifications > Capri |
| Assistant par défaut | appui long sur Accueil/Marche | Applis par défaut > Assistant numérique > Capri |
| Rappels à l'heure exacte | rappels pile à l'heure | Alarmes et rappels > Capri |
| Ne pas déranger | « active Ne pas déranger » | Accès Ne pas déranger > Capri |
| Contacts | « appelle Maman » | demandé dans Capri Sécurité |

Chaque bouton de Capri Sécurité ouvre la bonne page Android ; chaque pouvoir se coupe au même endroit.

## 4. Paramètres restreints (APK installé depuis un fichier)

Android 15+ grise l'accessibilité et l'accès aux notifications pour une app installée hors store. Pour débloquer :

1. Paramètres > Applications > **Capri** (ou Capri Sécurité > Infos de l'appli).
2. Menu **⋮** en haut à droite > **Autoriser les paramètres restreints** (confirme avec ton code).
3. Reviens activer l'accessibilité / l'accès aux notifications.

## 5. Garder Capri en vie sur HiOS

HiOS ferme volontiers les apps en arrière-plan. Selon ta version de HiOS, les menus peuvent porter des noms un peu différents :

1. **Batterie** : Capri en « Aucune restriction » / « Ne pas optimiser » (bouton dans Capri Sécurité > Sans limite de batterie).
2. **Démarrage automatique** : dans **Phone Master** ou Paramètres > Gestion des applis, autorise Capri.
3. **Applis récentes** : tire la carte de Capri vers le bas (ou touche son cadenas) pour qu'un nettoyage ne la ferme pas.
4. **Notifications** : laisse le canal « Bulle Capri » actif (Android exige cette notification tant que la bulle tourne).

Après un redémarrage du téléphone, si « Démarrer après un redémarrage » est activé, la bulle revient ; pour le micro et Hey Capri, ouvre Capri une fois (règle d'Android).

## 6. Hey Capri

Réglages > HEY CAPRI > **Enregistrer mon Hey Capri** : dis-le trois fois dans un endroit calme, puis **Tester**. Ensuite :

- Dis « Hey Capri », **attends le petit bip** (ou son « Oui ? »), puis ta demande.
- Par défaut elle n'écoute qu'écran allumé et jamais en profil Économie. Le point vert du micro d'Android reste visible pendant l'écoute.
- Trop de faux réveils : baisse la sensibilité. Elle ne t'entend pas : monte-la, ou réenregistre plus près du téléphone.
- Capri Device affiche ce que l'écoute coûte vraiment au processeur sur ton téléphone.

## 7. Voix hors ligne

- Paramètres > Système > Langue et saisie > **Synthèse vocale** : moteur Google, télécharge la voix **Français**.
- Paramètres de reconnaissance vocale de Google : télécharge le **français hors ligne**, puis Capri > Réglages > OREILLES > **Sur l'appareil**.

## 8. Synchro avec le PC

Voir [SYNC.md](SYNC.md).
