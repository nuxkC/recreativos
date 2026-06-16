import nextCoreWebVitals from "eslint-config-next/core-web-vitals";
import nextTypescript from "eslint-config-next/typescript";

// ESLint 9 / flat config (Next 16 eliminó `next lint`). eslint-config-next 16
// exporta flat configs nativos, así que se importan directos (sin FlatCompat).
// Las reglas del proyecto se mantienen idénticas a las del antiguo `.eslintrc.json`.
const eslintConfig = [
  { ignores: [".next/**", "out/**", "build/**", "coverage/**"] },
  ...nextCoreWebVitals,
  ...nextTypescript,
  {
    rules: {
      "@typescript-eslint/no-unused-vars": [
        "error",
        { argsIgnorePattern: "^_", varsIgnorePattern: "^_" },
      ],
      "@typescript-eslint/no-explicit-any": "error",
      "@typescript-eslint/consistent-type-imports": [
        "warn",
        { prefer: "type-imports", fixStyle: "inline-type-imports" },
      ],
      "no-console": ["warn", { allow: ["warn", "error"] }],
      // react-hooks 7 (eslint-config-next 16) añade esta regla. Los usos actuales
      // son intencionados (guards de montaje anti-hydration + count-up de cifras),
      // no bugs; se deja como AVISO para no refactorizar a ciegas en un upgrade de
      // tooling. Deuda: revisar caso por caso (¿estado derivado / key remount?).
      "react-hooks/set-state-in-effect": "warn",
    },
  },
];

export default eslintConfig;
