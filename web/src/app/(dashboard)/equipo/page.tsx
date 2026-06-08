import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";

import { EquipoTable } from "@/components/equipo/equipo-table";
import { InvitarMiembro } from "@/components/equipo/invitar-miembro";
import { requireRol } from "@/lib/auth/guards";
import { ROLES_ADMIN } from "@/lib/auth/roles";
import { listarMiembros } from "@/lib/equipo/queries";
import { createClient } from "@/lib/supabase/server";

export default async function EquipoPage() {
  const activa = await requireRol(ROLES_ADMIN);
  const t = await getTranslations("equipo");
  const tNav = await getTranslations("nav");

  const supabase = createClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) redirect("/login");

  const miembros = await listarMiembros(activa.empresa.id, user.id);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-4">
        <div className="space-y-1">
          <h1 className="text-2xl font-semibold tracking-tight">{tNav("equipo")}</h1>
          <p className="text-sm text-muted-foreground">{tNav("descriptions.equipo")}</p>
        </div>
        <InvitarMiembro rolActivo={activa.rol} />
      </div>
      <EquipoTable miembros={miembros} rolActivo={activa.rol} />
      <p className="text-xs text-muted-foreground">{t("nota.invitaciones")}</p>
    </div>
  );
}
