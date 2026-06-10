# Recre — Requisitos

## Introducción

Plataforma multi-tenant para empresas de explotación de máquinas recreativas. Cubre:

- **Web (back-office)** para administradores y gestores de empresa.
- **App Android** para técnicos en ruta.
- **Backend en Supabase** (Postgres + Auth + Storage + Edge Functions).

Permite gestionar empresas, licencias, máquinas, locales, instalaciones (asociaciones máquina-licencia-local) y recaudaciones con desglose de denominaciones, firma del titular, impresión de ticket por impresora térmica Bluetooth (AGPTEK PT210) y reparto automático tasa / parte cliente / parte empresa.

## Objetivos

1. Gestionar el inventario de licencias, máquinas, locales e instalaciones de varias empresas en una misma plataforma.
2. Permitir al técnico realizar recaudaciones en sitio, con o sin cobertura, con cálculo automático y verificación física por denominaciones.
3. Garantizar trazabilidad: ninguna recaudación se borra; los conflictos se reconcilian.
4. Generar tickets impresos en el momento y guardar PDFs accesibles desde la web.
5. Aislar los datos entre empresas mediante RLS de Supabase.

## Roles

- `owner`: dueño de la empresa. Todo, incluido borrar empresa.
- `admin`: todo menos borrar empresa.
- `gestor`: CRUD de licencias, máquinas, locales, instalaciones; lectura de recaudaciones.
- `tecnico`: lista de locales/máquinas, realiza recaudaciones, registra cambios de placa.
- `contable`: lectura de recaudaciones, exportaciones.

Un usuario puede tener distintos roles en distintas empresas.

## Idioma

Toda la UI, mensajes y tickets en **español**.

---

## Historias de usuario

### HU-1 Acceso multi-empresa
**Como** usuario que pertenece a varias empresas
**Quiero** seleccionar la empresa con la que trabajo al iniciar sesión
**Para** que mis datos y operaciones queden aislados a esa empresa.

Criterios:
- Si pertenezco a >1 empresa, la app/web me muestra un selector tras login.
- Puedo cambiar de empresa activa desde un menú sin cerrar sesión.
- Todos los datos visibles se filtran automáticamente por la empresa activa.

### HU-2 Gestión de licencias (web)
**Como** gestor
**Quiero** dar de alta, editar y dar de baja licencias
**Para** mantener actualizada la información oficial.

Criterios:
- Cada licencia tiene número, tipo, fecha de expedición, fecha de caducidad, comunidad autónoma, estado y notas.
- Las licencias activas que están en una instalación activa no se pueden dar de baja sin cerrar la instalación.
- El sistema avisa de licencias que caducan en menos de 90 días.

### HU-3 Gestión de máquinas (web)
**Como** gestor
**Quiero** dar de alta máquinas con su valor de crédito y contadores iniciales
**Para** poder instalarlas posteriormente.

Criterios:
- Cada máquina tiene número de serie, modelo, fabricante, **valor del crédito (típicamente 0.10 o 0.20 €)**, contadores iniciales (entradas y salidas, 0 si la máquina es nueva), estado.
- Una máquina solo puede estar en una instalación activa a la vez.
- Histórico completo de instalaciones por máquina visible en su detalle.

### HU-4 Gestión de locales (web)
**Como** gestor
**Quiero** dar de alta locales con sus datos del titular
**Para** poder asociar máquinas a ellos.

Criterios:
- Cada local tiene nombre, dirección, datos del titular (nombre, NIF/CIF, teléfono, email).
- Un local puede tener varias máquinas instaladas simultáneamente.

### HU-5 Creación de instalación (web)
**Como** gestor
**Quiero** asociar una máquina + licencia + local con tasa semanal y porcentaje del local
**Para** definir las condiciones económicas de explotación.

Criterios:
- Al crear se introduce: local, máquina libre, licencia libre, **tasa semanal en €**, **porcentaje del local (0–100)**, fecha de inicio, contadores base (auto-rellenados desde la máquina), notas.
- No permite combinar máquinas o licencias ya activas en otra instalación.
- Al cerrar una instalación se solicita fecha de fin.

### HU-6 Lista de locales del técnico (app)
**Como** técnico
**Quiero** ver mis locales con un indicador de máquinas pendientes
**Para** organizar mi ruta.

Criterios:
- Cada local muestra nombre, dirección, número de máquinas, días desde la última recaudación.
- Pull-to-refresh fuerza sincronización si hay red.

### HU-7 Recaudación de una máquina (app)
**Como** técnico
**Quiero** introducir contadores y desglose de denominaciones para registrar la recaudación
**Para** repartir el dinero correctamente con el local y dejar registro firmado.

Criterios:
- Pantalla de contadores muestra los anteriores (read-only) y pide los actuales.
- Validación: contadores actuales ≥ anteriores.
- Cálculo automático de bruto, semanas de tasa, tasa total, neto, parte local, parte empresa.
- Si bruto < tasa → mensaje "Recaudación insuficiente, no procede recaudar"; se registra **un log mínimo** (no una recaudación) y se cierra el flujo.
- Pantalla de denominaciones del total: el botón Continuar solo se habilita cuando la suma exacta coincide con el bruto.
- Pantalla de denominaciones de la parte local: igual, suma exacta con la parte_local.
- Pantalla de firma: el titular del local firma en pantalla (canvas).
- Botón Guardar e imprimir → persiste en BBDD y emite ticket por la impresora Bluetooth AGPTEK PT210.
- Si el técnico abandona el flujo antes de Guardar, todo se descarta.

### HU-8 Recaudación en cadena de un local con varias máquinas (app)
**Como** técnico
**Quiero** recaudar todas las máquinas pendientes de un local consecutivamente
**Para** no tener que volver a navegar al detalle del local entre cada una.

Criterios:
- Botón "Recaudar todas" en el detalle del local.
- El flujo se ejecuta en **orden fijo** definido por la app (orden alfabético/numero de serie).
- Al finalizar cada máquina se imprime su ticket y se firma por separado.
- Al guardar e imprimir aparece un botón "Siguiente máquina (X/N)".
- Si una máquina tiene bruto < tasa, se muestra el aviso y se ofrece "Saltar a la siguiente".
- Al terminar la última, se vuelve al detalle del local con todas marcadas.

### HU-9 Cambio de placa (app)
**Como** técnico
**Quiero** registrar el cambio de placa de una máquina averiada
**Para** que la siguiente recaudación parta de los nuevos contadores (típicamente 0).

Criterios:
- Acción "Registrar cambio de placa" desde el detalle de máquina/instalación.
- Se piden: fecha, contadores nuevos (entradas y salidas, por defecto 0), motivo, nº de serie placa anterior y nueva (opcional), foto (opcional), notas.
- No genera ticket ni reparto.
- La siguiente recaudación toma como baseline los contadores del cambio de placa.

### HU-10 Recaudación robusta sin red (app)
**Como** técnico que está en una zona sin cobertura
**Quiero** poder hacer recaudaciones offline y que se suban cuando recupere red
**Para** no quedarme bloqueado.

Criterios:
- La app funciona offline si tiene datos sincronizados.
- Si el dispositivo lleva más de **48 h** sin sincronizar, se exige conexión antes de iniciar nuevas recaudaciones.
- Las recaudaciones se encolan cifradas localmente y se suben cuando hay red.
- El servidor **siempre acepta** la recaudación y, si detecta conflicto (la baseline ya no es la última), la marca como tal y crea una alerta.

### HU-11 Resolución de conflictos (web)
**Como** admin
**Quiero** revisar las recaudaciones marcadas como conflicto
**Para** decidir si aceptarlas, sustituirlas por los valores recalculados o anularlas.

Criterios:
- Pantalla "Recaudaciones en conflicto" con lista pendiente.
- Detalle muestra valores originales del técnico y valores recalculados con la baseline correcta.
- Acciones: aceptar, sustituir, anular (con motivo).
- El sistema notifica al técnico (push o email) la resolución.

### HU-12 Bloqueo optimista entre técnicos (app)
**Como** técnico que abre la pantalla de recaudación con red
**Quiero** ver si otro técnico está recaudando esa misma instalación ahora
**Para** evitar trabajo duplicado.

Criterios:
- Al abrir la pantalla con red se inserta un lock con TTL 30 min.
- Si ya hay un lock activo de otro técnico, se muestra aviso "Siendo recaudada por X desde HH:MM. ¿Continuar de todos modos?".
- Si confirmo, mi lock sustituye al anterior.

### HU-13 Impresión y archivo del ticket
**Como** técnico
**Quiero** que el ticket se imprima por la AGPTEK PT210 vía Bluetooth y se guarde el PDF
**Para** entregar copia al titular y tener archivo accesible desde la web.

Criterios:
- La app empareja la impresora Bluetooth desde Ajustes.
- Al guardar la recaudación se genera el PDF en Storage y se imprime el ticket vía ESC/POS.
- Desde el detalle de la recaudación en la web se puede descargar el PDF y solicitar reimpresión.

### HU-14 Foto de los contadores (fase 1 manual, fase 2 OCR)
**Como** técnico
**Quiero** poder adjuntar fotos del display de la máquina
**Para** dejar evidencia de la lectura de contadores.

Criterios fase 1:
- Botones "Foto entradas" y "Foto salidas" en la pantalla de contadores. Opcional en fase 1.
- Las fotos se suben a Storage y se vinculan a la recaudación.

Criterios fase 2:
- Un OCR detecta automáticamente el valor del contador y lo pre-rellena en el campo numérico.
- El técnico siempre confirma o corrige el valor.

### HU-15 Recaudaciones inmutables, anulación auditada
**Como** admin
**Quiero** poder anular una recaudación con motivo
**Para** corregir errores sin perder histórico.

Criterios:
- No existe "borrar". Solo `estado='anulada'` con motivo, autor y fecha.
- Las recaudaciones anuladas no cuentan para los informes financieros pero siguen visibles.
- La siguiente recaudación recalcula su baseline ignorando las anuladas.

### HU-16 Informes (web)
**Como** contable o admin
**Quiero** ver y exportar informes
**Para** llevar la contabilidad y auditar el negocio.

Criterios:
- Filtros por fecha, local, máquina, técnico.
- Agregados: por local/mes, por máquina/periodo, por técnico/periodo, resumen fiscal trimestral.
- Exportación a CSV.

### HU-17a CRUD completo desde la app
**Como** gestor o admin que está en ruta
**Quiero** poder crear, editar y dar de baja licencias, máquinas, locales e instalaciones desde la app
**Para** no depender de un ordenador cuando estoy en sitio.

Criterios:
- Pantalla de Gestión accesible solo si rol >= `gestor`.
- CRUD de licencias, máquinas, locales e instalaciones (alta, edición, cierre/baja) con las mismas validaciones que la web.
- Crear instalación valida máquina libre, licencia libre, contadores base, tasa semanal y porcentaje del local.
- Cerrar instalación pide fecha de fin.
- Operaciones de gestión requieren conexión (no se permiten offline; si no hay red, mensaje claro).
- Los técnicos puros (`tecnico`) no ven esta sección.

### HU-17 Gestión de equipo (web)
**Como** owner o admin
**Quiero** invitar usuarios a la empresa con un rol concreto
**Para** dar acceso al personal correspondiente.

Criterios:
- Invitación por email vía Supabase Auth.
- Cambio de rol y desactivación.

### HU-18 Alertas (web)
**Como** admin
**Quiero** ver alertas de licencias caducando, locales sin recaudar y conflictos
**Para** actuar a tiempo.

Criterios:
- Tabla `alerta` consolidada.
- Dashboard muestra contador de alertas no leídas.

---

## No funcionales

- **Multi-tenant** con RLS por `empresa_id`. Ningún query cruza empresas.
- **Idempotencia** en la subida de recaudaciones (clave única `idempotency_key`).
- **Latencia**: el cálculo de recaudación (Edge Function) responde en < 500 ms.
- **Disponibilidad**: depende de Supabase (objetivo razonable, no SLA estricto).
- **Idioma**: español de España.
- **GDPR**: contemplado a nivel de aislamiento, sin requisitos formales en fase 1.
- **Retención**: las recaudaciones se conservan indefinidamente.
- **Seguridad**: tokens JWT de Supabase; almacenamiento local cifrado en la app.
