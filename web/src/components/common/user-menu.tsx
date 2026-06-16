"use client";

// UserMenu + Avatar (TopBar web) — átomo F3-NAV-AVATAR-USERMENU.
//
// Identidad y sesión en el trailing de la TopBar. El Avatar (foto, iniciales o
// icono neutro) es el TRIGGER de un DropdownMenu (Radix) con: cabecera
// nombre+rol+email, selector de tema (claro/oscuro/sistema), Ajustes, y
// "Cerrar sesión" como acción DESTRUCTIVA que SIEMPRE pasa por confirmación.
//
// El Avatar es identidad, NUNCA estado de color: su relleno es `secondary`
// (marca tonal, con identidad) o `surface-2` (neutro, sin identidad); jamás
// success/warning/danger. El rol es metadato neutro (texto muted), nunca chip.
//
// Correcciones respecto al boceto del spec (verificadas contra el codebase):
//  - Confirmación: se usa <ConfirmDialog> (átomo real en common/, controlado por
//    estado) en lugar del inexistente <ConfirmButton>. El diálogo se monta FUERA
//    del DropdownMenuContent: así sobrevive al cierre del menú al activar el item
//    (el patrón <DropdownMenuItem asChild><ConfirmButton>> del boceto NUNCA
//    abriría el diálogo porque el item desmonta el menú en onSelect).
//  - `text-muted` del boceto renderiza la SUPERFICIE muted como texto; el token
//    de TEXTO neutro real es `text-muted-foreground` (== state-neutral): se usa
//    ese para rol, email e iconos.
//  - El resalte por teclado del item destructivo va por `data-[highlighted]`
//    (Radix), no por `hover:` (que solo cubre ratón): así el item más peligroso
//    muestra su fondo también navegando con flechas.
//  - El trigger es un <button> de 32px (no Button size="icon" = 36px) para que
//    el anillo de foco coincida con la caja visual declarada.

import { LogOut, Monitor, Moon, RefreshCcw, Settings, Sun, User } from "lucide-react";
import { useTranslations } from "next-intl";
import { useTheme } from "next-themes";
import Link from "next/link";
import * as React from "react";

import { ConfirmDialog } from "@/components/common/confirm-dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";

interface UserMenuProps {
  /** Nombre para mostrar; si falta, se usa el email como identidad. */
  nombre: string | null;
  email: string;
  /** Etiqueta de rol YA traducida (Propietario/Gestor/Técnico…). Metadato
   *  neutro: se pinta como texto muted, NUNCA como chip de marca/estado. */
  rol: string;
  /** URL de la foto; si no carga o no existe, cae a iniciales. */
  fotoUrl?: string | null;
  /** Nº de empresas del usuario; >1 habilita "Cambiar de empresa". */
  totalMembresias?: number;
  /** Cierra la sesión (POST /auth/signout). Lo provee el contenedor; el átomo
   *  NO ejecuta nada sin pasar antes por la confirmación. */
  onSignOut: () => void;
  /** Limpia la empresa activa para volver al selector. Requerido si
   *  totalMembresias > 1. */
  onCambiarEmpresa?: () => void;
  /** Cambio de empresa en curso: deshabilita el item. */
  cambioEmpresaPendiente?: boolean;
}

/** Iniciales 1–2 letras del nombre; si no hay nombre, primera letra del email. */
function iniciales(nombre: string | null, email: string): string {
  const base = (nombre ?? "").trim();
  if (base) {
    return base
      .split(/\s+/)
      .slice(0, 2)
      .map((parte) => parte.charAt(0))
      .join("")
      .toUpperCase();
  }
  return email.charAt(0).toUpperCase();
}

/**
 * Avatar reutilizable (trigger 32px / cabecera 36px). Prioridad de contenido:
 * (1) foto si carga; (2) iniciales (primary sobre secondary tonal);
 * (3) icono User neutro (muted sobre surface-2) si no hay identidad.
 * Decorativo: alt="" y aria-hidden — la identidad la aporta el aria-label
 * del trigger y la cabecera del menú, nunca la sola imagen.
 */
function Avatar({
  nombre,
  email,
  fotoUrl,
  size = "trigger",
}: {
  nombre: string | null;
  email: string;
  fotoUrl?: string | null;
  size?: "trigger" | "header";
}) {
  const [fotoOk, setFotoOk] = React.useState(Boolean(fotoUrl));
  const px = size === "header" ? "size-9" : "size-8";
  const hayIdentidad = Boolean((nombre ?? "").trim() || email);

  if (fotoUrl && fotoOk) {
    return (
      <img
        src={fotoUrl}
        alt=""
        aria-hidden
        loading="lazy"
        onError={() => setFotoOk(false)}
        // El cross-fade de aparición se suprime en reduced-motion (motion-reduce).
        className={cn(
          px,
          "rounded-full object-cover",
          "motion-safe:transition-opacity motion-safe:duration-150",
        )}
      />
    );
  }

  if (hayIdentidad) {
    // Iniciales: marca tonal de identidad (único uso legítimo de `secondary`
    // aquí). NO es un estado de color.
    return (
      <span
        aria-hidden
        className={cn(
          px,
          "flex items-center justify-center rounded-full",
          "bg-secondary text-primary text-[13px] leading-none font-semibold",
        )}
      >
        {iniciales(nombre, email)}
      </span>
    );
  }

  // Sin identidad → neutro (surface-2 + muted), nunca color de estado.
  return (
    <span
      aria-hidden
      className={cn(
        px,
        "bg-surface-2 text-muted-foreground flex items-center justify-center rounded-full",
      )}
    >
      <User className="size-4" />
    </span>
  );
}

export function UserMenu({
  nombre,
  email,
  rol,
  fotoUrl,
  totalMembresias = 1,
  onSignOut,
  onCambiarEmpresa,
  cambioEmpresaPendiente = false,
}: UserMenuProps) {
  const t = useTranslations();
  const { theme, setTheme } = useTheme();
  // Antes de montar, theme es indeterminado en SSR → value=undefined evita el
  // mismatch de hidratación (ningún radio marcado hasta montar).
  const [mounted, setMounted] = React.useState(false);
  const [confirmarSalida, setConfirmarSalida] = React.useState(false);
  React.useEffect(() => setMounted(true), []);

  const nombreVisible = nombre ?? email;
  // El placeholder {nombre} no existe en la clave i18n: se compone el label
  // completo aquí para que el lector anuncie la identidad desde el trigger.
  const triggerLabel = `${t("layout.userMenu")}, ${nombreVisible}`;

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          {/* Caja visual 32px; área pulsable ≥44px vía ::before invisible
              (inset-[-6px]) sin alterar el layout de 56px de la barra. El
              anillo de foco se dibuja sobre la caja de 32px, no sobre el área
              táctil. Avatar = identidad: sin sombra y sin chevron. */}
          <button
            type="button"
            aria-label={triggerLabel}
            aria-haspopup="menu"
            className={cn(
              "relative inline-flex size-8 items-center justify-center rounded-full p-0",
              "outline-hidden transition-colors motion-safe:duration-150",
              "before:absolute before:inset-[-6px] before:content-['']",
              "hover:bg-surface-2/60",
              "focus-visible:ring-ring focus-visible:ring-2 focus-visible:ring-offset-2",
            )}
          >
            <Avatar nombre={nombre} email={email} fotoUrl={fotoUrl} size="trigger" />
          </button>
        </DropdownMenuTrigger>

        <DropdownMenuContent
          align="end"
          sideOffset={8}
          className="shadow-overlay max-w-80 min-w-60 rounded-xl"
        >
          {/* Cabecera no interactiva: identidad (presentational). */}
          <DropdownMenuLabel className="font-normal">
            <div className="flex items-center gap-3 py-1">
              <Avatar nombre={nombre} email={email} fotoUrl={fotoUrl} size="header" />
              <div className="flex min-w-0 flex-col">
                <span className="text-foreground truncate text-sm font-semibold">
                  {nombreVisible}
                </span>
                {/* Rol = metadato neutro (muted), nunca chip de marca/estado. */}
                <span className="text-muted-foreground truncate text-xs">{rol}</span>
                {nombre ? (
                  <span className="text-muted-foreground truncate text-xs">{email}</span>
                ) : null}
              </div>
            </div>
          </DropdownMenuLabel>

          <DropdownMenuSeparator />

          {/* Tema: reutiliza el contrato del átomo ThemeToggle (claro/oscuro/
              sistema). El icono se aloja tras el pl-8 reservado al indicador. */}
          <DropdownMenuLabel className="text-muted-foreground px-2 py-1 text-xs font-medium tracking-wide uppercase">
            {t("tema.titulo")}
          </DropdownMenuLabel>
          <DropdownMenuRadioGroup value={mounted ? theme : undefined} onValueChange={setTheme}>
            <DropdownMenuRadioItem value="light" className="cursor-pointer gap-2">
              <Sun className="text-muted-foreground size-4" aria-hidden />
              <span>{t("tema.claro")}</span>
            </DropdownMenuRadioItem>
            <DropdownMenuRadioItem value="dark" className="cursor-pointer gap-2">
              <Moon className="text-muted-foreground size-4" aria-hidden />
              <span>{t("tema.oscuro")}</span>
            </DropdownMenuRadioItem>
            <DropdownMenuRadioItem value="system" className="cursor-pointer gap-2">
              <Monitor className="text-muted-foreground size-4" aria-hidden />
              <span>{t("tema.sistema")}</span>
            </DropdownMenuRadioItem>
          </DropdownMenuRadioGroup>

          <DropdownMenuSeparator />

          {totalMembresias > 1 && onCambiarEmpresa ? (
            <DropdownMenuItem
              disabled={cambioEmpresaPendiente}
              className="cursor-pointer"
              onSelect={(event) => {
                // Evita cerrar el menú con el cambio en vuelo (item disabled).
                event.preventDefault();
                onCambiarEmpresa();
              }}
            >
              <RefreshCcw className="text-muted-foreground size-4" aria-hidden />
              <span>{t("layout.cambiarEmpresa")}</span>
            </DropdownMenuItem>
          ) : null}

          <DropdownMenuItem asChild className="cursor-pointer">
            <Link href="/ajustes">
              <Settings className="text-muted-foreground size-4" aria-hidden />
              <span>{t("layout.ajustes")}</span>
            </Link>
          </DropdownMenuItem>

          <DropdownMenuSeparator />

          {/* Cerrar sesión: destructivo → NUNCA ejecuta directo. onSelect abre
              el ConfirmDialog (foco inicial en Cancelar). Texto/icono en
              danger-text (AA sobre la superficie del menú); NO fill danger.
              El resalte usa data-[highlighted] (ratón Y teclado), no hover. */}
          <DropdownMenuItem
            onSelect={() => {
              // El menú se cierra (comportamiento por defecto); el ConfirmDialog
              // vive fuera del menú, así que sobrevive y se abre por estado.
              setConfirmarSalida(true);
            }}
            className={cn(
              "text-danger-text cursor-pointer",
              "focus:text-danger-text data-highlighted:text-danger-text",
              "data-highlighted:bg-danger/10 dark:data-highlighted:bg-danger/15",
            )}
          >
            <LogOut className="size-4" aria-hidden />
            <span>{t("auth.signOut")}</span>
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>

      {/* Confirmación destructiva (átomo real): controlada por estado y montada
          FUERA del menú. Foco inicial en Cancelar; Esc/scrim cancela. */}
      <ConfirmDialog
        open={confirmarSalida}
        onOpenChange={setConfirmarSalida}
        title="¿Cerrar sesión?"
        description="Saldrás de tu cuenta en este dispositivo y volverás a la pantalla de acceso."
        confirmLabel={t("auth.signOut")}
        cancelLabel={t("common.cancelar")}
        onConfirm={() => {
          setConfirmarSalida(false);
          onSignOut();
        }}
      />
    </>
  );
}

export type { UserMenuProps };
