-- Recre — datos semilla para desarrollo local
--
-- Se ejecuta tras aplicar las migraciones al correr `supabase db reset`.
-- Simula una empresa YA EN MARCHA para no tener que recrear todo a mano.
--
-- Corre como `postgres` (superusuario): puentea RLS y los REVOKE de escritura,
-- así que puede insertar directamente en cualquier tabla. Idempotente vía
-- ON CONFLICT (usa UUIDs fijos), así que se puede re-ejecutar sin db reset.
--
-- Credenciales (todas con contraseña `123456`, email confirmado):
--   a@a.es      -> owner    (Aitor Cruzado)
--   lucia@a.es  -> admin    (Lucía Fernández)
--   marcos@a.es -> gestor   (Marcos Ibáñez)
--   pablo@a.es  -> tecnico  (Pablo Ortega)
--   sara@a.es   -> contable (Sara Molina)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Usuarios de auth (Supabase GoTrue) + identidades de email.
-- -----------------------------------------------------------------------------
do $$
declare
    r record;
begin
    for r in
        select * from (values
            ('a0000000-0000-0000-0000-000000000001'::uuid, 'a@a.es'),
            ('a0000000-0000-0000-0000-000000000002'::uuid, 'lucia@a.es'),
            ('a0000000-0000-0000-0000-000000000003'::uuid, 'marcos@a.es'),
            ('a0000000-0000-0000-0000-000000000004'::uuid, 'pablo@a.es'),
            ('a0000000-0000-0000-0000-000000000005'::uuid, 'sara@a.es')
        ) as t(id, email)
    loop
        insert into auth.users (
            instance_id, id, aud, role, email, encrypted_password,
            email_confirmed_at, created_at, updated_at,
            raw_app_meta_data, raw_user_meta_data,
            confirmation_token, recovery_token, email_change_token_new, email_change
        ) values (
            '00000000-0000-0000-0000-000000000000', r.id, 'authenticated', 'authenticated',
            r.email, crypt('123456', gen_salt('bf')),
            now(), now(), now(),
            '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb,
            '', '', '', ''
        )
        on conflict (id) do nothing;

        insert into auth.identities (
            id, provider_id, user_id, identity_data, provider,
            last_sign_in_at, created_at, updated_at
        ) values (
            gen_random_uuid(), r.id::text, r.id,
            jsonb_build_object(
                'sub', r.id::text, 'email', r.email,
                'email_verified', true, 'phone_verified', false
            ),
            'email', now(), now(), now()
        )
        on conflict do nothing;
    end loop;
end $$;

-- -----------------------------------------------------------------------------
-- 2. Empresa (suscripción activa, no trial).
-- -----------------------------------------------------------------------------
insert into public.empresa (
    id, nombre, cif, direccion, telefono, email, zona_horaria,
    ticket_cabecera, ticket_pie, estado_suscripcion
) values (
    'e0000000-0000-0000-0000-000000000001',
    'Recreativos Levante S.L.', 'B96541237',
    'Av. del Puerto, 142, 46023 Valencia', '963112233', 'info@recrelevante.es',
    'Europe/Madrid',
    'RECREATIVOS LEVANTE S.L. — B96541237',
    'Gracias por su confianza. Conserve este ticket.',
    'activa'
)
on conflict (id) do nothing;

-- -----------------------------------------------------------------------------
-- 3. Perfiles públicos (public.usuario) + membresías con rol.
-- -----------------------------------------------------------------------------
insert into public.usuario (id, nombre_completo, telefono) values
    ('a0000000-0000-0000-0000-000000000001', 'Aitor Cruzado',   '600111222'),
    ('a0000000-0000-0000-0000-000000000002', 'Lucía Fernández', '600222333'),
    ('a0000000-0000-0000-0000-000000000003', 'Marcos Ibáñez',   '600333444'),
    ('a0000000-0000-0000-0000-000000000004', 'Pablo Ortega',    '600444555'),
    ('a0000000-0000-0000-0000-000000000005', 'Sara Molina',     '600555666')
on conflict (id) do nothing;

insert into public.empresa_usuario (empresa_id, usuario_id, rol, activo) values
    ('e0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000001', 'owner',    true),
    ('e0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000002', 'admin',    true),
    ('e0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000003', 'gestor',   true),
    ('e0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000004', 'tecnico',  true),
    ('e0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000005', 'contable', true)
on conflict (empresa_id, usuario_id) do nothing;

-- -----------------------------------------------------------------------------
-- 4. Locales (establecimientos).
-- -----------------------------------------------------------------------------
insert into public.local (id, empresa_id, nombre, direccion, cif_o_nif, titular_nombre, telefono, email) values
    ('b0000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000001', 'Bar El Rincón',     'C/ Sagunto, 12, Valencia',    '12345678Z', 'José Pérez',              '961001001', 'elrincon@example.com'),
    ('b0000000-0000-0000-0000-000000000002', 'e0000000-0000-0000-0000-000000000001', 'Cafetería Central', 'Plaza Mayor, 3, Valencia',    '87654321X', 'María López',             '961002002', 'central@example.com'),
    ('b0000000-0000-0000-0000-000000000003', 'e0000000-0000-0000-0000-000000000001', 'Pub La Esquina',    'Av. Lauri Volpi, 8, Torrent', 'B98712345', 'Hostelería Torrent S.L.', '961003003', 'laesquina@example.com'),
    ('b0000000-0000-0000-0000-000000000004', 'e0000000-0000-0000-0000-000000000001', 'Salón Oasis',       'C/ Mayor, 45, Paterna',       'B45612378', 'Oasis Ocio S.L.',         '961004004', 'oasis@example.com'),
    ('b0000000-0000-0000-0000-000000000005', 'e0000000-0000-0000-0000-000000000001', 'Bar Gol',           'C/ Russafa, 21, Valencia',    '11223344C', 'Antonio Ruiz',            '961005005', 'bargol@example.com')
on conflict (id) do nothing;

-- -----------------------------------------------------------------------------
-- 5. Máquinas (valor de crédito 0,20 €). Contadores iniciales = lectura física
--    al darlas de alta; son la base de la primera instalación.
-- -----------------------------------------------------------------------------
insert into public.maquina (
    id, empresa_id, numero_serie, modelo, fabricante, valor_credito,
    contador_entradas_inicial, contador_salidas_inicial, estado
) values
    ('c0000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000001', 'SC-1001', 'Super Cherry', 'Cirsa',     0.20, 120000,  90000, 'instalada'),
    ('c0000000-0000-0000-0000-000000000002', 'e0000000-0000-0000-0000-000000000001', 'DM-1002', 'Diamond',      'Cirsa',     0.20,  80000,  55000, 'instalada'),
    ('c0000000-0000-0000-0000-000000000003', 'e0000000-0000-0000-0000-000000000001', 'GA-1003', 'Gallo',        'Unidesa',   0.20, 200000, 160000, 'instalada'),
    ('c0000000-0000-0000-0000-000000000004', 'e0000000-0000-0000-0000-000000000001', 'BG-1004', 'Bingo Plus',   'R. Franco', 0.20,  50000,  38000, 'instalada'),
    ('c0000000-0000-0000-0000-000000000005', 'e0000000-0000-0000-0000-000000000001', 'FR-1005', 'Fruit Mania',  'MGA',       0.20,      0,      0, 'almacen'),
    ('c0000000-0000-0000-0000-000000000006', 'e0000000-0000-0000-0000-000000000001', 'TW-1006', 'Twister',      'Cirsa',     0.20,  30000,  21000, 'instalada')
on conflict (id) do nothing;

-- -----------------------------------------------------------------------------
-- 6. Licencias.
-- -----------------------------------------------------------------------------
insert into public.licencia (
    id, empresa_id, numero, tipo, fecha_expedicion, fecha_caducidad, comunidad_autonoma, estado
) values
    ('d0000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000001', 'VAL-2024-001', 'B', '2024-01-10', '2027-01-10', 'Comunidad Valenciana', 'activa'),
    ('d0000000-0000-0000-0000-000000000002', 'e0000000-0000-0000-0000-000000000001', 'VAL-2024-002', 'B', '2024-01-10', '2027-01-10', 'Comunidad Valenciana', 'activa'),
    ('d0000000-0000-0000-0000-000000000003', 'e0000000-0000-0000-0000-000000000001', 'VAL-2024-003', 'B', '2023-09-01', '2026-09-01', 'Comunidad Valenciana', 'activa'),
    ('d0000000-0000-0000-0000-000000000004', 'e0000000-0000-0000-0000-000000000001', 'VAL-2024-004', 'B', '2024-03-01', '2027-03-01', 'Comunidad Valenciana', 'activa'),
    ('d0000000-0000-0000-0000-000000000005', 'e0000000-0000-0000-0000-000000000001', 'VAL-2024-005', 'B', '2024-03-01', '2027-03-01', 'Comunidad Valenciana', 'activa'),
    ('d0000000-0000-0000-0000-000000000006', 'e0000000-0000-0000-0000-000000000001', 'VAL-2024-006', 'B', '2023-07-15', '2026-07-15', 'Comunidad Valenciana', 'activa')
on conflict (id) do nothing;

-- -----------------------------------------------------------------------------
-- 7. Instalaciones (máquina + licencia + local). Damos la base de contadores
--    explícita (= contadores iniciales de la máquina, al no haber recaudaciones
--    previas). En un `db reset` el trigger trg_set_contador_base_instalacion la
--    recalcula al MISMO valor; así el seed funciona aunque el trigger no esté.
-- -----------------------------------------------------------------------------
insert into public.instalacion (
    id, empresa_id, maquina_id, licencia_id, local_id,
    fecha_inicio, tasa_semanal, porcentaje_local,
    contador_entradas_base, contador_salidas_base, estado
) values
    ('f0000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001', '2026-01-15', 50.00, 50.00, 120000,  90000, 'activa'),
    ('f0000000-0000-0000-0000-000000000002', 'e0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000002', 'd0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000002', '2026-02-01', 40.00, 50.00,  80000,  55000, 'activa'),
    ('f0000000-0000-0000-0000-000000000003', 'e0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000003', 'd0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000003', '2025-12-10', 60.00, 45.00, 200000, 160000, 'activa'),
    ('f0000000-0000-0000-0000-000000000004', 'e0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000004', 'd0000000-0000-0000-0000-000000000004', 'b0000000-0000-0000-0000-000000000004', '2026-03-05', 35.00, 50.00,  50000,  38000, 'activa'),
    ('f0000000-0000-0000-0000-000000000005', 'e0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000006', 'd0000000-0000-0000-0000-000000000006', 'b0000000-0000-0000-0000-000000000005', '2026-04-01', 45.00, 40.00,  30000,  21000, 'activa')
on conflict (id) do nothing;

-- -----------------------------------------------------------------------------
-- 8. Recaudaciones (historial firme). Técnico = Pablo Ortega.
--    Cifras coherentes con los CHECK: neta = bruta - tasa_total; partes suman
--    neta; tasa_total = tasa_semanal * semanas. Desgloses suman bruta/parte_local.
-- -----------------------------------------------------------------------------
insert into public.recaudacion (
    id, empresa_id, instalacion_id, tecnico_id, fecha,
    contador_entradas_anterior, contador_salidas_anterior,
    contador_entradas_actual, contador_salidas_actual,
    valor_credito_aplicado, recaudacion_bruta,
    semanas_aplicadas, tasa_semanal_aplicada, tasa_total_aplicada,
    recaudacion_neta, porcentaje_local_aplicado, parte_local, parte_empresa,
    desglose_total, desglose_local,
    idempotency_key, baseline_origen, baseline_id, estado
) values
    -- Instalación 1
    ('0a000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000001', 'f0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000004',
     '2026-02-12 11:30:00+01',
     120000, 90000, 124000, 91500,
     0.20, 500.00, 4, 50.00, 200.00, 300.00, 50.00, 150.00, 150.00,
     '[{"denominacion": 50, "cantidad": 8}, {"denominacion": 20, "cantidad": 5}]'::jsonb,
     '[{"denominacion": 50, "cantidad": 3}]'::jsonb,
     'seed-rec-1a', 'instalacion_base', null, 'firme'),
    ('0a000000-0000-0000-0000-000000000002', 'e0000000-0000-0000-0000-000000000001', 'f0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000004',
     '2026-03-12 11:30:00+01',
     124000, 91500, 129500, 93000,
     0.20, 800.00, 4, 50.00, 200.00, 600.00, 50.00, 300.00, 300.00,
     '[{"denominacion": 50, "cantidad": 12}, {"denominacion": 20, "cantidad": 10}]'::jsonb,
     '[{"denominacion": 50, "cantidad": 6}]'::jsonb,
     'seed-rec-1b', 'recaudacion_anterior', '0a000000-0000-0000-0000-000000000001', 'firme'),
    -- Instalación 2
    ('0a000000-0000-0000-0000-000000000003', 'e0000000-0000-0000-0000-000000000001', 'f0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000004',
     '2026-03-02 12:00:00+01',
     80000, 55000, 83000, 56000,
     0.20, 400.00, 4, 40.00, 160.00, 240.00, 50.00, 120.00, 120.00,
     '[{"denominacion": 20, "cantidad": 20}]'::jsonb,
     '[{"denominacion": 20, "cantidad": 6}]'::jsonb,
     'seed-rec-2a', 'instalacion_base', null, 'firme'),
    ('0a000000-0000-0000-0000-000000000004', 'e0000000-0000-0000-0000-000000000001', 'f0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000004',
     '2026-03-30 12:00:00+02',
     83000, 56000, 86500, 57000,
     0.20, 500.00, 4, 40.00, 160.00, 340.00, 50.00, 170.00, 170.00,
     '[{"denominacion": 50, "cantidad": 6}, {"denominacion": 20, "cantidad": 10}]'::jsonb,
     '[{"denominacion": 50, "cantidad": 3}, {"denominacion": 20, "cantidad": 1}]'::jsonb,
     'seed-rec-2b', 'recaudacion_anterior', '0a000000-0000-0000-0000-000000000003', 'firme'),
    -- Instalación 3
    ('0a000000-0000-0000-0000-000000000005', 'e0000000-0000-0000-0000-000000000001', 'f0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000004',
     '2026-01-09 10:15:00+01',
     200000, 160000, 206000, 162000,
     0.20, 800.00, 4, 60.00, 240.00, 560.00, 45.00, 252.00, 308.00,
     '[{"denominacion": 50, "cantidad": 16}]'::jsonb,
     '[{"denominacion": 50, "cantidad": 5}, {"denominacion": 2, "cantidad": 1}]'::jsonb,
     'seed-rec-3a', 'instalacion_base', null, 'firme'),
    ('0a000000-0000-0000-0000-000000000006', 'e0000000-0000-0000-0000-000000000001', 'f0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000004',
     '2026-02-06 10:15:00+01',
     206000, 162000, 213000, 164500,
     0.20, 900.00, 4, 60.00, 240.00, 660.00, 45.00, 297.00, 363.00,
     '[{"denominacion": 50, "cantidad": 18}]'::jsonb,
     '[{"denominacion": 50, "cantidad": 5}, {"denominacion": 20, "cantidad": 2}, {"denominacion": 5, "cantidad": 1}, {"denominacion": 2, "cantidad": 1}]'::jsonb,
     'seed-rec-3b', 'recaudacion_anterior', '0a000000-0000-0000-0000-000000000005', 'firme')
on conflict (id) do nothing;

-- -----------------------------------------------------------------------------
-- 9. Alertas de ejemplo (pendientes, para que el dashboard muestre actividad).
-- -----------------------------------------------------------------------------
insert into public.alerta (id, empresa_id, tipo, referencia_id, mensaje, destinatario_usuario_id, leida, creada_en) values
    ('0b000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000001', 'local_sin_recaudar', 'f0000000-0000-0000-0000-000000000004',
     'El local "Salón Oasis" lleva más de 30 días sin recaudación registrada.',
     'a0000000-0000-0000-0000-000000000003', false, '2026-06-01 09:00:00+02'),
    ('0b000000-0000-0000-0000-000000000002', 'e0000000-0000-0000-0000-000000000001', 'licencia_caducidad', 'd0000000-0000-0000-0000-000000000006',
     'La licencia VAL-2024-006 caduca el 15/07/2026. Renuévala a tiempo.',
     'a0000000-0000-0000-0000-000000000002', false, '2026-06-05 09:00:00+02')
on conflict (id) do nothing;
