-- =============================================================================
-- T-18 — Tests de `validar_desglose_denominaciones` y `sumar_desglose`.
--
-- Cubre forma del jsonb, denominaciones permitidas, cantidades, agregación.
-- =============================================================================

BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap;
SELECT plan(11);

-- ---------------------------------------------------------------- validador
SELECT ok(
    public.validar_desglose_denominaciones(
        '[{"denominacion":50,"cantidad":2},{"denominacion":10,"cantidad":4}]'::jsonb
    ),
    'Array bien formado y denominaciones válidas'
);

SELECT ok(
    public.validar_desglose_denominaciones('[]'::jsonb),
    'Array vacío es válido'
);

SELECT ok(
    NOT public.validar_desglose_denominaciones('"hola"'::jsonb),
    'String no es válido'
);

SELECT ok(
    NOT public.validar_desglose_denominaciones('{"denominacion":50,"cantidad":1}'::jsonb),
    'Objeto suelto (no array) no es válido'
);

SELECT ok(
    NOT public.validar_desglose_denominaciones(
        '[{"denominacion":3,"cantidad":1}]'::jsonb
    ),
    'Denominación no permitida (3) rechazada'
);

SELECT ok(
    NOT public.validar_desglose_denominaciones(
        '[{"denominacion":50,"cantidad":-1}]'::jsonb
    ),
    'Cantidad negativa rechazada'
);

SELECT ok(
    NOT public.validar_desglose_denominaciones(
        '[{"denominacion":50}]'::jsonb
    ),
    'Falta el campo cantidad: rechazado'
);

SELECT ok(
    NOT public.validar_desglose_denominaciones(
        '[{"cantidad":1}]'::jsonb
    ),
    'Falta el campo denominacion: rechazado'
);

SELECT ok(
    public.validar_desglose_denominaciones(
        '[{"denominacion":0.10,"cantidad":50},{"denominacion":0.20,"cantidad":25}]'::jsonb
    ),
    'Acepta denominaciones decimales 0.10 y 0.20'
);

-- ---------------------------------------------------------------- sumador
SELECT is(
    public.sumar_desglose(
        '[{"denominacion":50,"cantidad":2},{"denominacion":10,"cantidad":3}]'::jsonb
    ),
    130.00::numeric(10, 2),
    'Suma 50x2 + 10x3 = 130.00'
);

SELECT is(
    public.sumar_desglose('[]'::jsonb),
    0.00::numeric(10, 2),
    'Suma de array vacío = 0.00'
);

SELECT * FROM finish();
ROLLBACK;
