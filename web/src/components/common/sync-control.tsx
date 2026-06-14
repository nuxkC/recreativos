"use client";

// SyncControl (C-SYNC-01) — indicador de estado de sincronización offline-first.
// Reemplaza al banner stale rojo (T-1): "stale" es WARNING ámbar, nunca danger.
// El error real de red vive FUERA del átomo (toast/sonner danger); aquí solo se
// refleja idle / syncing / stale / pending. El estado NUNCA se comunica solo por
// color: cada estado lleva icono + texto (aria-label + título visible/label).

import * as React from "react";
import { RefreshCw } from "lucide-react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/**
 * Estados de la cola de sincronización. Se derivan de la última sync exitosa y
 * de las mutaciones encoladas (no se recalcula tiempo ad hoc: `staleSince` llega
 * ya formateado desde el helper de tiempo del dominio).
 */
export type SyncStatus = "idle" | "syncing" | "stale" | "pending" | "disabled";

export type SyncVariant = "compact" | "expanded";

export interface SyncControlProps {
  /** Estado actual de la sincronización. */
  status: SyncStatus;
  /** Antigüedad de la última sync ya formateada (ej. "3 h"). Solo aplica a stale. */
  staleSince?: string;
  /** Nº de cambios encolados sin subir. Relevante en pending/stale. */
  pendientes?: number;
  /** Metadato secundario ya formateado (ej. "Última: hace 5 min"). */
  ultimaSync?: string;
  /** Forma de presentación: icon-button (topbar) o card-fila (pantalla Sincronizar). */
  variant?: SyncVariant;
  /** Dispara la sincronización manual. */
  onSync: () => void;
  className?: string;
}

/** Label de estado (línea principal); también es el accesible del icon-button. */
function labelEstado(status: SyncStatus, staleSince?: string, pendientes = 0): string {
  switch (status) {
    case "syncing":
      return "Sincronizando…";
    case "stale":
      return staleSince ? `Sin sincronizar hace ${staleSince}` : "Sin sincronizar";
    case "pending":
      return `${pendientes} ${pendientes === 1 ? "cambio pendiente" : "cambios pendientes"}`;
    case "disabled":
    case "idle":
    default:
      return "Sincronizado";
  }
}

/** Metadato secundario (línea 2 de la forma expandida), siempre en muted. */
function metadato(
  status: SyncStatus,
  pendientes: number,
  ultimaSync?: string,
  staleSince?: string,
): string {
  if (status === "stale") {
    return pendientes > 0
      ? `${pendientes} ${pendientes === 1 ? "cambio en cola" : "cambios en cola"}`
      : staleSince
        ? `Sin sincronizar hace ${staleSince}`
        : "Sin sincronizar";
  }
  if (status === "pending" && pendientes > 0) {
    return `${pendientes} ${pendientes === 1 ? "cambio en cola" : "cambios en cola"}`;
  }
  return ultimaSync ?? "Al día";
}

/** Color del icono por estado: idle/pending=muted, syncing=primary, stale=warning. */
function tintIcono(status: SyncStatus): string {
  return cn(
    status === "stale" && "text-warning",
    status === "syncing" && "text-primary",
    (status === "idle" || status === "pending") && "text-muted-foreground",
    // disabled: muted al 40% (≈ 38% del spec) sin halo ni respuesta.
    status === "disabled" && "text-muted-foreground/40",
  );
}

/**
 * Icono ↻ con su tinte y rotación. El giro solo bajo motion-safe (respeta
 * prefers-reduced-motion): con reduced-motion el icono queda estático en primary.
 */
function IconoSync({ status }: { status: SyncStatus }) {
  return (
    <RefreshCw
      aria-hidden="true"
      className={cn(
        "size-5 transition-colors duration-150",
        tintIcono(status),
        // `spin` es la keyframe por defecto de Tailwind; aquí 180ms/vuelta, loop.
        status === "syncing" && "motion-safe:animate-[spin_180ms_linear_infinite]",
      )}
    />
  );
}

/**
 * Dot-badge de estado en la esquina superior-derecha. Solo en stale (warning,
 * con pulse lento) o con pendientes en cola sin estar stale (info, estático).
 * No aporta información exclusiva: el estado ya está en la label (a11y).
 */
function DotBadge({ status, pendientes }: { status: SyncStatus; pendientes: number }) {
  if (status === "stale") {
    return (
      <span
        aria-hidden="true"
        className="absolute right-0.5 top-0.5 size-2 rounded-full bg-warning ring-2 ring-background motion-safe:animate-pulse"
      />
    );
  }
  if (status === "pending" && pendientes > 0) {
    return (
      <span
        aria-hidden="true"
        className="absolute right-0.5 top-0.5 size-2 rounded-full bg-info ring-2 ring-background"
      />
    );
  }
  return null;
}

/**
 * SyncControl — un átomo, dos formas (`compact` | `expanded`) gobernadas por el
 * enum de estado. El control sigue siendo focusable durante loading (solo se
 * inhabilita el re-disparo), nunca se pinta de rojo, y anuncia el cambio de
 * estado vía aria-live="polite".
 */
export function SyncControl({
  status,
  staleSince,
  pendientes = 0,
  ultimaSync,
  variant = "compact",
  onSync,
  className,
}: SyncControlProps) {
  const label = labelEstado(status, staleSince, pendientes);
  const meta = metadato(status, pendientes, ultimaSync, staleSince);
  // No re-disparable mientras gira o si está inhabilitado; sigue siendo focusable.
  const noDisparable = status === "syncing" || status === "disabled";

  if (variant === "compact") {
    return (
      <Button
        type="button"
        variant="ghost"
        size="icon"
        // `title` da el hint on-hover sin depender de @radix-ui/react-tooltip (no instalado).
        title={label}
        aria-label={label}
        aria-live="polite"
        // aria-disabled (no `disabled` nativo): mantiene el botón en el tab order y
        // focusable mientras sincroniza/sin-sesión; la acción se inhibe en el onClick.
        aria-disabled={noDisparable}
        onClick={noDisparable ? undefined : onSync}
        className={cn(
          // size-9 = 36px visual; el ::after expande el hit-area a ≥44px (touch/puntero).
          "relative size-9 after:absolute after:left-1/2 after:top-1/2 after:size-11 after:-translate-x-1/2 after:-translate-y-1/2 after:content-['']",
          // el icono mono de sync es 20px; gana al [&_svg]:size-4 base del Button.
          "[&_svg]:size-5",
          "hover:bg-secondary focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2",
          className,
        )}
      >
        <IconoSync status={status} />
        <DotBadge status={status} pendientes={pendientes} />
      </Button>
    );
  }

  // Forma expandida: card surface-1 con border 1px (warning si stale). SIN sombra:
  // la elevación es por borde (light) / luminancia (dark). NO destructive/rojo.
  return (
    <div
      role="status"
      aria-live="polite"
      className={cn(
        "flex min-h-12 items-center gap-3 rounded-xl border bg-surface-1 p-grid-4",
        status === "stale" ? "border-warning" : "border-border",
        className,
      )}
    >
      <IconoSync status={status} />
      <div className="flex-1">
        <p
          className={cn(
            "text-body font-semibold",
            status === "stale" ? "text-warning" : "text-foreground",
          )}
        >
          {label}
        </p>
        {/* La cifra de tiempo/cola va en mono tabular (.num-tabular), nunca proporcional. */}
        <p className="num-tabular text-caption text-muted-foreground">{meta}</p>
      </div>
      <Button
        type="button"
        variant="secondary"
        className="min-h-11"
        aria-disabled={noDisparable}
        onClick={noDisparable ? undefined : onSync}
      >
        Sincronizar ahora
      </Button>
    </div>
  );
}
