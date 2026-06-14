import { cn } from "@/lib/utils";

type StepIndicatorProps = {
  /** Paso actual (1-indexado). */
  current: number;
  /** Total de pasos. undefined = total desconocido (p. ej. máquina cargando). */
  total?: number;
  /** Sustantivo del flujo: "Paso" (default) o "Máquina", etc. */
  label?: string;
  /** Conector entre actual y total ("Paso 1 de 3"). */
  connector?: string;
  /** numeric = solo "Paso 1 de 3"; bar = añade barra de segmentos (2–6 pasos). */
  variant?: "numeric" | "bar";
  className?: string;
};

/**
 * Progreso de una cadena de pasos en la cabecera de un flujo. La cifra actual va
 * en foreground (ancla de lectura) y el resto en muted (como el € de MoneyText).
 * La barra (variante bar) es la única excepción al acento ≤10%, pero su altura
 * 4px la mantiene mínima. Es de solo lectura (no es touch target). El cambio se
 * anuncia por aria-live polite; los dígitos visibles van aria-hidden.
 */
export function StepIndicator({
  current,
  total,
  label = "Paso",
  connector = "de",
  variant = "numeric",
  className,
}: StepIndicatorProps) {
  const a11y =
    total !== undefined ? `${label} ${current} ${connector} ${total}` : `${label} ${current}`;
  const showBar = variant === "bar" && total !== undefined && total >= 2 && total <= 6;

  return (
    <div
      role="status"
      aria-live="polite"
      aria-label={a11y}
      className={cn("flex flex-col gap-1.5", className)}
    >
      {/* Línea numérica (aria-hidden: la cubre el aria-label). current en foreground. */}
      <span
        aria-hidden
        className="font-mono text-xs font-medium tabular-nums text-muted-foreground"
      >
        {label} <span className="text-foreground">{current}</span>
        {total !== undefined ? ` ${connector} ${total}` : null}
      </span>

      {showBar ? (
        <div aria-hidden className="flex gap-1">
          {Array.from({ length: total }).map((_, i) => (
            <span
              key={i}
              className={cn(
                "h-1 flex-1 rounded-full ease-standard motion-safe:transition-colors motion-safe:duration-150",
                // completado (i<current) o activo → primary; pendiente → track neutro
                i < current ? "bg-primary" : "bg-surface-2 dark:bg-border",
              )}
            />
          ))}
        </div>
      ) : null}
    </div>
  );
}
