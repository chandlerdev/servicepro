"use client";

import { useCallback, useEffect, useRef, useState, type DependencyList } from "react";
import { errorMessage } from "@/lib/api";

interface ApiState<T> {
  key: string | null;
  data: T | undefined;
  error: string | null;
}

export interface UseApiResult<T> {
  data: T | undefined;
  error: string | null;
  /** true while the current deps have not produced a result yet */
  loading: boolean;
  /** Re-fetch. `silent` keeps the current data visible (no loading state). */
  reload: (opts?: { silent?: boolean }) => void;
  /** Locally replace data (optimistic updates). */
  setData: (updater: (prev: T | undefined) => T | undefined) => void;
}

/**
 * Minimal data-fetching hook: runs `fetcher` whenever `deps` change.
 * Previous data stays visible while a new request is in flight.
 * Never runs at build time (effects only run in the browser).
 */
export function useApi<T>(fetcher: () => Promise<T>, deps: DependencyList, enabled = true): UseApiResult<T> {
  const [nonce, setNonce] = useState(0);
  const [silentNonce, setSilentNonce] = useState(0);
  const [state, setState] = useState<ApiState<T>>({ key: null, data: undefined, error: null });
  const fetcherRef = useRef(fetcher);

  useEffect(() => {
    fetcherRef.current = fetcher;
  });

  const key = `${JSON.stringify(deps)}#${nonce}`;

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    fetcherRef.current().then(
      (data) => {
        if (!cancelled) setState({ key, data, error: null });
      },
      (err: unknown) => {
        if (!cancelled) setState((s) => ({ key, data: s.data, error: errorMessage(err) }));
      },
    );
    return () => {
      cancelled = true;
    };
  }, [key, silentNonce, enabled]);

  const reload = useCallback((opts?: { silent?: boolean }) => {
    if (opts?.silent) setSilentNonce((n) => n + 1);
    else setNonce((n) => n + 1);
  }, []);

  const setData = useCallback((updater: (prev: T | undefined) => T | undefined) => {
    setState((s) => ({ ...s, data: updater(s.data) }));
  }, []);

  return {
    data: state.data,
    error: state.error,
    loading: enabled && state.key !== key,
    reload,
    setData,
  };
}
