import { useCallback, useRef } from "react";

export function useThrottledCallback<T extends any[]>(
  callback: (...args: T) => void,
  deps: React.DependencyList,
): (...args: T) => void {
  const rafIdRef = useRef<number | null>(null);
  const latestArgsRef = useRef<T | null>(null);
  const callbackRef = useRef(callback);
  callbackRef.current = callback;

  return useCallback(
    (...args: T) => {
      latestArgsRef.current = args;
      if (rafIdRef.current !== null) return;

      rafIdRef.current = requestAnimationFrame(() => {
        const pendingArgs = latestArgsRef.current;
        rafIdRef.current = null;
        latestArgsRef.current = null;
        if (pendingArgs) {
          callbackRef.current(...pendingArgs);
        }
      });
    },
    deps, // eslint-disable-line react-hooks/exhaustive-deps
  );
}
