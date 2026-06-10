"use client";

import { useTranslations } from "next-intl";
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import {
  formatEuros,
  formatEurosCompacto,
  formatMesCorto,
  formatMesLargo,
} from "@/lib/informes/format";
import type { PuntoEvolucionMes } from "@/lib/informes/types";

import { ChartCard } from "./chart-card";
import { ChartDataTable, type ColumnaTabla, type FilaTabla } from "./chart-data-table";
import { ChartTooltip } from "./chart-tooltip";

const COLOR_BRUTO = "#2563eb";
const COLOR_PARTE_EMPRESA = "#059669";

interface EvolucionChartProps {
  datos: PuntoEvolucionMes[];
}

export function EvolucionChart({ datos }: EvolucionChartProps) {
  const t = useTranslations("informes");
  const tTabla = useTranslations("informes.tabla");

  const serie = datos.map((p) => ({
    mes: p.mes,
    bruto: p.brutoTotal,
    parteEmpresa: p.parteEmpresaTotal,
  }));

  const columnas: ColumnaTabla[] = [
    { key: "mes", label: tTabla("mes") },
    { key: "bruto", label: tTabla("bruto"), numerica: true },
    { key: "parteEmpresa", label: tTabla("parteEmpresa"), numerica: true },
    { key: "neto", label: tTabla("neto"), numerica: true },
    { key: "recaudaciones", label: tTabla("recaudaciones"), numerica: true },
  ];
  const filas: FilaTabla[] = datos.map((p) => ({
    id: p.mes,
    celdas: {
      mes: formatMesLargo(p.mes),
      bruto: formatEuros(p.brutoTotal),
      parteEmpresa: formatEuros(p.parteEmpresaTotal),
      neto: formatEuros(p.netoTotal),
      recaudaciones: String(p.numRecaudaciones),
    },
  }));

  return (
    <ChartCard
      title={t("evolucion.titulo")}
      description={t("evolucion.descripcion")}
      isEmpty={datos.length === 0}
    >
      <figure className="text-muted-foreground">
        <figcaption className="sr-only">{t("evolucion.aria")}</figcaption>
        <div role="img" aria-label={t("evolucion.aria")} style={{ width: "100%", height: 320 }}>
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={serie} margin={{ top: 8, right: 16, bottom: 0, left: 8 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="currentColor" strokeOpacity={0.15} />
              <XAxis
                dataKey="mes"
                tickFormatter={formatMesCorto}
                stroke="currentColor"
                tick={{ fontSize: 12, fill: "currentColor" }}
                tickLine={false}
              />
              <YAxis
                tickFormatter={formatEurosCompacto}
                stroke="currentColor"
                tick={{ fontSize: 12, fill: "currentColor" }}
                tickLine={false}
                width={72}
              />
              <Tooltip
                content={<ChartTooltip labelFormatter={formatMesLargo} />}
                cursor={{ stroke: "currentColor", strokeOpacity: 0.2 }}
              />
              <Legend wrapperStyle={{ fontSize: 12 }} />
              <Line
                type="monotone"
                dataKey="bruto"
                name={t("evolucion.bruto")}
                stroke={COLOR_BRUTO}
                strokeWidth={2}
                dot={false}
                activeDot={{ r: 4 }}
              />
              <Line
                type="monotone"
                dataKey="parteEmpresa"
                name={t("evolucion.parteEmpresa")}
                stroke={COLOR_PARTE_EMPRESA}
                strokeWidth={2}
                dot={false}
                activeDot={{ r: 4 }}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </figure>
      <ChartDataTable caption={t("evolucion.descripcion")} columnas={columnas} filas={filas} />
    </ChartCard>
  );
}
