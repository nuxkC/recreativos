"use client";

import { useId, useMemo } from "react";
import { Area, AreaChart, ReferenceLine, ResponsiveContainer } from "recharts";

import { cn } from "@/lib/utils";

/**
 * Rol semántico de la serie. `primary` (acento de marca, default) y `neutral`
 * (muted) son los habituales. `money`/`alert` SOLO cuando la serie significa
 * LITERALMENTE dinero positivo o alerta (avería/descuadre), nunca decoración:
 * el color es portador de significado y debe coincidir con la cifra/delta del host.
 */
export type SparkRole = "primary" | "money" | "alert" | "neutral";

/**
 * Color de pintura por rol → token CSS. Recharts necesita un valor de color
 * concreto (no una clase Tailwind), así que consumimos la `var(--rol)` directa
 * (mismo token que la cifra del host). El fill del rol se usa SOLO como línea
 * fina/área decorativa, jamás como texto (las variantes -text son para texto).
 */
const ROLE_COLOR: Record<SparkRole, string> = {
  primary: "var(--primary)",
  money: "var(--success)",
  alert: "var(--danger)",
  neutral: "var(--muted-foreground)",
};

export interface SparklineProps {
  /**
   * Serie numérica SOLO para coordenadas de pintura (~10-20 puntos, default ~14).
   * NUNCA es la fuente de una cifra mostrada: el importe real lo presenta
   * <MoneyText/>/KPI del host desde el Decimal exacto del servidor (SSOT). Aquí
   * `number` es lícito porque solo posiciona píxeles, no se muestra como dinero.
   */
  data: number[];
  /** Rol semántico de la serie (color). Default `primary` (acento de marca). */
  role?: SparkRole;
  /** Alto en px. 40 compacto · 64 dominante (E.2 2x2) · 32-48 fila analítica H.1. */
  height?: number;
  /** Área de relleno con gradiente 14%→0%. Se omite si la serie es plana. */
  showArea?: boolean;
  /** Punto final que ancla el último valor (diám. 3px, mismo color que la línea). */
  showEndDot?: boolean;
  /**
   * Dibuja una línea de referencia en 0 cuando la serie cruza el cero. Útil en
   * saldos/deltas (deuda mejorando vs. empeorando) para no insinuar tendencias
   * falsas: sin baseline, [-50,-40,-30] y [30,40,50] se verían idénticas.
   */
  baseline?: boolean;
  /**
   * Resumen accesible de la tendencia (p. ej. "Recaudación: tendencia al alza,
   * 14 semanas"). Si se pasa, el sparkline deja de ser puramente decorativo y se
   * anuncia con `role="img"`. Si se omite, es `aria-hidden` (decorativo y
   * redundante; el host comunica el dato/tendencia con texto+icono).
   */
  ariaLabel?: string;
  className?: string;
}

/**
 * Mini-gráfica de tendencia inline: comprime una serie temporal (~10-20 puntos)
 * en una línea + área sutil, sin ejes/grid/labels, junto a una cifra-héroe.
 *
 * Decisiones de diseño (adaptadas de la spec F3-A-SPARKLINE):
 * - Recharts `AreaChart` responsive: el ancho lo da el contenedor (100% del
 *   cuerpo del host); sin eje, grid ni tooltip — el carácter es "mini".
 * - SIN animación de dibujado: la spec original anima el path izq→der, pero esa
 *   animación se dibuja a saltos por vértice (engañosa) y el átomo es redundante
 *   con la cifra; `isAnimationActive={false}` también respeta reduced-motion sin
 *   depender de clases por estado.
 * - Serie plana → línea muted (sin tendencia) y sin área.
 * - El área nunca supera 14% de alpha para no competir con la cifra adyacente.
 */
export function Sparkline({
  data,
  role = "primary",
  height = 40,
  showArea = true,
  showEndDot = true,
  baseline = false,
  ariaLabel,
  className,
}: SparklineProps) {
  const gradientId = useId();

  // noUncheckedIndexedAccess: trabajamos con valores ya filtrados y defaults.
  const { min, max, flat, color, crossesZero, points } = useMemo(() => {
    const values = data.filter((v) => Number.isFinite(v));
    const lo = values.length > 0 ? Math.min(...values) : 0;
    const hi = values.length > 0 ? Math.max(...values) : 0;
    const isFlat = hi - lo < 1e-9;
    return {
      min: lo,
      max: hi,
      flat: isFlat,
      // Serie plana → muted: "sin tendencia" se pinta neutro aunque el rol sea
      // money/alert (el host conserva el significado en la cifra/chip adyacente).
      color: isFlat ? ROLE_COLOR.neutral : ROLE_COLOR[role],
      crossesZero: lo < 0 && hi > 0,
      // Recharts consume objetos: índice como X implícita, `v` como Y.
      points: values.map((v, i) => ({ i, v })),
    };
  }, [data, role]);

  // Estado vacío: <2 puntos no forman tendencia. El host muestra '0'/'—'.
  if (points.length < 2) {
    // Reserva el alto para no provocar saltos de layout; aria-hidden siempre.
    return <div style={{ height }} className={className} aria-hidden="true" />;
  }

  // Dominio Y: si `baseline` y la serie no cruza 0, ancla la base en 0 para que
  // la magnitud (no solo la pendiente) sea legible. Si cruza 0, [min, max] y la
  // ReferenceLine en 0 dan el marco de lectura correcto.
  const yDomain: [number, number] =
    baseline && !crossesZero ? [Math.min(0, min), Math.max(0, max)] : [min, max];

  const decorative = !ariaLabel;

  return (
    <div
      className={cn("w-full", className)}
      style={{ height }}
      // Decorativo (redundante con la cifra del host) salvo que el host pase un
      // resumen accesible; entonces es una imagen con nombre (no solo-color: el
      // ariaLabel describe la TENDENCIA en texto, no el color).
      {...(decorative
        ? { "aria-hidden": true as const }
        : { role: "img", "aria-label": ariaLabel })}
    >
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart
          data={points}
          // Padding vertical 2px: evita recortar el stroke/dot en máx/mín.
          margin={{ top: 2, right: showEndDot ? 3 : 0, bottom: 2, left: 0 }}
        >
          <defs>
            <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
              {/* Área 14%→0%: nunca supera 14% para no competir con la cifra. */}
              <stop offset="0%" stopColor={color} stopOpacity={0.14} />
              <stop offset="100%" stopColor={color} stopOpacity={0} />
            </linearGradient>
          </defs>
          {baseline && crossesZero ? (
            <ReferenceLine y={0} stroke="var(--border)" strokeWidth={1} />
          ) : null}
          <Area
            type="monotone"
            dataKey="v"
            // Dominio fijado por nuestro cálculo (recharts lo lee del eje, pero
            // sin <YAxis> usa el rango de datos; baseline lo amplía vía `min`).
            baseValue={yDomain[0]}
            stroke={color}
            strokeWidth={1.5}
            strokeLinecap="round"
            strokeLinejoin="round"
            fill={showArea && !flat ? `url(#${gradientId})` : "none"}
            fillOpacity={1}
            // SIN animación de dibujado: redundante y engañosa (saltos por
            // vértice); también evita movimiento bajo reduced-motion.
            isAnimationActive={false}
            activeDot={false}
            // Punto final 3px que ancla el último valor; sin marcadores intermedios.
            // Render-prop: recharts exige un elemento SVG por punto, así que los
            // intermedios se devuelven como nodo vacío.
            dot={
              showEndDot
                ? (props: { cx?: number; cy?: number; index?: number }) =>
                    props.index === points.length - 1 &&
                    typeof props.cx === "number" &&
                    typeof props.cy === "number" ? (
                      <circle
                        key={`end-${props.index}`}
                        cx={props.cx}
                        cy={props.cy}
                        r={3}
                        fill={color}
                      />
                    ) : (
                      <g key={`dot-${props.index ?? "x"}`} />
                    )
                : false
            }
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
