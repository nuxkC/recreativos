"use client";

import { AlertTriangle, CheckCircle, Info, Loader2, XCircle } from "lucide-react";
import { useTheme } from "next-themes";
import { Toaster as Sonner } from "sonner";

import { cn } from "@/lib/utils";

type ToasterProps = React.ComponentProps<typeof Sonner>;

/**
 * Toaster tokenizado (A-SNACKBAR-COLA, T-243). Feedback transitorio con icono de
 * rol SIEMPRE (nunca solo color): success/warning/danger/info + spinner `info`
 * para el pending de `toast.promise`/[toastEdge]. Superficie `surface-1` + borde
 * + sombra de overlay; el error hace un shake danger sutil al entrar
 * (respetando reduced-motion). El color lo controlan los tokens por rol, no
 * `richColors`. La duración por defecto cubre success/info; el error es sticky
 * por llamada (ver `lib/ui/toast.ts`).
 */
const Toaster = ({ ...props }: ToasterProps) => {
  const { theme = "system" } = useTheme();

  return (
    <Sonner
      theme={theme as ToasterProps["theme"]}
      className="toaster group"
      duration={4500}
      icons={{
        success: <CheckCircle className="text-success size-5" aria-hidden="true" />,
        error: <XCircle className="text-danger size-5" aria-hidden="true" />,
        warning: <AlertTriangle className="text-warning size-5" aria-hidden="true" />,
        info: <Info className="text-info size-5" aria-hidden="true" />,
        loading: (
          <Loader2 className="text-info size-5 motion-safe:animate-spin" aria-hidden="true" />
        ),
      }}
      toastOptions={{
        classNames: {
          toast: cn(
            "group toast gap-3 rounded-xl border border-border bg-surface-1 text-foreground shadow-lg",
            // El estado nunca va solo por color: el error además hace shake al entrar.
            "motion-safe:data-[type=error]:animate-danger-shake",
          ),
          title: "text-sm font-medium",
          description: "group-[.toast]:text-muted-foreground",
          actionButton: "group-[.toast]:bg-primary group-[.toast]:text-primary-foreground",
          cancelButton: "group-[.toast]:bg-muted group-[.toast]:text-muted-foreground",
          icon: "self-start",
        },
      }}
      {...props}
    />
  );
};

export { Toaster };
