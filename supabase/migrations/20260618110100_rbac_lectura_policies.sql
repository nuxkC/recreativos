-- =============================================================================
-- Planificación de recaudación — P2.2: RBAC de lectura estricto por operario.
--
-- Reemplaza las policies SELECT de las tablas operativas para que el TÉCNICO
-- solo lea sus locales asignados y lo que cuelga de ellos. owner/admin/gestor/
-- contable (ve-todo) no cambian. Migración aditiva: DROP POLICY IF EXISTS +
-- CREATE en migración nueva (no se edita 20260519230500_enable_rls_and_policies).
-- Las policies de INSERT/UPDATE/DELETE NO se tocan: las escrituras siguen igual.
--
-- Patrón: usuario_ve_todo_empresa(empresa_id) OR <camino al operario del local>.
-- Los caminos cruzados van por helpers SECURITY DEFINER (sin recursión de RLS).
-- Spec: docs/superpowers/specs/2026-06-18-planificacion-recaudacion-design.md §5.
-- =============================================================================

-- --- Locales + cadena por instalación ----------------------------------------

-- local: ve-todo, o soy el operario asignado.
DROP POLICY IF EXISTS local_select ON public.local;
CREATE POLICY local_select ON public.local
    FOR SELECT USING (
        public.usuario_ve_todo_empresa(empresa_id) OR operario_id = auth.uid()
    );

-- instalacion: a través de su local.
DROP POLICY IF EXISTS instalacion_select ON public.instalacion;
CREATE POLICY instalacion_select ON public.instalacion
    FOR SELECT USING (
        public.usuario_ve_todo_empresa(empresa_id) OR public.usuario_ve_local(local_id)
    );

-- recaudacion: a través de su instalación.
DROP POLICY IF EXISTS recaudacion_select ON public.recaudacion;
CREATE POLICY recaudacion_select ON public.recaudacion
    FOR SELECT USING (
        public.usuario_ve_todo_empresa(empresa_id) OR public.usuario_ve_instalacion(instalacion_id)
    );

-- cambio_placa: a través de su instalación.
DROP POLICY IF EXISTS cambio_placa_select ON public.cambio_placa;
CREATE POLICY cambio_placa_select ON public.cambio_placa
    FOR SELECT USING (
        public.usuario_ve_todo_empresa(empresa_id) OR public.usuario_ve_instalacion(instalacion_id)
    );

-- lectura_no_recaudada: a través de su instalación.
DROP POLICY IF EXISTS lectura_no_recaudada_select ON public.lectura_no_recaudada;
CREATE POLICY lectura_no_recaudada_select ON public.lectura_no_recaudada
    FOR SELECT USING (
        public.usuario_ve_todo_empresa(empresa_id) OR public.usuario_ve_instalacion(instalacion_id)
    );

-- recaudacion_lock: sin empresa_id; solo por la instalación.
DROP POLICY IF EXISTS recaudacion_lock_select ON public.recaudacion_lock;
CREATE POLICY recaudacion_lock_select ON public.recaudacion_lock
    FOR SELECT USING (public.usuario_ve_instalacion(instalacion_id));

-- --- Catálogo (visible si instalado en un local del operario) -----------------

-- maquina: el técnico la ve solo si está instalada en uno de sus locales. El
-- EXISTS sobre instalacion+local queda filtrado por sus policies estrictas, lo
-- que refuerza el criterio; los helpers internos (SECURITY DEFINER) evitan
-- recursión.
DROP POLICY IF EXISTS maquina_select ON public.maquina;
CREATE POLICY maquina_select ON public.maquina
    FOR SELECT USING (
        public.usuario_ve_todo_empresa(empresa_id)
        OR EXISTS (
            SELECT 1
              FROM public.instalacion i
              JOIN public.local l ON l.id = i.local_id
             WHERE i.maquina_id = maquina.id AND l.operario_id = auth.uid()
        )
    );

-- licencia: idem, vía instalacion.licencia_id.
DROP POLICY IF EXISTS licencia_select ON public.licencia;
CREATE POLICY licencia_select ON public.licencia
    FOR SELECT USING (
        public.usuario_ve_todo_empresa(empresa_id)
        OR EXISTS (
            SELECT 1
              FROM public.instalacion i
              JOIN public.local l ON l.id = i.local_id
             WHERE i.licencia_id = licencia.id AND l.operario_id = auth.uid()
        )
    );

-- --- Averías (máquina-céntricas; local_id es snapshot y puede ser NULL) -------

-- averia: el técnico la ve solo si su snapshot apunta a un local suyo. Las de
-- local_id NULL (máquina en almacén) solo las ve ve-todo.
DROP POLICY IF EXISTS averia_select ON public.averia;
CREATE POLICY averia_select ON public.averia
    FOR SELECT USING (
        public.usuario_ve_todo_empresa(empresa_id)
        OR (local_id IS NOT NULL AND public.usuario_ve_local(local_id))
    );

-- averia_recambio: cuelga de la avería; mismo criterio vía averia_id.
DROP POLICY IF EXISTS averia_recambio_select ON public.averia_recambio;
CREATE POLICY averia_recambio_select ON public.averia_recambio
    FOR SELECT USING (
        public.usuario_ve_todo_empresa(empresa_id)
        OR EXISTS (
            SELECT 1
              FROM public.averia a
             WHERE a.id = averia_recambio.averia_id
               AND a.local_id IS NOT NULL
               AND public.usuario_ve_local(a.local_id)
        )
    );

-- --- Deudas del local (tolva/préstamos y sus abonos) --------------------------

-- credito_local: por el local directo.
DROP POLICY IF EXISTS credito_local_select ON public.credito_local;
CREATE POLICY credito_local_select ON public.credito_local
    FOR SELECT USING (
        public.usuario_ve_todo_empresa(empresa_id) OR public.usuario_ve_local(local_id)
    );

-- recuperacion: tiene local_id directo (NOT NULL) → por el local.
DROP POLICY IF EXISTS recuperacion_select ON public.recuperacion;
CREATE POLICY recuperacion_select ON public.recuperacion
    FOR SELECT USING (
        public.usuario_ve_todo_empresa(empresa_id) OR public.usuario_ve_local(local_id)
    );
