# Tests SQL — pgTAP

Tests automatizados de las funciones SQL críticas del dominio Recre.

## Cobertura

| Archivo | Cubre | Tarea |
|---|---|---|
| `01_semanas_iso_entre.sql` | Cálculo de semanas ISO con casos del `design.md §5.2` y bordes (cambio de año, mismo día, fecha invertida) | T-18 |
| `02_obtener_baseline.sql` | Selección de baseline (instalación / recaudación / cambio de placa, empates, anuladas ignoradas) | T-18 |
| `03_denomination_validators.sql` | `validar_desglose_denominaciones` y `sumar_desglose` con denominaciones permitidas, formas inválidas, suma cero | T-18 |

## Ejecutar

### Con Supabase CLI (recomendado)

Aplica las migraciones a la DB local y corre los tests:

```bash
supabase test db
```

Internamente la CLI usa `pg_prove`. Debes tener la extensión `pgtap` disponible (Supabase la incluye en la imagen de Postgres).

### Manualmente con `pg_prove`

```bash
psql -c "CREATE EXTENSION IF NOT EXISTS pgtap;"
pg_prove --ext .sql supabase/tests/sql/
```

### Con `psql` puro

Cada archivo es ejecutable con `psql -f` y devuelve la salida estándar de pgTAP (TAP). Útil cuando solo se quiere correr un test concreto:

```bash
psql -h 127.0.0.1 -p 54322 -U postgres -d postgres -f supabase/tests/sql/01_semanas_iso_entre.sql
```

## Convenciones

- Los tests envuelven el cuerpo en `BEGIN ... ROLLBACK` para no dejar restos en la DB.
- Insertamos los datos mínimos necesarios; **no** dependemos del `seed.sql` para evitar acoplamientos frágiles.
- Cada test declara su `plan(N)` para que el desfase con la realidad falle ruidosamente.
