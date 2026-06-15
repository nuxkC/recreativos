"use client";

// Átomo Field/Inputs del design system Recre (web). Familia de campos de
// formulario que captura datos con el teclado del SISTEMA (nunca QWERTY para
// números, nunca un keypad in-app salvo denominaciones — eso es T-231). Unifica
// label, control, descripción, mensaje de error inline y los estados
// default/focus/disabled/read-only/loading/error/offline con su contrato a11y
// (label htmlFor, aria-describedby, aria-invalid, aria-readonly).
//
// Dinero money-safe: el valor viaja SIEMPRE como string y se opera con
// decimal.js; jamás Number/Float (spinners, locale del separador, redondeo). El
// cálculo económico definitivo vive en el SSOT del servidor: estos campos solo
// capturan y muestran. type="text" + inputMode="numeric"/"decimal", nunca
// type="number".
//
// Notas de tokens (la verdad está en globals.css / tailwind.config.ts):
//  - `muted-strong` (~7:1 sobre surface-2) se usa para el símbolo €/%, el sufijo
//    "(opcional)", la descripción y los iconos informativos; `muted-foreground`
//    (5.38:1) queda SOLO para el placeholder residual.
//  - Texto pequeño de estado: globals.css define `-text` (danger-text 7.48:1,
//    warning-text 7.63:1) para texto pequeño/etiqueta; el fill `danger`/`warning`
//    se reserva a iconos/cifras grandes. Por eso label/mensaje en error usan
//    `text-danger-text` y el pie offline `text-warning-text` (AA en caption).
//  - Radio del control = 8px = `rounded-md` (--radius-md). `rounded-lg` son 12px.
//  - --ring ya == primary, así que `ring-ring` pinta el petróleo de foco.

import * as React from "react";
import Decimal from "decimal.js";
import { CircleAlert, CloudOff, Eye, EyeOff, Loader2 } from "lucide-react";

import { cn } from "@/lib/utils";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

// ===========================================================================
// Densidad del control: táctil/responsive (44px) vs back-office con ratón (36px)
// ===========================================================================
export type FieldDensity = "touch" | "compact";

/** Alto del control: touch=h-11 (44px, target táctil); compact=h-9 (36px, solo back-office con ratón). */
function controlHeight(density: FieldDensity): string {
  return density === "compact" ? "h-9" : "h-11";
}

/**
 * Clases compartidas por todos los controles (input/select/datepicker) que
 * sobrescriben los defaults shadcn para alinearlos al spec: fondo surface-2
 * (diferenciarlo del card surface-1), radio 8px, foco ring=primary SIN sombra.
 * aria-[invalid] tiñe el borde en danger (el estado error nace del aria, no de
 * una clase suelta, para ser coherente con el lector). read-only conserva el
 * visual default (no se atenúa): solo cambia el cursor.
 */
function controlClasses(density: FieldDensity): string {
  return cn(
    controlHeight(density),
    "rounded-md bg-surface-2 shadow-none",
    "focus-visible:ring-1 focus-visible:ring-ring",
    "aria-[invalid=true]:border-danger",
    "read-only:cursor-default", // read-only ≠ disabled: NO se atenúa, sigue enfocable/copiable
  );
}

// ===========================================================================
// Subcomponentes estructurales (label / descripción / mensaje)
// ===========================================================================

/** Props base comunes a todos los campos (etiqueta, ayuda, error, offline). */
interface FieldBaseProps {
  /** id del control; ancla label htmlFor + aria-describedby. Si se omite se genera estable con useId. */
  id?: string;
  /** Etiqueta visible. Pasa a danger cuando hay error (color reforzado por icono+texto del mensaje). */
  label?: React.ReactNode;
  /** Añade el sufijo "(opcional)" junto al label. */
  optional?: boolean;
  /** Texto de ayuda (FormDescription). Reservado a pistas, no al error. */
  description?: React.ReactNode;
  /** Mensaje de error inline; cuando existe activa el estado error (borde/label/icono/texto en danger). */
  error?: React.ReactNode;
  /** Pie discreto "sin sincronizar" (warning ámbar + CloudOff). NUNCA danger: offline no es error. */
  offline?: boolean;
  /** Texto del pie offline; por defecto "Sin sincronizar". */
  offlineText?: string;
  /** Clase del contenedor FormItem. */
  className?: string;
}

/** FormItem — apila label/control/descripción/mensaje con el gap del spec (label→control 8px). */
const FormItem = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(
  ({ className, ...props }, ref) => (
    <div ref={ref} className={cn("flex flex-col gap-2", className)} {...props} />
  ),
);
FormItem.displayName = "FormItem";

/** FormLabel — caption; pasa a danger-text (texto pequeño AA) en error. */
const FormLabel = React.forwardRef<
  HTMLLabelElement,
  React.LabelHTMLAttributes<HTMLLabelElement> & { error?: boolean; optional?: boolean }
>(({ className, error, optional, children, ...props }, ref) => (
  <Label
    ref={ref}
    className={cn("text-caption", error && "text-danger-text", className)}
    {...props}
  >
    {children}
    {optional ? (
      // "(opcional)" en muted-strong (rol informativo ≥7:1 del spec).
      <span className="ml-1 font-normal text-muted-strong">(opcional)</span>
    ) : null}
  </Label>
));
FormLabel.displayName = "FormLabel";

/** FormDescription — ayuda en caption, en muted-strong (rol informativo ≥7:1). */
const FormDescription = React.forwardRef<
  HTMLParagraphElement,
  React.HTMLAttributes<HTMLParagraphElement>
>(({ className, ...props }, ref) => (
  <p ref={ref} className={cn("text-caption text-muted-strong", className)} {...props} />
));
FormDescription.displayName = "FormDescription";

/**
 * FormMessage — error inline: icono CircleAlert + texto en danger-text (estado
 * nunca solo-color). role="alert" + aria-live="polite" para anunciarse al
 * insertarse (el error aparece al salir/confirmar, no mientras se teclea).
 */
const FormMessage = React.forwardRef<
  HTMLParagraphElement,
  React.HTMLAttributes<HTMLParagraphElement>
>(({ className, children, ...props }, ref) => (
  <p
    ref={ref}
    role="alert"
    aria-live="polite"
    className={cn("flex items-center gap-1.5 text-caption text-danger-text", className)}
    {...props}
  >
    <CircleAlert aria-hidden className="size-4 shrink-0" />
    <span>{children}</span>
  </p>
));
FormMessage.displayName = "FormMessage";

/** Pie offline "sin sincronizar" — warning ámbar + CloudOff + texto. NUNCA danger. */
function OfflineNote({ text }: { text: string }) {
  return (
    <p className="flex items-center gap-1.5 text-caption text-warning-text">
      <CloudOff aria-hidden className="size-4 shrink-0" />
      <span>{text}</span>
    </p>
  );
}

/**
 * Cablea los ids de a11y de un campo de forma estable (useId, sin Math.random:
 * evita el mismatch de hidratación). Devuelve el id del control y el
 * aria-describedby que une descripción + error.
 */
function useFieldIds(externalId: string | undefined, hasDescription: boolean, hasError: boolean) {
  const reactId = React.useId();
  const id = externalId ?? reactId;
  const descriptionId = hasDescription ? `${id}-description` : undefined;
  const errorId = hasError ? `${id}-error` : undefined;
  const describedBy = [descriptionId, errorId].filter(Boolean).join(" ") || undefined;
  return { id, descriptionId, errorId, describedBy };
}

// ===========================================================================
// FieldText — texto libre (serie, modelo, nombre, email, password)
// ===========================================================================

interface FieldTextProps extends FieldBaseProps {
  /** "text" | "email" | "password". password añade botón ojo (toggle) con hit-area ≥44px. */
  variant?: "text" | "email" | "password";
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
  /** read-only: enfocable y copiable, sin edición; visual = default (NO atenuado). Distinto de disabled. */
  readOnly?: boolean;
  /** Spinner de carga en el adorno derecho (aria-busy). */
  loading?: boolean;
  autoComplete?: string;
  density?: FieldDensity;
}

/**
 * Campo de texto. email sube inputMode="email"; password trae un toggle de
 * visibilidad (Eye/EyeOff) cuyo botón mide ≥44px (min-h-11 min-w-11) aunque el
 * glifo quede a 16px; en h-9 sobresale con -my-1 para no recortar a 36px. El
 * estado error lo gobierna aria-invalid (no una clase suelta).
 */
const FieldText = React.forwardRef<HTMLInputElement, FieldTextProps>(
  (
    {
      variant = "text",
      value,
      onChange,
      label,
      optional,
      description,
      error,
      offline,
      offlineText = "Sin sincronizar",
      id,
      className,
      placeholder,
      disabled,
      readOnly,
      loading,
      autoComplete,
      density = "touch",
    },
    ref,
  ) => {
    const [visible, setVisible] = React.useState(false);
    const hasError = error != null && error !== false;
    const {
      id: fieldId,
      descriptionId,
      errorId,
      describedBy,
    } = useFieldIds(id, description != null, hasError);

    const isPassword = variant === "password";
    const type = isPassword
      ? visible
        ? "text"
        : "password"
      : variant === "email"
        ? "email"
        : "text";
    const trailing = isPassword || loading;

    return (
      <FormItem className={className}>
        {label != null ? (
          <FormLabel htmlFor={fieldId} error={hasError} optional={optional}>
            {label}
          </FormLabel>
        ) : null}
        <div className="relative">
          <Input
            ref={ref}
            id={fieldId}
            type={type}
            inputMode={variant === "email" ? "email" : undefined}
            value={value}
            onChange={(e) => onChange(e.target.value)}
            placeholder={placeholder}
            disabled={disabled}
            readOnly={readOnly}
            autoComplete={autoComplete}
            aria-invalid={hasError}
            aria-describedby={describedBy}
            aria-readonly={readOnly || undefined}
            aria-busy={loading || undefined}
            className={cn(controlClasses(density), trailing && "pr-11")}
          />
          {loading ? (
            <span className="pointer-events-none absolute inset-y-0 right-3 flex items-center text-muted-strong">
              <Loader2 aria-hidden className="size-4 animate-spin motion-reduce:animate-none" />
            </span>
          ) : isPassword ? (
            <button
              type="button"
              onClick={() => setVisible((v) => !v)}
              aria-label={visible ? "Ocultar contraseña" : "Mostrar contraseña"}
              aria-pressed={visible}
              className={cn(
                "absolute inset-y-0 right-0 -my-1 flex min-h-11 min-w-11 items-center justify-center",
                "rounded-md text-muted-foreground",
                "focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring",
              )}
            >
              {visible ? (
                <EyeOff aria-hidden className="size-4" />
              ) : (
                <Eye aria-hidden className="size-4" />
              )}
            </button>
          ) : null}
        </div>
        {description != null && !hasError ? (
          <FormDescription id={descriptionId}>{description}</FormDescription>
        ) : null}
        {hasError ? <FormMessage id={errorId}>{error}</FormMessage> : null}
        {offline ? <OfflineNote text={offlineText} /> : null}
      </FormItem>
    );
  },
);
FieldText.displayName = "FieldText";

// ===========================================================================
// FieldNum — entero (contadores/cantidades) y decimal money-safe (importes/%)
// ===========================================================================

/** Conserva solo dígitos (entero) o dígitos + una única coma decimal es-ES (decimal). NUNCA Number. */
function sanitizeNumeric(raw: string, isDecimal: boolean): string {
  if (!isDecimal) return raw.replace(/[^\d]/g, "");
  // Decimal es-ES: dígitos + una sola coma. Se descartan puntos/letras; comas
  // sobrantes se colapsan para no romper el parseo a Decimal aguas abajo.
  const cleaned = raw.replace(/[^\d,]/g, "");
  const firstComma = cleaned.indexOf(",");
  if (firstComma === -1) return cleaned;
  const head = cleaned.slice(0, firstComma + 1);
  const tail = cleaned.slice(firstComma + 1).replace(/,/g, "");
  return head + tail;
}

/**
 * Convierte el string visible es-ES (coma decimal) a un Decimal money-safe, o
 * null si no es un decimal válido. El consumidor transmite SIEMPRE el string del
 * Decimal (toFixed/valueOf), nunca un Number. Helper expuesto para validar al
 * salir/confirmar sin recalcular económicamente en cliente (eso es del SSOT).
 */
export function fieldNumToDecimal(value: string): Decimal | null {
  if (value === "" || value === ",") return null;
  try {
    // El input es-ES usa coma; Decimal espera punto.
    return new Decimal(value.replace(",", "."));
  } catch {
    return null;
  }
}

interface FieldNumProps extends FieldBaseProps {
  /** Valor SIEMPRE string (dinero → decimal.js; jamás Number/Float). */
  value: string;
  onChange: (value: string) => void;
  /** true → decimal (importe/%/tasa, teclado decimal); false → entero (contador, teclado numérico). */
  isDecimal?: boolean;
  /** Sufijo de unidad informativo ("€" / "%"). Va en el neutro de texto, el dígito en foreground. */
  suffix?: string;
  placeholder?: string;
  disabled?: boolean;
  readOnly?: boolean;
  loading?: boolean;
  density?: FieldDensity;
}

/**
 * Campo numérico. NUNCA type="number" (spinners, locale del separador, redondeo
 * float): type="text" + inputMode numeric/decimal sube el teclado del sistema
 * correcto. El valor se renderiza con .num-tabular (Geist Mono tabular-nums)
 * para que los dígitos no se desplacen; el sufijo €/% es informativo (no es el
 * dígito) y queda en muted-foreground, el dígito en foreground.
 */
const FieldNum = React.forwardRef<HTMLInputElement, FieldNumProps>(
  (
    {
      value,
      onChange,
      isDecimal = false,
      suffix,
      label,
      optional,
      description,
      error,
      offline,
      offlineText = "Sin sincronizar",
      id,
      className,
      placeholder,
      disabled,
      readOnly,
      loading,
      density = "touch",
    },
    ref,
  ) => {
    const hasError = error != null && error !== false;
    const {
      id: fieldId,
      descriptionId,
      errorId,
      describedBy,
    } = useFieldIds(id, description != null, hasError);
    const trailing = suffix != null || loading;
    const defaultPlaceholder = isDecimal ? "0,00" : "0";

    return (
      <FormItem className={className}>
        {label != null ? (
          <FormLabel htmlFor={fieldId} error={hasError} optional={optional}>
            {label}
          </FormLabel>
        ) : null}
        <div className="relative">
          <Input
            ref={ref}
            id={fieldId}
            type="text"
            inputMode={isDecimal ? "decimal" : "numeric"}
            autoComplete="off"
            value={value}
            onChange={(e) => onChange(sanitizeNumeric(e.target.value, isDecimal))}
            placeholder={placeholder ?? defaultPlaceholder}
            disabled={disabled}
            readOnly={readOnly}
            aria-invalid={hasError}
            aria-describedby={describedBy}
            aria-readonly={readOnly || undefined}
            aria-busy={loading || undefined}
            className={cn(controlClasses(density), "num-tabular", trailing && "pr-9")}
          />
          {loading ? (
            <span className="pointer-events-none absolute inset-y-0 right-3 flex items-center text-muted-strong">
              <Loader2 aria-hidden className="size-4 animate-spin motion-reduce:animate-none" />
            </span>
          ) : suffix != null ? (
            <span
              aria-hidden
              className="pointer-events-none absolute inset-y-0 right-3 flex items-center text-muted-strong"
            >
              {suffix}
            </span>
          ) : null}
        </div>
        {description != null && !hasError ? (
          <FormDescription id={descriptionId}>{description}</FormDescription>
        ) : null}
        {hasError ? <FormMessage id={errorId}>{error}</FormMessage> : null}
        {offline ? <OfflineNote text={offlineText} /> : null}
      </FormItem>
    );
  },
);
FieldNum.displayName = "FieldNum";

/** FieldDecimal — atajo de FieldNum con isDecimal y sufijo "€" por defecto (importes). */
const FieldDecimal = React.forwardRef<
  HTMLInputElement,
  Omit<FieldNumProps, "isDecimal"> & { suffix?: string }
>(({ suffix = "€", ...props }, ref) => <FieldNum ref={ref} isDecimal suffix={suffix} {...props} />);
FieldDecimal.displayName = "FieldDecimal";

// ===========================================================================
// FieldSelect — lista CORTA cerrada (p. ej. estado de máquina). Reusa ui/select.
// Para CCAA (19 items con filtro) el spec exige un combobox cmdk; cmdk NO está
// instalado, así que ese caso queda fuera de este átomo (ver uncertainties).
// ===========================================================================

interface FieldSelectOption {
  value: string;
  label: string;
}

interface FieldSelectProps extends FieldBaseProps {
  value: string | undefined;
  onChange: (value: string) => void;
  options: FieldSelectOption[];
  placeholder?: string;
  disabled?: boolean;
  density?: FieldDensity;
}

/**
 * Select corto sobre @/components/ui/select (Radix). El trigger comparte alto/
 * borde/radio/foco con los inputs; Radix marca el item seleccionado con su
 * check. No usar para listas largas (CCAA): eso pide combobox con filtro.
 */
const FieldSelect = React.forwardRef<HTMLButtonElement, FieldSelectProps>(
  (
    {
      value,
      onChange,
      options,
      label,
      optional,
      description,
      error,
      offline,
      offlineText = "Sin sincronizar",
      id,
      className,
      placeholder = "Seleccionar",
      disabled,
      density = "touch",
    },
    ref,
  ) => {
    const hasError = error != null && error !== false;
    const {
      id: fieldId,
      descriptionId,
      errorId,
      describedBy,
    } = useFieldIds(id, description != null, hasError);

    return (
      <FormItem className={className}>
        {label != null ? (
          <FormLabel id={`${fieldId}-label`} htmlFor={fieldId} error={hasError} optional={optional}>
            {label}
          </FormLabel>
        ) : null}
        <Select value={value} onValueChange={onChange} disabled={disabled}>
          <SelectTrigger
            ref={ref}
            id={fieldId}
            aria-labelledby={label != null ? `${fieldId}-label` : undefined}
            aria-invalid={hasError}
            aria-describedby={describedBy}
            className={cn(
              controlHeight(density),
              "rounded-md bg-surface-2 shadow-none",
              "aria-[invalid=true]:border-danger",
            )}
          >
            <SelectValue placeholder={placeholder} />
          </SelectTrigger>
          <SelectContent>
            {options.map((opt) => (
              <SelectItem key={opt.value} value={opt.value}>
                {opt.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        {description != null && !hasError ? (
          <FormDescription id={descriptionId}>{description}</FormDescription>
        ) : null}
        {hasError ? <FormMessage id={errorId}>{error}</FormMessage> : null}
        {offline ? <OfflineNote text={offlineText} /> : null}
      </FormItem>
    );
  },
);
FieldSelect.displayName = "FieldSelect";

// ===========================================================================
// FieldDate — DatePicker. Sin react-day-picker instalado → <input type="date">,
// que delega calendario y formato es-ES dd/MM/aaaa al control nativo del
// navegador (accesible y localizado por el SO). Ver uncertainties.
// ===========================================================================

interface FieldDateProps extends FieldBaseProps {
  /** Fecha ISO yyyy-MM-dd (lo que consume/emite <input type="date">). */
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  readOnly?: boolean;
  min?: string;
  max?: string;
  density?: FieldDensity;
}

/**
 * Selector de fecha. El calendario y el formato visible los aporta el control
 * nativo (`type="date"` muestra dd/MM/aaaa en locales es-ES). El valor de
 * modelo es ISO yyyy-MM-dd. Cuando se instale un calendario propio se podrá
 * sustituir por Popover+Calendar con trigger read-only (ver uncertainties).
 */
const FieldDate = React.forwardRef<HTMLInputElement, FieldDateProps>(
  (
    {
      value,
      onChange,
      label,
      optional,
      description,
      error,
      offline,
      offlineText = "Sin sincronizar",
      id,
      className,
      disabled,
      readOnly,
      min,
      max,
      density = "touch",
    },
    ref,
  ) => {
    const hasError = error != null && error !== false;
    const {
      id: fieldId,
      descriptionId,
      errorId,
      describedBy,
    } = useFieldIds(id, description != null, hasError);

    return (
      <FormItem className={className}>
        {label != null ? (
          <FormLabel htmlFor={fieldId} error={hasError} optional={optional}>
            {label}
          </FormLabel>
        ) : null}
        <Input
          ref={ref}
          id={fieldId}
          type="date"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          disabled={disabled}
          readOnly={readOnly}
          min={min}
          max={max}
          aria-invalid={hasError}
          aria-describedby={describedBy}
          aria-readonly={readOnly || undefined}
          className={cn(controlClasses(density))}
        />
        {description != null && !hasError ? (
          <FormDescription id={descriptionId}>{description}</FormDescription>
        ) : null}
        {hasError ? <FormMessage id={errorId}>{error}</FormMessage> : null}
        {offline ? <OfflineNote text={offlineText} /> : null}
      </FormItem>
    );
  },
);
FieldDate.displayName = "FieldDate";

export {
  FormItem,
  FormLabel,
  FormDescription,
  FormMessage,
  FieldText,
  FieldNum,
  FieldDecimal,
  FieldSelect,
  FieldDate,
  type FieldBaseProps,
  type FieldTextProps,
  type FieldNumProps,
  type FieldSelectProps,
  type FieldSelectOption,
  type FieldDateProps,
};
