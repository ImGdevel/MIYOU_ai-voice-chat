import { useState, useCallback } from "react";

interface UseApiCallResult<T extends (...args: any[]) => Promise<any>> {
  execute: (...args: Parameters<T>) => Promise<ReturnType<T> | undefined>;
  loading: boolean;
  error: Error | null;
  reset: () => void;
}

export function useApiCall<T extends (...args: any[]) => Promise<any>>(
  apiFunc: T,
  options?: {
    onSuccess?: (data: ReturnType<T>) => void;
    onError?: (error: Error) => void;
  }
): UseApiCallResult<T> {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const execute = useCallback(
    async (...args: Parameters<T>): Promise<ReturnType<T> | undefined> => {
      setLoading(true);
      setError(null);
      try {
        const result = await apiFunc(...args);
        options?.onSuccess?.(result);
        return result;
      } catch (err) {
        const processedError = err instanceof Error ? err : new Error(String(err));
        setError(processedError);
        options?.onError?.(processedError);
        return undefined;
      } finally {
        setLoading(false);
      }
    },
    [apiFunc, options]
  );

  const reset = useCallback(() => {
    setLoading(false);
    setError(null);
  }, []);

  return { execute, loading, error, reset };
}
