# Guía de despliegue — Recre (T-82)

Pasos para desplegar los tres componentes del proyecto: **Supabase** (backend),
**web** (back-office Next.js) y **Android** (APK para técnicos).

El orden recomendado es: **1) Supabase → 2) web → 3) Android**, porque tanto la
web como la app necesitan la URL y la `anon key` del proyecto Supabase.

---

## 1. Supabase (backend)

Despliega la base de datos (tablas, RLS, funciones, vistas), los buckets de
Storage y las Edge Functions.

### 1.1. Crear el proyecto

1. Crea un proyecto en [supabase.com](https://supabase.com/dashboard).
2. Anota desde **Project Settings → API**:
   - **Project URL** (`https://<ref>.supabase.co`)
   - **anon public key** (cliente)
   - **service_role key** (servidor; **nunca** se expone al cliente)
   - **Project Ref** (el `<ref>` de la URL)

### 1.2. Enlazar la CLI

Requiere [Supabase CLI](https://supabase.com/docs/guides/cli) `>= 2.x`.

```bash
cd supabase
supabase login
supabase link --project-ref <ref>
```

### 1.3. Aplicar migraciones

Esto crea **todo** el esquema: tablas, índices, funciones SQL
(`obtener_baseline`, `semanas_iso_entre`, validadores de denominaciones),
triggers `updated_at`, políticas RLS, vistas y los buckets de Storage
(`firmas`, `fotos-contadores`, `tickets`, `logos`, `cambios-placa`).

```bash
supabase db push
```

> Las migraciones son **aditivas**: no se editan una vez aplicadas. Para
> rectificar algo se crea una migración nueva.

### 1.4. Configurar secretos de las Edge Functions

La plataforma inyecta automáticamente `SUPABASE_URL`, `SUPABASE_ANON_KEY`,
`SUPABASE_SERVICE_ROLE_KEY` y `SUPABASE_DB_URL` en las funciones, así que **no**
hay que definirlos a mano. Solo hay que añadir los del envío de email
(usados por `enviar-email-tecnico`, ver [Resend](https://resend.com)):

```bash
supabase secrets set RESEND_API_KEY=re_xxxxxxxx
supabase secrets set RESEND_FROM="Recre <no-reply@tudominio.com>"
```

### 1.5. Desplegar las Edge Functions

```bash
# Todas de una vez
supabase functions deploy

# O una a una
supabase functions deploy calcular-recaudacion
```

Funciones del proyecto: `adquirir-lock`, `liberar-lock`,
`calcular-recaudacion`, `crear-recaudacion`, `anular-recaudacion`,
`crear-cambio-placa`, `cerrar-instalacion`, `resolver-conflicto`,
`reimprimir-ticket`, `invitar-usuario`, `enviar-email-tecnico`.

### 1.6. Configurar Auth

En **Authentication → Providers** habilita **Email**. En **URL Configuration**:

- **Site URL**: la URL pública de la web (paso 2).
- **Redirect URLs**: añade la URL de la web (y la de previews si usas Vercel).

> Hay un usuario/empresa semilla en `supabase/seed.sql` pensado para desarrollo
> local (`supabase db reset`). En producción se crean usuarios reales vía la
> función `invitar-usuario` desde la web (pantalla de Equipo).

---

## 2. Web (back-office Next.js)

App Next.js 14 (App Router). La opción recomendada es **Vercel**, pero al ser un
servidor Node estándar también se puede autoalojar.

### 2.1. Variables de entorno

| Variable | Valor |
|---|---|
| `NEXT_PUBLIC_SUPABASE_URL` | Project URL del paso 1.1 |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | anon key del paso 1.1 |

> Ambas tienen prefijo `NEXT_PUBLIC_` porque se usan en el cliente. La
> `service_role key` **no** se usa en la web.

### 2.2. Despliegue en Vercel

1. **New Project** → importa el repositorio.
2. **Root Directory**: `web`.
3. **Framework Preset**: Next.js (autodetectado). Build = `next build`.
4. Añade las dos variables de entorno de 2.1 en **Settings → Environment Variables**.
5. Deploy.
6. Copia la URL resultante y añádela a las **Redirect URLs** de Supabase Auth (paso 1.6).

### 2.3. Autoalojado (alternativa)

```bash
cd web
npm ci
NEXT_PUBLIC_SUPABASE_URL=... NEXT_PUBLIC_SUPABASE_ANON_KEY=... npm run build
NEXT_PUBLIC_SUPABASE_URL=... NEXT_PUBLIC_SUPABASE_ANON_KEY=... npm run start  # escucha en :3000
```

Sitúa un reverse proxy (Nginx/Caddy) con HTTPS por delante. Requiere Node 20+
(recomendado 22).

### 2.4. Verificación previa

```bash
cd web
npm run lint
npm run test          # unitarios (Vitest)
npm run test:e2e      # E2E (Playwright); arranca su propio servidor
```

---

## 3. Android (APK)

App Kotlin + Jetpack Compose. Requiere **JDK 17+** y **Android SDK 35**.

### 3.1. Credenciales

Las credenciales de Supabase se inyectan en `BuildConfig` desde
`local.properties` **o**, si no existe la clave, desde variables de entorno del
mismo nombre (útil en CI):

```properties
# android/local.properties
SUPABASE_URL=https://<ref>.supabase.co
SUPABASE_ANON_KEY=<anon-key>
sdk.dir=/ruta/al/Android/sdk
```

```bash
# Alternativa por entorno (CI)
export SUPABASE_URL=https://<ref>.supabase.co
export SUPABASE_ANON_KEY=<anon-key>
```

### 3.2. Build debug

```bash
cd android
./gradlew assembleDebug
# Salida: app/build/outputs/apk/debug/app-debug.apk
```

Para instalar en un dispositivo conectado: `./gradlew installDebug`.

### 3.3. Build release (firmado)

> **Importante:** hoy el tipo `release` está configurado para firmarse con la
> clave **debug** (`signingConfig = signingConfigs.getByName("debug")` en
> `app/build.gradle.kts`). Eso sirve para pruebas internas, pero **no** para
> publicar en Play Store. Para un release real:

1. Genera un keystore (una sola vez, guárdalo a buen recaudo):

   ```bash
   keytool -genkey -v -keystore recre-release.jks \
     -keyalg RSA -keysize 2048 -validity 10000 -alias recre
   ```

2. Define un `signingConfig` de release en `app/build.gradle.kts` que lea sus
   valores de `local.properties`/entorno (`RELEASE_STORE_FILE`,
   `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`) y
   asígnalo al `buildTypes.release`.

3. Genera el artefacto:

   ```bash
   ./gradlew assembleRelease     # APK: app/build/outputs/apk/release/
   ./gradlew bundleRelease       # AAB (recomendado para Play Store)
   ```

### 3.4. Distribución

- **APK**: reparte el `.apk` directamente (instalación lateral) para la beta.
- **AAB**: súbelo a **Google Play Console** (interno/cerrado/producción).

---

## Checklist de release

- [ ] `supabase db push` aplicado sin errores en el proyecto de producción.
- [ ] Secretos `RESEND_API_KEY` y `RESEND_FROM` configurados.
- [ ] Todas las Edge Functions desplegadas.
- [ ] Auth con Email habilitado y Site/Redirect URLs apuntando a la web.
- [ ] Web desplegada con sus dos `NEXT_PUBLIC_*` y URL añadida a Supabase Auth.
- [ ] APK/AAB generado con las credenciales del proyecto correcto.
- [ ] Smoke test: login web + login app + una recaudación de prueba.

---

Referencias del repo: `supabase/README.md`, `web/README.md`,
`android/README.md`, `.kiro/steering/architecture.md` y
`.kiro/specs/recre/design.md`.
