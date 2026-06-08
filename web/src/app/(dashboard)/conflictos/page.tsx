import { getTranslations } from "next-intl/server";

import { ConflictosTable } from "@/components/conflictos/conflictos-table";
import { Badge } from "@/components/ui/badge";
import { requireRol } from "@/lib/auth/guards";
import { ROLES_ADMIN } from "@/lib/auth/roles";
import { listarConflictos } from "@/lib/conflictos/queries";

export default async function ConflictosPage() {
  // El sidebar ya filtra esta sección a admin+, pero el guard hace
  // explícito el requisito por si llegan vía URL directa.
  const activa = await requireRol(ROLES_ADMIN);
  const tNav = await getTranslations("nav");
  const t = await getTranslations("conflictos");

  const conflictos = await listarConflictos(activa.empresa.id, {
    soloPendientes: true,
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-4">
        <div className="space-y-1">
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-semibold tracking-tight">{tNav("conflictos")}</h1>
            {conflictos.length > 0 ? (
              <Badge variant="warning">
                {t("contadorPendientes", { count: conflictos.length })}
              </Badge>
            ) : null}
          </div>
          <p className="text-sm text-muted-foreground">{tNav("descriptions.conflictos")}</p>
        </div>
      </div>
      <ConflictosTable conflictos={conflictos} />
    </div>
  );
}
