import { getRequestConfig } from "next-intl/server";

/**
 * Configuración de next-intl para el proyecto.
 *
 * De momento solo se EXPONE español (`es`). El uso de i18n desde el día 1
 * es una decisión cerrada en `.kiro/steering/conventions.md`: aunque solo
 * se sirva un idioma, todos los textos viajan por claves para no tener que
 * refactorizar cuando se añadan más locales.
 *
 * La infraestructura queda preparada para multi-idioma: para añadir un
 * locale basta con (1) crear `messages/<locale>.json`, (2) añadirlo a
 * `LOCALES` y (3) resolver el locale activo en `getRequestConfig` (cookie,
 * cabecera `Accept-Language` o segmento de ruta) usando `resolveLocale`.
 * No hace falta tocar los componentes, que ya consumen las claves vía
 * `useTranslations` / `getTranslations`.
 *
 * Nota: mientras solo haya un locale, `getRequestConfig` NO lee datos de la
 * petición (devuelve la constante `DEFAULT_LOCALE`). Esto permite que las
 * páginas sin estado de servidor se sigan renderizando de forma estática.
 * Al añadir un segundo locale habrá que leer la preferencia del request, lo
 * que opta a renderizado dinámico (comportamiento esperado de next-intl).
 */
export const LOCALES = ["es"] as const;

export type Locale = (typeof LOCALES)[number];

export const DEFAULT_LOCALE: Locale = "es";

export const TIME_ZONE = "Europe/Madrid";

/**
 * Normaliza un locale solicitado al conjunto soportado, cayendo al
 * `DEFAULT_LOCALE` si no se reconoce. Preparado para cuando se resuelva el
 * locale desde la petición.
 */
export function resolveLocale(requested: string | undefined | null): Locale {
  return LOCALES.includes(requested as Locale) ? (requested as Locale) : DEFAULT_LOCALE;
}

export default getRequestConfig(async () => {
  const locale = DEFAULT_LOCALE;

  return {
    locale,
    messages: (await import(`./messages/${locale}.json`)).default,
    timeZone: TIME_ZONE,
  };
});
