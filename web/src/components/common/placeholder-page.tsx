import type { LucideIcon } from "lucide-react";

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

interface PlaceholderPageProps {
  title: string;
  description: string;
  taskRef: string;
  icon?: LucideIcon;
}

/**
 * Placeholder reutilizable para las secciones del back-office cuyo CRUD
 * todavía no se ha implementado. Cada T-3X que las construya sustituye el
 * `page.tsx` correspondiente y este componente seguirá disponible para
 * cualquier nueva sección (p. ej. Informes en T-103).
 */
export function PlaceholderPage({ title, description, taskRef, icon: Icon }: PlaceholderPageProps) {
  return (
    <div className="space-y-4">
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        <p className="text-sm text-muted-foreground">{description}</p>
      </div>
      <Card className="max-w-xl border-dashed">
        <CardHeader className="flex-row items-center gap-3 space-y-0">
          {Icon ? <Icon className="size-5 text-muted-foreground" aria-hidden /> : null}
          <div className="space-y-1">
            <CardTitle className="text-base">Pendiente</CardTitle>
            <CardDescription>Esta sección se construye en {taskRef}.</CardDescription>
          </div>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          Mientras tanto, el layout, la navegación y la sesión por empresa ya están listos para que
          los siguientes PRs solo tengan que añadir la lógica de datos y formularios.
        </CardContent>
      </Card>
    </div>
  );
}
