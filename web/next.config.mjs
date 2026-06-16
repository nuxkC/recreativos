import createNextIntlPlugin from "next-intl/plugin";

const withNextIntl = createNextIntlPlugin("./src/i18n/request.ts");

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  experimental: {
    // T-244: transiciones de vista nativas en navegación (lista→detalle).
    // Habilitado por React 19 + Next 16. El morph con nombre va por CSS
    // `view-transition-name`; reduced-motion se respeta en globals.css.
    viewTransition: true,
  },
};

export default withNextIntl(nextConfig);
