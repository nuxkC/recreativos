import { useTranslations } from "next-intl";

import { Badge, type BadgeProps } from "@/components/ui/badge";
import type { Rol } from "@/lib/auth/roles";

const VARIANT_BY_ROL: Record<Rol, BadgeProps["variant"]> = {
  owner: "default",
  admin: "default",
  gestor: "secondary",
  tecnico: "secondary",
  contable: "secondary",
};

export function RolBadge({ rol }: { rol: Rol }) {
  const t = useTranslations("roles");
  return <Badge variant={VARIANT_BY_ROL[rol]}>{t(rol)}</Badge>;
}
