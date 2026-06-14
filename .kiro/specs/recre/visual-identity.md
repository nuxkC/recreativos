# Identidad visual — Recre (direcciones de estilo)

> Estado: **✅ DECIDIDO — "Confianza Industrial" (azul petróleo)** (elegida por el usuario). Este documento es la
> **fuente de verdad de la identidad y la paleta**; las alternativas 2–4 quedan como registro de lo descartado (y
> reserva: p.ej. el azul corporativo `#1D4ED8` como acento conservador). `design-system-plan.md` **referencia** esta
> paleta, no la duplica. Toda cifra económica es **SSOT servidor**; aquí solo se decide *presentación*.

## Veredicto de mercado (resumen del análisis)

Recre es **B2B de dinero + operaciones** para empresas de máquinas recreativas (es-ES): dos superficies, una app de
campo Android (técnico, sol directo, una mano, offline) y un back-office web (gestor/contable, datos densos).

- **Incumbentes del sector** (Nayax, Cantaloupe) y la **convención fintech** apuntan a lo mismo: **azul = confianza**;
  **verde = positivo**, **rojo/ámbar = alerta**; neutros = sofisticación para densidad. Paletas restringidas e
  intencionales puntúan más alto en *profesionalidad percibida*.
- **Restricción dura de Recre**: el verde (`#22C55E`) y el rojo (`#EF4444`) ya están **semánticamente ocupados** por
  el estado de dinero. El **azul es la única familia de confianza libre** → un acento azul/petróleo **nunca colisiona**
  con el estado.
- **Hoy las dos superficies están des-marcadas**: web = shadcn slate por defecto **sin acento**; Android = Material3
  con Indigo `#6366F1` genérico. La oportunidad es **converger ambas** en un único sistema de tokens por rol con un
  **acento propio**.

**Recomendación**: disciplina **N26** (un acento propio, tokens por rol, pares light/dark) ejecutada como
**Operativo-Pro** (base neutra, acento ≤10% de la UI, el dato manda) con **acento azul/petróleo**. Se descarta Revolut
para la UI de trabajo (gradientes/violeta → consumo, ruido en tablas, gasto de contraste); como mucho, en login/splash.

**Reglas no negociables de color** (todas las direcciones las respetan):
1. Verde **solo** = positivo/éxito; rojo **solo** = error/avería; ámbar **solo** = aviso. Nunca como marca.
2. **Nunca** comunicar estado solo por color → icono + texto siempre (sol directo + ~8% daltonismo rojo-verde).
3. Acento en **≤10%** de la pantalla (CTA primario, nav activo, fila/estado seleccionado).
4. Tokens con **nombre de rol** (`color-action`, `color-success-bg`), no de valor (`blue-600`).
5. Android: contraste de texto/acción objetivo **~7:1** (por encima de AA) para legibilidad al sol.

---

## ⭐ Dirección recomendada — "Confianza Industrial" (azul petróleo)

N26-disciplina + Operativo-Pro, con acento **azul petróleo propio**. Base neutra fría, el dato como héroe, un único
acento que dirige la acción. Distintiva sin ser "azul-banco genérico", e industrial sin ser fría.

**Paleta — Light**
| Rol | Hex | Uso |
|---|---|---|
| background | `#FAFBFC` | lienzo |
| surface-1 | `#FFFFFF` | cards, bento |
| surface-2 | `#F4F6F8` | elevada, filas alternas, inputs |
| **primary** | `#0E7490` | CTA, nav activo, foco, KPI recaudación |
| on-primary | `#FFFFFF` | texto sobre primary |
| secondary | `#E6F2F4` | botón tonal, chip neutro |
| success | `#0E8A55` | cuadra, sincronizado |
| warning | `#B45309` | pendiente, sin firmar |
| danger | `#DC2626` | conflicto, descuadre, avería |
| info | `#2563EB` | informativo (separado del primary) |
| border | `#E3E6EA` | hairlines 1px |
| muted | `#646B76` | texto secundario, unidades € |
| ring | `#0E7490` | foco 2px + offset |

**Paleta — Dark**
| Rol | Hex | | Rol | Hex |
|---|---|---|---|---|
| background | `#0B0C0E` | | success | `#34D399` |
| surface-1 | `#131519` | | warning | `#FBBF24` |
| surface-2 | `#1B1E24` | | danger | `#F87171` |
| primary | `#2BC4DD` | | info | `#60A5FA` |
| on-primary | `#06212A` | | border | `#262A31` |
| secondary | `#16323A` | | muted | `#9AA1AD` |

> Alternativa conservadora del acento si el petróleo compite visualmente con el verde de éxito en alguna pantalla:
> **azul corporativo `#1D4ED8`** (light) / `#5B8DEF` (dark) — más "azul-banco", máxima seguridad de hue.

**Tipografía**: Geist Sans (UI) + Geist Mono `tabular-nums` en toda cifra. Web: KPI 36/600, H1 24/600, body 14/440,
caption 12/500. Android +1 paso: importe 40/700 tabular, body 16/450. Autoridad por **tamaño y contención**, no por
grosor. `€` en muted, dígito en foreground; miles es-ES `1.234,56`.

**Forma/elevación**: radios 6/8/12/16 (Android +1: 12/16/20); elevación por **borde 1px en light** y por **luminancia
en dark** (sin sombras duras); sombras solo en overlays (menús, modales, Cmd+K).

**Iconografía**: **Lucide** (web, trazo 1.5px, outline; filled solo en nav activa) + **Phosphor** (Android, fill en
seleccionado, 24–28dp). Iconos de dominio propios (máquina, tolva, denominación, firma, impresora BT, avería,
conflicto) en la misma rejilla. Ilustración casi nula: estados vacíos = un glifo grande muted + frase. SVG mono-trazo
con `currentColor` para temar solo.

**Motion**: calmado y funcional (120–180ms, `cubic-bezier(0.2,0,0,1)`); Cmd+K/popovers con fade + 4px sin rebote.
Firma del producto: **count-up tabular** del neto/`parte_empresa` cuando responde el servidor (subraya el SSOT); badge
offline que pulsa lento; flash success al sincronizar; shake danger ante descuadre. Respeta `reduced-motion`.

**Componentes firma**: card (surface-1, border 1px, radio 12); botón primario (primary, 1 por pantalla; Android full-
width sticky 56dp); chips de estado "soft" (fondo del color al 12–16%, texto pleno, dot); tabla densa (header sticky
surface-2, filas 44px, importes tabular a la derecha, estado en chip, acciones en hover); KPI (número tabular dominante
+ delta + sparkline en accent); sidebar 240px / bottom-nav Android con píldora `primaryContainer`.

---

## Alternativa 2 — "Operativo Pro" (indigo-violeta, Linear/Stripe/Qonto)

Idéntica disciplina y estructura que la recomendada, pero con el acento **indigo-violeta canónico** del back-office
operativo de 2025-26 (entre el `#5e6ad2` de Linear y el blurple `#635BFF` de Stripe). Lee como "software financiero de
confianza"; es la opción más segura/establecida, ligeramente menos diferenciada que el petróleo.

**Light**: bg `#FAFBFC` · surf-1 `#FFFFFF` · surf-2 `#F4F6F8` · **primary `#5B5BD6`** · on-primary `#FFFFFF` ·
secondary `#EEEEFB` · accent(teal datos) `#0E9E8E` · success `#0E8A55` · warning `#B45309` · danger `#DC2626` ·
info `#2563EB` · border `#E3E6EA` · muted `#646B76` · ring `#5B5BD6`.
**Dark**: bg `#0B0C0E` · surf-1 `#131519` · surf-2 `#1B1E24` · **primary `#7C7CF0`** · on-primary `#0B0C0E` ·
accent `#3DD9C7` · success `#34D399` · warning `#FBBF24` · danger `#F87171` · info `#60A5FA` · border `#262A31` ·
muted `#9AA1AD`. Tipografía/forma/iconografía/motion = iguales a la recomendada.

---

## Alternativa 3 — "N26" (verde-menta, claro y aireado)

Máximo aire y cercanía ("dinero que respira"); jerarquía por espacio + tipografía. Acento **mint-teal Keppel**.
**Caveat**: el acento menta queda **cromáticamente cerca del verde de éxito** → exige separar con cuidado (marca
`#0B7A6E`/`#34C7A8` vs success `#15803D`) y reforzar estado con icono+texto.

**Light**: bg `#FBFBFA` · surf-1 `#FFFFFF` · surf-2 `#F3F4F2` · **primary `#0B7A6E`** · accent `#34C7A8` ·
success `#15803D` · warning `#B45309` · danger `#C0322B` · info `#1D6FB8` · border `#E4E5E2` · muted `#6B6E69`.
**Dark**: bg `#15161A` (gris-azulado, no negro) · surf-1 `#1D1F24` · surf-2 `#262A30` · **primary `#3DD6B4`** ·
accent `#5BE3C4` · success `#4ADE80` · warning `#F5A623` · danger `#F26A60` · info `#5AA9E6`. Radios más generosos
(8/12/16/24), ilustración spot suave mint-teal, motion con micro-energía en confirmaciones.

---

## Alternativa 4 — "Revolut" (violeta eléctrico, dark premium)

Oscuro premium de alta densidad: lienzo **cinder `#0A0A0F`**, único acento **cobalt-violet** que solo significa
DINERO/ACCIÓN, y **tarjeta KPI héroe con gradiente** violeta→azul (única superficie con gradiente). Diferenciador de
marca fuerte; **caveat**: vigilar densidad y contraste AA en tablas para contables; gradiente/violeta tienden a consumo.

**Light**: bg `#F5F6F8` · surf-1 `#FFFFFF` · **primary `#5B2BE6`** · accent `#3D63FF` · success `#0E9F6E` ·
warning `#D98A00` · danger `#E02424` · info `#0E7FE0` · border `#DDE1E8` · muted `#6B7280`.
**Dark (canónico)**: bg `#0A0A0F` · surf-1 `#14141C` · surf-2 `#1E1E2A` · **primary `#7C5CFF`** · on-primary `#0A0A0F` ·
accent `#5B8CFF` · success `#22C58B` · warning `#F5A524` · danger `#FF5A52` · info `#3FA9FF`. Radios grandes
(10/14/20/28), KPI héroe glossy con highlight interno, números display 48–56/700 con count-up/roll, springs con
overshoot mínimo.

---

## Decisión tomada

**Elegida: "Confianza Industrial" (azul petróleo)** — sección ⭐ de arriba. Las alternativas 2–4 quedan archivadas.
Próximos pasos:
1. Los **tokens definitivos** (paleta de arriba) se materializan en Fase 2: web (`globals.css` + `tailwind.config`)
   y Android (`Color.kt`/`Type.kt`/`Shapes`/`Theme.kt`).
2. El **refactor pantalla-por-pantalla** se hace sobre la IA de **`functional-audit-and-ia.md`** con esta identidad.
