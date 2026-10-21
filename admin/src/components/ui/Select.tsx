import type { ComponentProps } from "react";
import { cn } from "@/lib/utils";

export interface SelectOption {
  value: string | number;
  label: string;
  disabled?: boolean;
}

export interface SelectGroup {
  label: string;
  options: SelectOption[];
}

export interface SelectProps extends ComponentProps<"select"> {
  options?: SelectOption[];
  groups?: SelectGroup[];
  placeholder?: string;
}

/** Native select styled like the prototype. Supports flat options and optgroups. */
export function Select({
  options,
  groups,
  placeholder,
  className,
  children,
  "aria-invalid": invalid,
  ...rest
}: SelectProps) {
  return (
    <select className={cn("input", invalid && "input-error", className)} aria-invalid={invalid} {...rest}>
      {placeholder !== undefined && <option value="">{placeholder}</option>}
      {options?.map((o) => (
        <option key={o.value} value={o.value} disabled={o.disabled}>
          {o.label}
        </option>
      ))}
      {groups?.map((g) => (
        <optgroup key={g.label} label={g.label}>
          {g.options.map((o) => (
            <option key={o.value} value={o.value} disabled={o.disabled}>
              {o.label}
            </option>
          ))}
        </optgroup>
      ))}
      {children}
    </select>
  );
}
