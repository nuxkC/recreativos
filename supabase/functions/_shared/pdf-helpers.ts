/**
 * Helpers comunes de composición de PDFs con `pdf-lib`.
 *
 * Extraídos al introducir el segundo generador de PDF (boletín de
 * instalación, T-203), siguiendo la regla DRY del proyecto (2.º uso → se
 * evalúa, se extrae lo común). Reutilizados tanto por el ticket de
 * recaudación (`pdf.ts`) como por el boletín (`boletin-pdf.ts`).
 *
 * `PdfWriter` mantiene un cursor vertical (`y`) y ofrece primitivas de
 * escritura (líneas, pares clave/valor, separadores, imágenes) sobre una
 * página A4.
 */

import { type PDFFont, type PDFImage, type PDFPage, rgb } from "pdf-lib";
import { PDFDocument, StandardFonts } from "pdf-lib";

export const MARGIN = 50;
export const LINE_HEIGHT = 14;

/** Familias tipográficas estándar soportadas por los generadores. */
export type FontFamily = "courier" | "helvetica";

interface WriteLineOptions {
  bold?: boolean;
  size?: number;
  /** Color del texto en escala 0..1. Por defecto negro. */
  color?: { r: number; g: number; b: number };
}

/**
 * Escritor secuencial sobre una página PDF. El cursor `y` desciende a medida
 * que se escribe; las primitivas no hacen salto de página (los documentos del
 * proyecto caben en una A4).
 */
export class PdfWriter {
  private y: number;

  constructor(
    readonly pdf: PDFDocument,
    readonly page: PDFPage,
    private readonly font: PDFFont,
    private readonly fontBold: PDFFont,
  ) {
    this.y = page.getHeight() - MARGIN;
  }

  /** Posición vertical actual del cursor (en puntos desde abajo). */
  get cursorY(): number {
    return this.y;
  }

  /** Reposiciona el cursor vertical (uso puntual, p. ej. tras dibujar imágenes). */
  set cursorY(value: number) {
    this.y = value;
  }

  /** Ancho útil de la página (entre márgenes). */
  get contentWidth(): number {
    return this.page.getWidth() - MARGIN * 2;
  }

  /** Escribe una línea de texto y baja el cursor. */
  line(text: string, opts: WriteLineOptions = {}): void {
    const color = opts.color ?? { r: 0, g: 0, b: 0 };
    this.page.drawText(text, {
      x: MARGIN,
      y: this.y,
      size: opts.size ?? 10,
      font: opts.bold ? this.fontBold : this.font,
      color: rgb(color.r, color.g, color.b),
    });
    this.y -= LINE_HEIGHT;
  }

  /** Escribe un par etiqueta/valor en dos columnas. */
  kv(label: string, value: string, labelWidth = 180): void {
    this.page.drawText(label, {
      x: MARGIN,
      y: this.y,
      size: 10,
      font: this.font,
      color: rgb(0.3, 0.3, 0.3),
    });
    this.page.drawText(value, {
      x: MARGIN + labelWidth,
      y: this.y,
      size: 10,
      font: this.fontBold,
      color: rgb(0, 0, 0),
    });
    this.y -= LINE_HEIGHT;
  }

  /** Dibuja una línea horizontal separadora. */
  separator(): void {
    this.y -= 4;
    this.page.drawLine({
      start: { x: MARGIN, y: this.y },
      end: { x: this.page.getWidth() - MARGIN, y: this.y },
      thickness: 0.6,
      color: rgb(0.7, 0.7, 0.7),
    });
    this.y -= 10;
  }

  /** Espacio vertical extra. */
  gap(points = LINE_HEIGHT): void {
    this.y -= points;
  }

  /**
   * Embebe e inserta una imagen PNG escalada a `maxHeight`/`maxWidth`,
   * respetando la relación de aspecto. Devuelve `false` si no se pudo embeber.
   */
  async drawPng(bytes: Uint8Array, maxHeight = 60, maxWidth = 200): Promise<boolean> {
    try {
      const png = await this.pdf.embedPng(bytes);
      const ratio = png.width / png.height;
      const targetWidth = Math.min(maxHeight * ratio, maxWidth);
      this.page.drawImage(png, {
        x: MARGIN,
        y: this.y - maxHeight,
        width: targetWidth,
        height: maxHeight,
      });
      this.y -= maxHeight + 10;
      return true;
    } catch {
      return false;
    }
  }

  /** Embebe un PNG opcional como logo en la esquina superior derecha. */
  async drawLogo(bytes: Uint8Array, maxHeight = 48, maxWidth = 140): Promise<PDFImage | null> {
    try {
      const png = await this.pdf.embedPng(bytes);
      const ratio = png.width / png.height;
      const targetWidth = Math.min(maxHeight * ratio, maxWidth);
      this.page.drawImage(png, {
        x: this.page.getWidth() - MARGIN - targetWidth,
        y: this.page.getHeight() - MARGIN - maxHeight,
        width: targetWidth,
        height: maxHeight,
      });
      return png;
    } catch {
      return null;
    }
  }
}

/** Crea un documento A4 con una página y un `PdfWriter` listo para escribir. */
export async function crearPdfWriter(family: FontFamily = "courier"): Promise<PdfWriter> {
  const pdf = await PDFDocument.create();
  const page = pdf.addPage(); // A4 por defecto: 595.28 x 841.89
  const [regular, bold] = family === "helvetica"
    ? [StandardFonts.Helvetica, StandardFonts.HelveticaBold]
    : [StandardFonts.Courier, StandardFonts.CourierBold];
  const font = await pdf.embedFont(regular);
  const fontBold = await pdf.embedFont(bold);
  return new PdfWriter(pdf, page, font, fontBold);
}

/**
 * Formatea un importe en € en formato es-ES (`1.234,56 €`) a partir de su
 * representación decimal como string, sin pasar por `number` para no perder
 * precisión (ver convención de dinero en steering).
 */
export function formatEurosEs(value: string): string {
  return `${formatDecimalEs(value)} €`;
}

/** Formatea un porcentaje en formato es-ES (`33,33 %`). */
export function formatPorcentajeEs(value: string): string {
  return `${formatDecimalEs(value)} %`;
}

/** Formatea un decimal (como string) a es-ES con 2 decimales y separador de miles. */
export function formatDecimalEs(value: string): string {
  const trimmed = value.trim();
  const negative = trimmed.startsWith("-");
  const abs = negative ? trimmed.slice(1) : trimmed;
  const [intRaw = "0", decRaw = ""] = abs.split(".");
  const intPart = intRaw.replace(/^0+(?=\d)/, "") || "0";
  const dec = `${decRaw}00`.slice(0, 2);
  const grouped = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  return `${negative ? "-" : ""}${grouped},${dec}`;
}
