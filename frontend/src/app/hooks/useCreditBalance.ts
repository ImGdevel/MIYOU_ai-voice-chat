import { useState, useCallback, useEffect, useRef } from "react";
import { getMiyouUserId } from "../utils/userIdentity";

export function useCreditBalance(buildApiUrl: (path: string) => string) {
  const [balance, setBalance] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [lastDelta, setLastDelta] = useState<number | null>(null);
  const [changeId, setChangeId] = useState(0);
  const balanceRef = useRef<number | null>(null);

  const updateBalance = useCallback((nextBalance: number) => {
    const normalizedBalance = Math.max(0, nextBalance);
    const previousBalance = balanceRef.current;

    balanceRef.current = normalizedBalance;
    setBalance(normalizedBalance);

    if (previousBalance !== null && previousBalance !== normalizedBalance) {
      setLastDelta(normalizedBalance - previousBalance);
      setChangeId((current) => current + 1);
    }
  }, []);

  const applyOptimisticDelta = useCallback(
    (delta: number) => {
      const currentBalance = balanceRef.current;
      if (currentBalance === null) return;

      updateBalance(currentBalance + delta);
    },
    [updateBalance],
  );

  const refresh = useCallback(async () => {
    setIsLoading(balanceRef.current === null);
    try {
      const userId = await getMiyouUserId();
      const res = await fetch(
        buildApiUrl(`/credit/balance?userId=${encodeURIComponent(userId)}`),
      );
      if (!res.ok) return;
      const data = (await res.json()) as { balance: number };
      updateBalance(data.balance);
    } catch {
      // 백엔드 미실행 시 잔액을 null로 유지 — 배지는 "--" 표시
    } finally {
      setIsLoading(false);
    }
  }, [buildApiUrl, updateBalance]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return { balance, isLoading, refresh, applyOptimisticDelta, lastDelta, changeId };
}
