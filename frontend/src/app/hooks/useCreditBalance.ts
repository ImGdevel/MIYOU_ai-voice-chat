import { useState, useCallback, useEffect } from "react";
import { getMiyouUserId } from "../utils/userIdentity";

export function useCreditBalance(buildApiUrl: (path: string) => string) {
  const [balance, setBalance] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const refresh = useCallback(async () => {
    setIsLoading(true);
    try {
      const userId = await getMiyouUserId();
      const res = await fetch(
        buildApiUrl(`/credit/balance?userId=${encodeURIComponent(userId)}`),
      );
      if (!res.ok) return;
      const data = (await res.json()) as { balance: number };
      setBalance(data.balance);
    } catch {
      // 백엔드 미실행 시 잔액을 null로 유지 — 배지는 "--" 표시
    } finally {
      setIsLoading(false);
    }
  }, [buildApiUrl]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return { balance, isLoading, refresh };
}
