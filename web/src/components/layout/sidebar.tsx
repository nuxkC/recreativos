import { getTranslations } from "next-intl/server";

import type { Rol } from "@/lib/auth/roles";
import type { EmpresaResumen } from "@/lib/empresas/types";

import { seccionesPermitidas } from "./nav-config";
import { SidebarLink } from "./sidebar-link";

interface SidebarProps {
  empresa: EmpresaResumen;
  rol: Rol;
}

export async function Sidebar({ empresa, rol }: SidebarProps) {
  const t = await getTranslations();
  const secciones = seccionesPermitidas(rol);

  return (
    <aside
      aria-label={t("layout.sidebarLabel")}
      className="hidden w-60 shrink-0 flex-col border-r bg-background md:flex"
    >
      <div className="border-b px-4 py-4">
        <p className="text-xs uppercase tracking-wide text-muted-foreground">{t("app.name")}</p>
        <p className="truncate text-sm font-semibold" title={empresa.nombre}>
          {empresa.nombre}
        </p>
        <p className="text-xs text-muted-foreground">{t(`roles.${rol}`)}</p>
      </div>
      <nav className="flex-1 overflow-y-auto px-2 py-3">
        {secciones.map((seccion, idx) => (
          <div key={seccion.i18nKey ?? `seccion-${idx}`} className="mb-4">
            {seccion.i18nKey ? (
              <p className="px-3 pb-1 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                {t(`nav.sections.${seccion.i18nKey}`)}
              </p>
            ) : null}
            <div className="flex flex-col gap-0.5">
              {seccion.items.map((item) => (
                <SidebarLink
                  key={item.href}
                  href={item.href}
                  label={t(`nav.${item.i18nKey}`)}
                  icon={<item.icon className="size-4" aria-hidden />}
                />
              ))}
            </div>
          </div>
        ))}
      </nav>
    </aside>
  );
}
