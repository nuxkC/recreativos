# Recre — Guía de despliegue

Despliegue de los tres subproyectos del monorepo: **Supabase** (backend), **web**
(back-office Next.js) y **Android** (app de técnicos).

Esta guía documenta el proceso completo de puesta en producción. Los comandos y
nombres de variables se basan en la configuración real del repositorio
(`supabase/config.toml`, `web/package.json`, `web/.env.example`,
`android/app/build.gradle.kts`, `android/gradle/libs.versions.toml`, etc.).

> Seguridad: ningún secreto se versiona. Solo se versionan los `*.env.example`
> y `local.properties.example`. Las claves reales viven en `.env.local`,
> `local.properties`, el gestor de secretos del hosting y `supabase secrets`.
> Nunca se hardcodean claves ni se loguean tokens/firmas/PII.

---

## Índice

1. [Requisitos previos](#1-requisitos-previos)
2. [Orden de despliegue](#2-orden-de-despliegue)
3. [Supabase](#3-supabase)
4. [Web (Next.js)](#4-web-nextjs)
5. [Android (APK/AAB)](#5-android-apkaab)
6. [Checklist de verificación post-deploy](#6-checklist-de-verificación-post-deploy)
7. [Resumen de variables y secretos](#7-resumen-de-variables-y-secretos)

---

## 1. Requisitos previos

Versiones tomadas de los archivos de configuración del repo.

| Herramienta | Versión | Fuente en el repo |
|---|---|---|
| Supabase CLI | `>= 2.x` | `supabase/README.md` |
| Docker | requerido para `supabase start` (solo local) | `supabase/README.md` |
| PostgreSQL (remoto) | `17` | `config.toml` → `[db].major_version = 17` |
| Deno (Edge runtime) | `2` | `config.toml` → `[edge_runtime].deno_version = 2` |
| Node.js | `20+` (recomendado `22`) | `web/README.md` |
| npm | el que acompaña a Node | `web/package.json` |
| Next.js | `14.2.35` | `web/package.json` |
| JDK | `17` | `android/app/build.gradle.kts` (`JavaVersion.VERSION_17`) |
| Gradle (wrapper) | `8.10.2` | `android/gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | `8.7.3` | `android/gradle/libs.versions.toml` |
| Kotlin | `2.0.21` | `android/gradle/libs.versions.toml` |
| compileSdk / targetSdk | `35` | `android/gradle/libs.versions.toml` |
| minSdk | `26` (Android 8.0) | `android/gradle/libs.versions.toml` |

Cuentas / accesos necesarios:

- Proyecto Supabase en la nube (con su `project-ref`).
- Hosting para la web (recomendado Vercel; alternativa: cualquier host Node).
- Keystore de firma de release para Android (ver [§5](#5-android-apkaab)).
- (Opcional) Cuenta de Resend para el email al técnico (`enviar-email-tecnico`).

---

## 2. Orden de despliegue

El backend va primero porque la web y la app dependen de su URL y claves.

1. **Supabase**: enlazar proyecto → migraciones → secrets → Edge Functions.
2. **Web**: configurar variables → build → desplegar.
3. **Android**: configurar firma y endpoints → generar AAB/APK de release.

Las URLs de la web (dominio de producción) deben añadirse a la configuración de
Auth de Supabase (`site_url` / redirect URLs) antes de dar por cerrado el deploy.

---

## 3. Supabase

El backend es Postgres con RLS, Edge Functions en Deno y Storage privado.
La configuración local vive en `supabase/config.toml`.

### 3.1 Enlazar el proyecto remoto

```bash
# Autenticarse con la CLI (abre el navegador)
supabase login

# Enlazar el repo con el proyecto remoto (usa tu project-ref del dashboard)
supabase link --project-ref <project-ref>
```

`project_id = "recre"` ya está fijado en `config.toml`; el `--project-ref`
es el identificador del proyecto en la nube (Dashboard → Project Settings).

### 3.2 Aplicar migraciones

Las migraciones están en `supabase/migrations/` (ordenadas por timestamp) y son
**aditivas**: una vez aplicadas no se editan; se crea otra que rectifique.

```bash
# Revisar qué se aplicaría (recomendado antes de tocar producción)
supabase db push --dry-run

# Aplicar las migraciones pendientes al proyecto enlazado
supabase db push
```

Esto crea, entre otros: tablas core multi-tenant, inventario, recaudación y
auditoría, triggers de `updated_at`, funciones SQL (`semanas_iso_entre`,
`obtener_baseline`), validadores de denominaciones, helpers y políticas RLS,
vistas, y los **buckets de Storage** (ver §3.5).

> El `seed.sql` está pensado para desarrollo local (`supabase db reset`). **No**
> se ejecuta en producción con `db push`.

### 3.3 Configurar secrets / variables de entorno

En el entorno desplegado, Supabase **inyecta automáticamente** estas variables a
las Edge Functions (no hay que declararlas): `SUPABASE_URL`,
`SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY` y `SUPABASE_DB_URL`.
Las usan `_shared/db.ts` (`getUserClient` / `getServiceClient`).

Los **únicos secretos propios** que hay que configurar son los del email
(`enviar-email-tecnico`), y son opcionales: si falta `RESEND_API_KEY` la función
hace _skip_ y devuelve `email_skipped` sin abortar la resolución del conflicto.

```bash
# Configurar el secreto del proveedor de email (opcional)
supabase secrets set RESEND_API_KEY=<tu-api-key-de-resend>

# Remitente del email (opcional; por defecto "Recre <noreply@recre.app>")
supabase secrets set RESEND_FROM="Recre <noreply@tu-dominio.com>"

# Listar secretos configurados (muestra nombres y hashes, no valores)
supabase secrets list
```

Para SMTP de Auth (envío de invitaciones / confirmaciones en producción)
configura un servidor SMTP real en el dashboard de Auth, o vía `config.toml`
(`[auth.email.smtp]`) usando sustitución por variable de entorno
(p. ej. `pass = "env(SENDGRID_API_KEY)"`). No pongas credenciales en claro.

### 3.4 Desplegar Edge Functions

Las funciones viven en `supabase/functions/<kebab-case>/index.ts` y comparten
`_shared/`. Los imports y opciones de Deno están en `supabase/functions/deno.json`.

```bash
# Desplegar todas las funciones
supabase functions deploy

# O desplegar una concreta
supabase functions deploy calcular-recaudacion
```

Funciones actuales del repo:

| Función | Propósito |
|---|---|
| `calcular-recaudacion` | Calcula sin persistir (SSOT del cálculo). |
| `crear-recaudacion` | Valida, recalcula, detecta conflicto, genera PDF y sube a Storage. |
| `crear-cambio-placa` | Registra un cambio de placa. |
| `cerrar-instalacion` | Cierra una instalación. |
| `adquirir-lock` / `liberar-lock` | Lock optimista con TTL. |
| `anular-recaudacion` | Anula una recaudación. |
| `resolver-conflicto` | Resuelve conflictos; dispara el email al técnico. |
| `invitar-usuario` | Invitación de usuario a la empresa. |
| `reimprimir-ticket` | Reimpresión de ticket. |
| `enviar-email-tecnico` | Notifica por email al técnico (usa `RESEND_*`). |

> Verificación de la JWT: estas funciones esperan el JWT del usuario
> (`getUserClient`). `enviar-email-tecnico` la invoca `resolver-conflicto` de
> forma interna (fire-and-forget). Mantén la verificación de JWT por defecto
> salvo que una función concreta requiera lo contrario.

### 3.5 Buckets de Storage

Los buckets se crean con la migración
`supabase/migrations/20260519230700_create_storage_buckets.sql`, así que se
aprovisionan con `supabase db push`. Todos son **privados** (sin acceso público);
el acceso se hace por signed URL server-side o con el token del usuario, con la
convención de path `<bucket>/<empresa_id>/<resto>`.

| Bucket | Privado | Límite | MIME permitidos |
|---|---|---|---|
| `firmas` | sí | 5 MB | image/png, image/jpeg |
| `fotos-contadores` | sí | 10 MB | image/jpeg, image/png |
| `tickets` | sí | 10 MB | application/pdf |
| `logos` | sí | 2 MB | image/png, image/jpeg, image/svg+xml |
| `cambios-placa` | sí | 10 MB | image/jpeg, image/png |

Las políticas RLS de `storage.objects` restringen el acceso a miembros de la
empresa dueña del path (`tickets` y `logos` solo se escriben con service_role).

### 3.6 Local vs producción

| | Local | Producción |
|---|---|---|
| Arranque | `supabase start` (Docker) | proyecto en la nube |
| Migraciones + seed | `supabase db reset` | `supabase db push` (sin seed) |
| Edge Functions | `supabase functions serve` (hot reload) | `supabase functions deploy` |
| API URL | `http://127.0.0.1:54321` | `https://<project-ref>.supabase.co` |
| Studio | `http://127.0.0.1:54323` | Dashboard de Supabase |
| Email | Inbucket (`http://127.0.0.1:54324`) | SMTP real / Resend |

---

## 4. Web (Next.js)

Back-office en Next.js 14 (App Router) + TypeScript + Tailwind + shadcn/ui +
`@supabase/supabase-js` + `@supabase/ssr` + TanStack Query.

### 4.1 Variables de entorno

El acceso tipado está en `web/src/lib/env.ts`, que **falla rápido** si falta
alguna. Las variables reales (ver `web/.env.example`):

| Variable | Obligatoria | Uso |
|---|---|---|
| `NEXT_PUBLIC_SUPABASE_URL` | sí | URL del proyecto Supabase (cliente y servidor). |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | sí | Anon key pública. |
| `SUPABASE_SERVICE_ROLE_KEY` | no | Solo si código de servidor lo requiere. **Nunca** se expone al cliente. |
| `E2E_BASE_URL`, `E2E_USER_EMAIL`, `E2E_USER_PASSWORD`, `E2E_REUSE_SERVER` | no | Solo para Playwright en test, no en producción. |

> Las variables `NEXT_PUBLIC_*` se inlinean en el bundle en **tiempo de build**.
> Hay que definirlas en el entorno de build del hosting, no solo en runtime.

### 4.2 Build local de verificación

```bash
cd web
cp .env.example .env.local   # rellena con la URL y anon key reales
npm ci
npm run lint
npm run build
npm run start                # sirve el build de producción en :3000
```

### 4.3 Despliegue en Vercel (recomendado)

1. Importa el repo en Vercel y fija **Root Directory** = `web`.
2. Framework preset: **Next.js** (detecta `npm run build` automáticamente).
3. En _Environment Variables_ añade `NEXT_PUBLIC_SUPABASE_URL` y
   `NEXT_PUBLIC_SUPABASE_ANON_KEY` (y `SUPABASE_SERVICE_ROLE_KEY` solo si se usa
   en server, marcándola como secreta).
4. Despliega. Vercel ejecuta `next build` y publica.

### 4.4 Despliegue en host Node genérico

```bash
cd web
npm ci
npm run build
npm run start   # arranca next start (por defecto en :3000)
```

Sirve detrás de un reverse proxy (HTTPS) y mantén las env vars en el gestor de
secretos del host. Asegura Node 20+ en el servidor.

### 4.5 Configurar Auth tras desplegar

En el dashboard de Supabase (o `config.toml` para local), añade el dominio de
producción de la web a `site_url` y a las redirect URLs permitidas de Auth.
En local, el valor por defecto es `http://127.0.0.1:3000`.

### 4.6 Verificación

- La home redirige a login; el login autentica contra Supabase.
- Tras login, el selector de empresa y el dashboard cargan datos reales.
- No aparece el error «Falta la variable de entorno: …» (lo lanza `env.ts`).

---

## 5. Android (APK/AAB)

App de técnicos en Kotlin + Jetpack Compose + Hilt + Room + WorkManager +
Supabase Kotlin SDK. Build con el wrapper de Gradle (`./gradlew`).

### 5.1 Endpoints de Supabase que consume la app

`android/app/build.gradle.kts` lee la configuración desde `local.properties`
(no versionado) o variables de entorno, y la expone como `BuildConfig`:

| Clave | `BuildConfig` | Uso |
|---|---|---|
| `SUPABASE_URL` | `BuildConfig.SUPABASE_URL` | URL del proyecto Supabase. |
| `SUPABASE_ANON_KEY` | `BuildConfig.SUPABASE_ANON_KEY` | Anon key pública. |

Plantilla en `android/local.properties.example`. Para una build de release,
crea `android/local.properties` con los valores de **producción**:

```properties
SUPABASE_URL=https://<project-ref>.supabase.co
SUPABASE_ANON_KEY=<anon-key-de-produccion>
# sdk.dir lo rellena Android Studio
```

En CI puedes pasarlos como variables de entorno (`SUPABASE_URL`,
`SUPABASE_ANON_KEY`) en lugar de `local.properties`; el script las lee con el
mismo helper `localOrEnv(...)`.

### 5.2 Configuración de firma (keystore)

> Estado actual del repo: en `android/app/build.gradle.kts` el `buildType`
> `release` usa `signingConfig = signingConfigs.getByName("debug")`. Eso sirve
> para pruebas, pero **una build distribuible debe firmarse con un keystore de
> release propio**. Configúralo así, sin hardcodear claves.

1. Genera el keystore (una sola vez; guárdalo fuera del repo y con backup):

   ```bash
   keytool -genkeypair -v -keystore recre-release.jks \
     -keyalg RSA -keysize 2048 -validity 10000 -alias recre
   ```

2. Define las credenciales en `android/keystore.properties` (gitignored) o como
   variables de entorno en CI. **Nunca** en el `build.gradle.kts` ni en el repo:

   ```properties
   RECRE_KEYSTORE_FILE=/ruta/segura/recre-release.jks
   RECRE_KEYSTORE_PASSWORD=...
   RECRE_KEY_ALIAS=recre
   RECRE_KEY_PASSWORD=...
   ```

3. Añade el `signingConfig` de release leyendo esos valores (patrón
   `localOrEnv` ya presente en el módulo) y reemplaza la firma debug:

   ```kotlin
   signingConfigs {
       create("release") {
           storeFile = file(localOrEnv("RECRE_KEYSTORE_FILE"))
           storePassword = localOrEnv("RECRE_KEYSTORE_PASSWORD")
           keyAlias = localOrEnv("RECRE_KEY_ALIAS")
           keyPassword = localOrEnv("RECRE_KEY_PASSWORD")
       }
   }
   // en buildTypes.release:
   //   signingConfig = signingConfigs.getByName("release")
   ```

> No subas `recre-release.jks`, `keystore.properties` ni contraseñas al repo.
> Para Google Play, valora **Play App Signing** (subes la clave de upload y
> Google gestiona la de firma).

### 5.3 Generar el artefacto de release

El `release` ya tiene `isMinifyEnabled = true` y `isShrinkResources = true`
(R8 + reglas en `app/proguard-rules.pro`).

```bash
cd android

# AAB de release (formato para Google Play)
./gradlew :app:bundleRelease
# -> app/build/outputs/bundle/release/app-release.aab

# APK de release (instalación directa / distribución fuera de Play)
./gradlew :app:assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

En Windows usa `gradlew.bat` en lugar de `./gradlew`.

### 5.4 Verificación

- La APK instala en un dispositivo con Android 8.0+ (minSdk 26).
- La app arranca, hace login contra el Supabase de producción y sincroniza
  (locales, máquinas, instalaciones, baselines).
- `BuildConfig.SUPABASE_URL` apunta al proyecto correcto (no al placeholder
  `https://example.supabase.co`, que es el default si falta la clave).
- Para el AAB de Play: verifica la firma con
  `bundletool` / `jarsigner -verify` o desde la consola de Play.

---

## 6. Checklist de verificación post-deploy

**Supabase**

- [ ] `supabase db push --dry-run` no muestra migraciones pendientes.
- [ ] Las funciones aparecen desplegadas (`supabase functions list`).
- [ ] Los 5 buckets existen y son privados (`firmas`, `fotos-contadores`,
      `tickets`, `logos`, `cambios-placa`).
- [ ] `supabase secrets list` muestra `RESEND_API_KEY` si se usa email.
- [ ] RLS activa: un usuario no ve datos de otra empresa.

**Web**

- [ ] Build de producción sin errores (`npm run build`).
- [ ] Variables `NEXT_PUBLIC_*` definidas en el entorno de build del hosting.
- [ ] Login funciona y el dashboard carga datos reales.
- [ ] Dominio de producción añadido a las redirect URLs de Auth.
- [ ] Crear una recaudación de prueba llama a la Edge Function y genera el PDF.

**Android**

- [ ] `:app:assembleRelease` / `:app:bundleRelease` compilan.
- [ ] El artefacto está firmado con el keystore de **release** (no debug).
- [ ] `local.properties` (o env de CI) apunta al Supabase de producción.
- [ ] Login + sync inicial OK en un dispositivo real.
- [ ] Flujo de recaudación end-to-end OK (incluida impresión si aplica).

**Transversal**

- [ ] Ningún secreto versionado (`.env.local`, `local.properties`,
      `keystore.properties`, `*.jks` están gitignored).
- [ ] Logs sin PII ni tokens/firmas.

---

## 7. Resumen de variables y secretos

| Variable | Subproyecto | Dónde se configura | Versionada |
|---|---|---|---|
| `NEXT_PUBLIC_SUPABASE_URL` | web | `.env.local` / env del hosting (build) | no |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | web | `.env.local` / env del hosting (build) | no |
| `SUPABASE_SERVICE_ROLE_KEY` | web (server, opcional) | env del hosting (secreto) | no |
| `SUPABASE_URL` | android | `local.properties` / env de CI | no |
| `SUPABASE_ANON_KEY` | android | `local.properties` / env de CI | no |
| `RECRE_KEYSTORE_FILE` / `_PASSWORD` / `RECRE_KEY_ALIAS` / `RECRE_KEY_PASSWORD` | android | `keystore.properties` / env de CI | no |
| `SUPABASE_URL` / `SUPABASE_ANON_KEY` / `SUPABASE_SERVICE_ROLE_KEY` | supabase (Edge) | inyectadas por la plataforma | n/a |
| `RESEND_API_KEY` | supabase (Edge) | `supabase secrets set` | no |
| `RESEND_FROM` | supabase (Edge) | `supabase secrets set` (opcional) | no |

Plantillas versionadas de referencia: `web/.env.example` y
`android/local.properties.example`.

---

Ver también: [`supabase/README.md`](../supabase/README.md),
[`supabase/functions/README.md`](../supabase/functions/README.md),
[`web/README.md`](../web/README.md),
[`.kiro/steering/architecture.md`](../.kiro/steering/architecture.md) y
[`.kiro/steering/conventions.md`](../.kiro/steering/conventions.md).
