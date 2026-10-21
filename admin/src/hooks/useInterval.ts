"use client";

import { useEffect, useRef } from "react";

/** Run `callback` every `ms` milliseconds (pass null to pause). Skips while the tab is hidden. */
export function useInterval(callback: () => void, ms: number | null): void {
  const saved = useRef(callback);
  useEffect(() => {
    saved.current = callback;
  });
  useEffect(() => {
    if (ms === null) return;
    const id = setInterval(() => {
      if (typeof document !== "undefined" && document.visibilityState === "hidden") return;
      saved.current();
    }, ms);
    return () => clearInterval(id);
  }, [ms]);
}
