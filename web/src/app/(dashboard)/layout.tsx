import { redirect } from "next/navigation";
import type { ReactNode } from "react";

import { Sidebar } from "@/components/layout/sidebar";
import { Topbar } from "@/components/layout/topbar";
import { obtenerMembresiaActiva } from "@/lib/empresas/queries";
import { createClient } from "@/lib/supabase/server";

export default async function DashboardLayout({ children }: { children: ReactNode }) {
  const supabase = createClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();

  // El middleware ya protege estas rutas; defensa en profundidad.
  if (!user) {
    redirect("/login");
  }

  const { activa, membresias } = await obtenerMembresiaActiva();

  // Sin empresa válida en cookie y >1 membresía → forzar selección.
  // Con 0 membresías el usuario tampoco puede operar.
  if (!activa) {
    redirect(membresias.length === 0 ? "/sin-acceso" : "/seleccionar-empresa");
  }

  return (
    <div className="flex min-h-screen">
      <Sidebar empresa={activa.empresa} rol={activa.rol} />
      <div className="flex min-h-screen flex-1 flex-col">
        <Topbar
          email={user.email ?? ""}
          empresaActiva={activa.empresa}
          rolActivo={activa.rol}
          membresias={membresias}
        />
        <main className="flex-1 px-4 py-6 md:px-6">{children}</main>
      </div>
    </div>
  );
}
