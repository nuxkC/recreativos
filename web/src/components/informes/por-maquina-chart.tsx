"use client";

import { useTranslations } from "next-intl";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import { formatEuros, formatEurosCompacto } from "@/lib/informes/format";
import type { AgregadoPorMaquina } from "@/lib/informes/types";

import { ChartCard } from "./chart-card";
import { ChartDataTable, type ColumnaTabla, type FilaTabla } from "./chart-data-table";
import { ChartTooltip } from "./chart-tooltip";

const COLOR_BRUTO = "#2563eb";
const COLOR_PARTE_EMPRESA = "#059669";

interface PorMaquinaChartProps {
  datos: AgregadoPorMaquina[];
}

export function PorMaquinaChart({ datos }: PorMaquinaChartProps) {
  const t = useTranslations("informes");
  const tTabla = useTranslations("informes.tabla");

  const serie = datos.map((d) => ({
    maquina: d.maquinaEtiqueta,
    bruto: d.brutoTotal,
    parteEmpresa: d.parteEmpresaTotal,
  }));
  const altura = Math.max(220, datos.length * 48 + 40);

  const columnas: ColumnaTabla[] = [
    { key: "maquina", label: tTabla("maquina") },
    { key: "bruto", label: tTabla("bruto"), numerica: true },
    { key: "parteEmpresa", label: tTabla("parteEmpresa"), numerica: true },
    { key: "neto", label: tTabla("neto"), numerica: true },
    { key: "recaudaciones", label: tTabla("recaudaciones"), numerica: true },
  ];
  const filas: FilaTabla[] = datos.map((d) => ({
    id: d.maquinaId,
    celdas: {
      maquina: d.maquinaEtiqueta,
      bruto: formatEuros(d.brutoTotal),
      parteEmpresa: formatEuros(d.parteEmpresaTotal),
      neto: formatEuros(d.netoTotal),
      recaudaciones: String(d.numRecaudaciones),
    },
  }));

  return (
    <ChartCard
      title={t("porMaquina.titulo")}
      description={t("porMaquina.descripcion")}
      isEmpty={datos.length === 0}
    >
      <figure className="text-muted-foreground">
        <figcaption className="sr-only">{t("porMaquina.aria")}</figcaption>
        <div role="img" aria-label={t("porMaquina.aria")} style={{ width: "100%", height: altura }}>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart
              data={serie}
              layout="vertical"
              margin={{ top: 8, right: 16, bottom: 0, left: 8 }}
            >
              <CartesianGrid
                horizontal={false}
                strokeDasharray="3 3"
                stroke="currentColor"
                strokeOpacity={0.15}
              />
              <XAxis
                type="number"
                tickFormatter={formatEurosCompacto}
                stroke="currentColor"
                tick={{ fontSize: 12, fill: "currentColor" }}
                tickLine={false}
              />
              <YAxis
                type="category"
                dataKey="maquina"
                width={160}
                stroke="currentColor"
                tick={{ fontSize: 12, fill: "currentColor" }}
                tickLine={false}
              />
              <Tooltip
                content={<ChartTooltip />}
                cursor={{ fill: "currentColor", fillOpacity: 0.08 }}
              />
              <Legend wrapperStyle={{ fontSize: 12 }} />
              <Bar
                dataKey="bruto"
                name={t("porMaquina.bruto")}
                fill={COLOR_BRUTO}
                radius={[0, 4, 4, 0]}
              />
              <Bar
                dataKey="parteEmpresa"
                name={t("porMaquina.parteEmpresa")}
                fill={COLOR_PARTE_EMPRESA}
                radius={[0, 4, 4, 0]}
              />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </figure>
      <ChartDataTable caption={t("porMaquina.descripcion")} columnas={columnas} filas={filas} />
    </ChartCard>
  );
}
