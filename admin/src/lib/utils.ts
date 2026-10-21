import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

/** Merge class names, resolving Tailwind conflicts. */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}

/** Convert "" / undefined to undefined; trims strings. */
export function blankToUndef(v: string | null | undefined): string | undefined {
  const t = (v ?? "").trim();
  return t === "" ? undefined : t;
}

/** Parse an optional numeric text input: "" → null, otherwise a number. */
export function optionalNumber(v: string | null | undefined): number | null {
  const t = (v ?? "").toString().trim();
  if (t === "") return null;
  const n = Number(t);
  return Number.isFinite(n) ? n : null;
}

/** Google Maps point link. */
export function mapsPointUrl(lat: number, lng: number): string {
  return `https://www.google.com/maps/search/?api=1&query=${lat},${lng}`;
}

/** Google Maps directions link to a destination. */
export function mapsDirectionsUrl(lat: number, lng: number): string {
  return `https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}`;
}
