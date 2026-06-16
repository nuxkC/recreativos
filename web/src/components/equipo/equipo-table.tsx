"use client";

import { Loader2, MoreHorizontal, ShieldOff, UserCog, UserPlus } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { ROLES, type Rol } from "@/lib/auth/roles";
import { cambiarActivo, cambiarRol } from "@/lib/equipo/actions";
import type { MiembroEquipo } from "@/lib/equipo/types";

import { RolBadge } from "./rol-badge";

interface EquipoTableProps {
  miembros: MiembroEquipo[];
  rolActivo: Rol;
}

export function EquipoTable({ miembros, rolActivo }: EquipoTableProps) {
  const t = useTranslations("equipo");
  const tRoles = useTranslations("roles");
  const tErrores = useTranslations("equipo.errores");
  const [pendingId, setPendingId] = useState<string | null>(null);
  const [, startTransition] = useTransition();
  const [confirmDesactivar, setConfirmDesactivar] = useState<MiembroEquipo | null>(null);

  function onCambiarRol(miembro: MiembroEquipo, nuevoRol: Rol) {
    if (nuevoRol === miembro.rol) return;
    setPendingId(miembro.usuarioId);
    startTransition(async () => {
      try {
        const fd = new FormData();
        fd.set("rol", nuevoRol);
        const result = await cambiarRol(miembro.usuarioId, null, fd);
        if (!result.ok) {
          const code = result.error.code;
          toast.error(tErrores.has(code) ? tErrores(code) : tErrores("desconocido"));
          return;
        }
        toast.success(t("rolCambiadoOk"));
      } finally {
        setPendingId(null);
      }
    });
  }

  function onCambiarActivo(miembro: MiembroEquipo, activo: boolean) {
    setPendingId(miembro.usuarioId);
    startTransition(async () => {
      try {
        const result = await cambiarActivo(miembro.usuarioId, activo);
        if (!result.ok) {
          const code = result.error.code;
          toast.error(tErrores.has(code) ? tErrores(code) : tErrores("desconocido"));
          return;
        }
        toast.success(activo ? t("reactivadoOk") : t("desactivadoOk"));
      } finally {
        setPendingId(null);
        setConfirmDesactivar(null);
      }
    });
  }

  if (miembros.length === 0) {
    return (
      <div className="rounded-md border border-dashed p-8 text-center text-sm text-muted-foreground">
        {t("vacio")}
      </div>
    );
  }

  const yoSoyOwner = rolActivo === "owner";

  return (
    <>
      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t("campos.nombre")}</TableHead>
              <TableHead className="hidden md:table-cell">{t("campos.email")}</TableHead>
              <TableHead className="hidden lg:table-cell">{t("campos.telefono")}</TableHead>
              <TableHead>{t("campos.rol")}</TableHead>
              <TableHead>{t("campos.estado")}</TableHead>
              <TableHead className="w-12">
                <span className="sr-only">{t("accion.menu")}</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {miembros.map((miembro) => {
              const procesando = pendingId === miembro.usuarioId;
              // Reglas para deshabilitar el editor de rol:
              // - No editar tu propio rol.
              // - Si target es owner, solo el owner puede cambiarlo.
              const editorDeshabilitado =
                miembro.esYo ||
                (miembro.rol === "owner" && !yoSoyOwner) ||
                miembro.activo === false;
              return (
                <TableRow key={miembro.usuarioId}>
                  <TableCell className="font-medium">
                    {miembro.usuario.nombreCompleto ?? "—"}
                    {miembro.esYo ? (
                      <span className="ml-2 text-xs text-muted-foreground">{t("yo")}</span>
                    ) : null}
                  </TableCell>
                  <TableCell className="hidden text-muted-foreground md:table-cell">
                    {miembro.usuario.email ?? "—"}
                  </TableCell>
                  <TableCell className="hidden text-muted-foreground lg:table-cell">
                    {miembro.usuario.telefono ?? "—"}
                  </TableCell>
                  <TableCell>
                    {editorDeshabilitado ? (
                      <RolBadge rol={miembro.rol} />
                    ) : (
                      <Select
                        value={miembro.rol}
                        onValueChange={(v) => onCambiarRol(miembro, v as Rol)}
                        disabled={procesando}
                      >
                        <SelectTrigger className="h-8 w-32 text-xs" aria-label={t("campos.rol")}>
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {ROLES.map((r) => {
                            // Un no-owner no puede asignar el rol owner.
                            if (r === "owner" && !yoSoyOwner) return null;
                            return (
                              <SelectItem key={r} value={r}>
                                {tRoles(r)}
                              </SelectItem>
                            );
                          })}
                        </SelectContent>
                      </Select>
                    )}
                  </TableCell>
                  <TableCell>
                    {miembro.activo ? (
                      <span className="text-xs text-success-text">{t("estado.activo")}</span>
                    ) : (
                      <span className="text-xs text-muted-foreground">
                        {t("estado.desactivado")}
                      </span>
                    )}
                  </TableCell>
                  <TableCell>
                    {miembro.esYo ? null : (
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button
                            variant="ghost"
                            size="sm"
                            disabled={procesando}
                            aria-label={t("accion.menu")}
                          >
                            {procesando ? (
                              <Loader2 className="size-4 animate-spin" aria-hidden />
                            ) : (
                              <MoreHorizontal className="size-4" aria-hidden />
                            )}
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          {miembro.activo ? (
                            <DropdownMenuItem
                              onSelect={(event) => {
                                event.preventDefault();
                                setConfirmDesactivar(miembro);
                              }}
                              className="cursor-pointer text-destructive focus:text-destructive"
                              disabled={miembro.rol === "owner" && !yoSoyOwner}
                            >
                              <ShieldOff className="size-4" aria-hidden />
                              <span>{t("accion.desactivar")}</span>
                            </DropdownMenuItem>
                          ) : (
                            <DropdownMenuItem
                              onSelect={(event) => {
                                event.preventDefault();
                                onCambiarActivo(miembro, true);
                              }}
                              className="cursor-pointer"
                            >
                              <UserPlus className="size-4" aria-hidden />
                              <span>{t("accion.reactivar")}</span>
                            </DropdownMenuItem>
                          )}
                          <DropdownMenuSeparator />
                          <DropdownMenuItem disabled className="text-xs text-muted-foreground">
                            <UserCog className="size-4" aria-hidden />
                            <span>{t("accion.cambiarRolHint")}</span>
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    )}
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </div>

      <AlertDialog
        open={confirmDesactivar !== null}
        onOpenChange={(open) => {
          if (!open) setConfirmDesactivar(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("desactivar.titulo")}</AlertDialogTitle>
            <AlertDialogDescription>
              {t("desactivar.descripcion", {
                nombre:
                  confirmDesactivar?.usuario.nombreCompleto ??
                  confirmDesactivar?.usuario.email ??
                  "",
              })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={pendingId !== null}>
              {t("accion.cancelar")}
            </AlertDialogCancel>
            <AlertDialogAction
              disabled={pendingId !== null}
              onClick={(event) => {
                event.preventDefault();
                if (confirmDesactivar) onCambiarActivo(confirmDesactivar, false);
              }}
              className="hover:bg-destructive/90 bg-destructive text-destructive-foreground"
            >
              {pendingId === confirmDesactivar?.usuarioId ? (
                <Loader2 className="size-4 animate-spin" aria-hidden />
              ) : (
                <ShieldOff className="size-4" aria-hidden />
              )}
              <span>{t("accion.desactivarConfirmar")}</span>
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
