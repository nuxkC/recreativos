"use client";

// Design System "Confianza Industrial" — motion web (Fase 4 · T-231).
// SSOT: .kiro/specs/recre/fase3-design-tokens.md (§ Motion).
//
// Capa de `motion` 12.x para transiciones de presencia y layout. Las animaciones
// firma puramente decorativas (popover/pulse/spin/flash/shake) viven como CSS en
// globals.css; aquí va lo que `motion` hace mejor: entrada/salida con
// AnimatePresence y transiciones de layout. El count-up de cifras es presentación
// aparte (@number-flow, T-238) — aquí NO se anima ninguna cifra económica.
//
// reduce-motion: el MotionProvider envuelve el árbol con reducedMotion="user", que
// respeta prefers-reduced-motion del SO (motion 12.x desactiva los transforms; el
// cambio de opacidad/estado de información se conserva). Combina con la regla CSS
// equivalente de globals.css. No hace falta guardar cada componente a mano.

import {
  AnimatePresence,
  MotionConfig,
  motion,
  type Transition,
  type Variants,
} from "motion/react";
import type { ComponentPropsWithoutRef, ReactNode } from "react";

// Curvas de marca como bezier (motion no lee CSS vars). Espejo de los
// --motion-ease-* de globals.css.
export const MOTION_EASE = {
  standard: [0.2, 0, 0, 1] as [number, number, number, number], // entra rápido, frena suave
  accelerate: [0.4, 0, 1, 1] as [number, number, number, number], // lo que sale
  decelerate: [0, 0, 0, 1] as [number, number, number, number], // lo que entra
};

// Duraciones de marca en SEGUNDOS (motion usa segundos; CSS usa ms). 120-180ms.
export const MOTION_DURATION = { fast: 0.12, default: 0.15, slow: 0.18 };

// Transiciones listas para componer.
export const TRANSITION: Record<"fast" | "default" | "slow" | "enter" | "exit", Transition> = {
  fast: { duration: MOTION_DURATION.fast, ease: MOTION_EASE.standard },
  default: { duration: MOTION_DURATION.default, ease: MOTION_EASE.standard },
  slow: { duration: MOTION_DURATION.slow, ease: MOTION_EASE.standard },
  enter: { duration: MOTION_DURATION.fast, ease: MOTION_EASE.decelerate },
  exit: { duration: MOTION_DURATION.fast, ease: MOTION_EASE.accelerate },
};

/** Entrada estándar: fade + 4px hacia arriba (entradas de cards/popover/listas). */
export const fadeInUp: Variants = {
  hidden: { opacity: 0, y: 4 },
  visible: { opacity: 1, y: 0, transition: TRANSITION.enter },
  exit: { opacity: 0, y: 4, transition: TRANSITION.exit },
};

/**
 * Raíz de motion: respeta prefers-reduced-motion del usuario (reducedMotion="user").
 * Colócalo alto en el árbol cliente (debajo de los providers de la app).
 */
export function MotionProvider({ children }: { children: ReactNode }) {
  return <MotionConfig reducedMotion="user">{children}</MotionConfig>;
}

type FadeInProps = ComponentPropsWithoutRef<typeof motion.div>;

/**
 * Aparición fade + 4px con la curva de marca. Úsalo para entradas de bloques al
 * montar (no para cifras económicas). Cualquier prop de motion.div es válida.
 */
export function FadeIn({ children, ...props }: FadeInProps) {
  return (
    <motion.div initial="hidden" animate="visible" variants={fadeInUp} {...props}>
      {children}
    </motion.div>
  );
}

/**
 * Ítem de lista animable: entra/sale con [fadeInUp] y transiciona su posición
 * (layout) cuando otros ítems aparecen/desaparecen. Envuelve la lista en
 * [AnimatePresence] y da una `key` estable a cada ítem.
 */
export function MotionItem({ children, ...props }: FadeInProps) {
  return (
    <motion.div layout initial="hidden" animate="visible" exit="exit" variants={fadeInUp} {...props}>
      {children}
    </motion.div>
  );
}

// Re-export para componer presencia/layout en las features sin importar de
// "motion/react" en cada sitio (un único punto de entrada del vocabulario).
export { AnimatePresence, motion };
