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

## Estado

Inicializado en T-05 con login funcional contra Supabase Auth. Las pantallas reales (lista de locales, detalle, recaudación, gestión, etc.) se construyen en T-50..T-71.
