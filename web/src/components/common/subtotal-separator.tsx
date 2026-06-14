import type { ReactNode } from "react";

import { Separator } from "@/components/ui/separator";
import { MoneyText } from "@/components/common/money-text";
import { cn } from "@/lib/utils";

/**
 * Divisor/separador de subtotal. Línea 1px (border-border) que agrupa o divide
 * contenido en listas densas o bloques.
 *
 * Tres variantes:
 * 1. Plana (sin etiqueta): solo línea horizontal edge-to-edge o con margen.
 * 2. Con label centrado: etiqueta muted + dos líneas simétricas (flex:1 cada una).
 * 3. Con cifra: label muted + MoneyText neutro (foreground) + líneas simétricas.
 *
 * PROHIBIDO usar colores de rol semántico (success/danger/warning/info). El
 * separador es estructura pura, nunca porta semántica de estado.
 *
 * Tokens consumidos:
 * - border-border (línea 1px)
 * - text-muted-foreground (etiqueta, label)
 * - text-foreground (cifra vía MoneyText neutro)
 * - Tailwind spacing: space-2/3/4 (margen), Geist Mono tabular (cifra)
 */

export type SubtotalSeparatorVariant = "plain" | "labeled" | "labeled-with-amount";

export interface SubtotalSeparatorProps {
  /** Variante: plain (línea sola), labeled (label centrado), labeled-with-amount (label + cifra). */
  variant?: SubtotalSeparatorVariant;

  /** Etiqueta descriptiva (ej. 'Subtotal billetes', 'o introducir a mano'). Solo con variant="labeled*". */
  label?: string;

  /** Importe como string decimal (ej. '1000.00'). Solo con variant="labeled-with-amount". */
  amount?: string;

  /** Margen vertical de la regla plana (space-2/3/4). Default: space-3 (12px). */
  verticalSpacing?: "space-2" | "space-3" | "space-4";

  /** Clases adicionales para el contenedor. */
  className?: string;

  /** Clases adicionales para la línea (solo plain). */
  lineClassName?: string;
}

/**
 * Renderiza un divisor simple (horizontal, 1px border-border).
 * Reutilizable en listas densas, cards, etc.
 */
function RecreDivider({
  className,
  lineClassName,
}: {
  className?: string;
  lineClassName?: string;
}) {
  return (
    <div className={cn("w-full", className)}>
      <Separator
        orientation="horizontal"
        decorative
        className={cn("h-[1px] bg-border", lineClassName)}
      />
    </div>
  );
}

/**
 * Renderiza un divisor con etiqueta/cifra centrada.
 * Las dos líneas (izquierda/derecha) son simétricas (flex:1 cada una),
 * con padding horizontal space-3 (12px) a ambos lados del label/cifra.
 * El label es muted-foreground; la cifra es neutral (foreground).
 */
function LabeledDivider({
  label,
  amount,
  className,
}: {
  label: string;
  amount?: string;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "flex w-full items-center gap-0",
        "h-fit", // altura intrínseca del label (caption 13sp = ~16px web)
        className,
      )}
      role="separator"
      aria-label={
        amount
          ? `${label}: ${amount}€` // aria-label accesible para screen reader
          : label
      }
    >
      {/* Línea izquierda — flex:1 para reparto simétrico. */}
      <Separator
        orientation="horizontal"
        decorative
        className="h-[1px] min-w-[8px] flex-1 bg-border"
      />

      {/* Label + cifra centrados, padding horizontal space-3 (12px). */}
      <div className="flex flex-col items-center gap-0 px-3">
        {/* Label: muted-foreground, caption size (13sp/500 + mono tabular). */}
        <div className="text-cifra-caption font-medium text-muted-foreground">{label}</div>

        {/* Cifra (solo si amount está presente): MoneyText neutro, caption size. */}
        {amount && <MoneyText value={amount} size="caption" tone="neutral" />}
      </div>

      {/* Línea derecha — flex:1 para reparto simétrico. */}
      <Separator
        orientation="horizontal"
        decorative
        className="h-[1px] min-w-[8px] flex-1 bg-border"
      />
    </div>
  );
}

/**
 * Divisor/separador de subtotal en su forma completa.
 * Componente principal que delega a RecreDivider o LabeledDivider según variant.
 */
export function SubtotalSeparator({
  variant = "plain",
  label,
  amount,
  verticalSpacing = "space-3",
  className,
  lineClassName,
}: SubtotalSeparatorProps) {
  // Validación: label/amount solo con variantes labeled
  if ((label || amount) && variant === "plain") {
    console.warn(
      "SubtotalSeparator: label/amount provided but variant is 'plain'. Ignoring label/amount.",
    );
  }

  if (variant === "plain") {
    return (
      <div
        className={cn(
          "w-full",
          verticalSpacing && `my-${verticalSpacing.split("-")[1]}`,
          className,
        )}
      >
        <RecreDivider lineClassName={lineClassName} />
      </div>
    );
  }

  // variant === "labeled" || "labeled-with-amount"
  if (!label) {
    console.warn(
      `SubtotalSeparator: variant="${variant}" requires label prop. Rendering as plain instead.`,
    );
    return (
      <div
        className={cn(
          "w-full",
          verticalSpacing && `my-${verticalSpacing.split("-")[1]}`,
          className,
        )}
      >
        <RecreDivider lineClassName={lineClassName} />
      </div>
    );
  }

  return (
    <div
      className={cn("w-full", verticalSpacing && `my-${verticalSpacing.split("-")[1]}`, className)}
    >
      <LabeledDivider
        label={label}
        amount={variant === "labeled-with-amount" ? amount : undefined}
      />
    </div>
  );
}
