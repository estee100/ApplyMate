"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { errorMessage } from "./utils";

/**
 * Load data on mount (and when deps change), with loading/error state and a reload.
 * Stale responses from an earlier call are dropped, so fast filter changes never
 * show results for a query the user has already moved on from.
 */
export function useAsync<T>(load: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const callId = useRef(0);

  const run = useCallback(async () => {
    const id = ++callId.current;
    setLoading(true);
    setError(null);
    try {
      const result = await load();
      if (id === callId.current) setData(result);
    } catch (e) {
      if (id === callId.current) setError(errorMessage(e));
    } finally {
      if (id === callId.current) setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => {
    run();
  }, [run]);

  return { data, setData, error, loading, reload: run };
}

/** A value that only updates once it has stopped changing for `delay` ms. */
export function useDebounced<T>(value: T, delay = 350) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(t);
  }, [value, delay]);
  return debounced;
}
