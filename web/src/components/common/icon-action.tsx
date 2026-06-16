"use client";

import { Mail, MapPin, Phone } from "lucide-react";
import { cn } from "@/lib/utils";

/**
 * Tipo de acción de contacto que dispara una intención del SO.
 */
export enum TipoAccionContacto {
  LLAMAR = "llamar",
  MAPA = "mapa",
  EMAIL = "email",
}

export interface IconActionProps {
  /**
   * Tipo de acción: LLAMAR (tel:), MAPA (geo:), EMAIL (mailto:).
   */
  tipo: TipoAccionContacto;

  /**
   * Valor sin mostrar: número de teléfono, dirección o email.
   * NO se visualiza (es PII); solo se usa para construir el href.
   */
  valor: string;

  /**
   * Nombre de la entidad destino (p.ej. "Bar Pepe").
   * OBLIGATORIO: el aria-label DEBE nombrar la entidad para contexto accesible.
   * NUNCA genéricos como "Llamar"; siempre "Llamar a {nombreEntidad}".
   */
  nombreEntidad: string;

  /**
   * Variante visual: tonal (fondo surface-2) vs plain (sin fondo, solo icono).
   * Default false (plain, neutral).
   */
  tonal?: boolean;

  /**
   * Classes adicionales (para override puntual, NO la norma).
   */
  className?: string;

  /**
   * Si la acción está disponible (true) o deshabilitada temporalmente (false).
   * Disabled: glifo muted @0.5, sin pointer, sin ripple. Rarísimo.
   * Caso normal de 'dato ausente' = NO renderizar el componente, no disabled.
   */
  enabled?: boolean;
}

/**
 * Configuración por tipo: icono, href builder, etiqueta accesible.
 */
const CONFIG: Record<
  TipoAccionContacto,
  {
    Icon: typeof Phone;
    href: (valor: string) => string;
    label: (nombreEntidad: string) => string;
  }
> = {
  [TipoAccionContacto.LLAMAR]: {
    Icon: Phone,
    href: (v) => `tel:${v}`,
    label: (n) => `Llamar a ${n}`,
  },
  [TipoAccionContacto.MAPA]: {
    Icon: MapPin,
    href: (v) => `geo:${v}`,
    label: (n) => `Cómo llegar a ${n}`,
  },
  [TipoAccionContacto.EMAIL]: {
    Icon: Mail,
    href: (v) => `mailto:${v}`,
    label: (n) => `Enviar email a ${n}`,
  },
};

/**
 * IconAction: botón-icono contextual que dispara una intención del SO
 * sobre datos de contacto (tel:, geo:, mailto:).
 *
 * Anatomía:
 * - Caja visual cuadrada (web: 36×36px visual, hit-area ≥44px vía before:inset-[-4px])
 * - Glifo icono centrado, 20px (Phone, MapPin, Mail desde lucide-react)
 * - Color por defecto NEUTRO (muted foreground), NO marca ni color de rol
 *   porque marcar un teléfono no gasta presupuesto de acento ≤10%
 *   ni es dinero ni error ni pendiente.
 *
 * Estados:
 * - default/idle: glifo muted sobre surface-1 (plain) o surface-2 (tonal)
 * - hover: glifo sube a foreground, aparece fondo surface-2 si plain
 * - focus-visible: anillo ring 2px offset 2px (≥3:1 contraste)
 * - disabled: glifo muted @0.5, sin pointer, sin ripple (raro)
 *
 * A11y:
 * - aria-label OBLIGATORIO: nombra la entidad, NO genérico "Llamar"
 * - href es el mecanismo nativo (no JS onClick)
 * - disabled: sin pointer-events, sin hover effect
 *
 * Contraste verificado (Fase 3):
 * - muted #646B76 sobre surface-1 #FFFFFF = 5.38:1 (AA ✓)
 * - muted #646B76 sobre surface-2 #F4F6F8 = ~5.0:1 (AA ✓)
 * - foreground #11161B sobre surface-2 = ≥7:1 (AA+ ✓)
 *
 * Color NUNCA cambia a success/danger/info por la acción: es estado
 * del botón, no de la transacción. Si el intent falla (sin dialer/cliente
 * de correo), el error se comunica por Snackbar/Toast neutro, no aquí.
 */
export function IconAction({
  tipo,
  valor,
  nombreEntidad,
  tonal = false,
  enabled = true,
  className,
}: IconActionProps) {
  const { Icon, href, label } = CONFIG[tipo];

  return (
    <a
      href={href(valor)}
      aria-label={label(nombreEntidad)}
      aria-disabled={!enabled}
      className={cn(
        // Caja visual 36×36px centrada (web spec)
        "relative inline-flex h-9 w-9 items-center justify-center rounded-lg",
        // Hit-area ≥44px vía pseudo-element before:inset-[-4px]
        // = 36px + 2×4px = 44px (WCAG 2.5.5 target)
        'before:absolute before:-inset-1 before:content-[""]',

        // Color glifo: default muted, hover foreground
        "text-muted-foreground transition-all duration-150",
        "hover:text-foreground",

        // Variante: tonal (fondo surface-2) vs plain (sin fondo)
        tonal && "bg-surface-2 hover:bg-surface-2",
        !tonal && "hover:bg-surface-2",

        // Foco visible: anillo ring 2px offset 2px
        // ring == primary (#0E7490), contraste ≥3:1 contra fondo
        "focus-visible:outline-hidden",
        "focus-visible:ring-2 focus-visible:ring-offset-2",
        "focus-visible:ring-ring focus-visible:ring-offset-background",

        // Reduced motion: sin transiciones
        "motion-reduce:transition-none",

        // Disabled: opacidad reducida, sin interacción
        !enabled && ["opacity-50", "pointer-events-none", "cursor-not-allowed"],
        enabled && ["cursor-pointer"],

        className,
      )}
    >
      {/* Glifo icono: 20px, aria-hidden porque el label ya nombra la acción */}
      <Icon aria-hidden="true" className="size-5" />
    </a>
  );
}
