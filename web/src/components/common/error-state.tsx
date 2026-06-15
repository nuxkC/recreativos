"use client";

import * as React from "react";
import { AlertTriangle, CloudOff, Lock, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/**
 * ErrorState — card neutra de reintento (C-ERR-01)
 *
 * Comunica un fallo de carga recuperable (red, timeout, 5xx, permiso transitorio)
 * SIN alarmar. Es NEUTRO (surface-2 + borde) — NUNCA danger/rojo.
 *
 * Distinto de:
 * - EmptyState: ausencia legítima de datos, SIN botón Reintentar.
 * - Peligro (avería/descuadre): esos SÍ usan danger/rojo, no este componente.
 */

export type ErrorCausa = "generica" | "red" | "permiso";
export type ErrorVariante = "inline" | "card" | "page";

export interface ErrorStateProps {
  /** Título corto: "No se pudo cargar", "Sin conexión", "Acceso no disponible" */
  titulo: string;

  /** Texto de apoyo (una línea): "Comprueba tu conexión e inténtalo de nuevo" */
  descripcion: string;

  /** Callback de reintento */
  onReintentar: () => void;

  /** Causa del error — varía el icono (muted siempre) */
  causa?: ErrorCausa;

  /** Layout: inline (banner compacto), card (centrada en vacío), page (pantalla completa) */
  variante?: ErrorVariante;

  /** true = botón deshabilitado, label cambia a "Reintentando…", spinner gira */
  reintentando?: boolean;

  /** Si false, no muestra botón Reintentar (ej: permiso definitivo → accionSecundaria solo) */
  reintentable?: boolean;

  /** Label del botón: "Reintentar" (default) o "Comprobar de nuevo" (SinAcceso) */
  reintentarLabel?: string;

  /** Slot para acción secundaria de texto (link "Ver detalles" / "Salir" en SinAcceso) */
  accionSecundaria?: React.ReactNode;
}

/**
 * Mapeo: causa → icono Lucide, siempre tinte muted (neutro)
 */
const ICONO: Record<ErrorCausa, React.ComponentType<{ className?: string }>> = {
  generica: AlertTriangle,
  red: CloudOff,
  permiso: Lock,
};

/**
 * ErrorState: card neutra para fallos recuperables
 *
 * Tokens usados:
 * - Fondo: surface-2 (NEUTRO, no danger)
 * - Borde: border (1px, light solo)
 * - Texto/icono: muted-foreground (neutro)
 * - Títul: foreground (sobre surface-2, ~7:1)
 * - Botón: outline (surface-1 bg, border, ring=primary al foco)
 * - Sin sombra (solo borde/luminancia)
 *
 * Variantes:
 * - inline: fila [icono · texto flex-1 · botón compacto], min-h-12
 * - card: columna centrada, max-w-md, en lista vacía
 * - page: columna centrada fullscreen (SinAcceso, pantalla plena sin datos)
 *
 * Estados:
 * - default: card neutra + botón outline + icono muted
 * - loading: spinner en botón, label "Reintentando…", botón disabled
 * - offline: icono CloudOff, copy "Sin conexión" (pulse lento — implementado via CSS)
 * - disabled: si reintentable=false o en SinAcceso sin reintento
 *
 * Accesibilidad:
 * - role=status aria-live=polite: anuncio único, no spamea
 * - Estado NUNCA solo color: icono + título + texto diferencia el neutro
 * - Touch target botón ≥44px efectivos
 * - Contraste: título/desc ≥4.5:1 sobre surface-2 (AA)
 * - Foco visible ring=primary, offset 2px (en Button shadcn)
 */
export function ErrorState({
  titulo,
  descripcion,
  onReintentar,
  causa = "generica",
  variante = "card",
  reintentando = false,
  reintentable = true,
  reintentarLabel = "Reintentar",
  accionSecundaria,
}: ErrorStateProps) {
  const Icono = ICONO[causa];

  return (
    <div
      role="status"
      aria-live="polite"
      aria-label={reintentando ? `Reintentando. ${titulo}` : `Error: ${titulo}. ${descripcion}`}
      className={cn(
        // Contenedor NEUTRO: surface-2 + borde 1px, sin sombra.
        "rounded-lg border border-border bg-surface-2",

        // Distribuir por variante
        variante === "inline"
          ? // Banner compacto: fila, min-h-12 (48px touch-safe)
            "flex min-h-12 items-center gap-3 px-4 py-3"
          : // card + page: columna centrada, gap vertical, card acotada a max-w-md
            "mx-auto flex max-w-md flex-col items-center justify-center gap-3 p-6 text-center",

        // page: mismo card max-w-md pero centrado en una altura mínima de viewport
        // (NO se fuerza min-h-screen al propio card; centra dentro del área de contenido).
        variante === "page" && "min-h-[60vh]",
      )}
    >
      {/* Icono de causa (muted, nunca danger) */}
      <Icono
        className={cn(
          "shrink-0 text-muted-foreground",
          variante === "inline" ? "size-5" : "size-8",
        )}
        aria-hidden="true"
      />

      {/* Título + Descripción (en inline, flex-1 para que ocupe espacio) */}
      <div className={cn(variante === "inline" && "flex-1 text-left")}>
        <h2 className="text-base font-semibold text-foreground">{titulo}</h2>
        <p className="mt-1 text-sm text-muted-foreground">{descripcion}</p>
      </div>

      {/* Botón Reintentar (outline, variant secundario) */}
      {reintentable && (
        <Button
          onClick={onReintentar}
          disabled={reintentando}
          variant="outline"
          className={cn(
            // Target táctil ≥44px efectivos (exigido por el spec en web)
            "min-h-11",
            // En inline el botón es compacto a la derecha; en card/page centrado con gap
            variante === "inline" ? "shrink-0" : "mt-2",
            // Anillo de foco visible (ring=primary, 2px, offset 2px — en Button shadcn por defecto)
            "focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2",
          )}
          aria-busy={reintentando}
        >
          <RefreshCw
            className={cn("mr-2 size-4", reintentando && "motion-safe:animate-spin")}
            aria-hidden="true"
          />
          <span>{reintentando ? "Reintentando…" : reintentarLabel}</span>
        </Button>
      )}

      {/* Acción secundaria: link "Ver detalles" o "Salir" (opcional) */}
      {accionSecundaria && (
        <div className={cn(variante !== "inline" && "mt-2")}>{accionSecundaria}</div>
      )}
    </div>
  );
}
