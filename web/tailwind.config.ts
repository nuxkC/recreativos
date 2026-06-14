import type { Config } from "tailwindcss";

/**
 * Los tokens usan referencias `var(--xxx)` directas (sin envolver en `hsl()`).
 * Las variables en `src/app/globals.css` son los HEX de marca (Fase 3, SSOT en
 * .kiro/specs/recre/fase3-design-tokens.md), no canales sueltos: envolverlas en
 * `hsl()` produciría declaraciones CSS inválidas. Consumir SIEMPRE por rol
 * (success/warning/danger/info), nunca `emerald-100`/`amber-100` directos.
 */
const config: Config = {
  darkMode: ["class"],
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        background: "var(--background)",
        foreground: "var(--foreground)",
        surface: {
          "1": "var(--surface-1)",
          "2": "var(--surface-2)",
        },
        card: {
          DEFAULT: "var(--card)",
          foreground: "var(--card-foreground)",
        },
        popover: {
          DEFAULT: "var(--popover)",
          foreground: "var(--popover-foreground)",
        },
        primary: {
          DEFAULT: "var(--primary)",
          foreground: "var(--primary-foreground)",
          text: "var(--primary-text)",
        },
        secondary: {
          DEFAULT: "var(--secondary)",
          foreground: "var(--secondary-foreground)",
        },
        muted: {
          DEFAULT: "var(--muted)",
          foreground: "var(--muted-foreground)",
          strong: "var(--muted-strong)", // iconos/€/sufijos informativos ≥7:1
        },
        accent: {
          DEFAULT: "var(--accent)",
          foreground: "var(--accent-foreground)",
        },
        destructive: {
          DEFAULT: "var(--destructive)",
          foreground: "var(--destructive-foreground)",
        },
        // Roles semánticos de dominio — consumir SIEMPRE por rol.
        // `text` = variante -text (texto pequeño/etiqueta soft-chip, WCAG AA);
        // `DEFAULT` (fill) solo para iconos, rellenos y cifras grandes.
        success: {
          DEFAULT: "var(--success)",
          foreground: "var(--success-foreground)",
          subtle: "var(--success-subtle)",
          text: "var(--success-text)",
          "chip-bg": "var(--success-chip-bg)",
          "chip-fg": "var(--success-chip-fg)",
        },
        warning: {
          DEFAULT: "var(--warning)",
          foreground: "var(--warning-foreground)",
          subtle: "var(--warning-subtle)",
          text: "var(--warning-text)",
          "chip-bg": "var(--warning-chip-bg)",
          "chip-fg": "var(--warning-chip-fg)",
        },
        danger: {
          DEFAULT: "var(--danger)",
          foreground: "var(--danger-foreground)",
          subtle: "var(--danger-subtle)",
          text: "var(--danger-text)",
          "chip-bg": "var(--danger-chip-bg)",
          "chip-fg": "var(--danger-chip-fg)",
        },
        info: {
          DEFAULT: "var(--info)",
          foreground: "var(--info-foreground)",
          subtle: "var(--info-subtle)",
          text: "var(--info-text)",
          "chip-bg": "var(--info-chip-bg)",
          "chip-fg": "var(--info-chip-fg)",
        },
        // Superficie de ESTADO neutra (no marca): deuda EUR, offline, RBAC, etc.
        "state-neutral": {
          DEFAULT: "var(--state-neutral)",
          border: "var(--state-neutral-border)",
          foreground: "var(--state-neutral-foreground)",
          muted: "var(--state-neutral-muted)",
        },
        // Par opaco del chip neutral (offline/borrador) — distinto de muted shadcn.
        neutral: {
          "chip-bg": "var(--neutral-chip-bg)",
          "chip-fg": "var(--neutral-chip-fg)",
        },
        border: "var(--border)",
        "border-strong": "var(--border-strong)",
        input: "var(--input)",
        ring: "var(--ring)",
        chart: {
          "1": "var(--chart-1)",
          "2": "var(--chart-2)",
          "3": "var(--chart-3)",
          "4": "var(--chart-4)",
          "5": "var(--chart-5)",
        },
        sidebar: {
          DEFAULT: "var(--sidebar)",
          foreground: "var(--sidebar-foreground)",
          primary: "var(--sidebar-primary)",
          "primary-foreground": "var(--sidebar-primary-foreground)",
          accent: "var(--sidebar-accent)",
          "accent-foreground": "var(--sidebar-accent-foreground)",
          border: "var(--sidebar-border)",
          ring: "var(--sidebar-ring)",
        },
      },
      borderRadius: {
        sm: "var(--radius-sm)", // chips, badges, inputs compactos
        md: "var(--radius-md)", // botones, inputs, selects
        lg: "var(--radius-lg)", // cards, popover, dropdown (== --radius)
        xl: "var(--radius-xl)", // dialog, sheet/drawer, contenedores destacados
      },
      fontFamily: {
        sans: ["var(--font-sans)"],
        mono: ["var(--font-mono)"],
      },
      fontSize: {
        // Escala sans de marca (size + line-height + weight desde globals.css).
        kpi: ["var(--fs-kpi)", { lineHeight: "var(--lh-kpi)", fontWeight: "600" }],
        h1: ["var(--fs-h1)", { lineHeight: "var(--lh-h1)", fontWeight: "600" }],
        h2: ["var(--fs-h2)", { lineHeight: "var(--lh-h2)", fontWeight: "600" }],
        body: ["var(--fs-body)", { lineHeight: "var(--lh-body)", fontWeight: "440" }],
        "body-lg": ["var(--fs-body-lg)", { lineHeight: "var(--lh-body-lg)", fontWeight: "440" }],
        caption: ["var(--fs-caption)", { lineHeight: "var(--lh-caption)", fontWeight: "500" }],
        label: ["var(--fs-label)", { lineHeight: "var(--lh-label)", fontWeight: "600" }],
      },
      spacing: {
        // Rejilla canónica 4/8/12/16/24/32 (alias por intención de layout).
        "grid-1": "var(--space-1)",
        "grid-2": "var(--space-2)",
        "grid-3": "var(--space-3)",
        "grid-4": "var(--space-4)",
        "grid-6": "var(--space-6)",
        "grid-8": "var(--space-8)",
      },
      boxShadow: {
        // Elevación por capas: las cards se apoyan en borde 1px; las sombras
        // quedan SOLO para overlays (popover/tooltip) y modales (dialog/sheet).
        overlay: "var(--elevation-overlay)",
        modal: "var(--elevation-modal)",
      },
      transitionTimingFunction: {
        // Easing estándar del sistema (entradas/salidas de UI); `ease-standard`.
        standard: "cubic-bezier(0.2, 0, 0, 1)",
      },
    },
  },
  plugins: [require("tailwindcss-animate")],
};

export default config;
