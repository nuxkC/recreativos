"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { useForm, type FieldErrors } from "react-hook-form";
import { toast } from "sonner";

import { FieldDate } from "@/components/common/date-field";
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
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import {
  actualizarInstalacion,
  crearInstalacion,
  type ActionResult,
} from "@/lib/instalaciones/actions";
import { InstalacionInputSchema } from "@/lib/instalaciones/schemas";
import type {
  Instalacion,
  LicenciaResumen,
  LocalResumen,
  MaquinaResumen,
} from "@/lib/instalaciones/types";

type InstalacionFormValues = {
  maquinaId: string;
  licenciaId: string;
  localId: string;
  fechaInicio: string;
  tasaSemanal: string;
  porcentajeLocal: string;
  tolva: string;
  notas: string;
  // Calendario de recaudación del local (Planificación P1): valores en string
  // (vacío = sin fijar). Solo se editan en el alta; en la ficha del local después.
  cadenciaSemanas: string;
  fechaInicioRecaudacion: string;
};

interface InstalacionFormProps {
  mode: "create" | "edit";
  instalacion?: Instalacion;
  licencias: LicenciaResumen[];
  maquinas: MaquinaResumen[];
  locales: LocalResumen[];
}

/** "YYYY-MM-DD" → "DD/MM/YYYY" para los avisos. Sin librería: es solo display. */
function formatFechaInicio(iso: string | null | undefined): string {
  if (!iso) return "";
  const [y, m, d] = iso.split("-");
  return d && m && y ? `${d}/${m}/${y}` : iso;
}

function defaults(instalacion?: Instalacion): InstalacionFormValues {
  return {
    maquinaId: instalacion?.maquinaId ?? "",
    licenciaId: instalacion?.licenciaId ?? "",
    localId: instalacion?.localId ?? "",
    fechaInicio: instalacion?.fechaInicio ?? "",
    tasaSemanal: instalacion?.tasaSemanal ?? "",
    porcentajeLocal: instalacion?.porcentajeLocal ?? "50.00",
    // La tolva solo se fija en el alta; en edición el campo no se muestra.
    tolva: "0",
    notas: instalacion?.notas ?? "",
    cadenciaSemanas: "",
    fechaInicioRecaudacion: "",
  };
}

function buildFormData(values: InstalacionFormValues): FormData {
  const fd = new FormData();
  Object.entries(values).forEach(([k, v]) => fd.set(k, v));
  return fd;
}

export function InstalacionForm({
  mode,
  instalacion,
  licencias,
  maquinas,
  locales,
}: InstalacionFormProps) {
  const t = useTranslations("instalaciones");
  const tCampos = useTranslations("instalaciones.campos");
  const tValidacion = useTranslations("instalaciones.validacion");
  const tErrores = useTranslations("instalaciones.errores");
  const tCal = useTranslations("instalaciones.calendario");
  const router = useRouter();
  const [submitting, setSubmitting] = useState(false);
  // Cuando hay aviso de "2ª máquina" guardamos aquí los valores a confirmar.
  const [confirmacionPendiente, setConfirmacionPendiente] =
    useState<InstalacionFormValues | null>(null);
  // Modo "cadencia libre": el usuario eligió "Otra…" en el select de cadencia.
  const [cadenciaLibre, setCadenciaLibre] = useState(false);

  const isEdit = mode === "edit";
  const isClosed = isEdit && instalacion?.estado === "cerrada";
  const fkDisabled = isEdit; // FKs no se cambian en edición.

  const form = useForm<InstalacionFormValues>({
    resolver: zodResolver(InstalacionInputSchema, undefined, { raw: true }),
    defaultValues: defaults(instalacion),
  });

  const watchedLocalId = form.watch("localId");
  const localSeleccionado = locales.find((l) => l.id === watchedLocalId) ?? null;
  // ¿El local ya tiene un calendario fijado? (entonces añadir una máquina y
  // cambiarlo afecta a todas las máquinas de ese local: hay que avisar.)
  const localTeniaCalendario = localSeleccionado?.cadenciaSemanas != null;

  function applyServerErrors(fieldErrors: Record<string, string[]> | undefined): boolean {
    if (!fieldErrors) return false;
    let applied = false;
    for (const [field, errors] of Object.entries(fieldErrors)) {
      const code = errors[0];
      if (!code) continue;
      form.setError(field as keyof InstalacionFormValues, {
        type: "server",
        message: tValidacion.has(code) ? tValidacion(code) : code,
      });
      applied = true;
    }
    return applied;
  }

  // Envío real al servidor (tras pasar validación y, si procede, confirmación).
  async function enviar(values: InstalacionFormValues) {
    setSubmitting(true);
    try {
      const fd = buildFormData(values);
      let result: ActionResult | ActionResult<{ id: string }>;
      if (mode === "create") {
        result = await crearInstalacion(null, fd);
      } else if (instalacion) {
        result = await actualizarInstalacion(instalacion.id, null, fd);
      } else {
        throw new Error("InstalacionForm en modo edit sin instalacion");
      }

      if (!result.ok) {
        const applied = applyServerErrors(result.error.fieldErrors);
        if (!applied) {
          const code = result.error.code;
          toast.error(tErrores.has(code) ? tErrores(code) : tErrores("desconocido"));
        }
        return;
      }

      // Éxito: en "create" navegamos al detalle recién creado (el id viene en
      // result.data); en "update" basta con refrescar la ruta actual.
      toast.success(t("guardadoOk"));
      if (mode === "create") {
        router.push(`/instalaciones/${(result.data as { id: string }).id}`);
      } else {
        router.refresh();
      }
    } catch (err) {
      if (err && typeof err === "object" && "digest" in err) throw err;
      console.error("instalacion_form_unexpected_error", err);
      toast.error(tErrores("desconocido"));
    } finally {
      setSubmitting(false);
    }
  }

  // Puerta previa al envío: en el alta el calendario es obligatorio y, si el
  // local ya tenía uno distinto, se confirma el cambio antes de enviar.
  async function onSubmit(values: InstalacionFormValues) {
    if (mode === "create") {
      const faltaCadencia = !values.cadenciaSemanas;
      const faltaFecha = !values.fechaInicioRecaudacion;
      if (faltaCadencia || faltaFecha) {
        if (faltaCadencia) {
          form.setError("cadenciaSemanas", {
            type: "validate",
            message: tValidacion("calendarioRequerido"),
          });
        }
        if (faltaFecha) {
          form.setError("fechaInicioRecaudacion", {
            type: "validate",
            message: tValidacion("calendarioRequerido"),
          });
        }
        return;
      }
      const cambia =
        values.cadenciaSemanas !== String(localSeleccionado?.cadenciaSemanas ?? "") ||
        values.fechaInicioRecaudacion !== (localSeleccionado?.fechaInicioRecaudacion ?? "");
      if (localTeniaCalendario && cambia) {
        setConfirmacionPendiente(values);
        return;
      }
    }
    await enviar(values);
  }

  function onInvalid(errors: FieldErrors<InstalacionFormValues>) {
    for (const [field, fieldError] of Object.entries(errors)) {
      const message = fieldError?.message;
      if (typeof message === "string" && tValidacion.has(message)) {
        form.setError(field as keyof InstalacionFormValues, {
          type: "validate",
          message: tValidacion(message),
        });
      }
    }
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit, onInvalid)} className="space-y-6">
        <fieldset disabled={isClosed} className="space-y-6 disabled:opacity-70">
          <div className="grid gap-4 sm:grid-cols-2">
            {/* FKs (no editables en edit) ------------------------------------ */}
            <FormField
              control={form.control}
              name="maquinaId"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{tCampos("maquina")}</FormLabel>
                  <Select
                    value={field.value || undefined}
                    onValueChange={field.onChange}
                    disabled={fkDisabled}
                  >
                    <FormControl>
                      <SelectTrigger>
                        <SelectValue placeholder={t("placeholders.maquina")} />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {maquinas.map((m) => (
                        <SelectItem key={m.id} value={m.id}>
                          {m.numeroSerie}
                          {m.modelo ? ` — ${m.modelo}` : ""}
                        </SelectItem>
                      ))}
                      {fkDisabled &&
                      instalacion?.maquina &&
                      !maquinas.some((m) => m.id === instalacion.maquina!.id) ? (
                        <SelectItem value={instalacion.maquina.id}>
                          {instalacion.maquina.numeroSerie}
                          {instalacion.maquina.modelo ? ` — ${instalacion.maquina.modelo}` : ""}
                        </SelectItem>
                      ) : null}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="licenciaId"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{tCampos("licencia")}</FormLabel>
                  <Select
                    value={field.value || undefined}
                    onValueChange={field.onChange}
                    disabled={fkDisabled}
                  >
                    <FormControl>
                      <SelectTrigger>
                        <SelectValue placeholder={t("placeholders.licencia")} />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {licencias.map((l) => (
                        <SelectItem key={l.id} value={l.id}>
                          {l.numero}
                        </SelectItem>
                      ))}
                      {fkDisabled &&
                      instalacion?.licencia &&
                      !licencias.some((l) => l.id === instalacion.licencia!.id) ? (
                        <SelectItem value={instalacion.licencia.id}>
                          {instalacion.licencia.numero}
                        </SelectItem>
                      ) : null}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="localId"
              render={({ field }) => (
                <FormItem className="sm:col-span-2">
                  <FormLabel>{tCampos("local")}</FormLabel>
                  <Select
                    value={field.value || undefined}
                    onValueChange={(value) => {
                      field.onChange(value);
                      // Al elegir local, precarga su calendario (si lo tiene) en
                      // los campos de planificación; si no, los deja vacíos.
                      const l = locales.find((x) => x.id === value);
                      form.setValue(
                        "cadenciaSemanas",
                        l?.cadenciaSemanas != null ? String(l.cadenciaSemanas) : "",
                      );
                      form.setValue("fechaInicioRecaudacion", l?.fechaInicioRecaudacion ?? "");
                      setCadenciaLibre(
                        l?.cadenciaSemanas != null && ![1, 2, 4].includes(l.cadenciaSemanas),
                      );
                    }}
                    disabled={fkDisabled}
                  >
                    <FormControl>
                      <SelectTrigger>
                        <SelectValue placeholder={t("placeholders.local")} />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {locales.map((l) => (
                        <SelectItem key={l.id} value={l.id}>
                          {l.nombre}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />

            {/* Condiciones económicas y baseline ----------------------------- */}
            <FormField
              control={form.control}
              name="fechaInicio"
              render={({ field, fieldState }) => (
                <FieldDate
                  label={tCampos("fechaInicio")}
                  value={field.value}
                  onChange={field.onChange}
                  error={fieldState.error?.message}
                  density="compact"
                />
              )}
            />
            <FormField
              control={form.control}
              name="tasaSemanal"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{tCampos("tasaSemanal")}</FormLabel>
                  <FormControl>
                    <Input
                      type="text"
                      inputMode="decimal"
                      placeholder="0.00"
                      autoComplete="off"
                      {...field}
                    />
                  </FormControl>
                  <FormDescription>{tCampos("tasaSemanalAyuda")}</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="porcentajeLocal"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{tCampos("porcentajeLocal")}</FormLabel>
                  <FormControl>
                    <Input
                      type="text"
                      inputMode="decimal"
                      placeholder="50.00"
                      autoComplete="off"
                      {...field}
                    />
                  </FormControl>
                  <FormDescription>{tCampos("porcentajeLocalAyuda")}</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            {/* Tolva: solo en el alta. En edición no se muestra porque la deuda de
                tolva ya está creada y su ajuste va por el flujo de traslado. */}
            {mode === "create" ? (
              <FormField
                control={form.control}
                name="tolva"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{tCampos("tolva")}</FormLabel>
                    <FormControl>
                      <Input
                        type="text"
                        inputMode="decimal"
                        placeholder="0.00"
                        autoComplete="off"
                        {...field}
                      />
                    </FormControl>
                    <FormDescription>{tCampos("tolvaAyuda")}</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
            ) : null}

            {/* Planificación de la recaudación (solo en el alta) -------------- */}
            {mode === "create" ? (
              <div className="space-y-4 rounded-lg border border-border/60 bg-muted/30 p-4 sm:col-span-2">
                <div className="space-y-1">
                  <h3 className="text-sm font-medium">{tCal("seccion")}</h3>
                  <p className="text-sm text-muted-foreground">{tCal("ayuda")}</p>
                </div>
                <div className="grid gap-4 sm:grid-cols-2">
                  <FormField
                    control={form.control}
                    name="cadenciaSemanas"
                    render={({ field }) => {
                      const esPreset = ["1", "2", "4"].includes(field.value);
                      const enLibre = cadenciaLibre || (field.value !== "" && !esPreset);
                      return (
                        <FormItem>
                          <FormLabel>{tCampos("cadenciaSemanas")}</FormLabel>
                          <Select
                            value={enLibre ? "otro" : field.value || undefined}
                            onValueChange={(v) => {
                              if (v === "otro") {
                                setCadenciaLibre(true);
                                field.onChange("");
                              } else {
                                setCadenciaLibre(false);
                                field.onChange(v);
                              }
                            }}
                          >
                            <FormControl>
                              <SelectTrigger>
                                <SelectValue placeholder={tCal("presetPlaceholder")} />
                              </SelectTrigger>
                            </FormControl>
                            <SelectContent>
                              <SelectItem value="1">{tCal("presetSemanal")}</SelectItem>
                              <SelectItem value="2">{tCal("presetQuincenal")}</SelectItem>
                              <SelectItem value="4">{tCal("presetMensual")}</SelectItem>
                              <SelectItem value="otro">{tCal("presetOtro")}</SelectItem>
                            </SelectContent>
                          </Select>
                          {enLibre ? (
                            <FormControl>
                              <Input
                                type="number"
                                min={1}
                                step={1}
                                inputMode="numeric"
                                placeholder={tCal("cadenciaLibre")}
                                value={field.value}
                                onChange={(e) => field.onChange(e.target.value)}
                              />
                            </FormControl>
                          ) : null}
                          <FormMessage />
                        </FormItem>
                      );
                    }}
                  />
                  <FormField
                    control={form.control}
                    name="fechaInicioRecaudacion"
                    render={({ field, fieldState }) => (
                      <FieldDate
                        label={tCampos("fechaInicioRecaudacion")}
                        value={field.value}
                        onChange={field.onChange}
                        error={fieldState.error?.message}
                        density="compact"
                      />
                    )}
                  />
                </div>
              </div>
            ) : null}

            <FormField
              control={form.control}
              name="notas"
              render={({ field }) => (
                <FormItem className="sm:col-span-2">
                  <FormLabel>{tCampos("notas")}</FormLabel>
                  <FormControl>
                    <Textarea rows={4} maxLength={2000} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
        </fieldset>
        <div className="flex items-center justify-end gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={() => router.push("/instalaciones")}
            disabled={submitting}
          >
            {t("accion.cancelar")}
          </Button>
          <Button type="submit" disabled={submitting || isClosed}>
            {submitting ? (
              <>
                <Loader2 className="size-4 animate-spin" aria-hidden />
                <span>{t("accion.guardando")}</span>
              </>
            ) : (
              <span>{mode === "create" ? t("accion.crear") : t("accion.guardar")}</span>
            )}
          </Button>
        </div>
      </form>

      {/* Aviso de 2ª máquina: el local ya tenía calendario y se está cambiando. */}
      <AlertDialog
        open={confirmacionPendiente !== null}
        onOpenChange={(open) => {
          if (!open) setConfirmacionPendiente(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{tCal("avisoTitulo")}</AlertDialogTitle>
            <AlertDialogDescription>
              {tCal("avisoDescripcion", {
                nombre: localSeleccionado?.nombre ?? "",
                semanas: localSeleccionado?.cadenciaSemanas ?? 0,
                fecha: formatFechaInicio(localSeleccionado?.fechaInicioRecaudacion),
              })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{tCal("avisoCancelar")}</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => {
                const v = confirmacionPendiente;
                setConfirmacionPendiente(null);
                if (v) void enviar(v);
              }}
            >
              {tCal("avisoConfirmar")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </Form>
  );
}
