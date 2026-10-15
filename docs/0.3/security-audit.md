# Auditoría de seguridad y privacidad — MetroBoard 0.3

Reproducible: cada comprobación de la tabla 1 se puede repetir con el comando indicado. Esto es una revisión de código y de
comportamiento observado en un emulador, **no** una auditoría independiente ni una prueba de penetración.

## 1. Comprobaciones

| Comprobación | Resultado | Cómo repetirla |
|---|---|---|
| Permiso `INTERNET` | **Ausente** (también en el manifiesto fusionado del APK) | `aapt2 dump permissions app/build/outputs/apk/debug/app-debug.apk` |
| Permisos declarados | Solo `VIBRATE` y `READ_USER_DICTIONARY` (nivel *normal* en Android 17: `pm list permissions -f`), este último solo para «importar del diccionario de Android» | ídem |
| Dependencias de ejecución | **Ninguna** (`app/build.gradle.kts` solo declara dependencias de prueba) | `grep -nE "^\s*(implementation\|api)" app/build.gradle.kts` |
| Código de red / telemetría / logs | **Ninguno** en `app/src/main` (`java.net`, sockets, `android.util.Log`, `println`, `printStackTrace`) | `grep -rnE ... app/src/main/java` |
| Copias de seguridad | `allowBackup=false`, `fullBackupContent=false`, reglas de extracción que excluyen nube y transferencia | `AndroidManifest.xml`, `res/xml/data_extraction_rules.xml` |
| Componentes exportados | La actividad de ajustes (lanzador) y el servicio IME con `BIND_INPUT_METHOD` (lo estándar) | `AndroidManifest.xml` |
| Almacenamiento | Solo privado de la app: `keyboard_preferences`, `emoji_prefs` (emoji recientes), `personal_dictionary.json`, y un tono generado por `KeySound` | `grep -rn "getSharedPreferences\|filesDir" app/src/main/java` |
| Portapapeles | Se lee únicamente al pulsar 📋; **ya no se escribe nada** (la exportación dejó de usarlo) | `grep -rn "primaryClip" app/src/main/java` |
| Campos sensibles | Contraseña (texto/visible/web/numérica), correo, URL y filtro: sin sugerencias, autocorrección, aprendizaje ni lectura de texto; en contraseñas Retroceso no consulta el contexto | `EditorPolicyTest` (8 casos), `EditorControllerTest.sensitivePolicyDoesNotReadContextOrTransformText` |
| `IME_FLAG_NO_PERSONALIZED_LEARNING` | Desactiva sugerencias y aprendizaje | `EditorPolicyTest` |
| Importación de archivos | SAF (sin permiso de almacenamiento); límite 1 MiB leído por bloques, UTF-8 estricto, ≤ 20.000 palabras/idioma, palabras de 2–40 letras/dígitos/apóstrofos, idiomas fijos, ajustes en lista blanca con tipo y rango | `BackupFormatTest` (14) + importación adversaria en emulador |
| Exportación | Solo ajustes y palabras aprendidas (JSON v2); sin historial de escritura ni metadatos | `BackupFormatTest.theExportContainsNoTypingHistoryOrExtraMetadata` |
| Tokens tipo URL/correo en campos normales | No se corrigen ni se aprenden | `EditorControllerPredictionTest` |

## 2. Hallazgos corregidos en esta iteración

| Severidad | Hallazgo | Corrección |
|---|---|---|
| Media | La exportación copiaba **ajustes y palabras aprendidas al portapapeles del sistema** (legible por otras apps e historiales de portapapeles) | Exportar/importar por SAF, sin portapapeles |
| Media | La importación aceptaba JSON de cualquier tamaño/forma; los ajustes se guardaban sin validar (p. ej. un `NaN` en `pref_height_scale` quedaba en las preferencias) | `BackupFormat` con límites y lista blanca |
| Baja | Se aprendían fragmentos de URL/correo (`com`) | Protegidos |
| Baja | La política decía «aprendizaje opt-in», pero no existía ningún interruptor | Ajuste «Aprender palabras nuevas» (activado por defecto) |
| Baja | `docs/privacy.md` afirmaba que no se persistía nada | Reescrito |
| Baja | El diccionario personal se escribía sin atomicidad y un fallo se tragaba en silencio (podía perder todas las palabras) | Escritura a archivo temporal + movimiento atómico; prueba de recuperación ante archivo corrupto |

## 3. Riesgos residuales (no corregidos)

- `personal_dictionary.json` y los archivos exportados son **texto plano**; el primero está en almacenamiento privado, el segundo
  queda donde el usuario lo guarde. No hay cifrado.
- El diccionario personal se reescribe entero en cada palabra aprendida y en el hilo de la interfaz (ya es atómico, ver 2). Con pocos
  cientos de palabras es barato; no se midió el coste con diccionarios grandes.
- Una palabra que **tú** escribes (nombre, dirección, apodo) se guarda como palabra aprendida si el campo no es sensible. Se ve y se
  borra desde Ajustes, pero un campo de texto genérico que contenga un secreto no se puede distinguir de uno normal.
- No se promete protección frente a una app anfitriona maliciosa. Un IME ve por definición lo que se escribe.
- No se ha revisado la cadena de herramientas de compilación (Gradle, AGP) ni el contenido de las fuentes de los diccionarios más
  allá de su validez lingüística y su licencia.
- Las licencias de los datos distribuidos (CC-BY-SA-4.0, GPL) requieren **revisión legal** antes de una tienda.
