"use client";

import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

/**
 * Primitiva de Skeleton: placeholder inerte mientras se resuelve petición servidor.
 * Comunica "estoy trayendo datos" sin reflow al cargar contenido real.
 *
 * NO renderiza dinero ni texto real; vive solo en superficies neutras (surface-2).
 * Shimmer (banda de luz) respeta prefers-reduced-motion.
 * Cero acento primary: es carga, no estado (success/danger/warning/info).
 */

const skeletonVariants = cva(
  // Clase base: relleno surface-2 + shimmer suave
  "rounded-md bg-surface-2",
  {
    variants: {
      shape: {
        rect: "rounded-md",
        circle: "rounded-full",
      },
      animate: {
        true: "animate-pulse",
        false: "",
      },
    },
    defaultVariants: {
      shape: "rect",
      animate: true,
    },
  },
);

export interface SkeletonProps
  extends React.HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof skeletonVariants> {
  /** Width del placeholder (ej. "w-1/2", "w-24"). */
  className?: string;
  /** Deshabilitar animación (ej. en tests, o si prefers-reduced-motion). */
  animate?: boolean;
}

/**
 * Skeleton primitiva: bloque rect o circle con shimmer.
 * Altura/ancho se pasan vía `style` o `className`.
 *
 * Ejemplo:
 * <Skeleton className="h-3 w-2/5" />
 * <Skeleton className="h-9 w-full" shape="circle" />
 */
export const Skeleton = React.forwardRef<HTMLDivElement, SkeletonProps>(
  ({ className, shape, animate = true, ...props }, ref) => {
    // Respetar prefers-reduced-motion: desactivar shimmer.
    const [reducedMotion, setReducedMotion] = React.useState(false);

    React.useEffect(() => {
      const mediaQuery = window.matchMedia("(prefers-reduced-motion: reduce)");
      setReducedMotion(mediaQuery.matches);

      const handleChange = (e: MediaQueryListEvent) => {
        setReducedMotion(e.matches);
      };

      mediaQuery.addEventListener("change", handleChange);
      return () => mediaQuery.removeEventListener("change", handleChange);
    }, []);

    return (
      <div
        ref={ref}
        className={cn(skeletonVariants({ shape, animate: animate && !reducedMotion }), className)}
        {...props}
      />
    );
  },
);

Skeleton.displayName = "Skeleton";

// ─────────────────────────────────────────────────────────────────────────────
// Variante: SkeletonCard
// Molde de AppCard real mientras carga: borde 1px, radius, padding 16px,
// con líneas de título (60% ancho) y subtítulo (40% ancho).
// ─────────────────────────────────────────────────────────────────────────────

export interface SkeletonCardProps extends React.HTMLAttributes<HTMLDivElement> {
  /** Número de filas extra (además de título y subtítulo). */
  rows?: number;
  /** Deshabilitar animación de shimmer. */
  animate?: boolean;
}

/**
 * SkeletonCard: molde de card mientras carga.
 * Borde 1px (surface-1), radius lg, padding 16px.
 * Dentro: línea de título (60%), línea de subtítulo (40%), líneas opcionales.
 *
 * Ejemplo:
 * <SkeletonCard rows={2} />
 */
export const SkeletonCard = React.forwardRef<HTMLDivElement, SkeletonCardProps>(
  ({ className, rows = 0, animate = true, ...props }, ref) => {
    const [reducedMotion, setReducedMotion] = React.useState(false);

    React.useEffect(() => {
      const mediaQuery = window.matchMedia("(prefers-reduced-motion: reduce)");
      setReducedMotion(mediaQuery.matches);

      const handleChange = (e: MediaQueryListEvent) => {
        setReducedMotion(e.matches);
      };

      mediaQuery.addEventListener("change", handleChange);
      return () => mediaQuery.removeEventListener("change", handleChange);
    }, []);

    return (
      <div
        ref={ref}
        className={cn("rounded-lg border border-border bg-surface-1 p-4", className)}
        {...props}
      >
        {/* Título: 60% ancho, altura de línea (16px ≈ caption 12/16) */}
        <Skeleton className="mb-2 h-4 w-3/5" animate={animate && !reducedMotion} />

        {/* Subtítulo: 40% ancho, altura de línea (13px ≈ caption) */}
        <Skeleton className="mb-4 h-3 w-2/5" animate={animate && !reducedMotion} />

        {/* Filas extras opcionales: ancho variable, espaciadas */}
        {Array.from({ length: rows }).map((_, i) => (
          <Skeleton key={i} className="mb-2 h-3 w-full" animate={animate && !reducedMotion} />
        ))}
      </div>
    );
  },
);

SkeletonCard.displayName = "SkeletonCard";

// ─────────────────────────────────────────────────────────────────────────────
// Variante: SkeletonRow (cabecera de TablaDensa sticky)
// Molde de fila de tabla mientras carga: altura 44px, fondo surface-2.
// ─────────────────────────────────────────────────────────────────────────────

export interface SkeletonRowProps extends React.HTMLAttributes<HTMLDivElement> {
  /** Número de celdas a renderizar. */
  cellCount?: number;
  /** Deshabilitar animación. */
  animate?: boolean;
}

/**
 * SkeletonRow: molde de fila de tabla (TablaDensa).
 * Altura 44px, fondo surface-2, celdas espaciadas.
 *
 * Ejemplo:
 * <SkeletonRow cellCount={4} />
 */
export const SkeletonRow = React.forwardRef<HTMLDivElement, SkeletonRowProps>(
  ({ className, cellCount = 3, animate = true, ...props }, ref) => {
    const [reducedMotion, setReducedMotion] = React.useState(false);

    React.useEffect(() => {
      const mediaQuery = window.matchMedia("(prefers-reduced-motion: reduce)");
      setReducedMotion(mediaQuery.matches);

      const handleChange = (e: MediaQueryListEvent) => {
        setReducedMotion(e.matches);
      };

      mediaQuery.addEventListener("change", handleChange);
      return () => mediaQuery.removeEventListener("change", handleChange);
    }, []);

    return (
      <div
        ref={ref}
        className={cn("flex h-11 items-center gap-3 bg-surface-2", className)}
        {...props}
      >
        {Array.from({ length: cellCount }).map((_, i) => (
          <Skeleton key={i} className="h-3 flex-1" animate={animate && !reducedMotion} />
        ))}
      </div>
    );
  },
);

SkeletonRow.displayName = "SkeletonRow";
