"use client";

import {
  AlertTriangle,
  Download,
  Gamepad2,
  SearchX,
  type LucideIcon,
} from "lucide-react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import * as React from "react";

import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator,
  CommandShortcut,
} from "@/components/ui/command";
import {
  ROLES_ADMIN,
  ROLES_GESTION,
  type Rol,
} from "@/lib/auth/roles";
import { seccionesPermitidas } from "@/components/layout/nav-config";

/**
 * Paleta de comandos global (Cmd/Ctrl+K) · C-WEB-CMDK-01.
 *
 * No es un chat: es un navegador/runner de acciones del back-office. Se abre
 * con ⌘K (mac) / Ctrl+K (win·linux) o con un trigger externo, y reúne DOS
 * fuentes, AMBAS filtradas por rol con el mismo `.includes(rol)`:
 *   a) navegación del sidebar (`seccionesPermitidas`),
 *   b) acciones de dominio (`DOMAIN_ACTIONS`, cada una con sus `roles`).
 * Un rol sin permiso NUNCA ve el item (la ausencia es la regla, no el disabled):
 * la API lo rechazaría igual.
 *
 * El overlay (foco atrapado, Escape, click-fuera, scrim) lo aporta
 * `CommandDialog`, que reutiliza Dialog/DialogContent (Radix). cmdk mantiene el
 * foco en el input y mueve el item resaltado con ↑/↓ vía aria-activedescendant.
 *
 * Tokens: el item resaltado se distingue por el FONDO `accent` (tint petróleo),
 * nunca solo por color de texto (a11y). El icono se tiñe a `primary` SOLO
 * cuando está seleccionado (lo gobierna ui/command). Sin sombras propias: la
 * única sombra de la app vive en este overlay (shadow-modal del CommandDialog).
 */

/** Acción de dominio: misma forma de filtrado por rol que un NavItem. */
interface DomainAction {
  id: string;
  /** Etiqueta visible (es). */
  label: string;
  /** Grupo visible (Crear / Exportar / ...). */
  group: string;
  icon: LucideIcon;
  roles: readonly Rol[];
  /** Pista de atajo opcional (kbd a la derecha). */
  shortcut?: string;
  /** Ejecuta la acción: navega o dispara callback. */
  run: (router: { push: (href: string) => void }) => void;
}

/**
 * Registro de acciones de dominio. Cada acción HEREDA los `roles` del NavItem
 * al que conduce, para no derivar permisos a mano (Nueva máquina / Exportar
 * recaudaciones = gestión; Filtrar conflictos = admin).
 */
const DOMAIN_ACTIONS: readonly DomainAction[] = [
  {
    id: "crear-maquina",
    label: "Nueva máquina",
    group: "Crear",
    icon: Gamepad2,
    roles: ROLES_GESTION, // heredado de NAV /maquinas
    run: (r) => r.push("/maquinas/nueva"),
  },
  {
    id: "exportar-recaudaciones",
    label: "Exportar recaudaciones",
    group: "Exportar",
    icon: Download,
    roles: ROLES_GESTION, // export de /recaudaciones es de gestión
    run: (r) => r.push("/recaudaciones?export=1"),
  },
  {
    id: "filtrar-conflictos",
    label: "Filtrar conflictos",
    group: "Crear",
    icon: AlertTriangle,
    roles: ROLES_ADMIN, // heredado de NAV /conflictos
    shortcut: "⇧C",
    run: (r) => r.push("/conflictos"),
  },
];

/** Mismo patrón que `seccionesPermitidas`: filtra por rol con `.includes`. */
function accionesPermitidas(rol: Rol): DomainAction[] {
  return DOMAIN_ACTIONS.filter((accion) => accion.roles.includes(rol));
}

export interface CommandPaletteProps {
  /** Rol del usuario actual: filtra navegación y acciones. */
  rol: Rol;
  /**
   * Estado controlado opcional (p. ej. para abrir desde el trigger de la
   * topbar). Si se omite, la paleta gestiona su propio estado y solo responde
   * al atajo de teclado.
   */
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
}

export function CommandPalette({
  rol,
  open: openProp,
  onOpenChange,
}: CommandPaletteProps): React.ReactElement {
  const router = useRouter();
  const t = useTranslations("nav");

  // Estado interno solo cuando el consumidor no controla la apertura.
  const [openInterno, setOpenInterno] = React.useState(false);
  const esControlado = openProp !== undefined;
  const open = esControlado ? openProp : openInterno;

  const setOpen = React.useCallback(
    (siguiente: boolean) => {
      if (!esControlado) setOpenInterno(siguiente);
      onOpenChange?.(siguiente);
    },
    [esControlado, onOpenChange],
  );

  // Atajo global ⌘K / Ctrl+K: alterna la paleta. preventDefault para no chocar
  // con shortcuts del navegador. No capturamos K en inputs ajenos (salvo el
  // propio input de la paleta, que cmdk gestiona aparte).
  React.useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key !== "k" || !(e.metaKey || e.ctrlKey)) return;

      const objetivo = e.target;
      const enCampoAjeno =
        objetivo instanceof HTMLElement &&
        (objetivo.tagName === "INPUT" ||
          objetivo.tagName === "TEXTAREA" ||
          objetivo.isContentEditable) &&
        // El input de cmdk vive dentro del wrapper marcado: ahí sí permitimos.
        objetivo.closest("[cmdk-input-wrapper]") === null &&
        !open;

      if (enCampoAjeno) return;

      e.preventDefault();
      setOpen(!open);
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [open, setOpen]);

  const secciones = seccionesPermitidas(rol); // navegación filtrada por rol (= sidebar)
  const acciones = accionesPermitidas(rol); // MISMO filtro de rol para dominio

  // Cierra y navega en el mismo gesto (Enter o click sobre un item).
  const irA = React.useCallback(
    (href: string) => {
      setOpen(false);
      router.push(href);
    },
    [router, setOpen],
  );

  // Agrupa las acciones permitidas por su `group` (Crear / Exportar / ...),
  // preservando el orden de primera aparición.
  const gruposAccion = React.useMemo(() => {
    const mapa = new Map<string, DomainAction[]>();
    for (const accion of acciones) {
      const lista = mapa.get(accion.group) ?? [];
      lista.push(accion);
      mapa.set(accion.group, lista);
    }
    return Array.from(mapa.entries());
  }, [acciones]);

  const hayAcciones = gruposAccion.length > 0;

  return (
    <CommandDialog
      open={open}
      onOpenChange={setOpen}
      label="Paleta de comandos"
    >
      <CommandInput placeholder="Buscar o ejecutar una acción…" />
      {/* aria-live: anuncia a lectores de pantalla el cambio de resultados. */}
      <CommandList aria-live="polite">
        <CommandEmpty>
          <SearchX className="size-6 text-muted-foreground" aria-hidden="true" />
          <span>Sin resultados. Prueba con otra palabra.</span>
        </CommandEmpty>

        {/* Acciones de dominio — YA filtradas por rol. */}
        {gruposAccion.map(([group, items]) => (
          <CommandGroup key={group} heading={group}>
            {items.map((accion) => {
              const Icon = accion.icon;
              return (
                <CommandItem
                  key={accion.id}
                  // value: lo que cmdk usa para el filtrado fuzzy.
                  value={`${group} ${accion.label}`}
                  onSelect={() => {
                    setOpen(false);
                    accion.run(router);
                  }}
                >
                  <Icon aria-hidden="true" />
                  <span>{accion.label}</span>
                  {accion.shortcut ? (
                    <CommandShortcut>{accion.shortcut}</CommandShortcut>
                  ) : null}
                </CommandItem>
              );
            })}
          </CommandGroup>
        ))}

        {hayAcciones && secciones.length > 0 ? <CommandSeparator /> : null}

        {/* Navegación desde nav-config (claves i18n existentes en es.json). */}
        {secciones.map((seccion, indice) => {
          const heading = seccion.i18nKey
            ? t(`sections.${seccion.i18nKey}`)
            : undefined;
          return (
            <CommandGroup
              key={seccion.i18nKey ?? `seccion-${indice}`}
              heading={heading}
            >
              {seccion.items.map((item) => {
                const Icon = item.icon;
                const etiqueta = t(item.i18nKey);
                return (
                  <CommandItem
                    key={item.href}
                    value={`${heading ?? ""} ${etiqueta}`}
                    onSelect={() => irA(item.href)}
                  >
                    <Icon aria-hidden="true" />
                    <span>{etiqueta}</span>
                  </CommandItem>
                );
              })}
            </CommandGroup>
          );
        })}
      </CommandList>
    </CommandDialog>
  );
}
