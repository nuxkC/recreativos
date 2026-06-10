# Recre — Android

App para técnicos en **Kotlin** + **Jetpack Compose** + **Hilt** + **Room** + **WorkManager** + **Supabase Kotlin SDK**.

## Requisitos

- JDK 17 (mínimo) o JDK 21 recomendado
- Android Studio Koala (2024.1) o superior, con Android SDK 35
- Build con Gradle wrapper (incluido)

## Configuración

```bash
cp local.properties.example local.properties
# Rellena SUPABASE_URL y SUPABASE_ANON_KEY con tu proyecto
# Android Studio añadirá sdk.dir automáticamente la primera vez que abras el proyecto
```

## Comandos habituales

```bash
./gradlew assembleDebug         # APK debug
./gradlew installDebug          # APK debug en dispositivo conectado
./gradlew test                  # tests unitarios
./gradlew connectedAndroidTest  # tests instrumentados (necesita emulador o dispositivo)
./gradlew lint                  # Android Lint
./gradlew :app:dependencies     # árbol de dependencias
```

## Estructura

```
android/
  app/
    build.gradle.kts            # config del módulo app, BuildConfig de Supabase, deps
    proguard-rules.pro
    src/main/
      AndroidManifest.xml
      java/com/recre/app/
        RecreApp.kt             # @HiltAndroidApp, WorkManager Configuration.Provider
        MainActivity.kt         # @AndroidEntryPoint, NavHost
        RootViewModel.kt        # observa sesión activa
        core/
          data/
            local/              # Room DB + DAOs (DAOs llegan en T-51)
            repository/         # AuthRepository (T-50, ampliado en futuras tareas)
          util/                 # DomainResult, DomainError
        di/                     # módulos Hilt
        feature/
          auth/                 # LoginScreen + LoginViewModel
          home/                 # placeholder, se reemplaza en T-52
        ui/
          theme/                # Material 3 theme
      res/                      # strings, themes, mipmap, drawable, xml
  gradle/
    libs.versions.toml          # version catalog
    wrapper/
  build.gradle.kts              # plugins root
  settings.gradle.kts
  gradle.properties
  gradlew, gradlew.bat
  local.properties.example
```

## Convenciones

Antes de tocar código lee:

- `.kiro/steering/architecture.md` — Clean Architecture, capas, escalabilidad
- `.kiro/steering/conventions.md` — naming, dinero (BigDecimal), errores, testing

Reglas clave:

- ViewModels exponen `StateFlow<UiState>`. Composables son tontos.
- Repositorios como interfaces en `core/data/repository/`, implementación inyectada vía Hilt.
- Mappers explícitos: DTO ↔ entidad domain ↔ UI state.
- Sin cálculo de recaudación en cliente: se llama al endpoint `calcular-recaudacion` (Edge Function).
- Sin importes entre features (`feature/a` no importa de `feature/b`).
- Dinero con `java.math.BigDecimal` (half-up al céntimo). Nunca `Float`/`Double`.

## Notificaciones push (FCM) — T-101

La app recibe notificaciones push vía Firebase Cloud Messaging. El evento
inicial es la **resolución de un conflicto** de recaudación (sustituye al
email como canal principal; el email queda como complemento). El transporte
está preparado para añadir más eventos sin tocar la capa de red.

### Qué hay implementado

- `core/push/RecreMessagingService.kt` — `FirebaseMessagingService` que recibe
  el token (`onNewToken`) y los mensajes (`onMessageReceived`).
- `core/push/PushNotifier.kt` — canal de notificaciones (`conflictos`) y
  notificación con deep-link a "Mis recaudaciones" (detalle).
- `core/push/PushTokenManager.kt` — registra el token FCM en el backend
  (`registrar-device-token`) cuando hay empresa activa. **No-op** si Firebase
  no está inicializado.
- Permiso `POST_NOTIFICATIONS` (Android 13+) solicitado desde `MainActivity`.

### Configuración del proyecto Firebase (requerido para que funcione)

El fichero `app/google-services.json` es **específico de cada proyecto
Firebase** y **NO se versiona** (está en `.gitignore`). Sin él, la app
compila y funciona con normalidad, pero las push quedan inertes.

Pasos para activarlas:

1. Crea un proyecto en la [consola de Firebase](https://console.firebase.google.com/)
   y registra la app Android con el `applicationId` `com.recre.app`
   (y `com.recre.app.debug` para el build debug).
2. Descarga el `google-services.json` y colócalo en `android/app/google-services.json`.
3. Reconstruye. El plugin `com.google.gms.google-services` se aplica
   **automáticamente** solo si el fichero existe (ver `app/build.gradle.kts`),
   así que el build de CI sin fichero no se rompe.

> Decisión (T-101): el plugin `google-services` se aplica de forma
> **condicional**. Aplicarlo sin el JSON aborta el build con
> "File google-services.json is missing"; aplicarlo solo cuando el fichero
> existe permite que CI compile la app y a la vez que la mensajería funcione
> en cuanto se añada el proyecto Firebase real.

### Backend asociado (supabase/)

- Tabla `device_token` (migración `20260521000000_create_device_token_table.sql`).
- Edge Functions `registrar-device-token` y `enviar-push`.
- Credenciales FCM como secrets del proyecto Supabase (service account):

  ```bash
  supabase secrets set FCM_PROJECT_ID="tu-proyecto"
  supabase secrets set FCM_CLIENT_EMAIL="...@tu-proyecto.iam.gserviceaccount.com"
  supabase secrets set FCM_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n"
  ```

  Si faltan, `enviar-push` hace *skip* seguro (`push_skipped`), igual que el
  email sin `RESEND_API_KEY`.

## Estado

Inicializado en T-05 con login funcional contra Supabase Auth. Las pantallas reales (lista de locales, detalle, recaudación, gestión, etc.) se construyen en T-50..T-71.
