# MetroBoard 0.4.0 — mediciones

Fecha: 2026-10-12. Todo lo de abajo se midió en el emulador `Medium_Phone_API_37.0` (Android 17, ARM), no en un teléfono: sirve para
comparar versiones entre sí, no para prometer una cifra absoluta. Las órdenes están en `docs/0.4/` y en el historial de git.

## Latencia al escribir

Método: la versión *debug* registra, por cada tecla, cuánto esperó el toque antes de atenderse y cuánto tardó el procesamiento
(`adb logcat -s MB_LAT MB_PHASES`). 98 pulsaciones reales (`adb shell input tap`) de una frase en español, tres versiones
alternadas, dos tandas cada una, en la misma sesión del emulador.

| Versión | Procesar una tecla, mediana | p95 | máximo |
|---|---|---|---|
| Antes (sin caché del texto) | 6 y 5 ms | 37 y 33 ms | 55 y 60 ms |
| Con copia local del texto | 3 y 3 ms | 11 y 10 ms | 15 y 15 ms |
| Copia local + sugerencias en otro hilo | 2 y 2 ms | 4 y 4 ms | 8 y 10 ms |

Causa encontrada: `getTextBeforeCursor`/`getTextAfterCursor` son llamadas síncronas a la app (esperan a su hilo de interfaz) y se
hacían dos por letra; en el desglose por fases (`ipc.before` p95 25–28 ms, `ipc.after` p95 21–26 ms, `suggest` p95 38–40 ms).

## Word Flow con contexto (sintético)

Pares reales «palabra anterior → siguiente» de `<lang>.nextwords`, trazo sintético con ruido gaussiano, 400 pares por idioma.
Primera posición, sin → con contexto:

| Idioma | ruido 0,03 | ruido 0,05 |
|---|---|---|
| es | 88 → 93 % | 63 → 67 % |
| fr | 80 → 87 % | 57 → 64 % |
| de | 74 → 81 % | 52 → 56 % |
| en | 77 → 79 % | 46 → 47 % |

Los pares salen de las propias listas, así que es el caso favorable; las palabras fuera de ellas no cambian. El trazo es sintético
(modela a una persona cuidadosa): falta validarlo con trazos reales, para lo que haría falta un grabador.

## Datos de las listas de siguiente palabra

Ver [`../nextwords-build-report.md`](../nextwords-build-report.md): 29.000–33.000 palabras por idioma, 62.000–81.000 pares.

## 0.4.1 — reporte de pruebas en WhatsApp (2026-10-12)

El APK probado era la 0.4.0 completa (contiene la caché del texto, las sugerencias asíncronas y las listas de siguiente palabra),
así que **las mediciones del emulador no reflejaban el teléfono**.

- **Pulsaciones perdidas:** prueba instrumentada `fastSloppyTypingNeverDropsOrReordersAKey` (6 semillas × 120 pulsaciones, 6–14 por
  segundo, uno y dos pulgares con solapes, el pulgar «rodando» durante el contacto): pasa. La lógica de toques no descarta ni reordena.
- **Carga por tecla en el emulador** (98 teclas): espera del toque mediana 2 ms; procesar la tecla 2–4 ms; la barra espaciadora con
  erratas 5–8 ms (`edit`) y `getCursorCapsMode` 1–1,6 ms (ya eliminado).
- **Memoria:** el diccionario de un idioma ocupaba 51 MB de heap en la JVM; ahora 34 MB. Con un idioma cargado el proceso del teclado
  pasó de 54 a 43 MB de heap Java y de 103 a 84 MB de PSS.
- **Reinicio con PIN (emulador):** antes de desbloquear, `dumpsys user` da `RUNNING_LOCKED` y `ime list -s` solo muestra Gboard;
  tras desbloquear, `RUNNING_UNLOCKED`, el teclado sigue siendo el predeterminado (`settings get secure default_input_method`) y se
  muestra al instante.
- **Borrado mantenido:** mantener Retroceso 1,0 / 1,5 / 2,5 s borró 6 / 11 / 23 caracteres (con 60 ms fijos serían ≈ 9 / 18 / 35).

Lo que sigue sin poder afirmarse: la causa exacta del retraso en el teléfono. El informe de **Ajustes ▸ Acerca de ▸ diagnóstico** la
acotará: separa lo que espera el toque (el sistema o el hilo bloqueado), lo que tarda cada fase, los bloqueos del hilo principal, el
heap y los tiempos de carga del diccionario.

