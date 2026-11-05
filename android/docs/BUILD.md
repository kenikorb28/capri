# Compiler Capri Android (Android Studio sur ton PC)

Je n'ai pas pu produire l'APK ici : cet environnement n'a ni SDK Android ni accès aux dépôts Maven. Tout le code a été vérifié autrement (voir [TEST_REPORT.md](TEST_REPORT.md)), mais **la vraie compilation se fait chez toi**, en 10 minutes.

## 1. Ce qu'il faut

- **Android Studio** récent (toute version de 2025 ou 2026). Il fournit le JDK (17 ou plus est requis) et télécharge le SDK.
- Environ 6 Go d'espace disque pour Studio, le SDK et Gradle.
- Internet pour la première synchronisation (Gradle 8.14.3, plugin Android 8.13.2, Kotlin 2.2.21, ML Kit 16.0.1).

## 2. Ouvrir et compiler

1. Dézippe `CapriAndroid.zip`, puis dans Android Studio : **File > Open** et choisis le dossier `capri-android` (celui qui contient `settings.gradle.kts`).
2. Laisse la **Gradle Sync** se terminer. Si Studio demande d'installer **Android SDK Platform 36** ou des Build Tools, accepte.
3. Si Studio propose de passer le **plugin Android Gradle en 9.x** : réponds **non** (« Don't ask again for this project »). Le projet est réglé pour AGP 8.13.2.
4. **Build > Build App Bundle(s) / APK(s) > Build APK(s)**. L'APK debug sort ici :
   `app/build/outputs/apk/debug/app-debug.apk`

En ligne de commande (PowerShell, dans le dossier du projet) :

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug
```

`:core:test` lance les 38 tests du moteur, `:app:testDebugUnitTest` ceux du plugin calculatrice.

## 3. Installer sur le TECNO CL6k

- **Par câble (recommandé)** : active **Options pour les développeurs > Débogage USB**, branche le téléphone, puis le bouton ▶ Run de Studio (ou `adb install -r app\build\outputs\apk\debug\app-debug.apk`). Installée par adb, l'app n'est pas soumise aux « paramètres restreints ».
- **Par fichier** : copie l'APK sur le téléphone et ouvre-le (autorise l'installation depuis le gestionnaire de fichiers). Voir ensuite [INSTALL_TECNO_CL6K.md](INSTALL_TECNO_CL6K.md) pour débloquer l'accessibilité.

## 4. APK release (facultatif)

```powershell
keytool -genkeypair -v -keystore capri.jks -alias capri -keyalg RSA -keysize 4096 -validity 10000
$env:CAPRI_KEYSTORE="C:\chemin\capri.jks"; $env:CAPRI_KEYSTORE_PASSWORD="…"; $env:CAPRI_KEY_ALIAS="capri"; $env:CAPRI_KEY_PASSWORD="…"
.\gradlew.bat :app:assembleRelease
```

Le mot de passe n'est jamais écrit dans le projet : il passe par les variables d'environnement. L'APK release (minifié par R8) sort dans `app/build/outputs/apk/release/`. Garde `capri.jks` en lieu sûr : sans lui, impossible de mettre à jour l'app installée.

## 5. Capri Sync (Windows)

```powershell
cd pc-sync
.\build-sync.ps1
```

Le script utilise le `csc.exe` du .NET Framework 4.x fourni avec Windows (comme Capri), construit `CapriSync.exe` et lance son **auto-test** (`--selftest`) qui vérifie que le chiffrement du PC est identique à celui du téléphone. Si un test échoue, ne l'utilise pas et envoie-moi la sortie.

## 6. Sans Android Studio : GitHub Actions

Le fichier `.github/workflows/android.yml` compile l'APK debug sur les serveurs de GitHub à chaque push : pousse le dossier dans un dépôt GitHub privé, ouvre l'onglet **Actions**, et télécharge l'artefact **capri-debug-apk**.

## Problèmes fréquents

| Message | Solution |
|---|---|
| `SDK location not found` | Studio : File > Project Structure > SDK Location, ou ouvre le projet dans Studio une fois (il crée `local.properties`). |
| `Unsupported class file major version` / JDK | File > Settings > Build Tools > Gradle > Gradle JDK : choisis le JDK fourni par Studio (17+). |
| `Could not resolve com.google.mlkit:text-recognition` | Vérifie internet ; Studio > File > Sync Project with Gradle Files. |
| L'accessibilité est grisée sur le téléphone | Paramètres restreints : voir INSTALL_TECNO_CL6K.md, étape 4. |
