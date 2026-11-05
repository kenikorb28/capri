# Permissions de Capri Android

Principe : **le minimum nécessaire**, demandé au moment où ça sert, expliqué, et coupable à tout moment. Rien n'est lu, écouté, capturé ni envoyé en secret.

| Permission (manifeste) | Type | Quand elle sert | Comment la couper |
|---|---|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | normale | cerveau cloud, voix cloud, recherche web, synchro ; savoir si internet est là | — (sans internet, seules les commandes simples marchent, et Capri le dit) |
| `SYSTEM_ALERT_WINDOW` | spéciale | la bulle, le panneau, le HUD de jeu | Paramètres > Affichage par-dessus les autres apps |
| `FOREGROUND_SERVICE` + `_SPECIAL_USE` | normale | garder la bulle (avec sa notification permanente) | bouton Arrêter de la notification |
| `FOREGROUND_SERVICE_MICROPHONE` | normale | écouter depuis la bulle et « Hey Capri » | Capri Sécurité > Tout arrêter ; Hey Capri OFF |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | normale | une capture d'écran quand l'accessibilité est coupée (Android demande à chaque fois) | refuser la question d'Android |
| `RECORD_AUDIO` | dangereuse | parler à Capri, Hey Capri, l'interrompre en parlant | Infos de l'appli > Autorisations > Micro |
| `POST_NOTIFICATIONS` | dangereuse (13+) | rappels, avis « Capri agit sur ton écran », alertes batterie/stockage | Infos de l'appli > Notifications |
| `SCHEDULE_EXACT_ALARM` | spéciale | rappels à la minute près | Alarmes et rappels |
| `RECEIVE_BOOT_COMPLETED` | normale | reprogrammer les rappels, relancer la bulle si tu l'as choisi | Réglages > BULLE > Démarrer après un redémarrage |
| `VIBRATE` | normale | rappels | — |
| `SET_ALARM` | normale | réveils et minuteurs dans l'horloge du téléphone | — |
| `READ_CONTACTS` | dangereuse, facultative | « appelle Maman » trouve le numéro (reste sur le téléphone) | Infos de l'appli > Autorisations |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | normale | ouvrir la question « ne pas optimiser » d'Android | Paramètres > Batterie |
| `ACCESS_NOTIFICATION_POLICY` | spéciale, facultative | activer/couper Ne pas déranger sur demande | Accès Ne pas déranger |
| `CALL_PHONE` | dangereuse, facultative | URGENCE uniquement : appel direct après ton « oui » | Infos de l'appli > Autorisations |
| `ACCESS_COARSE/FINE_LOCATION` | dangereuse, facultative | URGENCE uniquement : ta position dans le SMS, après ton « oui » | Infos de l'appli > Autorisations |
| `BIND_ACCESSIBILITY_SERVICE` (service) | accordée par toi | lire l'écran, Retour/Accueil, appuyer/taper pour une tâche demandée | Accessibilité > Capri |
| `BIND_NOTIFICATION_LISTENER_SERVICE` (service) | accordée par toi | résumer tes notifications, surveillances | Accès aux notifications |
| `BIND_VOICE_INTERACTION` (service) | accordée par toi | assistant par défaut | Applis par défaut > Assistant numérique |

**Pas de permission `CAMERA`** : les photos passent par ton app Photo (tu appuies toi-même), et Capri ne voit que la photo prise.

## Ce que Capri ne fait jamais

- Lire ou taper un mot de passe, un code ou un numéro de carte ; capturer un écran protégé (`FLAG_SECURE`) ; toucher les écrans de permissions, d'installation ou ses propres autorisations.
- Envoyer, publier, payer, supprimer, effacer sa mémoire, appeler le contact d'urgence ou partager ta position **sans ton « oui »** — quel que soit le préréglage SÉCURITÉ (risque élevé = confirmation obligatoire).
- Envoyer tes notifications à un serveur par elle-même : elles partent vers ton cerveau seulement si tu poses une question dessus, codes masqués.
- Écouter en arrière-plan sans le point micro d'Android, ou garder un enregistrement : Hey Capri compare le son au fil de l'eau et ne stocke rien.
- Tricher dans un jeu ou automatiser un jeu.
- Demander le root ou de couper une protection d'Android.

## Niveaux de risque

| Niveau | Exemples | Confirmation |
|---|---|---|
| Faible | ouvrir une app, lire l'écran sur demande, la batterie | non |
| Moyen | réglages, média, préparer un appel/SMS, enregistrer un fichier | selon tes interrupteurs SÉCURITÉ (désactivé = elle demande) |
| Élevé | supprimer un fichier (elle dit son nom), appuyer sur Envoyer/Payer/Publier, effacer sa mémoire, appeler le contact d'urgence, partager ta position | **toujours** |
