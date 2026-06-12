import { format, parseISO } from "date-fns";
import { es } from "date-fns/locale";
import { useTranslations } from "next-intl";

import { AveriaDialog } from "@/components/averias/averia-dialog";
import { EstadoAveriaBadge } from "@/components/averias/estado-averia-badge";
import { RecambiosAveria } from "@/components/averias/recambios-averia";
import { ResolverAveria } from "@/components/averias/resolver-averia";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { type Averia, averiaAbierta } from "@/lib/averias/types";

interface HistorialAveriasProps {
  maquinaId: string;
  averias: Averia[];
  /** La máquina tiene instalación activa: habilita registrar merma de tolva. */
  maquinaInstalada: boolean;
}

function formatFecha(iso: string | null): string {
  if (!iso) return "—";
  try {
    return format(parseISO(iso), "dd/MM/yyyy HH:mm", { locale: es });
  } catch {
    return iso;
  }
}

export function HistorialAverias({ maquinaId, averias, maquinaInstalada }: HistorialAveriasProps) {
  const t = useTranslations("averias");
  const tCategoria = useTranslations("averias.categoria");

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between gap-4 space-y-0">
        <div className="space-y-1">
          <CardTitle className="text-lg">{t("historial.titulo")}</CardTitle>
          <p className="text-sm text-muted-foreground">{t("historial.descripcion")}</p>
        </div>
        <AveriaDialog maquinaId={maquinaId} maquinaInstalada={maquinaInstalada} />
      </CardHeader>
      <CardContent>
        {averias.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t("historial.vacio")}</p>
        ) : (
          <ul className="space-y-4">
            {averias.map((averia) => {
              const abierta = averiaAbierta(averia);
              return (
                <li key={averia.id} className="rounded-lg border p-4">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <EstadoAveriaBadge estado={averia.estado} />
                      <Badge variant="outline">{tCategoria(averia.categoria)}</Badge>
                      {averia.poneMaquinaFueraServicio && abierta ? (
                        <Badge variant="destructive">{t("etiqueta.fueraServicio")}</Badge>
                      ) : null}
                    </div>
                    <span className="text-sm text-muted-foreground">
                      {formatFecha(averia.fechaReporte)}
                    </span>
                  </div>

                  {averia.descripcion ? <p className="mt-2 text-sm">{averia.descripcion}</p> : null}

                  <p className="mt-2 text-xs text-muted-foreground">
                    {averia.localNombre
                      ? t("meta.enLocal", { local: averia.localNombre })
                      : t("meta.enAlmacen")}
                    {averia.estado === "resuelta"
                      ? ` · ${t("meta.resueltaEl", { fecha: formatFecha(averia.fechaResolucion) })}`
                      : null}
                  </p>

                  {averia.notas ? (
                    <p className="mt-2 whitespace-pre-line text-xs text-muted-foreground">
                      {averia.notas}
                    </p>
                  ) : null}

                  <div className="mt-3 border-t pt-3">
                    <RecambiosAveria
                      averiaId={averia.id}
                      maquinaId={maquinaId}
                      recambios={averia.recambios}
                      editable={abierta}
                    />
                  </div>

                  {abierta ? (
                    <div className="mt-3 flex flex-wrap gap-2">
                      <AveriaDialog maquinaId={maquinaId} averia={averia} />
                      <ResolverAveria averiaId={averia.id} maquinaId={maquinaId} />
                    </div>
                  ) : null}
                </li>
              );
            })}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
