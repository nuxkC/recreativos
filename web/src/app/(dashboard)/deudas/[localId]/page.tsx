import Link from "next/link";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { z } from "zod";

import { DeudasLocal } from "@/components/deudas/deudas-local";
import { rolCumple, requireRol } from "@/lib/auth/guards";
import { ROLES_ADMIN, ROLES_GESTION } from "@/lib/auth/roles";
import {
  listarCreditosLocal,
  listarRecuperacionesLocal,
  obtenerPorcentajeRecuperacionEmpresa,
  obtenerSaldoLocal,
} from "@/lib/deudas/queries";
import { obtenerLocal } from "@/lib/locales/queries";

const IdSchema = z.string().guid();

interface DeudasLocalPageProps {
  params: Promise<{ localId: string }>;
}

/**
 * Página de gestión de deuda de un local dentro de la sección Deudas (T-218).
 * Centro de mando completo: saldo, deudas abiertas/cerradas, libro mayor,
 * nuevo préstamo, pago en efectivo, condonación (admin) y % de recuperación.
 * Es a donde redirige el detalle del local para todo lo que sea gestión.
 */
export default async function DeudasLocalPage(props: DeudasLocalPageProps) {
  const params = await props.params;
  const activa = await requireRol(ROLES_GESTION);
  const t = await getTranslations("deudas");

  const idParsed = IdSchema.safeParse(params.localId);
  if (!idParsed.success) {
    notFound();
  }

  const local = await obtenerLocal(activa.empresa.id, idParsed.data);
  if (!local) {
    notFound();
  }

  const [saldo, creditos, recuperaciones, porcentajeEmpresa] = await Promise.all([
    obtenerSaldoLocal(activa.empresa.id, local.id),
    listarCreditosLocal(activa.empresa.id, local.id),
    listarRecuperacionesLocal(activa.empresa.id, local.id),
    obtenerPorcentajeRecuperacionEmpresa(activa.empresa.id),
  ]);
  const esAdmin = rolCumple(activa.rol, ROLES_ADMIN);

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="space-y-1">
        <Link href="/deudas" className="text-sm text-muted-foreground hover:underline">
          ← {t("seccion.volver")}
        </Link>
        <h1 className="text-2xl font-semibold tracking-tight">{local.nombre}</h1>
        <p className="text-sm text-muted-foreground">{local.direccion ?? "—"}</p>
      </div>

      <DeudasLocal
        localId={local.id}
        saldo={saldo}
        creditos={creditos}
        recuperaciones={recuperaciones}
        porcentajeEmpresa={porcentajeEmpresa}
        porcentajeLocal={local.porcentajeRecuperacion}
        esAdmin={esAdmin}
      />
    </div>
  );
}
