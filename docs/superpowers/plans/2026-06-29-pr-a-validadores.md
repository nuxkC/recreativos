# PR-A — Validadores de formato (CIF/NIF, teléfono, email) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Validar de verdad CIF/NIF/NIE y teléfono español (con dígito de control real) y unificar el email, en web y Android, sin reescribir lo que ya funciona.

**Architecture:** Una casa canónica TS en `web/src/lib/shared/validators.ts` que importan los schemas Zod (cubre formulario cliente + Server Action a la vez). En Android se **porta a mano** la misma lógica a `GestionShared.kt` (no hay codegen TS→Kotlin). El contrato que mantiene honestas las dos copias es una **tabla de vectores oro** (mismos inputs → mismos booleanos) replicada como test en Vitest y JUnit.

**Tech Stack:** TypeScript + Zod + Vitest (web); Kotlin + JUnit4 (Android). Sin dependencias nuevas.

## Global Constraints

- **Idioma:** docs/comentarios/UI en español; identificadores en inglés salvo términos de dominio. Mensajes de error UI en español.
- **TS `strict`, sin `any`** (usa `unknown`). Kotlin sin `!!`.
- **Validación condicional a campo no vacío:** CIF, teléfono y email son opcionales; solo se validan si el usuario escribe algo (vacío → `null`, sin error). Es el mismo contrato que ya tiene el email hoy.
- **Commits:** Conventional Commits `<tipo>(<scope>): <descripción> (T-NNN)`, presente, minúscula, ≤70 car. Asigna el siguiente `T-NNN` libre en `.kiro/specs/recre/tasks.md` y úsalo en todos los commits de esta rama.
- **Build Android:** exportar `JAVA_HOME=/snap/android-studio/current/jbr` en cada comando Gradle (no se hereda). Comandos Gradle se ejecutan **desde `android/`**.
- **Tests Android:** nombres de método de test en ASCII (sin tildes) — el locale no-UTF-8 mangla los nombres con tilde y rompe la ejecución.
- **Rutas absolutas, no `cd`:** usar `npm --prefix web …`. Para Gradle, ejecutar desde `android/`.
- **No tocar** `web/src/components/ui/` (shadcn generado). No aplica aquí pero queda dicho.

## File Structure

| Fichero | Responsabilidad | Acción |
|---------|-----------------|--------|
| `web/src/lib/shared/validators.ts` | Validadores puros + factories Zod compartidos (`esCifNif`, `esTelefonoEs`, `cifNifSchema`, `telefonoSchema`, `emailOptional`, `trimmedString`) | Crear |
| `web/src/lib/shared/validators.test.ts` | Vectores oro (Vitest) de los validadores puros + factories | Crear |
| `web/src/lib/locales/schemas.ts` | Cablear `cifONif`/`telefono`/`email` a los compartidos; borrar duplicados | Modificar |
| `web/src/lib/ajustes/schemas.ts` | Cablear `cif`/`telefono`/`email` a los compartidos; borrar duplicados | Modificar |
| `web/src/lib/locales/schemas.test.ts` | Verifica el cableado del schema de Local (rechaza CIF/tel inválidos, acepta vacío) | Crear |
| `web/src/i18n/messages/es.json` | Claves `cifInvalido` / `telefonoInvalido` en los namespaces de los forms de Local y Ajustes | Modificar |
| `android/.../feature/gestion/GestionShared.kt` | Puerto Kotlin: `esCifNif`, `esTelefono` (+ helpers privados) | Modificar |
| `android/.../feature/gestion/GestionValidadoresTest.kt` | Mismos vectores oro en JUnit | Crear |
| `android/.../feature/gestion/locales/LocalFormViewModel.kt` | Validar `cifONif`/`telefono` en `guardar()` | Modificar |
| `android/.../feature/gestion/locales/LocalFormScreen.kt` | Mostrar error de `cifONif`/`telefono` | Modificar |
| `android/app/src/main/res/values/strings.xml` | `gestion_validacion_cif`, `gestion_validacion_telefono` | Modificar |

**Vectores oro (el contrato — idénticos en Vitest y JUnit):**

| Input | `esCifNif` | Nota |
|-------|-----------|------|
| `12345678Z` | true | NIF válido |
| `00000000T` | true | NIF, 0 % 23 = 0 → T |
| ` 12345678-z ` | true | normaliza mayúsculas/espacios/guión |
| `12345678A` | false | letra de control errónea |
| `X1234567L` | true | NIE (X→0) |
| `Y1234567X` | true | NIE (Y→1) |
| `X1234567A` | false | NIE letra errónea |
| `A58818501` | true | CIF con control numérico |
| `A58818500` | false | CIF control numérico erróneo |
| `P1234567D` | true | CIF con control por letra |
| `P1234567A` | false | CIF control letra erróneo |
| `1234` | false | no es documento |

| Input | `esTelefonoEs` | Nota |
|-------|----------------|------|
| `612345678` | true | móvil |
| `912345678` | true | fijo |
| `+34 612 345 678` | true | normaliza prefijo+espacios |
| `0034612345678` | true | normaliza prefijo 0034 |
| `512345678` | false | no empieza 6-9 |
| `61234567` | false | 8 dígitos |
| `61234567a` | false | carácter no numérico |
| `1234` | false | demasiado corto |

---

### Task 1: Módulo compartido de validadores (web)

**Files:**
- Create: `web/src/lib/shared/validators.ts`
- Test: `web/src/lib/shared/validators.test.ts`

**Interfaces:**
- Produces:
  - `esCifNif(raw: string): boolean`
  - `esTelefonoEs(raw: string): boolean`
  - `normalizarTelefonoEs(raw: string): string`
  - `trimmedString` (Zod: `string` → `string | null`)
  - `emailOptional` (Zod: `string` → `string | null`, valida formato si no vacío; mensaje `"emailInvalido"`)
  - `cifNifSchema(maxMessage: string)` → Zod field (`string` → `string | null`; mensajes `maxMessage` y `"cifInvalido"`)
  - `telefonoSchema(maxMessage: string)` → Zod field (mensajes `maxMessage` y `"telefonoInvalido"`)

- [ ] **Step 1: Crear rama**

Run (desde la raíz del repo):
```bash
git checkout -b feat/validadores-formato
```

- [ ] **Step 2: Escribir el test que falla**

Create `web/src/lib/shared/validators.test.ts`:
```ts
import { describe, expect, it } from "vitest";

import { cifNifSchema, esCifNif, esTelefonoEs, telefonoSchema } from "./validators";

describe("esCifNif (vectores oro)", () => {
  it.each([
    ["12345678Z", true],
    ["00000000T", true],
    [" 12345678-z ", true],
    ["12345678A", false],
    ["X1234567L", true],
    ["Y1234567X", true],
    ["X1234567A", false],
    ["A58818501", true],
    ["A58818500", false],
    ["P1234567D", true],
    ["P1234567A", false],
    ["1234", false],
  ])("esCifNif(%j) === %s", (input, expected) => {
    expect(esCifNif(input as string)).toBe(expected);
  });
});

describe("esTelefonoEs (vectores oro)", () => {
  it.each([
    ["612345678", true],
    ["912345678", true],
    ["+34 612 345 678", true],
    ["0034612345678", true],
    ["512345678", false],
    ["61234567", false],
    ["61234567a", false],
    ["1234", false],
  ])("esTelefonoEs(%j) === %s", (input, expected) => {
    expect(esTelefonoEs(input as string)).toBe(expected);
  });
});

describe("cifNifSchema / telefonoSchema (condicional a no vacío)", () => {
  it("vacío → null sin error", () => {
    expect(cifNifSchema("cifMuyLargo").parse("")).toBeNull();
    expect(telefonoSchema("telMuyLargo").parse("   ")).toBeNull();
  });

  it("válido → normalizado conservado", () => {
    expect(cifNifSchema("cifMuyLargo").parse("12345678Z")).toBe("12345678Z");
    expect(telefonoSchema("telMuyLargo").parse("612345678")).toBe("612345678");
  });

  it("inválido → error con clave i18n", () => {
    const r = cifNifSchema("cifMuyLargo").safeParse("12345678A");
    expect(r.success).toBe(false);
    if (!r.success) expect(r.error.issues[0].message).toBe("cifInvalido");
  });
});
```

- [ ] **Step 3: Ejecutar el test y verificar que falla**

Run:
```bash
npm --prefix web run test -- src/lib/shared/validators.test.ts
```
Expected: FAIL — `Failed to resolve import "./validators"` (el módulo aún no existe).

- [ ] **Step 4: Implementar el módulo**

Create `web/src/lib/shared/validators.ts`:
```ts
import { z } from "zod";

/**
 * Validadores de formato compartidos por los schemas Zod de los formularios.
 * Casa canónica única en TS: lo importan los schemas (que a su vez consumen
 * el formulario cliente y la Server Action). El puerto Kotlin equivalente vive
 * en android `GestionShared.kt`; ambos se mantienen alineados por los mismos
 * vectores oro en sus test suites.
 */

/** Trim + null si queda vacío. */
export const trimmedString = z
  .string()
  .trim()
  .transform((v) => (v.length === 0 ? null : v));

/** Email opcional: vacío → null; si hay valor, valida formato. */
export const emailOptional = z
  .string()
  .trim()
  .transform((v) => (v.length === 0 ? null : v))
  .pipe(z.string().email({ message: "emailInvalido" }).nullable());

const DNI_LETRAS = "TRWAGMYFPDXBNJZSQVHLCKE";
const CIF_LETRAS_CONTROL = "JABCDEFGHI";

/** Mayúsculas, sin espacios ni guiones. */
function normalizarDocumento(raw: string): string {
  return raw.toUpperCase().replace(/[\s-]/g, "");
}

function esNif(doc: string): boolean {
  const numero = Number.parseInt(doc.slice(0, 8), 10);
  return doc[8] === DNI_LETRAS[numero % 23];
}

function esNie(doc: string): boolean {
  const prefijo = doc[0] === "X" ? "0" : doc[0] === "Y" ? "1" : "2";
  const numero = Number.parseInt(prefijo + doc.slice(1, 8), 10);
  return doc[8] === DNI_LETRAS[numero % 23];
}

function esCif(doc: string): boolean {
  const letra = doc[0];
  const control = doc[8];
  let suma = 0;
  for (let i = 0; i < 7; i++) {
    let n = doc.charCodeAt(i + 1) - 48; // dígitos en posiciones 1..7
    if (i % 2 === 0) {
      // posiciones impares (1ª, 3ª, …) se multiplican por 2 y se "suman dígitos"
      n *= 2;
      if (n > 9) n -= 9;
    }
    suma += n;
  }
  const e = (10 - (suma % 10)) % 10;
  const digitoControl = String(e);
  const letraControl = CIF_LETRAS_CONTROL[e];
  if ("PQSNWK".includes(letra)) return control === letraControl; // control por letra
  if ("ABEH".includes(letra)) return control === digitoControl; // control por dígito
  return control === digitoControl || control === letraControl; // ambos válidos
}

/** True si `raw` es un NIF, NIE o CIF español válido (dígito de control real). */
export function esCifNif(raw: string): boolean {
  const doc = normalizarDocumento(raw);
  if (/^\d{8}[A-Z]$/.test(doc)) return esNif(doc);
  if (/^[XYZ]\d{7}[A-Z]$/.test(doc)) return esNie(doc);
  if (/^[ABCDEFGHJKLMNPQRSUVW]\d{7}[0-9A-J]$/.test(doc)) return esCif(doc);
  return false;
}

/** Quita espacios/guiones y el prefijo internacional +34 / 0034. */
export function normalizarTelefonoEs(raw: string): string {
  return raw.replace(/[\s-]/g, "").replace(/^(\+34|0034)/, "");
}

/** True si `raw` es un teléfono español válido (9 dígitos, empieza 6-9). */
export function esTelefonoEs(raw: string): boolean {
  return /^[6-9]\d{8}$/.test(normalizarTelefonoEs(raw));
}

/**
 * Campo CIF/NIF opcional. Vacío → null (sin error); con valor, valida longitud
 * y dígito de control. `maxMessage` es la clave i18n del error de longitud
 * (difiere entre forms: "cifONifMuyLargo" en local, "cifMuyLargo" en ajustes).
 */
export function cifNifSchema(maxMessage: string) {
  return trimmedString.pipe(
    z
      .string()
      .max(20, { message: maxMessage })
      .refine((v) => esCifNif(v), { message: "cifInvalido" })
      .nullable(),
  );
}

/** Campo teléfono opcional. Vacío → null; con valor, valida longitud y formato. */
export function telefonoSchema(maxMessage: string) {
  return trimmedString.pipe(
    z
      .string()
      .max(30, { message: maxMessage })
      .refine((v) => esTelefonoEs(v), { message: "telefonoInvalido" })
      .nullable(),
  );
}
```

- [ ] **Step 5: Ejecutar el test y verificar que pasa**

Run:
```bash
npm --prefix web run test -- src/lib/shared/validators.test.ts
```
Expected: PASS (todos los casos verde).

- [ ] **Step 6: Lint + typecheck**

Run:
```bash
npm --prefix web run lint
```
Expected: sin errores en `src/lib/shared/validators.ts`.

- [ ] **Step 7: Commit**

```bash
git add web/src/lib/shared/validators.ts web/src/lib/shared/validators.test.ts
git commit -m "feat(web): validadores compartidos de CIF/NIF y teléfono (T-NNN)"
```

---

### Task 2: Cablear los schemas web + i18n

**Files:**
- Modify: `web/src/lib/locales/schemas.ts`
- Modify: `web/src/lib/ajustes/schemas.ts`
- Modify: `web/src/i18n/messages/es.json`
- Test: `web/src/lib/locales/schemas.test.ts` (crear)

**Interfaces:**
- Consumes: `cifNifSchema`, `telefonoSchema`, `emailOptional`, `trimmedString` de Task 1.

- [ ] **Step 1: Escribir el test que falla**

Create `web/src/lib/locales/schemas.test.ts`:
```ts
import { describe, expect, it } from "vitest";

import { LocalInputSchema } from "./schemas";

const base = {
  nombre: "Bar Pepe",
  direccion: "",
  cifONif: "",
  titularNombre: "",
  telefono: "",
  email: "",
  notas: "",
};

describe("LocalInputSchema (validación de formato)", () => {
  it("acepta campos opcionales vacíos como null", () => {
    const p = LocalInputSchema.parse(base);
    expect(p.cifONif).toBeNull();
    expect(p.telefono).toBeNull();
    expect(p.email).toBeNull();
  });

  it("rechaza un CIF/NIF inválido", () => {
    const r = LocalInputSchema.safeParse({ ...base, cifONif: "12345678A" });
    expect(r.success).toBe(false);
    if (!r.success) expect(r.error.flatten().fieldErrors.cifONif?.[0]).toBe("cifInvalido");
  });

  it("rechaza un teléfono inválido", () => {
    const r = LocalInputSchema.safeParse({ ...base, telefono: "512345678" });
    expect(r.success).toBe(false);
    if (!r.success) expect(r.error.flatten().fieldErrors.telefono?.[0]).toBe("telefonoInvalido");
  });

  it("acepta CIF y teléfono válidos", () => {
    const p = LocalInputSchema.parse({ ...base, cifONif: "12345678Z", telefono: "612345678" });
    expect(p.cifONif).toBe("12345678Z");
    expect(p.telefono).toBe("612345678");
  });
});
```

- [ ] **Step 2: Ejecutar y verificar que falla**

Run:
```bash
npm --prefix web run test -- src/lib/locales/schemas.test.ts
```
Expected: FAIL — el CIF `12345678A` se acepta hoy (no hay validación de formato).

- [ ] **Step 3: Cablear `locales/schemas.ts`**

En `web/src/lib/locales/schemas.ts`:
1. Borrar las definiciones locales `trimmedString` (líneas 9-12) y `emailOptional` (líneas 18-22).
2. Añadir el import al principio (tras `import { z } from "zod";`):
```ts
import { cifNifSchema, emailOptional, telefonoSchema, trimmedString } from "@/lib/shared/validators";
```
3. Sustituir las líneas de campo dentro de `LocalInputSchema`:
```ts
  cifONif: cifNifSchema("cifONifMuyLargo"),
```
```ts
  telefono: telefonoSchema("telefonoMuyLargo"),
```
(`email: emailOptional` se mantiene igual, ahora resuelto desde el import.)

- [ ] **Step 4: Cablear `ajustes/schemas.ts`**

En `web/src/lib/ajustes/schemas.ts`:
1. Borrar las definiciones locales `trimmedString` (líneas 5-8) y `optionalEmail` (líneas 10-14).
2. Cambiar el import de la línea 3 para añadir los compartidos:
```ts
import { z } from "zod";

import { cifNifSchema, emailOptional, telefonoSchema, trimmedString } from "@/lib/shared/validators";
import { REDONDEO_RECAUDACION_OPCIONES, ZONAS_HORARIAS } from "./types";
```
3. Sustituir campos dentro de `EmpresaAjustesSchema`:
```ts
  cif: cifNifSchema("cifMuyLargo"),
```
```ts
  telefono: telefonoSchema("telefonoMuyLargo"),
```
```ts
  email: emailOptional,
```
(la línea 25 `email: optionalEmail` pasa a `email: emailOptional`).

- [ ] **Step 5: Añadir claves i18n**

En `web/src/i18n/messages/es.json`, en el **namespace del formulario de Local** (junto a `"cifONifMuyLargo"` ~l.492 y `"telefonoMuyLargo"` ~l.494) añadir:
```json
"cifInvalido": "El CIF/NIF no es válido.",
"telefonoInvalido": "El teléfono no es válido (9 dígitos, empieza por 6-9).",
```
En el **namespace del formulario de Ajustes** (junto a `"telefonoMuyLargo"` ~l.1140) añadir las mismas dos claves:
```json
"cifInvalido": "El CIF no es válido.",
"telefonoInvalido": "El teléfono no es válido (9 dígitos, empieza por 6-9).",
```
> Nota: las claves deben vivir en el mismo namespace que ya contiene `emailInvalido`/`cifONifMuyLargo` de cada form, porque el componente resuelve el mensaje del refine con `t(message)` en ese namespace. Verifica con `grep -n '"cifONifMuyLargo"' web/src/i18n/messages/es.json` y `grep -n '"cifMuyLargo"' web/src/i18n/messages/es.json` para localizar los dos bloques exactos.

- [ ] **Step 6: Ejecutar tests y typecheck**

Run:
```bash
npm --prefix web run test -- src/lib/locales/schemas.test.ts src/lib/shared/validators.test.ts
npm --prefix web run lint
```
Expected: PASS y lint limpio.

- [ ] **Step 7: Commit**

```bash
git add web/src/lib/locales/schemas.ts web/src/lib/ajustes/schemas.ts web/src/i18n/messages/es.json web/src/lib/locales/schemas.test.ts
git commit -m "feat(web): valida CIF/NIF y teléfono en alta de local y ajustes (T-NNN)"
```

---

### Task 3: Validadores en Android (`GestionShared`) + JUnit

**Files:**
- Modify: `android/app/src/main/java/com/recre/app/feature/gestion/GestionShared.kt`
- Test: `android/app/src/test/java/com/recre/app/feature/gestion/GestionValidadoresTest.kt`

**Interfaces:**
- Produces: `fun esCifNif(raw: String): Boolean`, `fun esTelefono(raw: String): Boolean` (paquete `com.recre.app.feature.gestion`).

- [ ] **Step 1: Escribir el test que falla**

Create `android/app/src/test/java/com/recre/app/feature/gestion/GestionValidadoresTest.kt`:
```kotlin
package com.recre.app.feature.gestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vectores oro de CIF/NIF y teléfono: deben coincidir con web/src/lib/shared/validators.test.ts. */
class GestionValidadoresTest {

    @Test
    fun cifNif_validos() {
        listOf("12345678Z", "00000000T", " 12345678-z ", "X1234567L", "Y1234567X", "A58818501", "P1234567D")
            .forEach { assertTrue("esperaba valido: $it", esCifNif(it)) }
    }

    @Test
    fun cifNif_invalidos() {
        listOf("12345678A", "X1234567A", "A58818500", "P1234567A", "1234")
            .forEach { assertFalse("esperaba invalido: $it", esCifNif(it)) }
    }

    @Test
    fun telefono_validos() {
        listOf("612345678", "912345678", "+34 612 345 678", "0034612345678")
            .forEach { assertTrue("esperaba valido: $it", esTelefono(it)) }
    }

    @Test
    fun telefono_invalidos() {
        listOf("512345678", "61234567", "61234567a", "1234")
            .forEach { assertFalse("esperaba invalido: $it", esTelefono(it)) }
    }

    @Test
    fun cifNif_paridad_con_email_existente() {
        // sanity: el helper no rompe el módulo existente
        assertEquals(true, esEmailValido("a@b.com"))
    }
}
```

- [ ] **Step 2: Ejecutar y verificar que falla**

Run (desde `android/`):
```bash
JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:testDebugUnitTest --tests "com.recre.app.feature.gestion.GestionValidadoresTest"
```
Expected: FAIL de compilación — `esCifNif` / `esTelefono` no existen.

- [ ] **Step 3: Implementar el puerto Kotlin**

En `android/app/src/main/java/com/recre/app/feature/gestion/GestionShared.kt`, justo **después** de la línea 59 (`fun esEmailValido(...)`), añadir:
```kotlin
// ---------------------------------------------------------- documentos (CIF/NIF/NIE)

private const val DNI_LETRAS = "TRWAGMYFPDXBNJZSQVHLCKE"
private const val CIF_LETRAS_CONTROL = "JABCDEFGHI"

private fun normalizarDocumento(raw: String): String =
    raw.uppercase().replace(Regex("[\\s-]"), "")

private fun esNif(doc: String): Boolean {
    val numero = doc.substring(0, 8).toInt()
    return doc[8] == DNI_LETRAS[numero % 23]
}

private fun esNie(doc: String): Boolean {
    val prefijo = when (doc[0]) {
        'X' -> "0"
        'Y' -> "1"
        else -> "2"
    }
    val numero = (prefijo + doc.substring(1, 8)).toInt()
    return doc[8] == DNI_LETRAS[numero % 23]
}

private fun esCif(doc: String): Boolean {
    val letra = doc[0]
    val control = doc[8]
    var suma = 0
    for (i in 0 until 7) {
        var n = doc[i + 1] - '0' // dígitos en posiciones 1..7
        if (i % 2 == 0) {
            n *= 2
            if (n > 9) n -= 9
        }
        suma += n
    }
    val e = (10 - (suma % 10)) % 10
    val digitoControl = '0' + e
    val letraControl = CIF_LETRAS_CONTROL[e]
    return when {
        "PQSNWK".contains(letra) -> control == letraControl
        "ABEH".contains(letra) -> control == digitoControl
        else -> control == digitoControl || control == letraControl
    }
}

/** True si `raw` es un NIF, NIE o CIF español válido (dígito de control real). */
fun esCifNif(raw: String): Boolean {
    val doc = normalizarDocumento(raw)
    return when {
        Regex("^\\d{8}[A-Z]$").matches(doc) -> esNif(doc)
        Regex("^[XYZ]\\d{7}[A-Z]$").matches(doc) -> esNie(doc)
        Regex("^[ABCDEFGHJKLMNPQRSUVW]\\d{7}[0-9A-J]$").matches(doc) -> esCif(doc)
        else -> false
    }
}

// ---------------------------------------------------------- teléfono

private fun normalizarTelefonoEs(raw: String): String =
    raw.replace(Regex("[\\s-]"), "").replace(Regex("^(\\+34|0034)"), "")

/** True si `raw` es un teléfono español válido (9 dígitos, empieza 6-9). */
fun esTelefono(raw: String): Boolean =
    Regex("^[6-9]\\d{8}$").matches(normalizarTelefonoEs(raw))
```

- [ ] **Step 4: Ejecutar y verificar que pasa**

Run (desde `android/`):
```bash
JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:testDebugUnitTest --tests "com.recre.app.feature.gestion.GestionValidadoresTest"
```
Expected: PASS (4 tests de vectores + sanity).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/recre/app/feature/gestion/GestionShared.kt android/app/src/test/java/com/recre/app/feature/gestion/GestionValidadoresTest.kt
git commit -m "feat(android): valida CIF/NIF y teléfono en GestionShared (T-NNN)"
```

---

### Task 4: Cablear el formulario de Local (Android)

**Files:**
- Modify: `android/.../feature/gestion/locales/LocalFormViewModel.kt`
- Modify: `android/.../feature/gestion/locales/LocalFormScreen.kt`
- Modify: `android/app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `esCifNif`, `esTelefono` de Task 3.

- [ ] **Step 1: Validar en `guardar()`**

En `LocalFormViewModel.kt`:
1. Añadir los imports (junto a la línea 11 `import com.recre.app.feature.gestion.esEmailValido`):
```kotlin
import com.recre.app.feature.gestion.esCifNif
import com.recre.app.feature.gestion.esTelefono
```
2. En `guardar()`, justo **después** de la línea 104 (`if (email.isNotEmpty() && !esEmailValido(email)) ...`), añadir:
```kotlin
        val cif = s.cifONif.trim()
        if (cif.isNotEmpty() && !esCifNif(cif)) errores["cifONif"] = "cif_invalido"
        val telefono = s.telefono.trim()
        if (telefono.isNotEmpty() && !esTelefono(telefono)) errores["telefono"] = "telefono_invalido"
```

- [ ] **Step 2: Mostrar el error en pantalla**

En `LocalFormScreen.kt`, en el `GestionTextField` del CIF (líneas 75-80), añadir el parámetro `error`:
```kotlin
            GestionTextField(
                label = stringResource(R.string.gestion_local_cif),
                value = state.cifONif,
                onValueChange = viewModel::onCifChange,
                error = state.errores["cifONif"]?.let {
                    stringResource(R.string.gestion_validacion_cif)
                },
                modifier = Modifier.weight(1f),
            )
```
Y en el `GestionTextField` del teléfono (líneas 81-87):
```kotlin
            GestionTextField(
                label = stringResource(R.string.gestion_local_telefono),
                value = state.telefono,
                onValueChange = viewModel::onTelefonoChange,
                keyboardType = KeyboardType.Phone,
                error = state.errores["telefono"]?.let {
                    stringResource(R.string.gestion_validacion_telefono)
                },
                modifier = Modifier.weight(1f),
            )
```

- [ ] **Step 3: Añadir los strings**

En `android/app/src/main/res/values/strings.xml`, junto a la línea 494 (`gestion_validacion_email`), añadir:
```xml
    <string name="gestion_validacion_cif">CIF/NIF no válido.</string>
    <string name="gestion_validacion_telefono">Teléfono no válido (9 dígitos, empieza por 6-9).</string>
```

- [ ] **Step 4: Verificar compilación + tests**

Run (desde `android/`):
```bash
JAVA_HOME=/snap/android-studio/current/jbr ./gradlew assembleDebug
JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:testDebugUnitTest --tests "com.recre.app.feature.gestion.GestionValidadoresTest"
```
Expected: `assembleDebug` BUILD SUCCESSFUL y el test JUnit en verde.

> Smoke manual (opcional, no bloqueante): abrir alta de Local, escribir `12345678A` en CIF y `512345678` en teléfono → el campo muestra el error; con `12345678Z` y `612345678` guarda sin error; vacíos guardan sin error.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/recre/app/feature/gestion/locales/LocalFormViewModel.kt android/app/src/main/java/com/recre/app/feature/gestion/locales/LocalFormScreen.kt android/app/src/main/res/values/strings.xml
git commit -m "feat(android): bloquea guardar local con CIF/NIF o teléfono inválido (T-NNN)"
```

---

## Self-Review

- **Cobertura de la spec (§5 PR-A):** validar CIF/NIF (T1/T3 ✓), teléfono (T1/T3 ✓), unificar email centralizándolo en web (`emailOptional` compartido, T1+T2 ✓; Android conserva `esEmailValido`, sin cambios ✓), inyección en schema web cubre form+action (T2 ✓), Android dura y condicional a no vacío (T4 ✓), vectores oro replicados (T1 Vitest + T3 JUnit ✓), Edge **no** se toca (✓, no hay tarea). Sin huecos.
- **Placeholders:** `T-NNN` es el número de tarea a asignar desde `tasks.md` (valor externo, documentado en Global Constraints), no un placeholder de contenido. Los números de línea (`~l.492`, etc.) llevan comando `grep` de verificación. Sin TODO/TBD ni "handle edge cases".
- **Consistencia de tipos/nombres:** `esCifNif`/`esTelefonoEs` (TS) y `esCifNif`/`esTelefono` (Kotlin) — el nombre del teléfono difiere a propósito por convención de cada lado (`esTelefono` casa con `esEmailValido` en Kotlin); ambos se consumen dentro de su propia plataforma, no hay llamada cruzada. Claves de error: web usa `cifInvalido`/`telefonoInvalido` (i18n), Android usa `cif_invalido`/`telefono_invalido` (mapa de errores) → `gestion_validacion_cif`/`gestion_validacion_telefono` (strings). Coherente dentro de cada plataforma.

---

### Task 5: Unificar la validación de email (regex canónico + vectores oro)

**Contexto:** hoy web valida email con `z.string().email()` (motor propio de zod, además **deprecado en zod 4**) y Android con `EMAIL_REGEX`; reglas distintas → divergen en bordes. Este task adopta como **canónico el regex que Android YA usa** (`^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$`, exige dominio con TLD), lo pone en web reemplazando **todos** los `z.string().email()` de cliente, y lo fija con vectores oro replicados en Vitest y JUnit. Android **no cambia su regex** (cero regresión en móvil): solo se le añaden los vectores como cerrojo de paridad.

**Files:**
- Modify: `web/src/lib/shared/validators.ts` (añadir `EMAIL_REGEX` + `esEmailValido`; `emailOptional` pasa a `refine`)
- Modify: `web/src/lib/shared/validators.test.ts` (vectores oro de email)
- Modify: `web/src/lib/equipo/actions.ts:28` (`.email(...)` → `refine`)
- Modify: `web/src/lib/registro/schemas.ts:27` (`.email(...)` → `refine`)
- Modify: `web/src/app/(auth)/login/login-form.tsx:31` (`.email(...)` → `refine`)
- Modify: `web/src/app/(auth)/registro/registro-form.tsx:40` (`.email(...)` → `refine`)
- Modify: `android/app/src/test/java/com/recre/app/feature/gestion/GestionValidadoresTest.kt` (vectores oro de email)

**Interfaces:**
- Produces (web): `esEmailValido(raw: string): boolean`; `emailOptional` (mismo export, ya sin `z.string().email()`).
- Android: `esEmailValido` ya existe con el MISMO regex canónico (no se toca); se añaden vectores.

**Vectores oro de email (idénticos en Vitest y JUnit):**

| Input | válido |
|-------|--------|
| `user@example.com` | true |
| `first.last+tag@sub.domain.co` | true |
| `n@dominio.es` | true |
| `a@b` | false (sin TLD) |
| `sin-arroba.com` | false (sin @) |
| `@example.com` | false (sin parte local) |
| `user@dominio` | false (sin punto+TLD) |
| `user@dominio.c` | false (TLD de 1 letra) |

- [ ] **Step 1: Ampliar el test web (falla)**

En `web/src/lib/shared/validators.test.ts`: añadir `esEmailValido` al import desde `./validators` y este bloque:
```ts
describe("esEmailValido (vectores oro)", () => {
  it.each([
    ["user@example.com", true],
    ["first.last+tag@sub.domain.co", true],
    ["n@dominio.es", true],
    ["a@b", false],
    ["sin-arroba.com", false],
    ["@example.com", false],
    ["user@dominio", false],
    ["user@dominio.c", false],
  ])("esEmailValido(%j) === %s", (input, expected) => {
    expect(esEmailValido(input as string)).toBe(expected);
  });
});
```

- [ ] **Step 2: Ejecutar y verificar que falla**

Run:
```bash
npm --prefix web run test -- src/lib/shared/validators.test.ts
```
Expected: FAIL — `esEmailValido` no está exportado aún (import roto).

- [ ] **Step 3: Implementar el email canónico en `validators.ts`**

En `web/src/lib/shared/validators.ts`, añadir (junto a las demás constantes/validadores, antes de `emailOptional`):
```ts
// Regex de email canónico: pragmático, exige dominio con TLD (2+ letras).
// Byte-idéntico al de android GestionShared.kt (esEmailValido); ambos se fijan
// con los mismos vectores oro. Sustituye a z.string().email(), deprecado en zod 4.
const EMAIL_REGEX = /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;

/** True si `raw` tiene formato de email válido (local@dominio.tld). */
export function esEmailValido(raw: string): boolean {
  return EMAIL_REGEX.test(raw);
}
```
Y cambiar `emailOptional` para que use el refine en vez de `z.string().email()`:
```ts
export const emailOptional = z
  .string()
  .trim()
  .transform((v) => (v.length === 0 ? null : v))
  .pipe(
    z
      .string()
      .refine((v) => esEmailValido(v), { message: "emailInvalido" })
      .nullable(),
  );
```

- [ ] **Step 4: Ejecutar y verificar que pasa**

Run:
```bash
npm --prefix web run test -- src/lib/shared/validators.test.ts
```
Expected: PASS.

- [ ] **Step 5: Reemplazar los otros usos de `.email()` en web**

En cada uno de estos ficheros, importar `esEmailValido` desde `@/lib/shared/validators` y sustituir `.email({ message: X })` por `.refine((v) => esEmailValido(v), { message: X })` **conservando exactamente el argumento de mensaje `X`** existente:
- `web/src/lib/equipo/actions.ts:28` → `X = "emailInvalido"`
- `web/src/lib/registro/schemas.ts:27` → `X = "emailInvalido"`
- `web/src/app/(auth)/login/login-form.tsx:31` → `X = t("emailInvalid")` (¡ojo, la clave es `emailInvalid`, no `emailInvalido`!)
- `web/src/app/(auth)/registro/registro-form.tsx:40` → `X = t("emailInvalido")`

No cambiar nada más de esas cadenas Zod (min/trim/required se mantienen).

- [ ] **Step 6: Verificar web completo**

Run:
```bash
npm --prefix web run test
npm --prefix web run lint
```
Expected: toda la suite en verde; lint sin errores nuevos. Confirma que no queda ningún `z.string().email(` en `web/src` (fuera de tests): `grep -rn "\.email(" web/src --include=*.ts --include=*.tsx | grep -v "\.test\."` debe salir vacío.

- [ ] **Step 7: Cerrojo de paridad en Android (vectores oro)**

En `android/app/src/test/java/com/recre/app/feature/gestion/GestionValidadoresTest.kt`, añadir dos tests (los MISMOS vectores que web; pasan con el `EMAIL_REGEX` actual de Android, sin tocar `GestionShared.kt`):
```kotlin
    @Test
    fun email_validos() {
        listOf("user@example.com", "first.last+tag@sub.domain.co", "n@dominio.es")
            .forEach { assertTrue("esperaba valido: $it", esEmailValido(it)) }
    }

    @Test
    fun email_invalidos() {
        listOf("a@b", "sin-arroba.com", "@example.com", "user@dominio", "user@dominio.c")
            .forEach { assertFalse("esperaba invalido: $it", esEmailValido(it)) }
    }
```

- [ ] **Step 8: Ejecutar el test Android**

Run (desde `android/`, con el workaround de locale si hace falta):
```bash
cd /home/a/Escritorio/recre-main/android && LC_ALL=es_ES.utf8 JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:testDebugUnitTest --tests "com.recre.app.feature.gestion.GestionValidadoresTest"
```
Expected: PASS (incluye los 2 tests de email nuevos). Si un daemon Kotlin quedó atascado por locale, `./gradlew --stop` y reintentar.

- [ ] **Step 9: Commit**

```bash
git add web/src/lib/shared/validators.ts web/src/lib/shared/validators.test.ts web/src/lib/equipo/actions.ts web/src/lib/registro/schemas.ts "web/src/app/(auth)/login/login-form.tsx" "web/src/app/(auth)/registro/registro-form.tsx" android/app/src/test/java/com/recre/app/feature/gestion/GestionValidadoresTest.kt
git commit -m "feat(repo): unifica la validación de email en web y Android (T-267)"
```

**Self-review (Task 5):** El regex web es byte-idéntico al de Android (misma clase de caracteres, mismo `\.[A-Za-z]{2,}$`), así que los vectores oro compartidos garantizan paridad real; `esEmailValido` puro (sin dependencia de zod) elimina la deprecación de zod 4 en cliente; `emailOptional` mantiene su semántica (vacío→null, condicional a no vacío); el Edge (`_shared/schemas.ts`, Deno) queda **fuera de alcance** a propósito (server, otra runtime — pendiente si se aborda SSOT de email).
