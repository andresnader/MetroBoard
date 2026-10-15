# Compilar un APK firmado

El keystore y sus contraseñas **nunca van al repositorio**. `app/build.gradle.kts` los lee de un archivo local que git ignora
(`keystore.properties`) o de variables de entorno. Sin ellos, `assembleRelease` sigue funcionando y produce
`app-release-unsigned.apk`.

## 1. Tu keystore

Usa el mismo con el que firmas desde Android Studio (Build ▸ Generate Signed Bundle / APK). Si todavía no tienes uno:

```bash
keytool -genkeypair -v -keystore ~/ameizin-release.jks -alias metroboard -keyalg RSA -keysize 4096 -validity 10000
```

Guarda una copia del `.jks` y sus contraseñas en un lugar seguro y **fuera del repositorio**: si se pierde, no se pueden publicar
actualizaciones con la misma firma (Google Play permite *Play App Signing* para reducir ese riesgo).

## 2. `keystore.properties` (en la raíz del proyecto, ignorado por git)

```properties
storeFile=/Users/andresnader/ameizin-release.jks
storePassword=...
keyAlias=metroboard
keyPassword=...
```

También valen las variables de entorno `MB_STORE_FILE`, `MB_STORE_PASSWORD`, `MB_KEY_ALIAS` y `MB_KEY_PASSWORD` (para un CI).

## 3. Compilar y verificar

```bash
./gradlew :app:assembleRelease          # app/build/outputs/apk/release/app-release.apk
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

`apksigner` está en `~/Library/Android/sdk/build-tools/<versión>/`. Si no encuentra Java, usa el de Android Studio:
`export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

## 4. Subir de versión

```bash
python3 tools/release_manager.py --set-version 0.4.0    # sincroniza versionCode y versionName
python3 tools/release_manager.py --verify               # comprueba CHANGELOG y créditos obligatorios
```
