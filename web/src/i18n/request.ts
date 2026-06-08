import { getRequestConfig } from "next-intl/server";

/**
 * Configuración de next-intl para el proyecto.
 *
 * De momento solo soportamos español (`es`). El uso de i18n desde el día 1
 * es una decisión cerrada en `.kiro/steering/conventions.md`: aunque solo
 * se sirva un idioma, todos los textos viajan por claves para no tener que
 * refactorizar cuando se añadan más locales.
 */
export const DEFAULT_LOCALE = "es" as const;

export default getRequestConfig(async () => {
  return {
    locale: DEFAULT_LOCALE,
    messages: (await import(`./messages/${DEFAULT_LOCALE}.json`)).default,
    timeZone: "Europe/Madrid",
  };
});
