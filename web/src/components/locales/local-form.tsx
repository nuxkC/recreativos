"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { useForm, type FieldErrors } from "react-hook-form";
import { toast } from "sonner";

import { Combobox } from "@/components/common/combobox";
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
import { Textarea } from "@/components/ui/textarea";
import { COMUNIDADES_AUTONOMAS } from "@/lib/licencias/types";
import { actualizarLocal, crearLocal, type ActionResult } from "@/lib/locales/actions";
import {
  opcionesMunicipio,
  opcionesProvincia,
  type MunicipioOpcion,
  type ProvinciaOpcion,
} from "@/lib/locales/geo-opciones";
import { LocalInputSchema } from "@/lib/locales/schemas";
import type { Local } from "@/lib/locales/types";

type LocalFormValues = {
  nombre: string;
  comunidadAutonoma: string;
  provinciaCodigo: string;
  municipioCodigo: string;
  calle: string;
  codigoPostal: string;
  cifONif: string;
  titularNombre: string;
  telefono: string;
  email: string;
  notas: string;
};

interface LocalFormProps {
  mode: "create" | "edit";
  local?: Local;
  /** Referencia geográfica precargada (INE); la cascada filtra en cliente. */
  provincias: ProvinciaOpcion[];
  municipios: MunicipioOpcion[];
}

/** CCAA como lista cerrada (value = label): la "lista de oro" de 19. */
const CCAA_OPTIONS = COMUNIDADES_AUTONOMAS.map((c) => ({ value: c, label: c }));

function defaultsFromLocal(local?: Local): LocalFormValues {
  return {
    nombre: local?.nombre ?? "",
    comunidadAutonoma: local?.comunidadAutonoma ?? "",
    provinciaCodigo: local?.provinciaCodigo ?? "",
    municipioCodigo: local?.municipioCodigo ?? "",
    calle: local?.calle ?? "",
    codigoPostal: local?.codigoPostal ?? "",
    cifONif: local?.cifONif ?? "",
    titularNombre: local?.titularNombre ?? "",
    telefono: local?.telefono ?? "",
    email: local?.email ?? "",
    notas: local?.notas ?? "",
  };
}

function buildFormData(values: LocalFormValues): FormData {
  const fd = new FormData();
  fd.set("nombre", values.nombre);
  fd.set("comunidadAutonoma", values.comunidadAutonoma);
  fd.set("provinciaCodigo", values.provinciaCodigo);
  fd.set("municipioCodigo", values.municipioCodigo);
  fd.set("calle", values.calle);
  fd.set("codigoPostal", values.codigoPostal);
  fd.set("cifONif", values.cifONif);
  fd.set("titularNombre", values.titularNombre);
  fd.set("telefono", values.telefono);
  fd.set("email", values.email);
  fd.set("notas", values.notas);
  return fd;
}

export function LocalForm({ mode, local, provincias, municipios }: LocalFormProps) {
  const t = useTranslations("locales");
  const tCampos = useTranslations("locales.campos");
  const tGeo = useTranslations("locales.geo");
  const tValidacion = useTranslations("locales.validacion");
  const tErrores = useTranslations("locales.errores");
  const router = useRouter();
  const [submitting, setSubmitting] = useState(false);

  const form = useForm<LocalFormValues>({
    resolver: zodResolver(LocalInputSchema, undefined, {
      // Validamos los strings tal cual los introduce el usuario, sin pasar
      // por el `transform` del schema (que devolvería null para los vacíos
      // y rompería los inputs controlados). El parse final con transform
      // corre server-side.
      raw: true,
    }),
    defaultValues: defaultsFromLocal(local),
  });

  // Cascada de dirección: provincia depende de la CCAA y municipio de la
  // provincia. Observamos ambos para recalcular opciones y deshabilitar los
  // combos hijos. Al cambiar un padre se limpia el hijo para no dejar un par
  // incoherente (lo mismo que hace el CHECK/validador de BBDD).
  const comunidadSel = form.watch("comunidadAutonoma");
  const provinciaSel = form.watch("provinciaCodigo");
  const provinciaOpts = opcionesProvincia(provincias, comunidadSel || null);
  const municipioOpts = opcionesMunicipio(municipios, provinciaSel || null);

  function handleComunidadChange(nueva: string) {
    const actual = form.getValues("comunidadAutonoma");
    form.setValue("comunidadAutonoma", nueva, { shouldDirty: true, shouldValidate: true });
    if (nueva !== actual) {
      form.setValue("provinciaCodigo", "", { shouldDirty: true });
      form.setValue("municipioCodigo", "", { shouldDirty: true });
    }
  }

  function handleProvinciaChange(nueva: string) {
    const actual = form.getValues("provinciaCodigo");
    form.setValue("provinciaCodigo", nueva, { shouldDirty: true, shouldValidate: true });
    if (nueva !== actual) {
      form.setValue("municipioCodigo", "", { shouldDirty: true });
    }
  }

  function applyServerErrors(fieldErrors: Record<string, string[]> | undefined): boolean {
    if (!fieldErrors) return false;
    let applied = false;
    for (const [field, errors] of Object.entries(fieldErrors)) {
      const code = errors[0];
      if (!code) continue;
      form.setError(field as keyof LocalFormValues, {
        type: "server",
        message: tValidacion.has(code) ? tValidacion(code) : code,
      });
      applied = true;
    }
    return applied;
  }

  async function onSubmit(values: LocalFormValues) {
    setSubmitting(true);
    try {
      const formData = buildFormData(values);
      let result: ActionResult | ActionResult<{ id: string }>;
      if (mode === "create") {
        result = await crearLocal(null, formData);
      } else if (local) {
        result = await actualizarLocal(local.id, null, formData);
      } else {
        throw new Error("LocalForm en modo edit sin local");
      }

      // crear y actualizar devuelven siempre un ActionResult serializable;
      // tratamos aquí tanto el error como el éxito de ambos modos.
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
        router.push(`/locales/${(result.data as { id: string }).id}`);
      } else {
        router.refresh();
      }
    } catch (err) {
      // No interceptamos NEXT_REDIRECT (es un Error con `digest` interno);
      // si llega aquí es un error inesperado.
      if (err && typeof err === "object" && "digest" in err) {
        throw err;
      }
      console.error("local_form_unexpected_error", err);
      toast.error(tErrores("desconocido"));
    } finally {
      setSubmitting(false);
    }
  }

  function onInvalid(errors: FieldErrors<LocalFormValues>) {
    // Tradúcimos los códigos producidos por Zod (mensajes son claves i18n).
    for (const [field, fieldError] of Object.entries(errors)) {
      const message = fieldError?.message;
      if (typeof message === "string" && tValidacion.has(message)) {
        form.setError(field as keyof LocalFormValues, {
          type: "validate",
          message: tValidacion(message),
        });
      }
    }
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit, onInvalid)} className="space-y-6">
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            control={form.control}
            name="nombre"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{tCampos("nombre")}</FormLabel>
                <FormControl>
                  <Input autoComplete="off" maxLength={200} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="comunidadAutonoma"
            render={({ field, fieldState }) => (
              <FormItem>
                <FormLabel>{tCampos("comunidadAutonoma")}</FormLabel>
                <FormControl>
                  <Combobox
                    options={CCAA_OPTIONS}
                    value={field.value || null}
                    onChange={handleComunidadChange}
                    placeholder={tGeo("elegirCcaa")}
                    searchPlaceholder={tGeo("buscarCcaa")}
                    emptyMessage={tGeo("sinCoincidencias")}
                    error={!!fieldState.error}
                    aria-label={tCampos("comunidadAutonoma")}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="provinciaCodigo"
            render={({ field, fieldState }) => (
              <FormItem>
                <FormLabel>{tCampos("provincia")}</FormLabel>
                <FormControl>
                  <Combobox
                    options={provinciaOpts}
                    value={field.value || null}
                    onChange={handleProvinciaChange}
                    placeholder={tGeo("elegirProvincia")}
                    searchPlaceholder={tGeo("buscarProvincia")}
                    emptyMessage={tGeo("sinCoincidencias")}
                    disabled={!comunidadSel}
                    error={!!fieldState.error}
                    aria-label={tCampos("provincia")}
                  />
                </FormControl>
                {!comunidadSel ? (
                  <FormDescription>{tGeo("provinciaSinCcaa")}</FormDescription>
                ) : null}
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="municipioCodigo"
            render={({ field, fieldState }) => (
              <FormItem>
                <FormLabel>{tCampos("municipio")}</FormLabel>
                <FormControl>
                  <Combobox
                    options={municipioOpts}
                    value={field.value || null}
                    onChange={field.onChange}
                    placeholder={tGeo("elegirMunicipio")}
                    searchPlaceholder={tGeo("buscarMunicipio")}
                    emptyMessage={tGeo("sinCoincidencias")}
                    disabled={!provinciaSel}
                    error={!!fieldState.error}
                    aria-label={tCampos("municipio")}
                  />
                </FormControl>
                {!provinciaSel ? (
                  <FormDescription>{tGeo("municipioSinProvincia")}</FormDescription>
                ) : null}
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="calle"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{tCampos("calle")}</FormLabel>
                <FormControl>
                  <Input autoComplete="off" maxLength={300} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="codigoPostal"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{tCampos("codigoPostal")}</FormLabel>
                <FormControl>
                  <Input
                    autoComplete="off"
                    inputMode="numeric"
                    maxLength={5}
                    placeholder="28001"
                    {...field}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="cifONif"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{tCampos("cifONif")}</FormLabel>
                <FormControl>
                  <Input autoComplete="off" maxLength={20} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="titularNombre"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{tCampos("titularNombre")}</FormLabel>
                <FormControl>
                  <Input autoComplete="off" maxLength={150} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="telefono"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{tCampos("telefono")}</FormLabel>
                <FormControl>
                  <Input type="tel" autoComplete="off" inputMode="tel" maxLength={30} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="email"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{tCampos("email")}</FormLabel>
                <FormControl>
                  <Input
                    type="email"
                    autoComplete="off"
                    inputMode="email"
                    maxLength={254}
                    {...field}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
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
        <div className="flex items-center justify-end gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={() => router.push("/locales")}
            disabled={submitting}
          >
            {t("accion.cancelar")}
          </Button>
          <Button type="submit" disabled={submitting}>
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
    </Form>
  );
}
