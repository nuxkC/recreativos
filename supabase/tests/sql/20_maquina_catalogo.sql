-- =============================================================================
-- T-269 — La máquina referencia el catálogo global: crear_maquina resuelve el
-- texto a fabricante_id/modelo_id (busca-o-crea), denormaliza el nombre canónico
-- y mantiene la cascada coherente. El helper interno queda bloqueado a clientes.
-- =============================================================================

BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap;
SELECT plan(8);

-- fixtures: una empresa con un gestor
INSERT INTO auth.users (id) VALUES ('e1000000-0000-0000-0000-0000000000a1');
INSERT INTO public.usuario (id, nombre_completo) VALUES ('e1000000-0000-0000-0000-0000000000a1', 'Gestor');
INSERT INTO public.empresa (id, nombre, zona_horaria, trial_inicio, trial_fin)
    VALUES ('e1000000-0000-0000-0000-000000000001', 'Test Maquina Catalogo', 'UTC', now(), now() + interval '30 days');
INSERT INTO public.empresa_usuario (empresa_id, usuario_id, rol, activo) VALUES
    ('e1000000-0000-0000-0000-000000000001', 'e1000000-0000-0000-0000-0000000000a1', 'owner', true);

-- estructura + lockdown del helper interno
SELECT has_column('public', 'maquina', 'fabricante_id', 'maquina tiene fabricante_id');
SELECT has_column('public', 'maquina', 'modelo_id', 'maquina tiene modelo_id');
SELECT ok(
    NOT has_function_privilege('authenticated', 'public._resolver_catalogo(text, text)', 'EXECUTE'),
    'authenticated NO puede ejecutar _resolver_catalogo'
);
SELECT ok(
    NOT has_function_privilege('anon', 'public._resolver_catalogo(text, text)', 'EXECUTE'),
    'anon NO puede ejecutar _resolver_catalogo'
);

-- crear dos máquinas como gestor (mismo fabricante, distinta grafía; distinto modelo)
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claims = '{"sub":"e1000000-0000-0000-0000-0000000000a1","role":"authenticated"}';
SELECT public.crear_maquina('e1000000-0000-0000-0000-000000000001', 'S1', 'Diamond', 'Cirsa', 0.20, 0, 0, 'almacen', NULL);
SELECT public.crear_maquina('e1000000-0000-0000-0000-000000000001', 'S2', 'Twister', 'CIRSA', 0.20, 0, 0, 'almacen', NULL);
RESET ROLE;

-- comportamiento
SELECT ok(
    (SELECT fabricante_id FROM public.maquina WHERE numero_serie = 'S1') IS NOT NULL,
    'crear_maquina pobla fabricante_id'
);
SELECT is(
    (SELECT fabricante FROM public.maquina WHERE numero_serie = 'S1'),
    'Cirsa',
    'denormaliza el nombre canónico del fabricante'
);
SELECT is(
    (SELECT fabricante_id FROM public.maquina WHERE numero_serie = 'S1'),
    (SELECT fabricante_id FROM public.maquina WHERE numero_serie = 'S2'),
    'Cirsa/CIRSA normalizado -> mismo fabricante_id'
);
SELECT is(
    (SELECT mo.fabricante_id FROM public.modelo mo
       JOIN public.maquina m ON m.modelo_id = mo.id
      WHERE m.numero_serie = 'S1'),
    (SELECT fabricante_id FROM public.maquina WHERE numero_serie = 'S1'),
    'el modelo cuelga del fabricante correcto (cascada coherente)'
);

SELECT * FROM finish();
ROLLBACK;
