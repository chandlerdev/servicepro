/**
 * Mobile numbers as country calling code + national number.
 * Keep COUNTRIES in sync with PhoneRules.java in the backend.
 */
export const COUNTRIES: Record<string, { name: string; lengths: number[] }> = {
  "1": { name: "United States / Canada", lengths: [10] },
  "44": { name: "United Kingdom", lengths: [10] },
  "91": { name: "India", lengths: [10] },
  "61": { name: "Australia", lengths: [9] },
  "971": { name: "United Arab Emirates", lengths: [9] },
  "65": { name: "Singapore", lengths: [8] },
  "60": { name: "Malaysia", lengths: [9, 10] },
  "49": { name: "Germany", lengths: [10, 11] },
  "33": { name: "France", lengths: [9] },
  "86": { name: "China", lengths: [11] },
  "81": { name: "Japan", lengths: [10] },
  "52": { name: "Mexico", lengths: [10] },
};

/** Allowed national-number lengths for a calling code (unknown codes: 4 … 15 minus the code's digits). */
export function phoneRule(code: string): { min: number; max: number; exact?: number[] } {
  const known = COUNTRIES[code];
  if (known) return { min: Math.min(...known.lengths), max: Math.max(...known.lengths), exact: known.lengths };
  return { min: 4, max: Math.max(4, 15 - code.length) };
}

/** [9] → "9"; [9, 10] → "9 or 10"; [13, 16, 19] → "13, 16 or 19". */
export function orList(n: number[]) {
  return n.length === 1 ? String(n[0]) : `${n.slice(0, -1).join(", ")} or ${n[n.length - 1]}`;
}

/** Error message when `number` has the wrong length for `code`, else null. */
export function phoneLengthError(code: string, number: string): string | null {
  if (!/^\d+$/.test(number)) return null;
  const rule = phoneRule(code);
  const n = number.length;
  const ok = rule.exact ? rule.exact.includes(n) : n >= rule.min && n <= rule.max;
  if (ok) return null;
  return rule.exact ? `Must be ${orList(rule.exact)} digits for +${code}` : `Must be ${rule.min}–${rule.max} digits`;
}

/** Hint under the mobile field, e.g. "India — 10 digits". */
export function phoneHint(code: string) {
  const rule = phoneRule(code);
  const country = COUNTRIES[code]?.name;
  if (country) return `${country} — ${orList(rule.exact ?? [rule.min])} digits`;
  return code ? `${rule.min}–${rule.max} digits` : "Enter the country code first";
}

/** Typed / pasted country code ("+1", "001") → "1". */
export function cleanCountryCode(v: string) {
  return v.replace(/\D/g, "").replace(/^0+/, "").slice(0, 3);
}

/** "+1" + "2815550100" → "+1 (281) 555-0100"; other countries "+91 7810894925". */
export function formatPhone(code: string | null | undefined, number: string | null | undefined) {
  if (!number) return "—";
  if (code === "+1" && /^\d{10}$/.test(number)) {
    return `+1 (${number.slice(0, 3)}) ${number.slice(3, 6)}-${number.slice(6)}`;
  }
  return code ? `${code} ${number}` : number;
}
