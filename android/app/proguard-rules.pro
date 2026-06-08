# Reglas Proguard/R8 específicas del módulo `app`.
# Las generales vienen de los archivos por defecto de Android.

# Mantener clases serializables de Kotlinx Serialization para reflexión.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Hilt — generado automáticamente; suele bastar con los defaults.

# Supabase y Ktor: el shrinker se queja de clases internas no usadas.
-dontwarn io.netty.**
-dontwarn javax.naming.**
