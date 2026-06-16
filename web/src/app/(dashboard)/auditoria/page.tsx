import { getTranslations } from "next-intl/server";

import { AuditoriaFilters } from "@/components/auditoria/auditoria-filters";
import { AuditoriaTable } from "@/components/auditoria/auditoria-table";
import { requireRol } from "@/lib/auth/guards";
import { ROLES_ADMIN } from "@/lib/auth/roles";
import { listarEventosAuditoria } from "@/lib/auditoria/queries";
import {
  type AccionAuditoria,
  type EntidadAuditoria,
  isAccionAuditoria,
  isEntidadAuditoria,
} from "@/lib/auditoria/types";

const ISO_DATE_REGEX = /^\d{4}-\d{2}-\d{2}$/;

interface AuditoriaPageProps {
  searchParams: Promise<{
    accion?: string;
    entidad?: string;
    desde?: string;
    hasta?: string;
  }>;
}

export default async function AuditoriaPage(props: AuditoriaPageProps) {
  const searchParams = await props.searchParams;
  // La bitácora es información sensible: solo owner/admin (RLS lo refuerza).
  const activa = await requireRol(ROLES_ADMIN);
  const tNav = await getTranslations("nav");

  const accionParam: AccionAuditoria | null =
    searchParams.accion && isAccionAuditoria(searchParams.accion) ? searchParams.accion : null;
  const entidadParam: EntidadAuditoria | null =
    searchParams.entidad && isEntidadAuditoria(searchParams.entidad) ? searchParams.entidad : null;
  const desdeParam =
    searchParams.desde && ISO_DATE_REGEX.test(searchParams.desde) ? searchParams.desde : null;
  const hastaParam =
    searchParams.hasta && ISO_DATE_REGEX.test(searchParams.hasta) ? searchParams.hasta : null;

  const eventos = await listarEventosAuditoria(activa.empresa.id, {
    accion: accionParam,
    entidad: entidadParam,
    desde: desdeParam,
    hasta: hastaParam,
  });

  return (
    <div className="space-y-4">
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">{tNav("auditoria")}</h1>
        <p className="text-sm text-muted-foreground">{tNav("descriptions.auditoria")}</p>
      </div>
      <AuditoriaFilters
        accionInicial={accionParam}
        entidadInicial={entidadParam}
        desdeInicial={desdeParam}
        hastaInicial={hastaParam}
      />
      <AuditoriaTable eventos={eventos} />
    </div>
  );
}
