import { Coins } from "lucide-react";
import { motion } from "motion/react";

interface CreditBadgeProps {
  balance: number | null;
  isLoading: boolean;
  lastDelta?: number | null;
  changeId?: number;
}

export function CreditBadge({ balance, isLoading, lastDelta = null, changeId = 0 }: CreditBadgeProps) {
  const deltaValue = lastDelta ?? 0;
  const hasDelta = deltaValue !== 0;
  const isPositiveDelta = deltaValue > 0;
  const deltaText = hasDelta ? `${isPositiveDelta ? "+" : ""}${deltaValue.toLocaleString()}` : "";

  return (
    <div className="relative flex min-w-[96px] items-center justify-center gap-1.5 px-3 py-1.5 rounded-full bg-zinc-900/40 backdrop-blur-md border border-white/5 shadow-lg pointer-events-auto">
      <motion.div
        key={`coin-${changeId}`}
        initial={{ scale: 1 }}
        animate={{ scale: [1, 1.18, 1] }}
        transition={{ duration: 0.34, ease: "easeOut" }}
        className="flex shrink-0"
      >
        <Coins size={13} className="text-amber-400" />
      </motion.div>
      {isLoading ? (
        <span className="w-10 h-3 bg-zinc-700 rounded animate-pulse inline-block" />
      ) : (
        <motion.span
          key={`balance-${changeId}-${balance ?? "empty"}`}
          initial={{ opacity: 0, y: 4 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.18, ease: "easeOut" }}
          className="text-xs font-semibold text-zinc-300 tabular-nums"
        >
          {balance !== null ? balance.toLocaleString() : "--"}
        </motion.span>
      )}
      {hasDelta && (
        <motion.span
          key={`delta-${changeId}`}
          initial={{ opacity: 0, y: 0, scale: 0.96 }}
          animate={{ opacity: [0, 1, 0], y: [0, -7, -16], scale: [0.96, 1, 0.98] }}
          transition={{ duration: 0.9, ease: "easeOut" }}
          className={`absolute right-1 -bottom-5 rounded-full px-2 py-0.5 text-[10px] font-bold tabular-nums border ${
            isPositiveDelta
              ? "bg-emerald-500/15 border-emerald-400/30 text-emerald-200"
              : "bg-amber-500/15 border-amber-400/30 text-amber-200"
          }`}
        >
          {deltaText}
        </motion.span>
      )}
    </div>
  );
}
