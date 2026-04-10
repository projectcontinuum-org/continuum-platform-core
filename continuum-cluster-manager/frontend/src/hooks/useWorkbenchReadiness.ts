import { useState, useEffect, useRef, useCallback } from 'react';
import { workbenchApi } from '../api/workbench';

const POLL_INTERVAL_MS = 3000; // poll every 3 seconds

/**
 * Polls `{instanceName}/open/index.html` until it returns 200.
 * Only starts polling when the workbench status is RUNNING.
 *
 * Returns `ready` (boolean) and `checking` (true while still polling).
 */
export function useWorkbenchReadiness(
  instanceName: string,
  isRunning: boolean,
): { ready: boolean; checking: boolean } {
  const [ready, setReady] = useState(false);
  const [checking, setChecking] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const mountedRef = useRef(true);

  const poll = useCallback(async () => {
    if (!mountedRef.current) return;
    try {
      const ok = await workbenchApi.checkReady(instanceName);
      if (!mountedRef.current) return;
      if (ok) {
        setReady(true);
        setChecking(false);
        return;
      }
    } catch {
      // ignore – we'll retry
    }
    // schedule next poll
    if (mountedRef.current) {
      timerRef.current = setTimeout(poll, POLL_INTERVAL_MS);
    }
  }, [instanceName]);

  useEffect(() => {
    mountedRef.current = true;

    if (!isRunning) {
      // Not running → reset state, don't poll
      setReady(false);
      setChecking(false);
      return;
    }

    // Start polling
    setReady(false);
    setChecking(true);
    poll();

    return () => {
      mountedRef.current = false;
      if (timerRef.current) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }
    };
  }, [isRunning, poll]);

  return { ready, checking };
}

