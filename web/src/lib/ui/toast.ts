import { toast } from "sonner";

/** Forma de un toast de resultado: título + descripción y acción opcionales. */
export interface ToastResultado {
  message: string;
  description?: string;
  action?: { label: string; onClick: () => void };
}

type Resolver<T> = string | ToastResultado | ((arg: T) => string | ToastResultado);

interface ToastEdgeOpciones<T> {
  /** Texto del estado pending mientras la promesa no resuelve. */
  loading: string;
  /** Resultado en éxito (string corto o `{ message, description, action }`). */
  success: Resolver<T>;
  /** Resultado en error; suele llevar acción "Reintentar". */
  error: Resolver<unknown>;
}

function resolver<T>(r: Resolver<T>, arg: T): ToastResultado {
  const v = typeof r === "function" ? (r as (a: T) => string | ToastResultado)(arg) : r;
  return typeof v === "string" ? { message: v } : v;
}

/**
 * Envuelve una llamada a Edge Function / Server Action en el ciclo
 * pending→success/error (A-SNACKBAR-COLA, T-243). Refuerza el SSOT: el éxito
 * definitivo se anuncia cuando el servidor responde, no al teclear. Reutiliza un
 * único toast (`id`) para que el spinner `info` se transforme en success/danger
 * en el sitio. El error es **sticky** (`duration: Infinity`) para no perderse
 * «al sol» hasta que el usuario actúe o lo cierre.
 *
 * Devuelve la promesa original para poder `await`-earla aparte (la gestión de
 * éxito/error del toast es independiente del manejo del llamante).
 */
export function toastEdge<T>(promise: Promise<T>, opts: ToastEdgeOpciones<T>): Promise<T> {
  const id = toast.loading(opts.loading);

  void promise.then(
    (data) => {
      const r = resolver(opts.success, data);
      toast.success(r.message, { id, description: r.description, action: r.action });
    },
    (error: unknown) => {
      const r = resolver(opts.error, error);
      toast.error(r.message, { id, description: r.description, action: r.action, duration: Infinity });
    },
  );

  return promise;
}
