import type { Rol } from "@/lib/auth/roles";
import type { EmpresaResumen, Membresia } from "@/lib/empresas/types";

import { ThemeToggle } from "@/components/theme/theme-toggle";

import { EmpresaSwitcher } from "./empresa-switcher";
import { UserMenu } from "./user-menu";

interface TopbarProps {
  email: string;
  empresaActiva: EmpresaResumen;
  rolActivo: Rol;
  membresias: Membresia[];
}

export function Topbar({ email, empresaActiva, rolActivo, membresias }: TopbarProps) {
  return (
    <header className="flex h-14 shrink-0 items-center justify-between gap-3 border-b bg-background px-4">
      <EmpresaSwitcher
        empresaActivaId={empresaActiva.id}
        empresaActivaNombre={empresaActiva.nombre}
        rolActivo={rolActivo}
        membresias={membresias}
      />
      <div className="flex items-center gap-1">
        <ThemeToggle />
        <UserMenu email={email} totalMembresias={membresias.length} />
      </div>
    </header>
  );
}
