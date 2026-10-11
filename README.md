# MetroBoard

IME Android independiente en Kotlin, inspirado en el comportamiento, la ergonomía
y la dirección visual tipográfica y minimalista del diseño Metro / Word Flow de Windows Phone 8.1.
**No es una reproducción exacta ni un producto de Microsoft.** No contiene código, fuentes, imágenes,
iconos, sonidos ni diccionarios propietarios de Microsoft.

La fuente de requisitos es
[`wordflow_8_1_reverse_engineering_corpus_v0_1.xlsx`](wordflow_8_1_reverse_engineering_corpus_v0_1.xlsx).
Se leyeron sus seis hojas antes de implementar. La
[matriz de trazabilidad](docs/corpus-analysis.md) distingue Confirmado, Probable,
Inferido y Desconocido. La petición de esta iteración limita el alcance del corpus.

Nombre del proyecto: **MetroBoard**. Package ID: `com.windowsphonekeyboard.ime`.
Es un identificador de paquete técnico, sin afirmar afiliación.
La raíz Gradle se llama `MetroBoard`.

## Funcionalidades v0.1

- Servicio `InputMethodService` registrado con `BIND_INPUT_METHOD`, metadatos XML,
  subtipo QWERTY y pantalla de habilitación, selección y preferencias.
- QWERTY sin fila numérica; fondo oscuro, teclas rectangulares, sans-serif del
  sistema, espacio ancho rotulado `space`, punto, Shift y Backspace con repetición.
- Estados explícitos `AUTO_SHIFT`, `ONE_SHOT_SHIFT`, `CAPS_LOCK`, `LOWERCASE`.
  Etiquetas cambian entre mayúsculas y minúsculas. Shift activo tiene fondo blanco;
  automático añade un punto azul; Caps Lock añade una barra bajo la flecha.
- Mayúscula inicial y de oración con `InputConnection.getCursorCapsMode`, flags de
  `EditorInfo` y respaldo contextual `TextUtils.getCapsMode`. `.`, `!`, `?` activan
  AUTO_SHIFT en texto normal. No modifica automáticamente contraseñas/email/URL.
- Dos espacios consecutivos tras letra o dígito, hasta **400 ms**, producen `. `.
  La siguiente letra queda en mayúscula. Cursor, selección, otras teclas y cambio
  de campo cancelan la secuencia. Constante `DoubleSpaceHandler.DOUBLE_SPACE_TIMEOUT_MS`.
- Un toque manual de Shift activa ONE_SHOT, incluso desde AUTO. Dos toques dentro
  de **300 ms** bloquean mayúsculas; otro las desactiva. Dos toques más lentos
  activan/desactivan ONE_SHOT. Constante `ShiftStateManager.DOUBLE_TAP_TIMEOUT_MS`.
- Mantener `.` abre `, ? ! : ;`. Levanta el dedo y toca una alternativa. El popup
  es reutilizable; también hay vocales acentuadas y `ñ` mediante pulsación larga.
- `&123` muestra números y símbolos; `=\<` muestra más; `abc` regresa a letras.
- El botón de cara sonriente ofrece cuatro **emoticonos de texto** como placeholder.
  No es un catálogo emoji. Mantener espacio abre el selector Android de teclados.
- Enter usa la acción del editor (buscar, enviar, siguiente, listo…) o salto de
  línea, respetando `IME_FLAG_NO_ENTER_ACTION` y acciones personalizadas.
- Borrado de selecciones y de puntos de código Unicode, sin cortar pares sustitutos.
- Layouts Android para teléfono, fecha y números; respeta flags decimal/signo.
- Hasta tres sugerencias por prefijo, con lista española pequeña escrita a mano.
  Tocar una sustituye el prefijo; no añade espacio ni autocorrige. Se revalida al tocar.
- Vibración y sonido Android opcionales, **apagados por defecto**, con ajustes
  persistentes. Respetan las restricciones del sistema y el modo de sonido.

## Arquitectura

Un módulo `app` y paquetes con responsabilidades separadas:

```text
app/src/main/java/com/windowsphonekeyboard/ime/
  ime/          servicio, política por editor y adaptador InputConnection
  ui/           vistas, iconos geométricos propios y popup de alternativas
  keyboard/     layouts, EditorController, EditorPort, Shift, doble espacio
  prediction/   SuggestionEngine y motor local por prefijo
  dictionary/   PersonalDictionary y ContactDictionaryProvider
  gestures/     contratos GestureEngine y ShapeWritingDecoder
  settings/     activación, selección, campos de prueba y preferencias
  haptics/      feedback háptico Android
  sound/        sonido de tecla Android
  util/         colores y conversión dp
app/src/test/          tests JUnit de Kotlin puro
app/src/androidTest/  integración real con EditText/InputConnection y políticas
docs/                 diseño, evidencia, privacidad y validación
```

`KeyboardView → WordFlowImeService → EditorController → EditorPort → InputConnection`.
Las vistas no editan texto directamente. El controlador no depende de Android.
El motor de sugerencias es reemplazable. `DisabledPersonalDictionary` no almacena
nada; sus métodos y los contratos de gestos/contactos preparan futuras versiones
sin ejecutar aprendizaje, importar contactos ni capturar trazos.

## Herramientas y Android Studio

| Herramienta | Versión fijada |
|---|---|
| Android Gradle Plugin | 9.0.1 estable |
| Gradle Wrapper | 9.1.0 |
| Kotlin | 2.2.10 integrado en AGP 9 |
| compileSdk / targetSdk | 36 / 36 (Android 16) |
| SDK Build Tools | 36.0.0 |
| minSdk | 26 (Android 8.0) |
| JVM del bytecode | 17 |

Se eligió AGP 9.0.1 porque es estable, incluye Kotlin y admite API 36. Su
[tabla oficial de compatibilidad](https://developer.android.com/build/releases/agp-9-0-0-release-notes)
especifica Gradle 9.1.0, Build Tools 36.0.0 y JDK mínimo 17. Gradle 9.1 admite el
JBR 25 instalado con Android Studio en este equipo, según la
[matriz de Gradle](https://docs.gradle.org/current/userguide/compatibility.html).
También puede usarse JDK 17 o 21. No se añaden Compose, AndroidX ni librerías de
red a la aplicación; AndroidX se usa únicamente en tests instrumentados.

1. En Android Studio, **Open** y selecciona esta carpeta (la que contiene
   `settings.gradle.kts`).
2. En **SDK Manager**, instala Android SDK Platform 36, Build Tools 36.0.0 y
   Platform Tools. No se necesita NDK.
3. Usa el JDK integrado de Android Studio como **Gradle JDK**. Sincroniza Gradle.
4. Android Studio crea `local.properties` con `sdk.dir=...`; este archivo es local
   y está excluido de Git. También puede usarse `ANDROID_HOME`.
5. Selecciona `app` y **Build → Assemble Project**, o usa los comandos de abajo.

La primera sincronización requiere conexión para herramientas y dependencias.
El teclado instalado funciona completamente offline.

## Compilar y ejecutar tests

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleDebug
```

En Windows usa `gradlew.bat`. En este Mac, si `java` no encuentra un runtime:

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
./gradlew :app:assembleDebug
```

APK debug esperado tras compilación satisfactoria:
`app/build/outputs/apk/debug/app-debug.apk`.

Con dispositivo/emulador encendido:

```sh
./gradlew :app:connectedDebugAndroidTest
```

Los tests instrumentados verifican políticas de contraseña/email/URL/números,
acciones de Enter, mayúsculas contextuales del sistema, selecciones y borrado
Unicode con `EditText` e `InputConnection` reales. El
[informe de validación](docs/validation.md) distingue lo ejecutado de lo pendiente.

Existe un respaldo para el núcleo sin SDK/Gradle, usando Kotlin y JUnit incluidos
en Android Studio en macOS:

```sh
python3 tools/test-core-offline.py
```

Compila el código puro real y los mismos tests JUnit; **no verifica la aplicación
Android ni genera un APK**. El compilador de respaldo disponible aquí es 2.3.20.

## Instalar, habilitar y seleccionar

1. Conecta un Android 8.0+ con depuración USB, o inicia un AVD. Desde Android
   Studio pulsa **Run app**, o ejecuta:

   ```sh
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

2. Abre **MetroBoard** en el dispositivo.
3. Toca **1 habilitar teclado**. En la pantalla Android, habilita el teclado y
   revisa el aviso estándar del sistema sobre métodos de entrada.
4. Vuelve a la app y toca **2 seleccionar teclado**; elige **MetroBoard**.
5. Escribe en el campo de prueba y en una app normal, por ejemplo notas o mensajes.
   Android conserva la selección como teclado activo. Algunos fabricantes la
   denominan **Teclado predeterminado** dentro de **Idioma y entrada**.
6. Para cambiar de teclado, mantén pulsado espacio o usa el selector del sistema.

En un emulador de pruebas también se puede usar:

```sh
adb shell ime enable com.windowsphonekeyboard.ime/.ime.WordFlowImeService
adb shell ime set com.windowsphonekeyboard.ime/.ime.WordFlowImeService
```

Para escribir en un AVD con teclado físico, activa **Show virtual keyboard** en
los ajustes de teclado físico del dispositivo si el IME no aparece.

## Privacidad

Sin permiso `INTERNET`, analytics, logs de pulsaciones, contactos, micrófono ni
almacenamiento externo. No se guarda texto ni contraseñas. Solo persisten tres
booleanos de preferencias; los backups de la app están desactivados. No hay
aprendizaje en ningún campo en v0.1. En contraseñas se desactivan sugerencias,
capitalización automática y doble espacio; Shift manual sigue disponible.
Se respeta `IME_FLAG_NO_PERSONALIZED_LEARNING` desactivando también sugerencias.
Consulta [la política técnica completa](docs/privacy.md).

## Limitaciones conocidas

- Inspiración histórica; geometría, color exacto y timings propios. No se afirma
  fidelidad exacta del Shift histórico, que el corpus marca Probable.
- Un único subtipo QWERTY con demostración de vocabulario español; no hay motor
  multilingüe, autocorrección, siguiente palabra ni catálogo completo de emoji.
- El popup requiere levantar y elegir; no implementa el gesto deslizante &123 ni
  retorno automático desde emoji. Los layouts de campos son adaptaciones Android.
- No se transforma la capitalización de una palabra seleccionada con Shift
  (B002), ni se implementan shape writing, caret propio, contactos o nube.
- Backspace borra puntos de código, no clústeres de grafemas completos: un emoji
  compuesto o una letra con marca combinante puede requerir varios toques.
- Selección y capitalización dependen de que el editor anfitrión implemente las
  APIs Android y notifique movimientos de cursor. Editors especiales pueden variar.
- El botón de Enter con una etiqueta personalizada muy larga puede truncarse.
- Ajuste de tamaño, landscape, TalkBack y ergonomía en dispositivos reales deben
  seguir ampliándose según la [lista de aceptación](docs/validation.md).

## Roadmap

| Versión | Alcance |
|---|---|
| v0.1 | IME + layout + Shift + puntuación; sugerencias locales de demostración |
| v0.2 | SuggestionEngine ampliado + diccionario personal local y opt-in |
| v0.3 | Autocorrección y contexto, con reversión y controles de privacidad |
| v0.4 | Shape writing sobre las interfaces de gestos y decodificación |
| v0.5 | Ajuste fino de UX basado en corpus histórico y mediciones verificadas |

Las fuentes y decisiones están en [corpus-analysis.md](docs/corpus-analysis.md).
La integración IME sigue la [guía oficial Android](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method)
y el contrato de [InputConnection](https://developer.android.com/reference/android/view/inputmethod/InputConnection).
