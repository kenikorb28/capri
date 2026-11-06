# Synchro avec le PC : c'est maintenant Capri Link

Depuis la version **2.0.0** de l'écosystème, le téléphone se synchronise avec **Capri Desktop lui-même** grâce à **Capri Link** : la même Capri, avec la même mémoire, sur le PC et le téléphone, et une synchro automatique.

- Guide pour les relier, utiliser et tester : [../../docs/CAPRI-LINK.md](../../docs/CAPRI-LINK.md)
- Protocole et règles de la mémoire : [../../contract/](../../contract/)

## Et l'ancien Capri Sync (v1) ?

Le petit programme séparé `pc-sync/CapriSync.exe` (v1) reste dans le dépôt pour mémoire, mais il n'est plus utilisé : Capri Link le remplace et fait tout ce qu'il faisait (conversation envoyée au PC, « qu'est-ce que je faisais sur le PC », mémoire, fichiers dans les deux sens), en mieux.

- Son appairage ne marche pas avec Capri Link (le chiffrement de l'appairage a changé) : le téléphone l'efface tout seul à la mise à jour, et il suffit d'appairer une fois avec Capri Link.
- Les deux utilisent le port 47917 : ne lance plus `CapriSync.exe`, sinon Capri Link ne peut pas écouter (sa fenêtre le signale).
