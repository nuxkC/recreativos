"use client";

import type { LucideIcon } from "lucide-react";
import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

interface EmptyStateProps {
  /** Icono de dominio (Lucide), grande en muted, decorativo. */
  icon: LucideIcon;
  /** Título: qué falta ("Aún no hay máquinas", "No tienes locales con máquina instalada"). */
  title: string;
  /** Descripción: acción sugerida o porqué. */
  description: string;
  /** Opcional: CTA (si omitido, vacío no accionable). */
  action?: {
    label: string;
    onClick: () => void;
  };
  /** Variante "quitar filtros": CTA ghost en vez de primary (cuando el vacío es por filtro, no inventario real vacío). */
  filtered?: boolean;
  /** true dentro de TablaDensa: padding reducido (py-10 vs py-12, size-10 vs size-12). */
  compact?: boolean;
  /** Clase adicional para el contenedor. */
  className?: string;
}

/**
 * EmptyState · Átomo del design system.
 *
 * Comunica ausencia de datos sin parecer error (NUNCA rojo).
 * Cubre dos semánticas: (a) vacío-por-filtro/sin-datos, (b) inventario realmente vacío.
 *
 * Anatomía: Glifo grande en muted + título + descripción + CTA opcional.
 * - Glifo: 48px (w-12 h-12) o 40px (w-10 h-10) si compact, decorativo (aria-hidden), strokeWidth 1.5.
 * - Título: h2 en foreground, "qué falta".
 * - Descripción: body pequeño en muted-foreground.
 * - CTA: Button default (primary + icono Plus) o ghost si filtered (quitar filtros).
 *
 * Espaciado: gap-4 (glifo↔título), gap-1 (título↔desc), mt-6 (desc↔CTA).
 * Contenedor: transparent, sin superficie propia (se apoya en card/tabla).
 * Animación: fade-in + slide-in desde abajo, respeta prefers-reduced-motion.
 */
export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
  filtered = false,
  compact = false,
  className,
}: EmptyStateProps) {
  return (
    <div
      role="status"
      aria-live="polite"
      className={cn(
        "flex flex-col items-center justify-center px-6 text-center",
        compact ? "py-10" : "py-12",
        "duration-150 motion-safe:animate-in motion-safe:fade-in motion-safe:slide-in-from-bottom-1",
        className,
      )}
    >
      {/* Glifo: grande en muted, sin fondo ni círculo, decorativo. */}
      <Icon
        aria-hidden="true"
        className={cn(compact ? "size-10" : "size-12", "text-muted-foreground")}
        strokeWidth={1.5}
      />

      {/* Título: h2 en foreground, qué falta. */}
      <h2
        className={cn("font-semibold text-foreground", compact ? "mt-3 text-base" : "mt-4 text-lg")}
      >
        {title}
      </h2>

      {/* Descripción: body pequeño en muted, máx ancho sm (448px) para legibilidad. */}
      <p className="mt-1 max-w-sm text-sm text-muted-foreground">{description}</p>

      {/* CTA opcional: primary con Plus (icono) o ghost si filtered. */}
      {action ? (
        <Button onClick={action.onClick} variant={filtered ? "ghost" : "default"} className="mt-6">
          {!filtered ? <Plus className="size-4" aria-hidden /> : null}
          {action.label}
        </Button>
      ) : null}
    </div>
  );
}
