# Voz y microcopy — Recre (rediseño F0)

> Artefacto de texto, no código. Define la voz de marca y las reglas para reescribir
> errores, vacíos y botones. Lo consumen TODAS las fases del rediseño al redactar copy
> (los textos viven en `res/values/strings.xml`, nunca hardcodeados en pantalla).

## 1. La voz

El técnico está de pie, en un local con ruido, a veces sin cobertura, con prisa. La app
le habla como un compañero competente: **calmada, precisa, en español llano**.

- **Calmada**: ni alarmista ni efusiva. Un descuadre no es una catástrofe; un acierto no
  necesita confeti textual.
- **Precisa**: dice exactamente qué pasa y qué hacer. Cero ambigüedad.
- **Llana**: cero tecnicismos hacia el técnico (`422`, `timeout`, `JWT`, `payload`,
  `baseline`… fuera del texto visible). El término de dominio sí se usa cuando es el
  vocabulario real del oficio (recaudación, instalación, máquina, local, tasa).
- **Breve**: una frase para el qué, una para el cómo. El móvil se lee de un vistazo.
- **En segunda persona y presente**: "Revisa la caja", no "El usuario deberá revisar".
- **Sin culpar**: "La caja no llega para la tasa", no "Has metido mal los datos".

## 2. Do / Don't

| ❌ Evitar | ✅ Preferir |
|---|---|
| "Error 422: insufficient_funds" | "La caja no llega para cubrir la tasa. Revísalo." |
| "Network request failed" | "Sin conexión. Se subirá solo cuando vuelva la red." |
| "Operación completada con éxito" | "Recaudación guardada." |
| "¿Está seguro de que desea continuar?" | "¿Confirmas la recaudación de 1.200,00 €?" |
| "No data available" | "Aún no hay recaudaciones en este local." |
| "Validación fallida en el campo importe" | "El importe tiene que ser mayor que 0." |
| "Sincronizando datos con el servidor…" | "Subiendo…" |
| "¡Genial! ¡Todo perfecto! 🎉" | "Cuadra." |

Reglas rápidas:
- Importes SIEMPRE formateados es-ES con € (`1.200,00 €`), nunca crudos.
- El estado nunca se comunica solo por color: el texto lo nombra (icono + palabra).
- Nada de jerga de error: el código técnico va al log, no a la pantalla.
- Mayúscula solo inicial; sin gritos en mayúsculas; sin signos de exclamación salvo
  cuando el tono lo pida de verdad (casi nunca).

## 3. Plantillas por estado (los 7)

1. **Cargando** — verbo en gerundio, una palabra. "Cargando…", "Subiendo…",
   "Calculando…". Acompaña a un skeleton/spinner, nunca a una pantalla en blanco.
2. **Vacío** — constata + invita, sin dramatismo. "Aún no hay recaudaciones aquí."
   (+ CTA si procede: "Recaudar"). Ilustración propia opcional.
3. **Error de red** — neutro, no rojo de alarma; promete reintento automático cuando
   aplica. "Sin conexión. Se subirá solo cuando vuelva la red." Si es bloqueante:
   "Esto necesita conexión. Inténtalo cuando recuperes red."
4. **Éxito** — seco y concreto, nombra lo guardado. "Recaudación guardada.",
   "Cuadra." Sin celebración recargada.
5. **Confirmación** — pregunta cerrada con el dato clave dentro. "¿Confirmas la
   recaudación de 1.200,00 €?" Botones: acción afirmativa nombrada ("Confirmar"),
   nunca "Sí/No" sueltos.
6. **Descuadre / aviso** — describe la diferencia y qué revisar, sin acusar. "La caja
   no cuadra con los contadores por 12,50 €. Revisa el desglose."
7. **Offline / cola pendiente** — informa de que el trabajo no se pierde. "Sin
   conexión · se subirá al recuperar la red." (banner P8).

## 4. Botones (verbos de acción)

- Verbo en infinitivo o imperativo corto: "Recaudar", "Confirmar", "Reintentar",
  "Descartar", "Reimprimir", "Sincronizar ahora".
- El CTA principal nombra la acción, no "Aceptar"/"OK".
- Acciones destructivas: verbo explícito ("Descartar", "Anular"), nunca "Sí".

---

**Pendiente (fases posteriores):** glosario término-a-término dominio↔UI y revisión de
los strings existentes en `strings.xml` contra esta guía al migrar cada pantalla.
