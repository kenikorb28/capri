# Les six ajouts de novembre (Capri Android)

État au 12 novembre 2026. **Testé** veut dire : tests automatiques du moteur (`AddsTest`, sur JVM). **Compilé** veut dire : l'app se compile sur GitHub. **Rien de cette page n'a encore été essayé sur un vrai téléphone.**

| Ajout | Où le trouver | Moteur | App |
|---|---|---|---|
| Sauvegarde chiffrée | Réglages > SAUVEGARDE | Testé | Compilé |
| Consommation et plafonds | Réglages > CONSOMMATION | Testé | Compilé |
| Cache vocal | Réglages > CONSOMMATION | Testé | Compilé |
| Mémoire des corrections | Dans la discussion, puis page Mémoire | Testé | Compilé |
| « Capri, vérifie-toi » | Réglages > PROTECTION, ou à la voix | Résumé testé | Compilé |
| Empreinte et arrêt d'urgence | Réglages > PROTECTION | Blocage des outils testé | Compilé |

## 1. Sauvegarde chiffrée

Un seul fichier `.capribak`, fermé par ton mot de passe (8 caractères ou plus). Tu choisis où le ranger avec le sélecteur de fichiers d'Android.

- **Dedans** : tout le dossier de Capri (notes, souvenirs, discussions, projets, rappels, Hey Capri, compteurs) et tes réglages. Tes clés API seulement si tu coches « Inclure mes clés API ».
- **Pas dedans** : l'appairage Capri Link et les protections, qui appartiennent au téléphone que tu as en main.
- **Chiffrement** : AES-256-GCM, clé tirée du mot de passe par PBKDF2-HMAC-SHA256 (210 000 tours), sel et nonce neufs à chaque fichier. Un mauvais mot de passe et un fichier abîmé sont refusés de la même façon.
- **Mot de passe perdu = sauvegarde perdue.** Personne ne peut l'ouvrir à ta place.
- **Restaurer** remplace la mémoire et les réglages du téléphone. Ce qu'il y avait est gardé une fois de côté (`capri-before-restore`, dans le dossier privé de l'app), l'appairage Capri Link est effacé pour que la première synchro reparte proprement, puis Capri redémarre.
- Limite : 64 Mo.

## 2. Consommation et plafonds

- Le **cerveau cloud** est compté en jetons, la **voix cloud** en caractères (Fish Audio en octets UTF-8 : une lettre accentuée en vaut deux). Ton PC n'est jamais compté.
- Les comptes sont ceux du fournisseur quand il les donne ; sinon ils sont estimés (environ 4 caractères par jeton) et marqués « en partie estimés ».
- Tu fixes un plafond mensuel pour chacun (vide = aucun). À 80 %, elle te prévient une fois. Au plafond : la voix cloud laisse la place à la voix du téléphone ; le cerveau cloud laisse la place à ton PC s'il est configuré, sinon seules les commandes simples marchent.
- Les prix par million sont facultatifs et ne servent qu'à afficher une estimation. Rien n'est facturé par Capri.
- À la voix ou au clavier : « combien j'ai consommé ? ».

## 3. Cache vocal

Les phrases courtes (160 caractères au plus) déjà dites par une voix cloud sont gardées en audio. La même phrase avec la même voix, le même modèle et la même vitesse rejoue sans appel, sans attente et sans réseau. Le cache est limité à 24 Mo (les plus anciennes partent), vit dans le cache de l'app (Android peut le vider seul), et se coupe ou se vide dans Réglages > CONSOMMATION.

## 4. Mémoire des corrections

- Tu dis « tu t'es trompée : … », « correction : … » ou « la prochaine fois, … ». Elle garde la suite, dans tes mots.
- Une correction est une note qui commence par « Correction : ». Elle se voit, se modifie et s'oublie sur la page Mémoire comme toute note, et elle voyage avec les notes par Capri Link.
- Les 30 dernières lui sont rappelées à chaque réponse, avec la consigne de les suivre à chaque fois.
- « Quelles sont tes corrections ? » les liste.
- Mêmes limites que les notes : 200 caractères, et rien de privé (mots de passe, numéros…).

## 5. « Capri, vérifie-toi »

Elle essaie vraiment chaque fonction et affiche 🟢 marche, 🟡 à regarder, 🔴 ne marche pas, ⚪ désactivé par toi.

| Essai | Ce qu'elle fait |
|---|---|
| Internet | regarde la connexion |
| Cerveau | demande la liste des modèles avec ta clé (vrai appel, gratuit) |
| Voix | voix cloud : dit « Test. » (quelques caractères de crédit) ; voix du téléphone : vérifie qu'elle est prête |
| Micro, Hey Capri, Bulle, Mains sur l'écran, Notifications, Rappels, Batterie | lit l'autorisation ou l'état réel dans Android |
| Capri Link | fait une vraie synchronisation avec ton PC |
| Stockage, Protection | place libre ; empreinte ou verrou disponible |

Dans la discussion ou à la voix, elle donne le résumé ; le tableau complet est dans Réglages > PROTECTION > « Vérifie-toi ».

## 6. Empreinte et arrêt d'urgence

**Empreinte ou code du téléphone.** C'est la question d'Android lui-même (empreinte, visage ou code) : Capri ne voit jamais l'empreinte, elle reçoit seulement oui ou non. Quand l'interrupteur est activé, elle est demandée :

- avant chaque action à risque élevé (envoyer, payer, supprimer, effacer sa mémoire), après ton « oui » ;
- pour ouvrir la page Mémoire ;
- pour créer ou restaurer une sauvegarde ;
- pour reprendre après un arrêt d'urgence, et pour couper cette protection.

Sans empreinte ni verrou d'écran configuré dans Android, l'interrupteur refuse de s'activer.

**Arrêt d'urgence.** Le bouton, ou la phrase « arrêt d'urgence », coupe sa voix et la tâche en cours, bloque tous ses outils, met en pause Hey Capri et Capri Link. Il reste actif même après un redémarrage, jusqu'à « reprends » ou le bouton Reprendre. Elle peut toujours discuter.

**Limite connue :** l'arrêt d'urgence n'arrête pas encore Capri sur le PC. Capri Link v2 n'a pas d'ordre « stop » : il faudra l'ajouter au contrat et des deux côtés.

## À essayer sur le téléphone

1. Créer une sauvegarde, puis la restaurer (d'abord sans les clés).
2. Mettre un petit plafond de voix (par exemple 200 caractères) et vérifier la bascule sur la voix du téléphone.
3. Faire dire deux fois la même phrase courte avec Fish Audio : la seconde doit partir sans attente.
4. Dire « tu t'es trompée : … », puis vérifier la note sur la page Mémoire.
5. Lancer « Vérifie-toi » et comparer avec ce que tu constates.
6. Activer l'empreinte, ouvrir la page Mémoire, puis essayer l'arrêt d'urgence et « reprends ».
