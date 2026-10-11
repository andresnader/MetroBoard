# Privacidad v0.1

- Sin `INTERNET` ni código de red, analytics, crash reporting o logs de texto.
- Sin permisos de contactos, almacenamiento externo, cámara, micrófono ni ubicación.
- No se persisten pulsaciones, prefijos, frases, contraseñas ni palabras aprendidas.
  `DisabledPersonalDictionary` no guarda nada aunque se llame a `learn`.
- SharedPreferences contiene únicamente `vibration`, `sound` y `suggestions`.
  `allowBackup=false`, `fullBackupContent=false` y reglas explícitas Android 12+
  que excluyen backup en nube y transferencia entre dispositivos. Los campos de prueba de la
  Activity no guardan su estado y no solicitan autofill.
- En texto normal se consulta contexto acotado del editor en memoria: hasta
  256 unidades UTF-16 para capitalización y 49 para sugerencias. No hay buffer
  histórico, composición persistida ni almacenamiento de contexto en el motor.
- Contraseñas normales, visibles, web y numéricas: sin predicción, aprendizaje,
  lectura para sugerencias, mayúsculas automáticas ni sustitución de doble espacio.
  Shift manual continúa funcionando para escribir contraseñas.
- El borrado puede consultar temporalmente caracteres adyacentes para mantener
  pares sustitutos intactos al usar el fallback de editores antiguos. No los
  muestra ni almacena. La selección se gestiona mediante posiciones, no contenido.
- `IME_FLAG_NO_PERSONALIZED_LEARNING`, `TYPE_TEXT_FLAG_NO_SUGGESTIONS`,
  `TYPE_TEXT_FLAG_AUTO_COMPLETE`, email y URL desactivan las sugerencias.
- Al terminar un campo se limpia la barra, el popup y el estado transitorio de
  Shift/doble espacio. El adaptador solo conserva posiciones de cursor esperadas
  mientras procesa confirmaciones del editor.
- El motor de demostración es local y determinista. No hay nube, sincronización,
  importación de contactos ni telemetría.

La app anfitriona sigue siendo responsable de sus propios datos y de declarar
correctamente inputType. No se promete protección frente a una app anfitriona
maliciosa ni se adivina si un campo de texto genérico contiene un secreto.

Para v0.2, cualquier aprendizaje requerirá una decisión explícita de producto,
opt-in del usuario, almacenamiento privado local, borrado accesible y pruebas
que impidan aprender en campos sensibles. El contrato por sí solo no habilita
esa funcionalidad.
