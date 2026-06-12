import {
  AlertTriangle,
  BarChart3,
  Coins,
  Gamepad2,
  HandCoins,
  History,
  LayoutDashboard,
  Replace,
  ScrollText,
  Settings,
  Store,
  Users,
  Wrench,
  type LucideIcon,
} from "lucide-react";

import { ROLES, ROLES_ADMIN, ROLES_GESTION, type Rol } from "@/lib/auth/roles";
import { ROLES_INFORMES } from "@/lib/informes/permisos";

/**
 * Catálogo de entradas del sidebar. Cada item lleva qué roles pueden
 * verlo. La autorización efectiva la fuerza el backend (RLS + Edge);
 * esto es solo para la UI: nadie debería ver enlaces que llevan a
 * pantallas que la API rechazaría.
 */
export interface NavItem {
  href: string;
  /** Clave dentro de `nav.<key>` en `i18n/messages/es.json`. */
  i18nKey: string;
  icon: LucideIcon;
  roles: readonly Rol[];
}

export interface NavSection {
  /** Clave dentro de `nav.sections.<key>` (opcional). */
  i18nKey?: string;
  items: NavItem[];
}

export const NAV_SECTIONS: readonly NavSection[] = [
  {
    items: [
      {
        href: "/dashboard",
        i18nKey: "dashboard",
        icon: LayoutDashboard,
        roles: ROLES,
      },
    ],
  },
  {
    i18nKey: "inventario",
    items: [
      { href: "/licencias", i18nKey: "licencias", icon: ScrollText, roles: ROLES_GESTION },
      { href: "/maquinas", i18nKey: "maquinas", icon: Gamepad2, roles: ROLES_GESTION },
      { href: "/locales", i18nKey: "locales", icon: Store, roles: ROLES_GESTION },
      {
        href: "/instalaciones",
        i18nKey: "instalaciones",
        icon: Wrench,
        roles: ROLES_GESTION,
      },
    ],
  },
  {
    i18nKey: "operacion",
    items: [
      {
        href: "/recaudaciones",
        i18nKey: "recaudaciones",
        icon: Coins,
        roles: ROLES,
      },
      {
        href: "/deudas",
        i18nKey: "deudas",
        icon: HandCoins,
        roles: ROLES_GESTION,
      },
      {
        href: "/cambios-placa",
        i18nKey: "cambiosPlaca",
        icon: Replace,
        roles: ROLES,
      },
      {
        href: "/conflictos",
        i18nKey: "conflictos",
        icon: AlertTriangle,
        roles: ROLES_ADMIN,
      },
    ],
  },
  {
    i18nKey: "analitica",
    items: [
      {
        href: "/informes",
        i18nKey: "informes",
        icon: BarChart3,
        roles: ROLES_INFORMES,
      },
    ],
  },
  {
    i18nKey: "administracion",
    items: [
      { href: "/equipo", i18nKey: "equipo", icon: Users, roles: ROLES_ADMIN },
      { href: "/auditoria", i18nKey: "auditoria", icon: History, roles: ROLES_ADMIN },
      { href: "/ajustes", i18nKey: "ajustes", icon: Settings, roles: ROLES_ADMIN },
    ],
  },
];

export function seccionesPermitidas(rol: Rol): NavSection[] {
  return NAV_SECTIONS.map((seccion) => ({
    ...seccion,
    items: seccion.items.filter((item) => item.roles.includes(rol)),
  })).filter((seccion) => seccion.items.length > 0);
}
