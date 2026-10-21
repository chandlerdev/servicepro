/**
 * Credit-card input helpers (brand detection, lengths, formatting, checksum).
 * Keep brands / lengths in sync with Cards.java in the backend.
 */
import { z } from "zod";
import { orList } from "@/lib/phone";

export function cardBrand(d: string): string {
  if (/^4/.test(d)) return "Visa";
  if (/^(5[1-5]|2(2[2-9]|[3-6]\d|7[01]|720))/.test(d)) return "Mastercard";
  if (/^3[47]/.test(d)) return "Amex";
  if (/^(6011|65|64[4-9])/.test(d)) return "Discover";
  if (/^35(2[89]|[3-8]\d)/.test(d)) return "JCB";
  if (/^3(0[0-5]|[689])/.test(d)) return "Diners";
  return "Card";
}

export function cardLengths(brand: string): number[] {
  switch (brand) {
    case "Amex":
      return [15];
    case "Mastercard":
      return [16];
    case "Visa":
      return [13, 16, 19];
    case "Diners":
      return [14, 16, 19];
    default:
      return [16, 17, 18, 19];
  }
}

/** Digits only, capped at the brand's longest number; Amex shown 4-6-5, others in groups of 4. */
export function formatCard(v: string) {
  let d = v.replace(/\D/g, "");
  d = d.slice(0, Math.max(...cardLengths(cardBrand(d))));
  if (cardBrand(d) === "Amex") {
    return [d.slice(0, 4), d.slice(4, 10), d.slice(10, 15)].filter(Boolean).join(" ");
  }
  return d.replace(/(\d{4})(?=\d)/g, "$1 ");
}

/** "MMYY" typing → "MM/YY". */
export function formatExpiry(v: string) {
  const d = v.replace(/\D/g, "").slice(0, 4);
  return d.length > 2 ? `${d.slice(0, 2)}/${d.slice(2)}` : d;
}

export function expiryText(month: number, year: number) {
  return `${String(month).padStart(2, "0")}/${String(year).slice(-2)}`;
}

/** Card-number checksum (catches typos before the backend does). */
export function luhn(digits: string) {
  let sum = 0;
  let dbl = false;
  for (let i = digits.length - 1; i >= 0; i--) {
    let n = Number(digits[i]);
    if (dbl) {
      n *= 2;
      if (n > 9) n -= 9;
    }
    sum += n;
    dbl = !dbl;
  }
  return sum % 10 === 0;
}

/** Wrong length for the brand / failed checksum → message; blank or valid → null. */
export function cardNumberError(value: string): string | null {
  const d = value.replace(/\s/g, "");
  if (!d) return null;
  const brand = cardBrand(d);
  const lengths = cardLengths(brand);
  if (!lengths.includes(d.length)) return `${brand} numbers have ${orList(lengths)} digits`;
  if (!luhn(d)) return "Enter a valid card number";
  return null;
}

/** Hint under the card field while typing, e.g. "Visa — 13, 16 or 19 digits". */
export function cardHint(value: string): string | null {
  const d = value.replace(/\s/g, "");
  if (d.length < 2) return null;
  const brand = cardBrand(d);
  return `${brand} — ${orList(cardLengths(brand))} digits`;
}

/** "MM/YY", not in the past. */
export const cardExpirySchema = z
  .string()
  .trim()
  .regex(/^(0[1-9]|1[0-2])\/\d{2}$/, "Enter the expiry as MM/YY")
  .refine((v) => {
    const [mm, yy] = v.split("/").map(Number);
    const now = new Date();
    const year = 2000 + yy;
    return year > now.getFullYear() || (year === now.getFullYear() && mm >= now.getMonth() + 1);
  }, "This card has expired");
