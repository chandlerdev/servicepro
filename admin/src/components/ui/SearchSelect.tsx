"use client";

import { useEffect, useId, useLayoutEffect, useMemo, useRef, useState, type KeyboardEvent } from "react";
import { createPortal } from "react-dom";
import { Check, ChevronDown, Loader2, Plus, Search, X } from "lucide-react";
import { cn } from "@/lib/utils";

export interface SearchSelectOption {
  value: string;
  label: string;
}

export interface SearchSelectProps {
  value: string;
  onChange: (value: string) => void;
  options: SearchSelectOption[];
  /** shown when nothing is selected */
  placeholder?: string;
  /** adds a "clear" (×) button, e.g. for filters like "All cities" */
  clearable?: boolean;
  onBlur?: () => void;
  disabled?: boolean;
  "aria-invalid"?: boolean;
  "aria-label"?: string;
  id?: string;
  className?: string;
  searchPlaceholder?: string;
  /**
   * Lets the user add what they typed when it isn't in the list (e.g. a new city). Return the new option's value;
   * throw to show an error under the search box.
   */
  onCreate?: (text: string) => Promise<string>;
  /** text of the add row, e.g. (t) => `Add "${t}" as a new city` */
  createLabel?: (text: string) => string;
}

/** How many matches are rendered at once (the list can hold thousands of cities). */
const MAX_SHOWN = 100;

/**
 * Dropdown with a search box that always opens BELOW the field (rendered in a portal, so it is not cut off
 * inside pop-ups). Keyboard: ↑ ↓ to move, Enter to pick, Esc to close.
 */
export function SearchSelect({
  value,
  onChange,
  options,
  placeholder = "— Select —",
  clearable,
  onBlur,
  disabled,
  "aria-invalid": invalid,
  "aria-label": ariaLabel,
  id,
  className,
  searchPlaceholder = "Type to search…",
  onCreate,
  createLabel = (t) => `Add "${t}"`,
}: SearchSelectProps) {
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(0);
  const [pos, setPos] = useState<{ left: number; top: number; width: number; maxHeight: number } | null>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const listRef = useRef<HTMLUListElement>(null);
  const listId = useId();

  const selected = options.find((o) => o.value === value);

  // names starting with the search text first, then names containing it
  const matches = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return options;
    const starts: SearchSelectOption[] = [];
    const contains: SearchSelectOption[] = [];
    for (const o of options) {
      const l = o.label.toLowerCase();
      if (l.startsWith(q)) starts.push(o);
      else if (l.includes(q)) contains.push(o);
    }
    return [...starts, ...contains];
  }, [options, query]);
  const shown = matches.slice(0, MAX_SHOWN);

  const openPanel = () => {
    if (disabled) return;
    setQuery("");
    const i = options.findIndex((o) => o.value === value);
    setActive(i >= 0 && i < MAX_SHOWN ? i : 0);
    setOpen(true);
  };

  const close = (refocus: boolean) => {
    setOpen(false);
    onBlur?.();
    if (refocus) triggerRef.current?.focus();
  };

  const pick = (o: SearchSelectOption) => {
    onChange(o.value);
    close(true);
  };

  // "Add …" row: when something is typed that isn't exactly in the list
  const typed = tidyName(query);
  const canCreate =
    !!onCreate && typed.length >= 2 && !options.some((o) => o.label.toLowerCase() === typed.toLowerCase());
  const createIndex = shown.length; // the add row comes right after the matches

  const create = async () => {
    if (!onCreate || creating) return;
    setCreating(true);
    setCreateError(null);
    try {
      const v = await onCreate(typed);
      onChange(v);
      close(true);
    } catch (err) {
      setCreateError(err instanceof Error && err.message ? err.message : "Couldn't add it — please try again");
    } finally {
      setCreating(false);
    }
  };

  /** Panel sits right under the field, as wide as the field, and never runs off the bottom of the window. */
  const place = () => {
    const r = triggerRef.current?.getBoundingClientRect();
    if (!r) return;
    const spaceBelow = window.innerHeight - r.bottom - 12;
    setPos({ left: r.left, top: r.bottom + 4, width: Math.max(r.width, 240), maxHeight: Math.max(180, Math.min(340, spaceBelow)) });
  };

  useLayoutEffect(() => {
    if (!open) return;
    place();
    const onMove = () => place();
    window.addEventListener("resize", onMove);
    window.addEventListener("scroll", onMove, true);
    return () => {
      window.removeEventListener("resize", onMove);
      window.removeEventListener("scroll", onMove, true);
    };
  }, [open]);

  // close when clicking anywhere else
  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      const t = e.target as Node;
      if (triggerRef.current?.contains(t) || panelRef.current?.contains(t)) return;
      close(false);
    };
    document.addEventListener("mousedown", onDown);
    return () => document.removeEventListener("mousedown", onDown);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  // keep the highlighted row visible
  useEffect(() => {
    if (!open) return;
    listRef.current?.querySelector<HTMLElement>(`[data-index="${active}"]`)?.scrollIntoView({ block: "nearest" });
  }, [active, open]);

  const onPanelKey = (e: KeyboardEvent) => {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setActive((a) => Math.min(a + 1, canCreate ? createIndex : shown.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActive((a) => Math.max(a - 1, 0));
    } else if (e.key === "Enter") {
      e.preventDefault();
      if (canCreate && (active === createIndex || shown.length === 0)) void create();
      else if (shown[active]) pick(shown[active]);
    } else if (e.key === "Escape") {
      e.preventDefault();
      e.stopPropagation(); // close only the dropdown, not the pop-up around it
      close(true);
    } else if (e.key === "Tab") {
      close(false);
    }
  };

  return (
    <div className={cn("relative", className)}>
      <button
        ref={triggerRef}
        id={id}
        type="button"
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? listId : undefined}
        aria-label={ariaLabel}
        className={cn(
          "input flex w-full cursor-pointer items-center gap-2 text-left",
          invalid && "input-error",
          disabled && "cursor-not-allowed opacity-60",
        )}
        onClick={() => (open ? close(false) : openPanel())}
        onKeyDown={(e) => {
          if (!open && (e.key === "ArrowDown" || e.key === "Enter" || e.key === " ")) {
            e.preventDefault();
            openPanel();
          }
        }}
      >
        <span className={cn("min-w-0 flex-1 truncate", !selected && "text-muted")}>
          {selected ? selected.label : value || placeholder}
        </span>
        {clearable && value && !disabled && (
          <span
            role="button"
            tabIndex={-1}
            aria-label="Clear"
            className="text-muted hover:text-ink"
            onClick={(e) => {
              e.stopPropagation();
              onChange("");
            }}
          >
            <X size={15} />
          </span>
        )}
        <ChevronDown size={16} className={cn("shrink-0 text-muted transition-transform", open && "rotate-180")} />
      </button>

      {open &&
        pos &&
        createPortal(
          <div
            ref={panelRef}
            className="fixed z-[2100] flex flex-col overflow-hidden rounded-[10px] border border-line bg-white shadow-[0_12px_32px_rgba(0,0,0,.18)]"
            style={{ left: pos.left, top: pos.top, width: pos.width, maxHeight: pos.maxHeight }}
            onKeyDown={onPanelKey}
          >
            <div className="flex items-center gap-2 border-b border-line px-3 py-2">
              <Search size={15} className="shrink-0 text-muted" />
              <input
                autoFocus
                className="min-w-0 flex-1 bg-transparent text-[13.5px] outline-none"
                placeholder={searchPlaceholder}
                aria-label="Search"
                aria-controls={listId}
                value={query}
                onChange={(e) => {
                  setQuery(e.target.value);
                  setActive(0);
                  setCreateError(null);
                }}
              />
            </div>
            <ul ref={listRef} id={listId} role="listbox" className="min-h-0 flex-1 overflow-y-auto py-1">
              {shown.length === 0 && !canCreate && <li className="px-3 py-2 text-[13px] text-muted">No matches</li>}
              {shown.map((o, i) => (
                <li
                  key={o.value}
                  data-index={i}
                  role="option"
                  aria-selected={o.value === value}
                  className={cn(
                    "flex cursor-pointer items-center gap-2 px-3 py-[7px] text-[13.5px]",
                    i === active && "bg-brand-light",
                    o.value === value && "font-semibold text-brand-dark",
                  )}
                  onMouseEnter={() => setActive(i)}
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => pick(o)}
                >
                  <span className="min-w-0 flex-1 truncate">{o.label}</span>
                  {o.value === value && <Check size={15} className="shrink-0" />}
                </li>
              ))}
            </ul>
            {canCreate && (
              <div className="border-t border-line">
                <button
                  type="button"
                  data-index={createIndex}
                  disabled={creating}
                  className={cn(
                    "flex w-full cursor-pointer items-center gap-2 px-3 py-2 text-left text-[13.5px] font-medium text-brand-dark",
                    active === createIndex && "bg-brand-light",
                  )}
                  onMouseEnter={() => setActive(createIndex)}
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => void create()}
                >
                  {creating ? <Loader2 size={15} className="shrink-0 animate-spin" /> : <Plus size={15} className="shrink-0" />}
                  <span className="min-w-0 flex-1 truncate">{creating ? "Adding…" : createLabel(typed)}</span>
                </button>
                {createError && <div className="px-3 pb-2 text-[12px] text-danger">{createError}</div>}
              </div>
            )}
            {matches.length > MAX_SHOWN && (
              <div className="border-t border-line px-3 py-1.5 text-[11.5px] text-muted">
                Showing {MAX_SHOWN} of {matches.length} — keep typing to narrow down
              </div>
            )}
          </div>,
          document.body,
        )}
    </div>
  );
}

/** "  new   braunfels " → "New Braunfels" (all-lowercase typing gets capitalised; anything else is kept as typed). */
function tidyName(text: string) {
  const t = text.trim().replace(/\s+/g, " ");
  return t === t.toLowerCase() ? t.replace(/(^|[\s-])\p{L}/gu, (m) => m.toUpperCase()) : t;
}
