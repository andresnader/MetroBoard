# Diseño v0.1

La petición del usuario fija el alcance y autoriza su implementación. El proyecto
se crea en `wkb`, raíz disponible, con nombre de proyecto `MetroBoard`.
No existe un proyecto anterior ni un AGENTS.md aplicable.

Se elige un único módulo Android con vistas estándar, frente a Compose (más
dependencias) o un Canvas monolítico (más trabajo de accesibilidad). Las teclas son
vistas accesibles; los iconos geométricos son propios. No se usa KeyboardView obsoleto.

El servicio enlaza InputConnection con EditorController mediante EditorPort.
ShiftStateManager, DoubleSpaceHandler y el motor de prefijos son Kotlin puro.
EditorPolicy adapta inputType, flags de privacidad y acciones. El popup de
alternativas es reutilizable. Preferencias persisten solo tres booleanos.

El teclado usa fondo #101010, teclas #303030, texto blanco, acento #40B9E9,
sans-serif del sistema, cuatro filas compactas, barra de sugerencias de 40 dp y
barra de espacio ancha. Se mantiene geometría LTR para QWERTY en entornos RTL.
Estas medidas/colores son decisiones propias, no una reproducción exacta.

La lógica normal utiliza getCursorCapsMode con CAP_SENTENCES más los flags del
editor, y TextUtils.getCapsMode sobre contexto acotado como respaldo. Los campos
email, URI, contraseña, números y teléfono no reciben transformación automática.
Las selecciones se reemplazan al escribir y se borran completas con Backspace.
Los cambios de cursor invalidan doble espacio y Shift manual puntual.

El doble espacio exige dos acciones consecutivas, dentro de 400 ms, sin selección,
precedidas por una letra/dígito y un espacio. Shift tiene ventana de 300 ms.
Ambos son valores propios porque Q003 está abierto.

Se valida la lógica con JUnit y la integración de InputConnection con tests Android
independientes. Se intentará assembleDebug, testDebugUnitTest, lintDebug y una nueva
compilación. La falta de SDK/red no se confundirá con compilación satisfactoria.
