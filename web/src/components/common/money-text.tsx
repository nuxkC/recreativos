import type { ReactNode } from "react";

import { eurAriaLabel, splitEur } from "@/lib/money/format";
import { cn } from "@/lib/utils";

type MoneyTextSize = "kpi" | "inline" | "caption";

/**
 * Color del DÍGITO por tono. Los separadores («.», «,») y el «€» van SIEMPRE en
 * muted; el signo «−» hereda el color del dígito. Las variantes de rol usan los
 * tokens `-text` (oscurecidos, AA verificado), NUNCA el fill pleno (falla AA).
 */
const toneDigitClass = {
  neutral: "text-foreground",
  success: "text-success-text",
  danger: "text-danger-text",
  warning: "text-warning-text",
} as const;

/** Tamaños semánticos → utilidades mono+tabular de globals.css. */
const sizeClass: Record<MoneyTextSize, string> = {
  kpi: "text-importe", // mono 36/40/600 + tnum
  inline: "text-cifra", // mono 16/24/500 + tnum
  caption: "text-cifra-caption", // mono 12/16/500 + tnum
};

type MoneyTextBase = {
  /** Importe como string decimal del servidor (SSOT). NUNCA number/float. */
  value: string | null | undefined;
  size?: MoneyTextSize;
  /** Cifra dominante (neto, parte_empresa): peso 600 por jerarquía, no por color. */
  bold?: boolean;
  /** Solo KPI: atenúa los decimales a muted para jerarquía óptica. */
  decimalsMuted?: boolean;
  /** Sufijo de unidad en muted («/sem», «neto»), fuera del bloque tabular. */
  suffix?: string;
  className?: string;
};

/**
 * Tono: `neutral` por defecto (dígito en foreground). Cualquier tono de rol
 * EXIGE icono — estado nunca solo-color — y el tipo lo fuerza (unión
 * discriminada): no se puede pintar success/danger/warning sin icono.
 */
type MoneyTextTone =
  | { tone?: "neutral"; icon?: never }
  | { tone: "success" | "danger" | "warning"; icon: ReactNode };

export type MoneyTextProps = MoneyTextBase & MoneyTextTone;

/**
 * Primitivo de presentación de toda cifra económica en Recre. Renderiza un
 * importe money-safe (Geist Mono tabular, dígitos en foreground, «€» y
 * separadores en muted, es-ES) en tres tamaños. Centraliza la regla «verde solo
 * dinero positivo / rojo solo descuadre»: ningún consumidor tiñe importes por su
 * cuenta. No recalcula nada: solo presenta lo que devolvió el servidor.
 */
export function MoneyText({
  value,
  size = "inline",
  bold = false,
  decimalsMuted = false,
  suffix,
  className,
  ...tone
}: MoneyTextProps) {
  const role = tone.tone ?? "neutral";
  const icon = "icon" in tone ? tone.icon : null;
  const f = splitEur(value);

  // Placeholder muted: NUNCA mostrar «0,00 €» como si fuera dato real.
  if (f.invalid) {
    return <span className={cn(sizeClass[size], "text-muted-foreground", className)}>—</span>;
  }

  // Separadores de miles del entero: «.» en muted (aria-hidden: lo cubre el
  // aria-label), dígitos en el color del dígito.
  const integerNodes = f.integer.split("").map((ch, i) =>
    ch === "." ? (
      <span key={i} aria-hidden className="text-muted-foreground">
        .
      </span>
    ) : (
      <span key={i}>{ch}</span>
    ),
  );

  return (
    <span
      aria-label={eurAriaLabel(value)}
      className={cn(
        "inline-flex items-center whitespace-nowrap",
        sizeClass[size],
        toneDigitClass[role],
        bold && "font-semibold",
        className,
      )}
    >
      {role !== "neutral" && icon ? (
        <span aria-hidden className="mr-1 inline-flex shrink-0 items-center [&_svg]:size-[1em]">
          {icon}
        </span>
      ) : null}
      {/* Signo «−» pegado al primer dígito; NO aria-hidden (lo cubre el aria-label). */}
      {f.negative ? <span>−</span> : null}
      {integerNodes}
      <span aria-hidden className="text-muted-foreground">
        ,
      </span>
      <span className={cn(decimalsMuted && size === "kpi" && "text-muted-foreground")}>
        {f.decimals}
      </span>
      {/* «€» fuera del bloque tabular, muted, separado 0.25rem (aria-hidden). */}
      <span aria-hidden className="ml-1 text-muted-foreground">
        €
      </span>
      {suffix ? (
        <span aria-hidden className="ml-1 text-muted-foreground">
          {suffix}
        </span>
      ) : null}
    </span>
  );
}
