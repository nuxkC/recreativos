# Cuadre semanal de caja del técnico — Diseño

Fecha: 2026-06-29 · Estado: aprobado en brainstorming, pendiente de plan de implementación.

## Contexto y problema

El técnico recauda las máquinas de los bares a lo largo de la semana (lunes a
viernes). En cada recaudación saca el efectivo de la máquina y entrega al bar su
parte **en mano** (con firma). El resto —el efectivo de la empresa— se lo queda
y lo acumula durante la semana. A final de semana debe **entregar todo ese
dinero** y, antes, comprobar que lo que lleva físicamente encima cuadra con la
suma de lo recaudado esa semana.

Hoy la app no le da ninguna herramienta para ese cuadre: tiene que sumarlo a
mano a partir de los tickets. Como ya guardamos cada recaudación con su desglose
exacto de denominaciones, podemos calcular automáticamente cuánto y en qué
monedas/billetes **debería** llevar, y dejar que cuente lo que lleva de verdad y
compare.

## Objetivo y alcance

Un apartado nuevo en la **app Android del técnico** ("Mi caja") que, para la
semana ISO en curso (y semanas anteriores en lectura):

1. Muestra cuánto efectivo de la empresa **debería llevar** el técnico — total €
   y desglose por denominación — derivado de sus recaudaciones de esa semana.
2. Le deja **contar** su efectivo físico (por denominación) y **compara**:
   cuadra / sobran X € / faltan X €, con la diferencia por denominación para
   localizar el descuadre.
3. El recuento físico **se guarda en el móvil** y sobrevive a salir/volver.

### No-objetivos (YAGNI, explícitamente fuera)

- **Sin oficina/web**: es una herramienta de autocomprobación del técnico. La
  oficina no ve ni valida el cuadre (no hay tabla de liquidación server-side ni
  pantalla web).
- **Sin estado de "entregado/liquidado"**: el periodo es la semana ISO del
  calendario, automática; no hay un cierre manual que reinicie el periodo.
- **Sin recálculo de dinero**: no se recalcula ninguna cifra económica; solo se
  **agregan** importes que el servidor ya calculó y persistió por recaudación.

## Decisiones (acordadas en brainstorming)

| Tema | Decisión |
|---|---|
| Alcance | Solo el técnico, en la app Android. |
| Qué se compara | Efectivo llevado = **Σ(`desglose_total` − `desglose_local`)** de la semana, **total € y por denominación**. |
| Periodo | Semana ISO (lun–dom) en la **TZ de la empresa**, automática; semanas pasadas en lectura. |
| Origen del "esperado" | **Servidor (autoritativo)** vía una vista nueva. |
| Recaudaciones sin subir | **Exigir subir todo primero**: si hay pendientes, se bloquea el cuadre y se ofrece subir. |
| Recuento físico | **Persistido en el móvil** (Room), sobrevive a salir/volver. |
| Mecanismo servidor | **Vista SQL** `security_invoker` + lectura PostgREST (enfoque A). |

### Verificación del modelo de datos (hecha)

`public.recaudacion` guarda `desglose_total jsonb` y `desglose_local jsonb`
(arrays de `{denominacion, cantidad}`), con constraints:

- `desglose_total` cuadra con la recaudación bruta (constraint
  `chk_desglose_total_suma`) → es **exactamente** lo sacado de la máquina.
- `desglose_local` cuadra con lo entregado al local en mano (la parte del local;
  cuando hay recuperación de deuda, lo realmente pagado al local) → registrado
  al céntimo.

Lo que el técnico se queda y lleva encima = `total − local`, e incluye —además
de la parte de la empresa— la tasa y el importe recuperado de deudas, que
también van a la empresa. Por tanto `total − local` por denominación es **dato
exacto persistido**. Matiz
menor: la app (`DenominacionesScreen`, paso Local) solo exige que el desglose
del local **sume** `parteLocal`/`pagadoLocal`, no capea cada denominación por la
disponible en el total; así que en un caso raro (dar cambio con fondo propio) el
neto de **una** denominación podría ser negativo. **El total € siempre es
exacto** y es la cifra que manda en el cuadre.

## Modelo de datos / servidor

Migración aditiva e inmutable (`YYYYMMDDHHMMSS_…`) con una vista nueva.

### Vista `v_cuadre_semanal_tecnico`

- `security_invoker = true` (igual que `v_recaudacion_historica`): la RLS P2 de
  `recaudacion` aplica; se añade `tecnico_id = auth.uid()` para que sea la caja
  **propia** del técnico.
- Grano: una fila por **(semana ISO, denominación)**.
- Solo cuenta recaudaciones **vigentes** (excluye anuladas — mecanismo exacto a
  confirmar contra el modelo de anulación al implementar).

Columnas:

| columna | significado |
|---|---|
| `empresa_id`, `tecnico_id` | contexto; el filtro `tecnico_id = auth.uid()` acota a tu caja |
| `semana_inicio` | lunes de la semana ISO, en la **TZ de la empresa**: `date_trunc('week', fecha AT TIME ZONE zona_horaria)::date` |
| `denominacion` | numeric ∈ {0.10, 0.20, 0.50, 1, 2, 5, 10, 20, 50} |
| `cantidad_neta` | `Σ cantidad(total) − Σ cantidad(local)` de esa denominación en la semana |
| `importe_neto` | `denominacion × cantidad_neta` |
| `num_recaudaciones` | recaudaciones de la semana, vía `COUNT(DISTINCT recaudacion_id) OVER (PARTITION BY semana_inicio)` (mismo valor en todas sus filas) |

Agregación del `jsonb`: `jsonb_array_elements(desglose_total)` y
`jsonb_array_elements(desglose_local)` se desanidan a (denominación, cantidad) y
se suman con signo (`+total`, `−local`) agrupando por `semana_inicio` y
`denominacion`. Reutiliza el conjunto de denominaciones ya validado en BBDD.

Derivados en el cliente: el **total € de la semana** = `Σ importe_neto` sobre sus
denominaciones (agregación de display; el dinero por recaudación ya es SSOT). El
histórico de semanas sale filtrando por `semana_inicio`.

### Test pgTAP (`supabase/tests/sql/`, `BEGIN…ROLLBACK`)

- Neto por denominación, total € y `num_recaudaciones` correctos con 2–3
  recaudaciones en una semana.
- **Aislamiento**: un técnico no ve la caja de otro.
- Frontera de semana respeta la TZ de la empresa (recaudación domingo 23:30).
- Excluye recaudaciones anuladas.

## Android: capas (`feature/cuadre/`)

Clean Architecture `data → domain ← ui`. Dinero siempre `BigDecimal` (el DTO
transporta `String`). Las features no se importan entre sí.

### Data

- **DTO + `CuadreRepository`**: lee `v_cuadre_semanal_tecnico` por PostgREST
  (cliente Supabase, como el resto), filtrando por `semana_inicio`. Mapea a
  dominio. Dinero `String → BigDecimal`.
- **Room `CuadreRecuentoEntity`** (recuento físico persistido): PK
  `(empresa_id, tecnico_id, semana_inicio)`; denominaciones contadas como JSON
  `Map<denominacion, cantidad>`; `updated_at`. DAO `upsert` +
  `observar(empresa, tecnico, semana): Flow`. Es lo que hace que el recuento
  siga ahí al salir/volver.

### Domain

- `CuadreSemanal`: `semana`, `numRecaudaciones`, `totalEsperado: BigDecimal`,
  `lineas: List<LineaCuadre(denominacion, cantidadEsperada, importeEsperado)>`.
- Cálculo de **diferencia** (puro, testeable): por denominación
  `cantidadContada − cantidadEsperada`; total `totalContado − totalEsperado`.
  Veredicto: *cuadra* (= 0) / *sobran X* (> 0) / *faltan X* (< 0).

### UI

- `CuadreViewModel` combina 3 fuentes: (a) cuadre del servidor de la semana
  elegida, (b) recuento de Room, (c) contador de pendientes
  (`RecaudacionPendienteDao.observarContadorPendientes`).
- `StateFlow<CuadreUiState>` (sealed):
  - `Cargando`
  - `BloqueadoPorPendientes(reintentables: Int, fallidas: Int)`
  - `Listo(semana, numRecaudaciones, totalEsperado, totalContado, diferencia, lineas)`
  - `Vacio` (semana sin recaudaciones)
  - `SinConexion` / `Error`
- Acciones: `onContarChange(denominacion, cantidad)` → `upsert` en Room;
  `onSubirPendientes()` → dispara la cola de subida; `onCambiarSemana(±1)`.
- **En vivo**: se re-carga ante `revision` del `RealtimeManager` (patrón
  realtime universal: `revision.drop(1).collect { cargar() }`), para reflejar
  recaudaciones que se sincronizan o se anulan.

### Navegación

Acceso desde **Inicio/agenda** (tarjeta/acción "Mi caja de la semana"). No toca
la barra de navegación inferior (es semanal, no diario).

## Pantalla / UX

Reutiliza el sistema de diseño ("Confianza Industrial"): `RecreTopBar`,
`AppCard`, `CountUpText` (dinero), `StatusChip`, Lottie de éxito. Pensada para el
pulgar.

### Estado normal ("Listo")

```
┌──────────────────────────────────────────┐
│ ‹  Mi caja · Semana 26 (22–28 jun)     ›  │   TopBar; ‹ › cambia de semana
├──────────────────────────────────────────┤
│  Deberías llevar                          │
│       347,50 €        (de 12 recaudac.)   │   héroe (CountUpText)
│  ───────────────────────────────────────  │
│  Llevas 327,50 €   · [ FALTAN 20,00 € ]   │   al contar; StatusChip rojo/verde
├──────────────────────────────────────────┤
│  Denominación   Deberías   Tú cuentas   Δ │
│   50 €              2          [ 2 ]     · │
│   20 €              4          [ 3 ]    −1 │   Δ por fila localiza el descuadre
│   10 €              6          [ 6 ]     · │
│    …                                       │
│   0,10 €           14         [ 14 ]     · │
└──────────────────────────────────────────┘
```

- **Conteo en tabla inline**: una fila por denominación con *Deberías* (servidor)
  y *Tú cuentas* (input, guardado en Room) y la Δ por fila. Se ve todo a la vez
  → natural para cuadrar.
- **Veredicto**: `totalContado − totalEsperado` → **Cuadra** (check verde,
  Lottie) / **Sobran X €** / **Faltan X €**. La Δ por fila indica *dónde*.
- **Semanas pasadas**: con ‹ › se retrocede; en lectura se ve *Deberías* y, si se
  contó, el recuento guardado.

### Otros estados

- **Bloqueado por pendientes**: en vez del cuadre, una tarjeta. Si hay
  reintentables → *"Tienes N recaudaciones sin subir. El cuadre necesita que
  estén todas. [Subir ahora]"*. Si hay `fallida` → *"Revisa el panel de
  subidas"* (no se arregla reintentando). Al llegar a 0, se muestra el cuadre.
- **Vacío**: semana sin recaudaciones → "Aún no has recaudado esta semana".
- **Sin red**: el esperado es del servidor; sin red ni cache → "conéctate para
  ver el cuadre" (el recuento local sí se ve).

## Errores y casos límite

- **Semana sin recaudaciones**: esperado 0 €, sin alarma.
- **Pendientes reintentables vs `fallida`**: el bloqueo los distingue (ver
  estados); el contador `observarContadorPendientes` incluye ambos.
- **Recaudación anulada**: la vista cuenta solo vigentes.
- **Neto negativo en una denominación** (fondo propio): se muestra tal cual; el
  total € manda.
- **Frontera de semana / TZ**: asignación en TZ de la empresa dentro de la vista.

## Testing

- **pgTAP** (vista): ver sección de servidor.
- **Android unit** (lo de valor): cálculo de **diferencia** (dominio puro,
  `BigDecimal`); DAO `upsert`/`observe` del recuento; máquina de estados del VM
  (bloqueado/listo/vacío). Validar dependencias con `assembleDebug` (gotcha de
  locale no-UTF-8 en `./gradlew test`).
- Sin e2e (Android).

## Orden de implementación (alto nivel)

1. **Servidor**: migración con `v_cuadre_semanal_tecnico` + test pgTAP. `db reset`
   + `supabase test db` en verde.
2. **Android data**: DTO + `CuadreRepository` (lectura de la vista); Room
   `CuadreRecuentoEntity` + DAO + migración Room.
3. **Android domain**: modelos + cálculo de diferencia (con tests).
4. **Android ui**: `CuadreViewModel` (combina servidor + Room + pendientes +
   realtime) y pantalla Compose; acceso desde Inicio/agenda.
5. **Pulido**: estados (bloqueado/vacío/sin red), Lottie de "cuadra", textos
   `strings.xml`.

## Riesgos y cuestiones abiertas

- **Mecanismo de anulación**: confirmar cómo se marca/excluye una recaudación
  anulada para el filtro "vigentes" de la vista (al implementar el paso 1).
- **Agregación `jsonb` en la vista**: el desanidado con signo y el `GROUP BY` por
  (semana, denominación) deben rendir bien con el volumen real; índices por
  `(tecnico_id, fecha)` ya existentes deberían bastar, revisar `EXPLAIN`.
- **Coherencia "subir primero"**: el gate depende del contador local de
  pendientes; si una recaudación quedó `fallida`, el técnico no puede cuadrar
  hasta resolverla en el panel de subidas (comportamiento deseado, pero conviene
  que el copy lo deje claro).
