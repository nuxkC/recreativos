"use client";

import { Loader2, Plus } from "lucide-react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ROLES, type Rol } from "@/lib/auth/roles";
import { invitarMiembro } from "@/lib/equipo/actions";

interface InvitarMiembroProps {
  /** El owner es el único que puede invitar a otro owner. */
  rolActivo: Rol;
}

export function InvitarMiembro({ rolActivo }: InvitarMiembroProps) {
  const t = useTranslations("equipo");
  const tValidacion = useTranslations("equipo.validacion");
  const tErrores = useTranslations("equipo.errores");
  const tRoles = useTranslations("roles");
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [pending, startTransition] = useTransition();
  const [email, setEmail] = useState("");
  const [nombreCompleto, setNombreCompleto] = useState("");
  const [rol, setRol] = useState<Rol>("tecnico");
  const [errors, setErrors] = useState<Record<string, string>>({});

  const rolesDisponibles = rolActivo === "owner" ? ROLES : ROLES.filter((r) => r !== "owner");

  function reset() {
    setEmail("");
    setNombreCompleto("");
    setRol("tecnico");
    setErrors({});
  }

  function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrors({});
    const fd = new FormData();
    fd.set("email", email.trim());
    fd.set("nombreCompleto", nombreCompleto.trim());
    fd.set("rol", rol);

    startTransition(async () => {
      const result = await invitarMiembro(null, fd);
      if (!result.ok) {
        const fieldErrors = result.error.fieldErrors;
        if (fieldErrors) {
          const nuevos: Record<string, string> = {};
          for (const [field, codes] of Object.entries(fieldErrors)) {
            const code = codes[0];
            if (!code) continue;
            nuevos[field] = tValidacion.has(code) ? tValidacion(code) : code;
          }
          setErrors(nuevos);
          return;
        }
        const code = result.error.code;
        toast.error(tErrores.has(code) ? tErrores(code) : tErrores("desconocido"));
        return;
      }
      toast.success(t("invitar.ok"));
      setOpen(false);
      reset();
      router.refresh();
    });
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(o) => {
        setOpen(o);
        if (!o) reset();
      }}
    >
      <DialogTrigger asChild>
        <Button className="gap-2">
          <Plus className="size-4" aria-hidden />
          {t("accion.invitar")}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("invitar.titulo")}</DialogTitle>
          <DialogDescription>{t("invitar.descripcion")}</DialogDescription>
        </DialogHeader>
        <form onSubmit={onSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="invitar-email">{t("campos.email")}</Label>
            <Input
              id="invitar-email"
              type="email"
              autoComplete="off"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="persona@empresa.com"
              aria-invalid={errors.email ? "true" : undefined}
            />
            {errors.email ? (
              <p className="text-[0.8rem] font-medium text-destructive">{errors.email}</p>
            ) : null}
          </div>
          <div className="space-y-2">
            <Label htmlFor="invitar-nombre">{t("campos.nombreCompleto")}</Label>
            <Input
              id="invitar-nombre"
              type="text"
              autoComplete="off"
              maxLength={150}
              value={nombreCompleto}
              onChange={(e) => setNombreCompleto(e.target.value)}
              placeholder={t("placeholders.nombreCompleto")}
            />
            {errors.nombreCompleto ? (
              <p className="text-[0.8rem] font-medium text-destructive">{errors.nombreCompleto}</p>
            ) : null}
          </div>
          <div className="space-y-2">
            <Label htmlFor="invitar-rol">{t("campos.rol")}</Label>
            <Select value={rol} onValueChange={(v) => setRol(v as Rol)}>
              <SelectTrigger id="invitar-rol">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {rolesDisponibles.map((r) => (
                  <SelectItem key={r} value={r}>
                    {tRoles(r)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {errors.rol ? (
              <p className="text-[0.8rem] font-medium text-destructive">{errors.rol}</p>
            ) : null}
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setOpen(false)}
              disabled={pending}
            >
              {t("accion.cancelar")}
            </Button>
            <Button type="submit" disabled={pending} className="gap-2">
              {pending ? (
                <Loader2 className="size-4 animate-spin" aria-hidden />
              ) : (
                <Plus className="size-4" aria-hidden />
              )}
              <span>{t("accion.invitarConfirmar")}</span>
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
