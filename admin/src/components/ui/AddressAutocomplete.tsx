"use client";

import { useEffect, useId, useLayoutEffect, useRef, useState, type KeyboardEvent } from "react";
import { createPortal } from "react-dom";
import { CheckCircle2, Loader2, MapPin } from "lucide-react";
import { ApiError } from "@/lib/api";
import { geoApi } from "@/lib/services";
import { cn } from "@/lib/utils";
import type { AddressSuggestion, PlaceDetails } from "@/types/api";

export interface AddressAutocompleteProps {
  /** the address text shown in the box */
  value: string;
  /** true once the address was picked from the suggestions (or is the unchanged saved one) */
  verified: boolean;
  /** user typed: the parent should store the text and forget any picked place */
  onTextChange: (text: string) => void;
  /** user picked a suggestion: full address + coordinates (+ the session to send with the save) */
  onPick: (place: PlaceDetails, session: string) => void;
  /** address look-up isn't available (no Google key on the server) — free typing is allowed then */
  onUnavailable?: () => void;
  placeholder?: string;
  "aria-invalid"?: boolean;
  id?: string;
}

function newSession() {
  return typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

/**
 * Address box with Google suggestions shown BELOW the field; only a picked suggestion counts as a valid address.
 * Keyboard: ↑ ↓ to move, Enter to pick, Esc to close.
 */
export function AddressAutocomplete({
  value,
  verified,
  onTextChange,
  onPick,
  onUnavailable,
  placeholder = "Start typing the address…",
  "aria-invalid": invalid,
  id,
}: AddressAutocompleteProps) {
  const [suggestions, setSuggestions] = useState<AddressSuggestion[]>([]);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [picking, setPicking] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [active, setActive] = useState(0);
  const [typed, setTyped] = useState(false);
  const [pos, setPos] = useState<{ left: number; top: number; width: number } | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const session = useRef(newSession());
  const requestNo = useRef(0);
  const listId = useId();

  const place = () => {
    const r = inputRef.current?.getBoundingClientRect();
    if (r) setPos({ left: r.left, top: r.bottom + 4, width: Math.max(r.width, 280) });
  };

  // look up suggestions 300 ms after the user stops typing
  useEffect(() => {
    if (!typed) return;
    const q = value.trim();
    if (q.length < 3) return; // too short — cleared in the input's onChange
    const no = ++requestNo.current;
    const t = setTimeout(async () => {
      setLoading(true);
      try {
        const list = await geoApi.autocomplete(q, session.current);
        if (no !== requestNo.current) return; // an older request finished late
        setSuggestions(list);
        setProblem(list.length === 0 ? "No matching address — check the spelling" : null);
        setActive(0);
        setOpen(true);
      } catch (err) {
        if (no !== requestNo.current) return;
        if (err instanceof ApiError && err.status === 503) {
          onUnavailable?.();
          setOpen(false);
        } else {
          setProblem("Address look-up failed — please try again");
          setSuggestions([]);
          setOpen(true);
        }
      } finally {
        if (no === requestNo.current) setLoading(false);
      }
    }, 300);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value, typed]);

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

  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      const t = e.target as Node;
      if (inputRef.current?.contains(t) || panelRef.current?.contains(t)) return;
      setOpen(false);
    };
    document.addEventListener("mousedown", onDown);
    return () => document.removeEventListener("mousedown", onDown);
  }, [open]);

  const pick = async (s: AddressSuggestion) => {
    setOpen(false);
    setPicking(true);
    try {
      const details = await geoApi.place(s.placeId, session.current);
      onPick(details, session.current);
      setTyped(false);
      session.current = newSession(); // a new typing session starts after each pick
    } catch {
      setProblem("Couldn't load that address — pick it again");
      setOpen(true);
    } finally {
      setPicking(false);
    }
  };

  const onKey = (e: KeyboardEvent<HTMLInputElement>) => {
    if (!open || suggestions.length === 0) return;
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setActive((a) => Math.min(a + 1, suggestions.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActive((a) => Math.max(a - 1, 0));
    } else if (e.key === "Enter") {
      e.preventDefault(); // don't submit the form
      void pick(suggestions[active]);
    } else if (e.key === "Escape") {
      e.preventDefault();
      e.stopPropagation(); // close only the suggestions, not the pop-up around them
      setOpen(false);
    }
  };

  return (
    <div className="relative">
      <MapPin size={16} className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-muted" />
      <input
        ref={inputRef}
        id={id}
        type="text"
        role="combobox"
        aria-expanded={open}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-invalid={invalid}
        autoComplete="off"
        placeholder={placeholder}
        className={cn("input pr-9 pl-9", invalid && "input-error")}
        value={value}
        onChange={(e) => {
          setTyped(true);
          if (e.target.value.trim().length < 3) {
            requestNo.current++; // ignore any request still in flight
            setSuggestions([]);
            setOpen(false);
            setLoading(false);
          }
          onTextChange(e.target.value);
        }}
        onFocus={() => {
          if (typed && suggestions.length > 0) setOpen(true);
        }}
        onKeyDown={onKey}
      />
      <span className="pointer-events-none absolute top-1/2 right-3 -translate-y-1/2">
        {loading || picking ? (
          <Loader2 size={16} className="animate-spin text-muted" />
        ) : verified && value ? (
          <CheckCircle2 size={16} className="text-ok" aria-label="Verified address" />
        ) : null}
      </span>

      {open &&
        pos &&
        createPortal(
          <div
            ref={panelRef}
            className="fixed z-[2100] overflow-hidden rounded-[10px] border border-line bg-white shadow-[0_12px_32px_rgba(0,0,0,.18)]"
            style={{ left: pos.left, top: pos.top, width: pos.width }}
          >
            {problem && suggestions.length === 0 ? (
              <div className="px-3 py-2.5 text-[13px] text-muted">{problem}</div>
            ) : (
              <ul id={listId} role="listbox" className="max-h-[300px] overflow-y-auto py-1">
                {suggestions.map((s, i) => (
                  <li
                    key={s.placeId}
                    role="option"
                    aria-selected={i === active}
                    className={cn("flex cursor-pointer items-start gap-2 px-3 py-2", i === active && "bg-brand-light")}
                    onMouseEnter={() => setActive(i)}
                    onMouseDown={(e) => e.preventDefault()}
                    onClick={() => void pick(s)}
                  >
                    <MapPin size={15} className="mt-0.5 shrink-0 text-muted" />
                    <span className="min-w-0">
                      <span className="block truncate text-[13.5px] font-medium">{s.mainText ?? s.text}</span>
                      {s.secondaryText && (
                        <span className="block truncate text-[12px] text-muted">{s.secondaryText}</span>
                      )}
                    </span>
                  </li>
                ))}
              </ul>
            )}
            <div className="border-t border-line px-3 py-1 text-right text-[10.5px] text-muted">Powered by Google</div>
          </div>,
          document.body,
        )}
    </div>
  );
}
