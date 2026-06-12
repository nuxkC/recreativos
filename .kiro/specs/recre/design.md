# Recre — Diseño técnico

## 1. Stack

| Capa | Tecnología |
|---|---|
| Base de datos | Supabase Postgres (managed) |
| Autenticación | Supabase Auth (email + password) |
| API | Supabase REST/RPC + Edge Functions (Deno + TypeScript) |
| Storage | Supabase Storage (firmas, fotos, PDFs, logos) |
| Web | Next.js 14 (App Router) + TypeScript + Tailwind + shadcn/ui + supabase-js |
| App Android | Kotlin + Jetpack Compose + Supabase Kotlin SDK + Room (cache) + WorkManager (sync) |
| Impresión | Librería ESC/POS para Android, conexión Bluetooth a AGPTEK PT210 |
| PDFs | Generación server-side en Edge Function (ej. `pdf-lib` o similar) |
| CI/CD | GitHub Actions (a definir) |

## 2. Arquitectura

```
+----------------+         +-------------------+
|   Web Next.js  |         |  Android (Kotlin) |
+--------+-------+         +---------+---------+
         |                            |
         |  HTTPS (supabase-js / SDK) |
         v                            v
+----------------------------------------------+
|              Supabase                        |
|  Auth | Postgres+RLS | Storage | Edge Funcs  |
+----------------------------------------------+
```

- La web y la app hablan **directamente con Supabase** para CRUD simples (con RLS).
- Las operaciones críticas (cálculo y persistencia de recaudación, generación de PDF, resolución de conflictos) pasan por **Edge Functions** que validan y centralizan la lógica.

## 3. Modelo de datos (Postgres)

Convenciones: todas las tablas tienen `id uuid PK DEFAULT gen_random_uuid()`, `created_at timestamptz DEFAULT now()`, `updated_at timestamptz` mantenido por trigger. Los nombres de campo van en snake_case y en español por consistencia con la UI.

### 3.1 `empresa`
```sql
empresa (
  id uuid PK,
  nombre text NOT NULL,
  cif text,
  direccion text,
  telefono text,
  email text,
  logo_url text,
  zona_horaria text DEFAULT 'Europe/Madrid',
  ticket_cabecera text,
  ticket_pie text,
  porcentaje_recuperacion smallint NOT NULL DEFAULT 0  -- % de la parte_local retenido por defecto para amortizar deudas del local (T-212)
)
```

### 3.2 `usuario`
Perfil que extiende `auth.users`.
```sql
usuario (
  id uuid PK REFERENCES auth.users(id) ON DELETE CASCADE,
  nombre_completo text NOT NULL,
  telefono text,
  avatar_url text
)
```

### 3.3 `empresa_usuario`
```sql
empresa_usuario (
  empresa_id uuid REFERENCES empresa(id) ON DELETE CASCADE,
  usuario_id uuid REFERENCES usuario(id) ON DELETE CASCADE,
  rol text NOT NULL CHECK (rol IN ('owner','admin','gestor','tecnico','contable')),
  activo boolean NOT NULL DEFAULT true,
  PRIMARY KEY (empresa_id, usuario_id)
)
```

### 3.4 `licencia`
```sql
licencia (
  id uuid PK,
  empresa_id uuid REFERENCES empresa(id) ON DELETE RESTRICT,
  numero text NOT NULL,
  tipo text,
  fecha_expedicion date,
  fecha_caducidad date,
  comunidad_autonoma text,
  estado text NOT NULL DEFAULT 'activa' CHECK (estado IN ('activa','suspendida','caducada','baja')),
  notas text,
  UNIQUE (empresa_id, numero)
)
```

### 3.5 `maquina`
```sql
maquina (
  id uuid PK,
  empresa_id uuid REFERENCES empresa(id) ON DELETE RESTRICT,
  numero_serie text NOT NULL,
  modelo text,
  fabricante text,
  valor_credito numeric(4,2) NOT NULL CHECK (valor_credito > 0),
  contador_entradas_inicial bigint NOT NULL DEFAULT 0,
  contador_salidas_inicial bigint NOT NULL DEFAULT 0,
  estado text NOT NULL DEFAULT 'almacen' CHECK (estado IN ('almacen','instalada','averiada','baja')),
  notas text,
  UNIQUE (empresa_id, numero_serie)
)
```

### 3.6 `local`
```sql
local (
  id uuid PK,
  empresa_id uuid REFERENCES empresa(id) ON DELETE RESTRICT,
  nombre text NOT NULL,
  direccion text,
  cif_o_nif text,
  titular_nombre text,
  telefono text,
  email text,
  notas text,
  porcentaje_recuperacion smallint  -- override del % de recuperación; NULL = hereda empresa.porcentaje_recuperacion (T-212)
)
```

### 3.7 `instalacion`
```sql
instalacion (
  id uuid PK,
  empresa_id uuid REFERENCES empresa(id) ON DELETE RESTRICT,
  maquina_id uuid REFERENCES maquina(id),
  licencia_id uuid REFERENCES licencia(id),
  local_id uuid REFERENCES local(id),
  fecha_inicio date NOT NULL,
  fecha_fin date,
  tasa_semanal numeric(8,2) NOT NULL CHECK (tasa_semanal >= 0),
  porcentaje_local numeric(5,2) NOT NULL CHECK (porcentaje_local BETWEEN 0 AND 100),
  contador_entradas_base bigint NOT NULL,
  contador_salidas_base bigint NOT NULL,
  tolva numeric(10,2) NOT NULL DEFAULT 0 CHECK (tolva >= 0),  -- dinero físico dejado en la máquina (informativo; la deuda del local va en credito_local) (T-212)
  estado text NOT NULL DEFAULT 'activa' CHECK (estado IN ('activa','cerrada')),
  notas text
);

-- Una máquina solo puede estar en una instalación activa
CREATE UNIQUE INDEX uq_maquina_activa ON instalacion(maquina_id) WHERE estado = 'activa';
-- Una licencia solo puede estar en una instalación activa
CREATE UNIQUE INDEX uq_licencia_activa ON instalacion(licencia_id) WHERE estado = 'activa';
```

### 3.8 `cambio_placa`
```sql
cambio_placa (
  id uuid PK,
  empresa_id uuid REFERENCES empresa(id) ON DELETE RESTRICT,
  instalacion_id uuid REFERENCES instalacion(id),
  fecha timestamptz NOT NULL,
  usuario_id uuid REFERENCES usuario(id),
  contador_entradas_nuevo bigint NOT NULL DEFAULT 0,
  contador_salidas_nuevo bigint NOT NULL DEFAULT 0,
  motivo text,
  numero_serie_placa_anterior text,
  numero_serie_placa_nueva text,
  foto_url text,
  notas text
)
```

### 3.9 `recaudacion`
```sql
recaudacion (
  id uuid PK,
  empresa_id uuid REFERENCES empresa(id) ON DELETE RESTRICT,
  instalacion_id uuid REFERENCES instalacion(id),
  tecnico_id uuid REFERENCES usuario(id),
  fecha timestamptz NOT NULL,

  contador_entradas_anterior bigint NOT NULL,
  contador_salidas_anterior bigint NOT NULL,
  contador_entradas_actual bigint NOT NULL,
  contador_salidas_actual bigint NOT NULL,

  valor_credito_aplicado numeric(4,2) NOT NULL,

  recaudacion_bruta numeric(10,2) NOT NULL,
  semanas_aplicadas integer NOT NULL CHECK (semanas_aplicadas >= 0),
  tasa_semanal_aplicada numeric(8,2) NOT NULL,
  tasa_total_aplicada numeric(10,2) NOT NULL,
  recaudacion_neta numeric(10,2) NOT NULL,
  porcentaje_local_aplicado numeric(5,2) NOT NULL,
  parte_local numeric(10,2) NOT NULL,
  parte_empresa numeric(10,2) NOT NULL,
  recuperado_total numeric(10,2) NOT NULL DEFAULT 0,            -- retenido de la parte_local para amortizar deuda (T-214); <= parte_local
  pagado_local numeric(10,2) GENERATED ALWAYS AS (parte_local - recuperado_total) STORED,  -- lo que se lleva el local; desglose_local cuadra con esto

  -- desglose embebido
  desglose_total jsonb NOT NULL,
  desglose_local jsonb NOT NULL,

  -- evidencia
  firma_url text,
  foto_entradas_url text,
  foto_salidas_url text,
  ocr_entradas_valor bigint,
  ocr_salidas_valor bigint,
  pdf_url text,

  observaciones text,

  -- trazabilidad / sync
  dispositivo_id text,
  idempotency_key text NOT NULL UNIQUE,
  baseline_origen text NOT NULL CHECK (baseline_origen IN ('recaudacion_anterior','cambio_placa','instalacion_base')),
  baseline_id uuid,

  -- conflicto
  conflicto boolean NOT NULL DEFAULT false,
  bruto_recalculado numeric(10,2),
  neto_recalculado numeric(10,2),
  parte_local_recalculada numeric(10,2),
  parte_empresa_recalculada numeric(10,2),
  revisado_por uuid REFERENCES usuario(id),
  revisado_en timestamptz,
  resolucion text CHECK (resolucion IN ('aceptada','sustituida','anulada')),
  resolucion_notas text,

  -- estado
  estado text NOT NULL DEFAULT 'firme' CHECK (estado IN ('firme','anulada')),
  motivo_anulacion text,
  anulada_por uuid REFERENCES usuario(id),
  anulada_en timestamptz
);

CREATE INDEX idx_recaudacion_instalacion_fecha ON recaudacion(instalacion_id, fecha DESC);
CREATE INDEX idx_recaudacion_empresa_fecha ON recaudacion(empresa_id, fecha DESC);
CREATE INDEX idx_recaudacion_conflicto ON recaudacion(empresa_id) WHERE conflicto = true AND revisado_en IS NULL;
```

Estructura de `desglose_total` y `desglose_local` (validada en Edge Function):
```json
[
  {"denominacion": 20, "cantidad": 2},
  {"denominacion": 10, "cantidad": 4}
]
```

Las denominaciones permitidas: `0.10, 0.20, 0.50, 1, 2, 5, 10, 20, 50`.

### 3.10 `recaudacion_lock`
```sql
recaudacion_lock (
  instalacion_id uuid PK REFERENCES instalacion(id) ON DELETE CASCADE,
  tecnico_id uuid NOT NULL REFERENCES usuario(id),
  dispositivo_id text,
  started_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL
)
```

### 3.11 `lectura_no_recaudada`
Log mínimo de visitas en las que `bruto < tasa` y no se recaudó.
```sql
lectura_no_recaudada (
  id uuid PK,
  empresa_id uuid REFERENCES empresa(id) ON DELETE RESTRICT,
  instalacion_id uuid REFERENCES instalacion(id),
  tecnico_id uuid REFERENCES usuario(id),
  fecha timestamptz NOT NULL,
  contador_entradas_actual bigint NOT NULL,
  contador_salidas_actual bigint NOT NULL,
  bruto_estimado numeric(10,2) NOT NULL,
  tasa_estimada numeric(10,2) NOT NULL,
  notas text
)
```

### 3.12 `alerta`
```sql
alerta (
  id uuid PK,
  empresa_id uuid REFERENCES empresa(id) ON DELETE CASCADE,
  tipo text NOT NULL CHECK (tipo IN (
    'recaudacion_conflicto','licencia_caducidad','local_sin_recaudar','recaudacion_anulada','otra'
  )),
  referencia_id uuid,
  mensaje text NOT NULL,
  destinatario_usuario_id uuid REFERENCES usuario(id),
  leida boolean NOT NULL DEFAULT false,
  creada_en timestamptz NOT NULL DEFAULT now()
)
```

### 3.13 Vistas útiles

- `v_instalacion_actual`: instalación activa con joins a máquina, local, licencia y baseline calculada.
- `v_recaudaciones_por_local_mes`, `v_recaudaciones_por_maquina_mes`: agregados.
- `v_alertas_pendientes`: alertas no leídas por empresa.
- `v_credito_local_saldo`: cada deuda (tolva/préstamo) con su importe recuperado y saldo vivo (`principal − Σ recuperaciones`).
- `v_local_saldo`: saldo de deuda agregado por local (solo deudas abiertas), desglosado en tolva/préstamo. Alimenta el libro mayor y la tarjeta "capital en la calle".

### 3.14 `credito_local` (T-212)
```sql
credito_local (
  id uuid PK,
  empresa_id uuid REFERENCES empresa(id) ON DELETE RESTRICT,
  local_id uuid REFERENCES local(id) ON DELETE RESTRICT,
  tipo text CHECK (tipo IN ('tolva','prestamo')),
  instalacion_id uuid REFERENCES instalacion(id) ON DELETE SET NULL,  -- puntero re-apuntable; tolva: dónde está ahora, préstamo: NULL
  principal numeric(10,2) CHECK (principal > 0),  -- tolva: round(porcentaje_local × tolva); préstamo: lo prestado
  tipo_interes numeric(5,2) DEFAULT 0,            -- guardado, sin devengo en v1
  fecha date NOT NULL,
  estado text DEFAULT 'abierto' CHECK (estado IN ('abierto','saldado','condonado')),
  notas text
);
```

Deuda del local con la empresa. **Invariante de traslado**: la deuda de tolva
PERTENECE AL LOCAL, no a la instalación; `instalacion_id` es solo un puntero
re-apuntable. Al cambiar de máquina (cerrar instalación + abrir otra) la MISMA
tolva se traslada re-apuntando este crédito vía
`crear_instalacion(..., p_tolva_continua_credito_id)`, nunca duplicándola. El
delta de importe (más → deuda adicional; menos → condonación parcial) lo
resolverá el flujo de traslado completo (trabajo futuro; en T-212 solo se
re-apunta preservando el principal).

### 3.15 `recuperacion` (T-212)
```sql
recuperacion (
  id uuid PK,
  empresa_id uuid REFERENCES empresa(id) ON DELETE RESTRICT,
  local_id uuid REFERENCES local(id) ON DELETE RESTRICT,
  credito_id uuid REFERENCES credito_local(id) ON DELETE RESTRICT,
  origen text CHECK (origen IN ('efectivo','recaudacion')),
  importe numeric(10,2) CHECK (importe > 0),
  recaudacion_id uuid REFERENCES recaudacion(id),  -- NOT NULL si origen='recaudacion': de qué recaudación se retuvo
  fecha timestamptz DEFAULT now(),
  usuario_id uuid REFERENCES usuario(id)
);
```

Libro mayor (append-only) de abonos a una deuda. El saldo vivo se deriva en
`v_credito_local_saldo`. Da la trazabilidad de cuánto se quitó, de dónde y en qué
recaudaciones.

### 3.16 `averia` (T-220 · columnas de tolva en T-223)
```sql
averia (
  id uuid PK,
  empresa_id uuid REFERENCES empresa(id) ON DELETE RESTRICT,
  maquina_id uuid NOT NULL REFERENCES maquina(id) ON DELETE RESTRICT,   -- identidad estable: el historial sigue a la MÁQUINA
  instalacion_id uuid REFERENCES instalacion(id) ON DELETE SET NULL,    -- snapshot de dónde estaba al ocurrir (NULL si en almacén)
  local_id uuid REFERENCES local(id) ON DELETE SET NULL,                -- snapshot del local (se conserva aunque cambie/cierre la instalación)
  categoria text NOT NULL CHECK (categoria IN
    ('atasco_billete','atasco_moneda','error','falta_pago','no_enciende','otro')),
  descripcion text,
  estado text NOT NULL DEFAULT 'abierta' CHECK (estado IN ('abierta','en_reparacion','resuelta')),
  pone_maquina_fuera_servicio boolean NOT NULL DEFAULT false,  -- si true, la máquina pasa a 'averiada' mientras la avería siga abierta
  reportada_por uuid REFERENCES usuario(id),
  resuelta_por uuid REFERENCES usuario(id),
  fecha_reporte timestamptz NOT NULL DEFAULT now(),
  fecha_resolucion timestamptz,
  notas text,
  -- Fase 2 (T-223, migración aditiva): merma de tolva por premio pagado.
  afecta_tolva boolean NOT NULL DEFAULT false,
  importe_tolva numeric(10,2) NOT NULL DEFAULT 0 CHECK (importe_tolva >= 0),
  CONSTRAINT chk_averia_resuelta      CHECK (estado <> 'resuelta' OR fecha_resolucion IS NOT NULL),
  CONSTRAINT chk_averia_tolva_importe CHECK (afecta_tolva OR importe_tolva = 0),
  CONSTRAINT chk_averia_tolva_inst    CHECK (NOT afecta_tolva OR instalacion_id IS NOT NULL)  -- solo se recupera estando instalada
);

CREATE INDEX idx_averia_maquina ON averia(maquina_id, fecha_reporte DESC);  -- historial por máquina
```

**Historial por máquina (hoja de vida).** La avería cuelga de `maquina_id`, no de
`instalacion_id`: una máquina pasa por varios locales/instalaciones a lo largo de
su vida y su historial de averías debe atravesarlos todos. `instalacion_id` y
`local_id` se guardan como **snapshot** del momento (re-apuntar la máquina o
cerrar la instalación no reescribe averías pasadas). Consultar por `maquina_id`
(orden `fecha_reporte DESC`) da la trazabilidad completa de *qué falló* y *qué se
cambió* (vía §3.17).

**Estado de la máquina.** `maquina.estado` es single-valued; `'averiada'` es
**consecuencia derivada** de tener ≥1 avería abierta con
`pone_maquina_fuera_servicio = true`, no una edición suelta. La RPC que abre/resuelve
averías transiciona `maquina.estado`: al abrir → `'averiada'`; al resolver la última
así → vuelve a `'instalada'` si tiene instalación activa, si no a `'almacen'` (se
deriva, no se almacena el estado anterior). Un atasco leve (`pone_maquina_fuera_servicio = false`)
no saca la máquina de servicio.

### 3.17 `averia_recambio` (T-220)
```sql
averia_recambio (
  id uuid PK,
  empresa_id uuid REFERENCES empresa(id) ON DELETE RESTRICT,
  averia_id uuid NOT NULL REFERENCES averia(id) ON DELETE CASCADE,  -- subordinado a la avería
  pieza text NOT NULL,
  cantidad integer NOT NULL DEFAULT 1 CHECK (cantidad > 0),
  coste numeric(10,2) CHECK (coste >= 0),  -- INFORMATIVO (gasto de la empresa); NO se recupera de la recaudación
  notas text
);
```

Piezas cambiadas al reparar una avería (1 avería → N recambios). El `coste` es
informativo para control de mantenimiento; **no** entra en el reparto ni se
recupera (solo el premio de tolva, §5.6).

### 3.18 `tolva_movimiento` + tolva efectiva (T-223)
```sql
tolva_movimiento (
  id uuid PK,
  empresa_id uuid REFERENCES empresa(id) ON DELETE RESTRICT,
  instalacion_id uuid NOT NULL REFERENCES instalacion(id) ON DELETE RESTRICT,
  tipo text NOT NULL CHECK (tipo IN ('merma','reposicion')),
  importe numeric(10,2) NOT NULL CHECK (importe > 0),
  averia_id uuid REFERENCES averia(id),            -- merma: qué avería la causó
  recaudacion_id uuid REFERENCES recaudacion(id),  -- reposición: en qué recaudación se repuso
  fecha timestamptz NOT NULL DEFAULT now(),
  usuario_id uuid REFERENCES usuario(id),
  notas text
);
```

`instalacion.tolva` (§3.7) pasa a interpretarse como **tolva teórica** (nivel
objetivo cargado al instalar). La **tolva efectiva se DERIVA** de un ledger
append-only (mismo patrón que `v_credito_local_saldo`), nunca se almacena mutable:

```sql
-- v_instalacion_tolva
teorica   = instalacion.tolva
merma      = Σ importe WHERE tipo = 'merma'
repuesto   = Σ importe WHERE tipo = 'reposicion'
efectiva   = teorica - merma + repuesto
pendiente  = teorica - efectiva   -- = merma - repuesto; lo que falta por reponer
```

Una avería con `afecta_tolva = true` inserta una `merma`; la recaudación que repone
inserta una `reposicion` (§5.6). `pendiente` es lo que se recuperará en la siguiente
recaudación. La merma por avería **no toca** la deuda de tolva de §3.14 (esa es la
parte del *cebado* que debe el local; esto es un premio compartido).

## 4. Row Level Security (RLS)

- Todas las tablas con `empresa_id` activan RLS.
- Política base por tabla:
  ```sql
  USING (empresa_id IN (
    SELECT empresa_id FROM empresa_usuario
    WHERE usuario_id = auth.uid() AND activo = true
  ))
  ```
- Políticas adicionales por rol cuando aplique (p. ej. `tecnico` no puede borrar nada, solo leer y crear recaudaciones; `gestor` no puede gestionar usuarios).
- `usuario` tiene política `id = auth.uid()` para ver su propio perfil; los demás perfiles solo se exponen via vistas a través de `empresa_usuario`.

## 5. Algoritmos clave

### 5.1 Cálculo de baseline

Función SQL `obtener_baseline(p_instalacion_id uuid, p_fecha timestamptz)` devuelve:
- `entradas bigint`, `salidas bigint`, `fecha_referencia timestamptz`, `origen text`, `referencia_id uuid`.

Lógica:
1. Buscar la **última recaudación firme** de la instalación con `fecha < p_fecha`.
2. Buscar el **último cambio de placa** con `fecha < p_fecha`.
3. El que tenga `fecha` mayor gana:
   - Si es recaudación firme → `entradas = contador_entradas_actual`, `salidas = contador_salidas_actual`, origen `recaudacion_anterior`.
   - Si es cambio de placa → `entradas = contador_entradas_nuevo`, `salidas = contador_salidas_nuevo`, origen `cambio_placa`.
4. Si no hay ninguno → `entradas = contador_entradas_base`, `salidas = contador_salidas_base`, fecha `fecha_inicio`, origen `instalacion_base`.

### 5.2 Cálculo de semanas (ISO)

Función `semanas_iso_entre(p_desde timestamptz, p_hasta timestamptz, p_tz text)`:

```python
# Pseudocódigo
desde_local = p_desde at time zone p_tz
hasta_local = p_hasta at time zone p_tz
(year_d, week_d) = iso_year_week(desde_local)
(year_h, week_h) = iso_year_week(hasta_local)
semanas = total_iso_weeks_between((year_d, week_d), (year_h, week_h))
# Excluye la semana de referencia, incluye la actual
return max(semanas, 0)
```

Donde `total_iso_weeks_between` cuenta la diferencia en semanas ISO contemplando cambios de año.

Ejemplos verificables:
- (2026-W20-Vie, 2026-W21-Lun) → 1
- (2026-W22-Mié, 2026-W22-Vie) → 0
- (2026-W22-Vie, 2026-W25-Lun) → 3
- (2026-W20-Vie, 2026-W22-Mié) → 2 (primera recaudación con instalación en W20)

### 5.3 Cálculo de recaudación

Función `calcular_recaudacion(p_instalacion_id, p_entradas_actual, p_salidas_actual, p_fecha)`:

```
baseline = obtener_baseline(p_instalacion_id, p_fecha)
inst = SELECT * FROM instalacion WHERE id = p_instalacion_id

bruto = ((p_entradas_actual - baseline.entradas) - (p_salidas_actual - baseline.salidas)) * maquina.valor_credito

semanas = semanas_iso_entre(baseline.fecha_referencia, p_fecha, empresa.zona_horaria)
tasa_total = semanas * inst.tasa_semanal

if bruto < tasa_total:
    return { procede: false, bruto, tasa_total, semanas, ... }

neto = bruto - tasa_total
parte_local = round(neto * inst.porcentaje_local / 100, 2)  # redondeo bancario al céntimo
parte_empresa = neto - parte_local  # absorbe el redondeo

return {
  procede: true,
  baseline_origen, baseline_id, baseline_entradas, baseline_salidas,
  bruto, semanas, tasa_semanal, tasa_total,
  neto, porcentaje_local, parte_local, parte_empresa,
  valor_credito
}
```

El cliente y el server llaman a esta misma función. La verdad es la del server.

### 5.4 Validación de denominaciones

La Edge Function `crear-recaudacion` valida:
- Cada `denominacion` está en `{0.10, 0.20, 0.50, 1, 2, 5, 10, 20, 50}`.
- Cada `cantidad` es entero ≥ 0.
- `sum(denominacion * cantidad) for desglose_total == bruto` (con tolerancia 0).
- `sum(denominacion * cantidad) for desglose_local == parte_local`.

### 5.5 Recuperación de deuda (T-212 modelo · T-214 automática)

El local debe a la empresa una **tolva** (`porcentaje_local × tolva` física) y/o
**préstamos**. Se recuperan reteniendo un % de su `parte_local` en cada
recaudación, o en efectivo. Config del %:
`COALESCE(local.porcentaje_recuperacion, empresa.porcentaje_recuperacion)`;
0 = sin recuperación automática.

Por cada recaudación con `parte_local > 0` y deuda pendiente:

```
pct        = COALESCE(local.porcentaje_recuperacion, empresa.porcentaje_recuperacion)
objetivo   = round(parte_local * pct / 100, 2)
recuperado = min(objetivo, saldo_total_deuda)   # nunca más que lo que se debe
# imputación por orden: tolva primero, luego FIFO (préstamos por antigüedad);
# el técnico puede reordenar manualmente.
pagado_local = parte_local - recuperado          # lo que se lleva el local
```

El dinero retenido **NO es ingreso de la empresa**: es amortización de deuda. Por
eso `parte_empresa` NO cambia, el cálculo SSOT (`calculo.ts`, §5.3) NO se toca, y
la retención se registra aparte en `recuperacion`. En T-214 la Edge Function de
recaudación persistirá recaudación + recuperaciones atómicamente,
`desglose_local` pasará a cuadrar con `pagado_local` (en vez de `parte_local`,
§5.4) y anular una recaudación revertirá sus recuperaciones.

**Ejemplo** (bruto 260, tasa 60, % local 50, deuda 100, % recuperación 100):
neto = 200 → parte_local 100, parte_empresa 100. recuperado = min(100, 100) = 100
→ el local se lleva 0; la empresa recibe 60 (tasa) + 100 (su parte) y además
amortiza 100 de deuda. Saldo de deuda → 0.

### 5.6 Recuperación de avería de tolva (T-224 · **modifica el SSOT**)

Cuando una avería paga un premio de la tolva **sin que el contador de salidas lo
registre**, el reparto queda inflado y la empresa se come el agujero en silencio.
La recuperación lo corrige haciendo que ese premio *no contado* se comporte como
uno contado: se repone **del neto, tras la tasa y ANTES del reparto**, de modo que
local y empresa lo asumen según su %. A diferencia de §5.5, esto **sí cambia
`parte_empresa` y `parte_local`**, por lo que vive DENTRO de `calculo.ts` (y su
espejo `Calculo.kt`), no aparte.

Por cada recaudación con `pendiente_tolva > 0` (de `v_instalacion_tolva`, §3.18):

```
neto         = bruto - tasa_total                       # §5.3, sin cambios hasta aquí
pendiente    = max(0, tolva_teorica - tolva_efectiva)   # lo que falta por reponer
reposicion   = min(neto, pendiente)                     # arrastrable: el resto va a la siguiente
base_reparto = neto - reposicion
parte_local  = round(base_reparto * pct_local / 100, 2)
parte_empresa = base_reparto - parte_local              # absorbe el redondeo de céntimos
# la reposición se devuelve FÍSICAMENTE a la tolva: tolva_movimiento(tipo='reposicion')
# → tolva_efectiva sube; pendiente baja.
```

**Orden cuando coinciden las dos recuperaciones** en una misma recaudación: primero
la de avería (§5.6, pre-reparto, baja `base_reparto`), luego sobre la `parte_local`
resultante la de deuda (§5.5, post-reparto). Invariante:
`parte_local + parte_empresa = neto - reposicion`. La migración rectifica cualquier
constraint que ate el reparto a `neto` (como T-214 rectificó `chk_desglose_local_suma`).

**Ejemplo** (bruto 100, tasa 0, % local 50, pendiente_tolva 50):
neto = 100 → reposición = min(100, 50) = 50 → base_reparto = 50 → parte_local 25,
parte_empresa 25. La tolva efectiva vuelve a la teórica. El premio de 50 € queda
repartido 25/25, igual que si el contador lo hubiera registrado.

**Edge case (a resolver en T-223):** si la máquina se da de **baja/se retira** con
`pendiente_tolva > 0` y no hay recaudación futura de la que reponer, la merma
pendiente se salda en efectivo o se condona (RPC de admin), análogo a `credito_local`.

## 6. Edge Functions

| Función | Método | Descripción |
|---|---|---|
| `calcular-recaudacion` | POST | Recibe contadores propuestos, devuelve cifras (no persiste). |
| `crear-recaudacion` | POST | Persiste recaudación; recalcula server-side; detecta conflicto; sube firma/fotos; genera PDF; devuelve URL del PDF y datos para imprimir. |
| `crear-cambio-placa` | POST | Registra cambio de placa con sus contadores nuevos. |
| `cerrar-instalacion` | POST | Marca instalación como cerrada; valida que no tenga lock activo. |
| `resolver-conflicto` | POST | Aplica resolución (aceptar / sustituir / anular). |
| `anular-recaudacion` | POST | Anula con motivo; recalcula PDFs si hace falta. |
| `adquirir-lock` / `liberar-lock` | POST | Lock optimista por instalación. |
| `invitar-usuario` | POST | Crea invitación a la empresa. |
| `reimprimir-ticket` | POST | Devuelve PDF y/o payload ESC/POS para reimpresión. |

Las funciones validan permisos releyendo `empresa_usuario` para el `auth.uid()` y `empresa_id` enviado.

## 7. Storage

Buckets privados con RLS:

- `firmas/{empresa_id}/{recaudacion_id}.png`
- `fotos-contadores/{empresa_id}/{recaudacion_id}/{tipo}.jpg`
- `tickets/{empresa_id}/{recaudacion_id}.pdf`
- `logos/{empresa_id}/logo.png`
- `cambios-placa/{empresa_id}/{cambio_id}.jpg`

Acceso siempre via signed URL con expiración corta (p. ej. 10 minutos) generada server-side.

## 8. Estrategia offline (resumen)

Principio: **una recaudación enviada por el técnico siempre se persiste**.

1. Sync forzado al abrir la app (si hay red) y al entrar al detalle de un local (si hay red).
2. Si el dispositivo lleva > 48 h sin sincronizar, se exige conexión antes de iniciar nuevas recaudaciones.
3. Lock optimista en `recaudacion_lock` cuando hay red al abrir la pantalla de recaudación. Aviso (no bloqueo duro) si otro técnico tiene lock.
4. La app envía siempre `idempotency_key`, `baseline_origen`, `baseline_id`, `baseline_entradas`, `baseline_salidas`.
5. El server recalcula la baseline en el momento de recibir:
   - Si coincide → recaudación firme, sin conflicto.
   - Si no coincide → recaudación firme con `conflicto = true`, valores recalculados guardados en columnas paralelas, alerta para admin.
6. La pantalla web **"Recaudaciones en conflicto"** permite al admin: aceptar tal cual, sustituir importes por recalculados o anular.
7. Notificación al técnico de la resolución (push si está logueado, email como fallback).

## 9. Pantallas Web

Sidebar con: Dashboard, Licencias, Máquinas, Locales, Instalaciones, Recaudaciones, Cambios de placa, Conflictos, Informes, Equipo, Ajustes.

### 9.1 Login
Email + password. Si pertenece a >1 empresa → selector tras login.

### 9.2 Dashboard
- Recaudaciones del mes (€) y comparativa con mes anterior.
- Nº de máquinas activas / total.
- Licencias caducando en 90 días.
- Locales sin recaudar > 3 semanas.
- Conflictos pendientes.
- Gráfica de recaudación por mes (12 meses).

### 9.3 Licencias
Tabla con buscador, filtros por estado y CCAA. Crear/editar/dar de baja. Detalle con histórico de instalaciones.

### 9.4 Máquinas
Tabla con filtros. Detalle: instalaciones históricas, recaudaciones acumuladas, eventos de cambio de placa, gráfica de recaudación.

### 9.5 Locales
Tabla con buscador. Detalle: máquinas instaladas (varias posibles), histórico de recaudaciones, datos del titular.

### 9.6 Instalaciones
Tabla con filtros. Crear con: local, máquina libre, licencia libre, tasa_semanal, porcentaje_local, fecha_inicio, contadores base. Cerrar: pide fecha de fin.

### 9.7 Recaudaciones
Listado con filtros (fecha, local, máquina, técnico, estado, conflicto). Detalle: todos los datos, desgloses, foto, firma, PDF. Acciones: descargar PDF, reimprimir, anular (si rol).

### 9.8 Cambios de placa
Listado por máquina/instalación. Detalle solo lectura.

### 9.9 Conflictos
Lista de recaudaciones con `conflicto = true AND revisado_en IS NULL`. Detalle con comparación valores originales / recalculados. Acciones: aceptar / sustituir / anular.

### 9.10 Informes
- Por local / mes
- Por máquina / periodo
- Por técnico / periodo
- Resumen fiscal trimestral (tasas pagadas)
- Exportación CSV

### 9.11 Equipo
Lista de usuarios de la empresa con rol y estado. Invitar, cambiar rol, desactivar.

### 9.12 Ajustes
Datos fiscales, logo, configuración del ticket (cabecera/pie), zona horaria, umbral de antigüedad de sync.

## 10. Pantallas App Android

### 10.1 Login
Email + password.

### 10.2 Selector de empresa
Si pertenece a >1.

### 10.3 Lista de locales
Buscador + lista. Cada item: nombre, dirección, nº de máquinas, días desde última recaudación. Pull-to-refresh.

### 10.4 Detalle de local
Lista de máquinas con estado visual:
- 🟦 Pendiente, 🟩 Recaudada hoy, 🟧 Insuficiente hoy, 🟥 Cambio de placa pendiente.

Botón **"Recaudar todas"** y por máquina **"Recaudar"** o **"Cambio de placa"**.

### 10.5 Recaudación — paso 1: Contadores
- Datos de instalación visibles (tasa_semanal, %, valor_credito).
- Contadores anteriores read-only.
- Inputs grandes para entradas/salidas actuales.
- Botones de cámara (fase 1 opcional, fase 2 con OCR).
- Botón **Calcular** → llama a `calcular-recaudacion`.
- Si bruto < tasa: muestra "Recaudación insuficiente". Botón **"Cerrar sin recaudar"** que:
  - En "Recaudar todas" → pasa a la siguiente máquina.
  - En recaudación individual → vuelve al detalle del local.
  - Registra en `lectura_no_recaudada`.

### 10.6 Recaudación — paso 2: Denominaciones del total
Encabezado fijo: **Total = X €**, **Suma actual = Y €**.
Grid 3x3 de denominaciones: 0.10, 0.20, 0.50, 1, 2, 5, 10, 20, 50.
Cada caja: etiqueta, − / +, input numérico, subtotal.
Botón Continuar habilitado solo si suma == total exacto.

### 10.7 Recaudación — paso 3: Denominaciones de la parte local
Igual con objetivo `parte_local`.

### 10.8 Recaudación — paso 4: Firma y confirmación
- Resumen completo.
- Canvas para firma del titular.
- Campo observaciones.
- Botón **Guardar e imprimir**.

### 10.9 Pantalla de éxito
- Resumen breve.
- Botón **Reimprimir ticket**.
- Si hay máquinas pendientes en el local: botón **Siguiente máquina (X/N)**.
- Si no: botón **Volver al local**.

### 10.10 Cambio de placa
Formulario: fecha, contadores nuevos (default 0), motivo, nº de serie placa anterior y nueva (opcional), foto (opcional), notas. Botón Guardar.

### 10.11 Mis recaudaciones
Histórico personal del técnico. Reimprimir ticket si coincidencia con la impresora actual.

### 10.12 Gestión (rol >= gestor) — CRUD completo en fase 1
Sección "Gestión" en el menú principal, visible solo si rol >= `gestor`.

Subsecciones:
- **Licencias**: listado, alta, edición, baja. Mismos campos y validaciones que la web (HU-2).
- **Máquinas**: listado, alta (incluyendo `valor_credito` y contadores iniciales), edición, baja.
- **Locales**: listado, alta, edición, baja.
- **Instalaciones**: listado, alta (con selector de máquina libre, licencia libre, local, `tasa_semanal`, `porcentaje_local`, contadores base, fecha_inicio, notas), cierre (con fecha_fin).

Reglas de operación:
- **Las acciones de gestión exigen conexión a internet**. Si no hay red, se muestra un aviso claro y se bloquea el botón de guardado. Esto evita inconsistencias en el inventario que sí podrían generarse offline (a diferencia de las recaudaciones, donde la verdad es física y siempre se persiste).
- Las validaciones se aplican tanto en cliente como en servidor (Edge Function o RLS según corresponda).
- Tras crear/editar, se refresca la cache local (Room) de los datos afectados.

### 10.13 Ajustes
Cambiar empresa activa, vincular impresora Bluetooth, forzar sincronización, cerrar sesión.

## 11. Layout del ticket (AGPTEK PT210, 58 mm, 32 col)

```
[LOGO opcional]
EMPRESA S.L.
CIF: B12345678
--------------------------------
RECAUDACIÓN
Fecha: 19/05/2026 11:42
Local: Bar Ejemplo
Direcc.: C/ Mayor 12
Máquina: AB-12345 (Mod X)
Licencia: 0001234
Técnico: Juan Pérez
--------------------------------
Cont. Entradas:  12500 -> 13200
Cont. Salidas:    9800 -> 10100
Créditos netos:        400
Valor crédito:        0,20 €
--------------------------------
Bruto:              80,00 €
Semanas tasa:            2
Tasa semanal:       10,00 €
Tasa total:         20,00 €
Neto:               60,00 €
% Local:               50%
Parte Local:        30,00 €
Parte Empresa:      30,00 €
--------------------------------
Desglose Total:
  20€ x 2 =      40,00
  10€ x 4 =      40,00
Desglose Local:
  10€ x 3 =      30,00
--------------------------------
Firma titular:

[firma]

Gracias.
```

PDF en A4 con misma información más detalles de auditoría (idempotency_key, dispositivo).

## 12. Seguridad

- JWT de Supabase Auth en cada request.
- RLS por empresa.
- Edge Functions revalidan rol del usuario (`empresa_usuario.rol`) antes de acciones sensibles.
- Storage privado con signed URLs.
- Almacenamiento local Android cifrado (Room + EncryptedSharedPreferences para claves).

### 12.1 Invariante de escritura: solo vía función

Los clientes (`authenticated`/`anon`) **solo pueden LEER**. Toda escritura en la
BBDD pasa por una función; la BBDD rechaza el INSERT/UPDATE/DELETE directo. Esto
se impone con dos capas a la vez:

1. **REVOKE** de `INSERT, UPDATE, DELETE` a `authenticated`/`anon` sobre **todas**
   las tablas de dominio (no basta con RLS: Supabase concede esos privilegios por
   defecto y RLS solo los acota). Las policies `*_select` siguen permitiendo lectura.
2. **Capa de escritura** que sí puede escribir porque puentea esos privilegios:
   - **RPC `SECURITY DEFINER`** (dueño `postgres`) para el CRUD de los clientes.
     Validan rol + tenant internamente con los helpers `usuario_es_*`. Ej.:
     `crear/actualizar/eliminar_{licencia,maquina,local,instalacion}`,
     `actualizar_ajustes_empresa`, `cambiar_rol_miembro`, `cambiar_estado_miembro`,
     `marcar_alerta_leida`, `marcar_alertas_leidas_empresa`, `crear_prestamo`,
     `registrar_recuperacion_efectivo`, `condonar_credito` (admin),
     `set_porcentaje_recuperacion_local`. Se conceden
     (`GRANT EXECUTE`) solo a `authenticated`.
   - **Edge Functions con `service_role`** para flujos operativos y efectos
     externos (cálculo SSOT, locks, PDFs, emails): `crear-recaudacion`,
     `crear-cambio-placa`, `adquirir-lock`/`liberar-lock`, `anular-recaudacion`,
     `resolver-conflicto`, `cerrar-instalacion`, `invitar-usuario`,
     `registrar-device-token`, `registrar-empresa`, `resumen-mensual`. Validan
     rol + tenant en TS antes de escribir.

Toda tabla nueva nace con RLS, sin grant de escritura a clientes, y con su RPC o
Edge Function de escritura. El guardarraíl `tests/sql/07_lockdown_escritura_global.sql`
falla si alguna tabla de dominio concede escritura directa a `authenticated`/`anon`.

## 13. Decisiones cerradas

| Tema | Decisión |
|---|---|
| Multi-empresa | Sí, con selector |
| Histórico de instalaciones | Conservado |
| Tasa | Por instalación, importe semanal manual |
| Cálculo de semanas | ISO calendario, exclusiva referencia / inclusiva actual |
| Cálculo de reparto | (bruto − tasa) × % local; empresa absorbe redondeo |
| Bruto < tasa | No se recauda; se registra `lectura_no_recaudada` |
| Cambio de placa | Tabla independiente, flujo aparte |
| Permitir descuadres | No, suma exacta obligatoria |
| Borrador a medias | No, se descarta al salir |
| Firma | Por máquina (una por recaudación) |
| Foto contadores | Opcional fase 1; OCR fase 2 |
| Impresora | AGPTEK PT210 Bluetooth, ESC/POS |
| Guardar PDF | Sí, en Storage |
| Stack web | Next.js + Tailwind + shadcn/ui |
| Stack móvil | Kotlin + Jetpack Compose |
| Roles | owner, admin, gestor, tecnico, contable |
| Integración contable | No |
| Boletines de instalación | No |
| GDPR formal | No (fase 1) |
| Idioma | Español |
| Anulación de recaudaciones | Solo admin/owner, motivo obligatorio |
| Antigüedad máxima sin sync | 48 h |
| Notificación de resolución de conflicto | Email en fase 1 (push FCM en fase 2) |
| Orden en "Recaudar todas" | Fijo |
| Borrar lectura insuficiente | Solo log mínimo |
| CRUD desde la app | Completo en fase 1 (rol >= gestor), exige conexión |
| Redondeo | Half-up al céntimo; empresa absorbe la diferencia |
| Tolva | Dinero físico en la máquina (informativo); deuda del local = `porcentaje_local × tolva` |
| Préstamos al local | Sin límite; principal sin interés en v1 (`tipo_interes` guardado, no devenga) |
| Recuperación de deuda | Auto reteniendo % de `parte_local` (config empresa + override local) o en efectivo; tolva primero, luego FIFO; retención NO es ingreso (no toca el SSOT) |
| Traslado de tolva | La deuda pertenece al local; cambiar de máquina la re-apunta, no la duplica |
