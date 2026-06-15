"use client";

// ConfirmDialog destructivo — átomo ÚNICO de confirmación destructiva (web).
//
// Razón de ser (seguridad + a11y): (a) el foco inicial recae SIEMPRE en el
// botón SEGURO (Cancelar), nunca en el destructivo, para evitar confirmar por
// error con Enter; (b) la API hace IMPOSIBLE ejecutar onConfirm sin pasar por
// aquí — el onClick destructivo vive dentro de AlertDialogAction y no se
// expone otra vía. Reemplaza el ConfirmButton suelto del átomo Botones.
//
// Cuerpo NEUTRO (surface-1): el rojo se reserva al icono de advertencia y al
// botón de acción; nunca se tiñe el fondo del diálogo de danger-subtle.
// on-danger = text-danger-foreground (token canónico del proyecto): #FFFFFF
// light / #3a0a0a dark (blanco sobre danger dark falla AA, de ahí el oscuro).

import * as React from "react";
import { AlertTriangle, Loader2 } from "lucide-react";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { cn } from "@/lib/utils";

/** Variante con MOTIVO obligatorio (F.3 condonar deuda): el botón destructivo
 *  permanece bloqueado hasta que el campo no esté vacío y el motivo queda en
 *  auditoría. Texto libre (no es una cifra) → teclado del sistema. */
type ConfirmReason = {
  value: string;
  onChange: (value: string) => void;
  label?: string;
  /** Por defecto true cuando se pasa el objeto reason: el motivo es obligatorio. */
  required?: boolean;
  placeholder?: string;
};

type ConfirmDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description: string;
  /** Callback destructivo: solo se dispara desde el botón de confirmación. */
  onConfirm: () => void;
  confirmLabel?: string;
  cancelLabel?: string;
  /** Confirmación en curso (persiste en servidor): spinner en el destructivo,
   *  Cancelar deshabilitado y el cierre por scrim/Escape/back queda bloqueado. */
  loading?: boolean;
  /** Error de servidor inline bajo el footer; el diálogo NO se cierra y se
   *  anuncia al lector (role=alert + aria-live). */
  errorMessage?: string;
  /** Variante con campo de motivo obligatorio. */
  reason?: ConfirmReason;
};

const REASON_FIELD_ID = "confirm-dialog-reason";
const REASON_ERROR_ID = "confirm-dialog-reason-error";
const REASON_HELP_ID = "confirm-dialog-reason-help";
const SERVER_ERROR_ID = "confirm-dialog-server-error";

export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  onConfirm,
  confirmLabel = "Eliminar",
  cancelLabel = "Cancelar",
  loading = false,
  errorMessage,
  reason,
}: ConfirmDialogProps) {
  const cancelRef = React.useRef<HTMLButtonElement>(null);
  // El error del motivo se muestra "tras intento", no en el vacío inicial:
  // mientras tanto el botón está bloqueado con helper NEUTRO (no rojo).
  const [attempted, setAttempted] = React.useState(false);

  const reasonRequired = reason ? (reason.required ?? true) : false;
  const reasonEmpty = reasonRequired && !reason!.value.trim();
  // Bloquea el confirmar; el error visible (borde+mensaje danger) solo aparece
  // tras intentar enviar — separa "sin rellenar" (helper) de "inválido".
  const reasonInvalidAfterAttempt = reasonEmpty && attempted;

  // Resetea el "intento" al cerrar para no arrastrar el error a la próxima
  // apertura (el reason en sí lo controla el consumidor).
  React.useEffect(() => {
    if (!open) setAttempted(false);
  }, [open]);

  function handleConfirm(event: React.MouseEvent<HTMLButtonElement>) {
    if (loading) return;
    if (reasonEmpty) {
      // No cierra el diálogo: revela el error y mantiene el foco.
      event.preventDefault();
      setAttempted(true);
      return;
    }
    onConfirm();
  }

  // Cualquier cierre (scrim/Escape/back/Cancelar) es la salida SEGURA, pero
  // durante loading se ignora para evitar doble submit / cierre a medias.
  function handleOpenChange(next: boolean) {
    if (loading) return;
    onOpenChange(next);
  }

  // Radix AlertDialog ya es NO-dismissible por click-fuera (semántica de
  // alertdialog: su tipo omite onPointerDownOutside/onInteractOutside). Solo
  // queda Escape, que SALTA onOpenChange: se intercepta durante loading.
  function blockEscapeWhileLoading(event: KeyboardEvent) {
    if (loading) event.preventDefault();
  }

  const reasonDescribedBy = reasonInvalidAfterAttempt
    ? REASON_ERROR_ID
    : reasonRequired
      ? REASON_HELP_ID
      : undefined;

  return (
    <AlertDialog open={open} onOpenChange={handleOpenChange}>
      {/* role=alertdialog + aria-modal + focus-trap son nativos de Radix. */}
      <AlertDialogContent
        className="min-w-[320px] max-w-[440px] gap-grid-6 rounded-xl border-border bg-surface-1 p-grid-6 shadow-modal"
        onEscapeKeyDown={blockEscapeWhileLoading}
        // Foco inicial SIEMPRE en Cancelar (salida segura), nunca en el
        // destructivo ni en el textarea del motivo (que va antes en el DOM).
        onOpenAutoFocus={(event) => {
          event.preventDefault();
          cancelRef.current?.focus();
        }}
      >
        <AlertDialogHeader>
          <AlertDialogTitle className="flex items-center gap-grid-2 text-h2 text-foreground">
            {/* Icono decorativo: lo destructivo va en texto+confirmación, no solo en el rojo. */}
            <AlertTriangle className="size-5 shrink-0 text-danger" aria-hidden="true" />
            <span>{title}</span>
          </AlertDialogTitle>
          {/* Énfasis: la consecuencia irreversible va en foreground, no en muted. */}
          <AlertDialogDescription className="text-body text-foreground">
            {description}
          </AlertDialogDescription>
        </AlertDialogHeader>

        {reason && (
          <div className="grid gap-grid-1">
            <label htmlFor={REASON_FIELD_ID} className="text-label text-foreground">
              {reason.label ?? "Motivo"}
            </label>
            <textarea
              id={REASON_FIELD_ID}
              value={reason.value}
              onChange={(event) => reason.onChange(event.target.value)}
              disabled={loading}
              required={reasonRequired}
              aria-required={reasonRequired}
              aria-invalid={reasonInvalidAfterAttempt || undefined}
              aria-describedby={reasonDescribedBy}
              placeholder={reason.placeholder}
              rows={3}
              className={cn(
                "min-h-[80px] w-full rounded-md border bg-surface-1 p-grid-2 text-body text-foreground",
                "placeholder:text-muted-foreground",
                "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-surface-1",
                "disabled:cursor-not-allowed disabled:opacity-50",
                // Sin intento: borde de control reforzado (≥3:1) + helper neutro.
                // Tras intento vacío: borde danger.
                reasonInvalidAfterAttempt ? "border-danger" : "border-border-strong",
              )}
            />
            {reasonInvalidAfterAttempt ? (
              // Validación (texto pequeño): danger-text = #a81818 light (7.48:1) / danger dark.
              <p id={REASON_ERROR_ID} role="alert" className="text-caption text-danger-text">
                El motivo es obligatorio
              </p>
            ) : (
              reasonRequired && (
                <p id={REASON_HELP_ID} className="text-caption text-muted-foreground">
                  Indica el motivo; quedará registrado en la auditoría
                </p>
              )
            )}
          </div>
        )}

        {/* Error de servidor inline: el diálogo sigue abierto y se anuncia. */}
        {errorMessage && (
          <p
            id={SERVER_ERROR_ID}
            role="alert"
            aria-live="assertive"
            className="text-caption text-danger-text"
          >
            {errorMessage}
          </p>
        )}

        <AlertDialogFooter>
          {/* Cancelar = salida segura, recibe el foco inicial. min-h-[44px]
              fuerza el target táctil (la base shadcn hereda h-10 = 40px). */}
          <AlertDialogCancel
            ref={cancelRef}
            disabled={loading}
            className="min-h-[44px] disabled:opacity-50"
          >
            {cancelLabel}
          </AlertDialogCancel>
          <AlertDialogAction
            // El motivo vacío NO usa `disabled` nativo: así el botón conserva
            // foco/anuncio y handleConfirm revela el error sin cerrar. El gate
            // visual va por opacidad + aria-disabled; loading sí deshabilita.
            disabled={loading}
            onClick={handleConfirm}
            aria-busy={loading || undefined}
            aria-disabled={reasonEmpty || undefined}
            aria-describedby={errorMessage ? SERVER_ERROR_ID : undefined}
            className={cn(
              "min-h-[44px] bg-danger text-danger-foreground",
              "hover:bg-danger/90 active:bg-danger/85",
              // El anillo sobre fondo danger sería invisible (≈1.1:1): el offset
              // sobre surface-1 lo hace visible (≥3:1).
              "focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-surface-1",
              "disabled:opacity-50",
              reasonEmpty && "opacity-50",
            )}
          >
            {loading && <Loader2 className="mr-grid-2 size-4 animate-spin" aria-hidden="true" />}
            {confirmLabel}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

export type { ConfirmDialogProps, ConfirmReason };
