import { Coins } from "lucide-react";

interface CreditBadgeProps {
  balance: number | null;
  isLoading: boolean;
}

export function CreditBadge({ balance, isLoading }: CreditBadgeProps) {
  return (
    <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-zinc-900/40 backdrop-blur-md border border-white/5 shadow-lg pointer-events-auto">
      <Coins size={13} className="text-amber-400 shrink-0" />
      {isLoading ? (
        <span className="w-10 h-3 bg-zinc-700 rounded animate-pulse inline-block" />
      ) : (
        <span className="text-xs font-semibold text-zinc-300 tabular-nums">
          {balance !== null ? balance.toLocaleString() : "--"}
        </span>
      )}
    </div>
  );
}
