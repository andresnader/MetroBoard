# Privacidad — MetroBoard 0.3

Lo que el código hace hoy (comprobado en [`0.3/security-audit.md`](0.3/security-audit.md)). Donde algo no está probado se dice.

## Red y permisos

- Sin permiso `INTERNET`, sin dependencias de terceros, sin código de red, analytics, crash reporting ni logs de texto.
  Los diccionarios viajan en el APK; las herramientas de `tools/` descargan fuentes **solo en la máquina de desarrollo**.
- Permisos declarados: `VIBRATE` (háptica) y `READ_USER_DICTIONARY` (nivel *normal* en Android: se usa solo al pulsar
  «importar del diccionario de Android»). Sin contactos, almacenamiento externo, cámara, micrófono ni ubicación.
- `allowBackup=false`, `fullBackupContent=false` y reglas Android 12+ que excluyen copia en la nube y transferencia.

## Qué se guarda en el dispositivo

- **Preferencias** (`keyboard_preferences`): ajustes de sonido, vibración, sugerencias, tema, idioma, etc. Ningún texto.
- **Diccionario personal** (`personal_dictionary.json`, almacenamiento privado de la app): palabras que **tú** escribes y que no
  están en el diccionario del idioma, con un contador de usos. No guarda frases, contexto ni orden de escritura.
  - Una palabra usada una vez no se sugiere ni protege de la autocorrección (podría ser una errata); desde la segunda vez
    sí. Deshacer una autocorrección con Retroceso cuenta como confirmación inmediata.
  - Está separado por idioma. Se puede ver, borrar palabra a palabra, borrar entero y exportar/importar.
  - **Se puede desactivar**: Ajustes › typing › «Aprender palabras nuevas». Está **activado por defecto**; si se prefiere
    opt-in, es un cambio de una línea (`KeyboardPreferences.learnWords`).
- Nunca se guardan contraseñas, números de tarjeta ni contenido de campos protegidos (ver abajo).

## Cuándo no se lee ni se aprende

El texto del campo no se lee para sugerir, corregir ni aprender —y nada se aprende— cuando:

- el campo es de contraseña (texto, visible, web o numérica), correo electrónico, URL o filtro;
- el editor declara `IME_FLAG_NO_PERSONALIZED_LEARNING`, `TYPE_TEXT_FLAG_NO_SUGGESTIONS` o `TYPE_TEXT_FLAG_AUTO_COMPLETE`;
- el usuario desactiva «Texto predictivo» (o «Aprender palabras nuevas», para el aprendizaje).

En contraseñas, Retroceso borra un punto de código sin consultar el texto (no usa la segmentación por grafemas).
Los fragmentos de URL, correo o identificadores escritos en un campo normal (`www.sitio.com`, `ana@correo.com`,
`nombre_usuario`) tampoco se corrigen ni se aprenden. Mientras el diccionario real aún se está cargando, el motor no
sugiere, corrige ni aprende.

## Copia de seguridad

- **Exportar / importar** usa el Selector de archivos de Android (SAF): no hay permiso de almacenamiento y **nada pasa por el
  portapapeles** (versiones anteriores copiaban ajustes y palabras al portapapeles del sistema).
- El archivo (JSON v2) contiene solo ajustes y las palabras aprendidas por idioma. No incluye historial de escritura.
- La importación trata el archivo como no confiable: máximo 1 MiB, UTF-8 estricto, ≤ 20.000 palabras por idioma, palabras de 2–40
  letras/dígitos/apóstrofos, idiomas es/en/fr/de, y cada ajuste se valida por tipo y rango. Un archivo rechazado muestra un
  mensaje genérico y no se vuelca ni se registra su contenido.

## Portapapeles

El botón 📋 de la barra de sugerencias pega el contenido actual del portapapeles **solo cuando lo pulsas**. No se lee en
segundo plano ni se guarda historial.

## Límites

La aplicación anfitriona sigue siendo responsable de declarar bien `inputType`. No se promete protección frente a una app
anfitriona maliciosa ni se intenta adivinar si un campo de texto genérico contiene un secreto.
