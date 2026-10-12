# MetroBoard

Un desarrollo de **Ameizin** — Andrés Nader · [info@ameizin.ec](mailto:info@ameizin.ec)

IME Android independiente en Kotlin, inspirado en el comportamiento, la ergonomía
y la dirección visual tipográfica y minimalista del diseño Metro / Word Flow de Windows Phone 8.1.
**No es una reproducción exacta ni un producto de Microsoft.** No contiene código, fuentes, imágenes,
iconos, sonidos ni diccionarios propietarios de Microsoft.

La fuente de requisitos es
[`wordflow_8_1_reverse_engineering_corpus_v0_1.xlsx`](wordflow_8_1_reverse_engineering_corpus_v0_1.xlsx).
Se leyeron sus seis hojas antes de implementar. La
[matriz de trazabilidad](docs/corpus-analysis.md) distingue Confirmado, Probable,
Inferido y Desconocido. La petición de esta iteración limita el alcance del corpus.

Nombre del proyecto: **MetroBoard**. Package ID: `com.metroboard.ime`.
Es un identificador de paquete técnico, sin afirmar afiliación.
La raíz Gradle se llama `MetroBoard`.

## Licencia, Código Abierto y Atribución

MetroBoard es un proyecto de **software libre y de código abierto (Open Source)** distribuido bajo la licencia [GNU Affero General Public License v3 (AGPL-3.0)](LICENSE).

Este proyecto está inspirado en múltiples proyectos y trabajos de la comunidad:
- **Microsoft Word Flow / Windows Phone 8.1**: Inspiración del diseño ergonómico Metro UI y dinámica de escritura.
- **ChiBoard (ChiApps)**: Teclado comunitario Metro en Android (referencia de arquitectura; desde 0.3 no se distribuye ningún dato suyo).
- **Diccionarios Hunspell de LibreOffice** (RLA-ES de Santiago Bosio, SCOWL de Kevin Atkinson, Grammalecte de Olivier R., igerman98 de Björn Jacke): validez de las palabras en es/en/fr/de.
- **FrequencyWords (Hermit Dave) sobre OpenSubtitles 2018**: frecuencias de las cuatro listas (CC-BY-SA-4.0).
- **Android Open Source Project (AOSP)**: Arquitectura base de `InputMethodService`.

Agradecemos formalmente a cada autor y comunidad. Consulta [ATTRIBUTION.md](ATTRIBUTION.md) para los agradecimientos y licencias completas, y [CHANGELOG.md](CHANGELOG.md) para el historial de versiones.

## Funcionalidades (v0.4)

- Servicio `InputMethodService` registrado con `BIND_INPUT_METHOD`, metadatos XML,
  subtipo QWERTY y pantalla de habilitación, selección y preferencias.
- QWERTY sin fila numérica; fondo oscuro, teclas rectangulares, sans-serif del
  sistema, espacio ancho rotulado `space`, punto, Shift y Backspace con repetición.
- Estados explícitos `AUTO_SHIFT`, `ONE_SHOT_SHIFT`, `CAPS_LOCK`, `LOWERCASE`.
  Etiquetas cambian entre mayúsculas y minúsculas. Shift activo tiene fondo blanco;
  automático añade un punto azul; Caps Lock añade una barra bajo la flecha.
- Mayúscula inicial y de oración con `InputConnection.getCursorCapsMode`, flags de
  `EditorInfo` y respaldo contextual `TextUtils.getCapsMode`. Tras `. `, `! ` o `? `
  (punto y **espacio**) y tras un salto de línea se activa AUTO_SHIFT; `www.sitio.com` o
  `3.14` no se alteran. Shift sobre texto seleccionado alterna Palabra → PALABRA → palabra.
  No modifica automáticamente contraseñas/email/URL.
- Dos espacios consecutivos tras letra o dígito, hasta **400 ms**, producen `. `.
  La siguiente letra queda en mayúscula. Cursor, selección, otras teclas y cambio
  de campo cancelan la secuencia. Constante `DoubleSpaceHandler.DOUBLE_SPACE_TIMEOUT_MS`.
- Un toque manual de Shift activa ONE_SHOT, incluso desde AUTO. Dos toques dentro
  de **300 ms** bloquean mayúsculas; otro las desactiva. Dos toques más lentos
  activan/desactivan ONE_SHOT. Constante `ShiftStateManager.DOUBLE_TAP_TIMEOUT_MS`.
- Mantener `.` abre `, ? ! : ;`. Levanta el dedo y toca una alternativa. El popup
  es reutilizable; también hay vocales acentuadas y `ñ` mediante pulsación larga.
- `&123` muestra números y símbolos; `=\<` muestra más; `abc` regresa a letras.
- El botón de cara sonriente abre un panel de emoji con categorías, recientes y tonos de piel (unos 800 emoji; sin
  búsqueda ni catálogo Unicode completo). Mantener espacio abre el selector de idioma y de teclados.
- Enter usa la acción del editor (buscar, enviar, siguiente, listo…) o salto de
  línea, respetando `IME_FLAG_NO_ENTER_ACTION` y acciones personalizadas.
- Borrado de selecciones y de **caracteres percibidos** (emoji con tono de piel, banderas,
  familias ZWJ y letras con marca combinante se borran de un toque); en contraseñas, de un punto de código.
- Layouts Android para teléfono, fecha y números; respeta flags decimal/signo.
- Hasta tres sugerencias (la mejor en el centro, en negrita) con diccionarios offline reales de **60.000 palabras** para
  es/en/fr/de, ordenadas por frecuencia. Una palabra válida no se cambia; la autocorrección es conservadora, reversible
  con Retroceso y no toca nombres propios, siglas, URL ni correos. Elegir una sugerencia añade un espacio. Con una
  palabra seleccionada en el editor se ofrecen reemplazos. Ver [docs/dictionaries.md](docs/dictionaries.md).
- Vibración y sonido Android opcionales, **apagados por defecto**, con ajustes
  persistentes. Respetan las restricciones del sistema y el modo de sonido.

## Arquitectura

Un módulo `app` y paquetes con responsabilidades separadas:

```text
app/src/main/java/com/metroboard/ime/
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
adb shell ime enable com.metroboard.ime/.ime.WordFlowImeService
adb shell ime set com.metroboard.ime/.ime.WordFlowImeService
```

Para escribir en un AVD con teclado físico, activa **Show virtual keyboard** en
los ajustes de teclado físico del dispositivo si el IME no aparece.

## Privacidad

Sin permiso `INTERNET`, sin dependencias de terceros, sin analytics ni logs de pulsaciones; backups de la app
desactivados. Solo persisten las preferencias y un **diccionario personal local** con las palabras nuevas que tú escribes
(desactivable en Ajustes › typing; se puede ver, borrar, exportar e importar). Nada se lee ni se aprende en campos de
contraseña, correo o URL, ni cuando el editor declara `IME_FLAG_NO_PERSONALIZED_LEARNING`. La copia de seguridad usa el
Selector de archivos de Android, no el portapapeles. Detalle completo y límites: [docs/privacy.md](docs/privacy.md).

## Limitaciones conocidas

Lista completa y contexto en [docs/0.3/qa-report.md](docs/0.3/qa-report.md). Las principales:

- **Sin validación en hardware real.** Todo lo medido sale de pruebas unitarias/instrumentadas y de un emulador
  (Android 17, API 37). Los swipes de las pruebas son sintéticos, no pulgares reales.
- No probados: TalkBack, orientación horizontal, navegación de tres botones, otras versiones de Android (8–16),
  pantallas pequeñas/grandes, tamaño de fuente aumentado.
- Predicción de **siguiente palabra** limitada: solo 15–30 pares de bigramas/trigramas curados por idioma.
- Los diccionarios usan frecuencias de subtítulos (sesgo coloquial) y un trie de 35–50 MB por idioma (máximo 2 en memoria).
  La primera carga tras instalar puede tardar varios segundos en el emulador; mientras tanto no hay sugerencias.
- Licencias: los datos de frecuencia son CC-BY-SA-4.0 y los diccionarios es/de son GPL; encajan con la AGPL del proyecto
  según sus autores, pero **falta una revisión legal** antes de publicar en una tienda (ver [`NOTICE`](NOTICE)).
- El doble toque sobre una palabra pertenece a la app anfitriona: un IME no lo recibe. MetroBoard solo reacciona a la
  selección que el editor le comunica.
- Sonidos: efectos del sistema con volumen ajustable; no hay sonidos propios estilo Windows Phone. Háptica: intensidades
  según el hardware.
- Sin diccionario de contactos, sin historial de portapapeles, sin búsqueda de emoji, sin variantes regionales.
- Fidelidad histórica: geometría, color exacto y tiempos propios; el Shift del corpus se marca «Probable».

## Roadmap

| Versión | Alcance |
|---|---|
| v0.1 | IME + layout + Shift + puntuación; sugerencias locales de demostración |
| v0.2 | Prototipo ampliado (rama principal local): 4 idiomas en la UI, Word Flow, emoji, diccionario personal |
| v0.3 | **Calidad de escritura**: multitáctil, idiomas aislados, diccionarios reales, autocorrección conservadora y reversible, Word Flow sobre todo el léxico, copia de seguridad validada |
| v0.4 | **Más rápido y más inteligente** (2026-10-12): respuesta al escribir (p95 37 → 4 ms en el emulador), siguiente palabra real (~30.000 por idioma), Word Flow con contexto, editor del diccionario personal, tipos de campo (correo, URL, nombres), lo copiado en la barra, vista previa en vivo y firma de release. Detalle en [CHANGELOG](CHANGELOG.md) y [docs/0.4](docs/0.4/README.md) |
| v0.5 | Cinemática y grabador de trazos reales, imágenes en el portapapeles, importar diccionarios, validación en hardware y accesibilidad |

Las fuentes y decisiones están en [corpus-analysis.md](docs/corpus-analysis.md).
La integración IME sigue la [guía oficial Android](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method)
y el contrato de [InputConnection](https://developer.android.com/reference/android/view/inputmethod/InputConnection).
