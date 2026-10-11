# Estado de validación v0.1

Revisión del 10 de octubre de 2026 (America/Guayaquil).

## Evidencia ejecutada

| Comprobación | Resultado | Alcance |
|---|---|---|
| `:app:assembleDebug`, Android Studio, 23:31 | `BUILD SUCCESSFUL` | Código actual, incluidos los ajustes de visibilidad del IME |
| Run app, Medium Phone API 37.0 | Instalación completada; pantalla de ajustes abierta | Emulador Android 17; no dispositivo físico |
| `python3 tools/test-core-offline.py`, durante esta revisión | 43 tests JUnit aprobados | Shift, doble espacio, controlador y sugerencias; no renderiza el IME |
| `testDebugUnitTest`, informe de las 23:11 | 43 tests aprobados, sin fallos | Ejecución Gradle anterior a los últimos ajustes de visibilidad |
| `connectedDebugAndroidTest`, informe de las 22:56 | 15 tests aprobados, sin fallos; exit code 0 | Políticas e integración con `EditText`/`InputConnection`, antes de los últimos ajustes |
| `lintDebug`, informe de las 22:44 | 0 errores, 5 advertencias | Informe anterior a las últimas modificaciones; no representa una nueva ejecución |

Los tests instrumentados crean campos `EditText` en memoria, sin adjuntarlos a
una ventana. Verifican edición y políticas, pero **no demuestran que el servicio
IME aparezca ni que sus botones funcionen en una aplicación anfitriona**.

APK: `app/build/outputs/apk/debug/app-debug.apk` (versión 0.1.0).

## Último problema investigado

El teclado no aparecía durante las pruebas manuales. El código actual contiene:

- `SettingsActivity`: `windowSoftInputMode="stateHidden|adjustResize"`.
- `WordFlowImeService.onEvaluateInputViewShown()`: devuelve `true`.

Al abrir la app recién instalada, el estado visible fue **«Habilitado. Falta
seleccionarlo.»**. El selector Android mostraba Gboard seleccionado y
**Windows Phone Keyboard → QWERTY · español básico** disponible. Esto demuestra
que la habilitación y la selección son pasos distintos; no prueba por sí solo
que los ajustes de visibilidad sean necesarios o suficientes.

La herramienta de control remoto permitió compilar, instalar y abrir el selector,
pero falló en los clics por coordenadas (`noWindowsAvailable`). Se pidió completar
la selección manualmente. No se registra como aprobada una prueba visual pendiente.

## Aceptación manual pendiente

- [ ] Seleccionar el IME y confirmar que aparece al tocar un campo de texto.
- [ ] Escribir `Hola` tocando las teclas: mayúscula inicial y retorno a minúsculas.
- [ ] Dos espacios rápidos tras la palabra: `Hola. ` y AUTO_SHIFT para la siguiente letra.
- [ ] Dos espacios separados por más de 400 ms: conservar los dos espacios.
- [ ] Shift manual: una sola mayúscula; doble toque rápido: Caps Lock; otro toque: salida.
- [ ] Backspace, Enter, `&123`, retorno a letras y alternativas al mantener `.`.
- [ ] Repetir escritura en una aplicación distinta de la pantalla de ajustes.
- [ ] Contraseña ficticia sin sugerencias ni transformaciones automáticas; email y número.
- [ ] Orientación horizontal, TalkBack y ergonomía en dispositivo físico.

No se ha confirmado aún el funcionamiento completo del IME en pantalla. Los
resultados automatizados y la instalación satisfactoria no sustituyen esta lista.
