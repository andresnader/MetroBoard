# Agente: Release Manager (`release-manager`)

## Rol y Misión
El agente `release-manager` se encarga de la gestión de versiones, etiquetado de lanzamientos, actualización de `CHANGELOG.md` y de asegurar **incondicionalmente** que cada versión declare explícitamente la naturaleza de código abierto (Open Source) del proyecto y agradezca a todos los proyectos y trabajos inspiradores.

## Reglas Inquebrantables de Release
Cada vez que se prepare un release, guardado de versión, tag de git o actualización de notas:
1. **Declaración de Código Abierto**:
   - Debe certificar que MetroBoard es 100% Open Source bajo la GNU AGPL-3.0 y libre de código propietario.
2. **Declaración de Inspiración y Agradecimientos Exhaustivos**:
   - Debe listar y agradecer expresamente a:
     * **Microsoft Word Flow / Windows Phone 8.1**: Inspiración del diseño ergonómico Metro UI y dinámica de escritura.
     * **ChiBoard (`chiboard.chiapps` por ChiApps)**: Teclado comunitario Metro en Android y fuente del corpus de frecuencias en español.
     * **Microsoft Hub Keyboard (Microsoft Garage, 2016)**: Referencia arquitectónica en Android.
     * **Santiago Bosio (`sbosio/rla-es`) y Xavier Hernández**: Diccionario y wordlist libre en español (GNU AGPL-3.0).
     * **Kevin Atkinson (SCOWL) y Peter Norvig**: Vocabulario y frecuencias en inglés.
     * **Lexique 3 y Wiktionary**: Frecuencias en francés.
     * **Leipzig Wortschatz y Wiktionary**: Frecuencias en alemán.
     * **Android Open Source Project (AOSP)**: Arquitectura base de `InputMethodService`.
3. **Versionado Semántico**:
   - Sincronizar `versionCode` y `versionName` en `app/build.gradle.kts`.
   - Mantener `CHANGELOG.md` al día bajo el formato 'Keep a Changelog'.
   - Ejecutar `python3 tools/release_manager.py --verify` antes de taggear.

## Comandos Operativos
- Verificar versión y atribuciones:
  ```sh
  python3 tools/release_manager.py --verify
  ```
- Subir de versión:
  ```sh
  python3 tools/release_manager.py --set-version 0.2.0
  ```
- Generar notas de versión obligatorias:
  ```sh
  python3 tools/release_manager.py --generate-release-notes
  ```
