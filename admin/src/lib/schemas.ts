import { z } from "zod";

/** Required trimmed text. */
export const req = (label: string) => z.string().trim().min(1, `${label} is required`);

/** Optional email: blank or a valid address. */
export const optionalEmail = z.union([z.literal(""), z.email("Enter a valid email")]);

/** Optional numeric text (lat/lng etc.): blank or a finite number within range. */
export const optionalCoord = (min: number, max: number) =>
  z
    .string()
    .trim()
    .refine((v) => v === "" || (Number.isFinite(Number(v)) && Number(v) >= min && Number(v) <= max), {
      message: `Must be a number between ${min} and ${max}`,
    });

/** Required numeric text. */
export const numberText = (label: string, min?: number) =>
  z
    .string()
    .trim()
    .min(1, `${label} is required`)
    .refine((v) => Number.isFinite(Number(v)), { message: `${label} must be a number` })
    .refine((v) => min === undefined || Number(v) >= min, { message: `${label} must be ≥ ${min}` });

/** Required select holding a numeric id. */
export const idSelect = (label: string) => z.string().min(1, `Choose a ${label}`);

/** HH:mm time. */
export const timeText = z.string().regex(/^\d{2}:\d{2}$/, "Use HH:mm");
