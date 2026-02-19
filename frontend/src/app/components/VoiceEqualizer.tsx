import { motion } from "motion/react";

interface VoiceEqualizerProps {
  status: "idle" | "listening" | "processing" | "speaking";
}

const BAR_DELAYS = ["0ms", "100ms", "200ms", "300ms", "400ms"];
const BAR_DURATIONS = ["0.6s", "0.8s", "0.7s", "0.9s", "0.65s"];

export function VoiceEqualizer({ status }: VoiceEqualizerProps) {
  const isAnimating = status === "speaking" || status === "listening";

  return (
    <div className="relative flex items-center justify-center w-64 h-64">
      {/* Central Core */}
      <motion.div
        animate={{
          scale: status === "listening" ? 1.1 : 1,
        }}
        transition={{ duration: 0.3 }}
        style={
          status === "processing"
            ? { animation: "spin-slow 2.5s linear infinite, pulse-scale 2s ease-in-out infinite" }
            : undefined
        }
        className={`z-10 w-24 h-24 rounded-full backdrop-blur-md border border-white/10 transition-all duration-500 flex items-center justify-center
          ${status === "idle" ? "bg-white/5 shadow-[0_0_20px_rgba(255,255,255,0.05)]" : ""}
          ${status === "listening" ? "bg-white/20 shadow-[0_0_40px_rgba(255,255,255,0.2)] border-white/30" : ""}
          ${status === "processing" ? "bg-white/10 border-white/20" : ""}
          ${status === "speaking" ? "bg-white/20 shadow-[0_0_50px_rgba(255,255,255,0.1)]" : ""}
        `}
      >
        {status === "processing" && (
          <div className="w-12 h-12 border-t-2 border-white/50 rounded-full animate-spin" />
        )}

        {isAnimating && (
          <div className="flex items-end justify-center gap-1 h-10 w-16">
            {BAR_DELAYS.map((delay, i) => (
              <div
                key={i}
                className={`w-2 rounded-full origin-bottom ${status === "listening" ? "bg-white/60" : "bg-white/40"}`}
                style={{
                  height: "80%",
                  animation: `voiceBar ${BAR_DURATIONS[i]} ease-in-out infinite alternate`,
                  animationDelay: delay,
                }}
              />
            ))}
          </div>
        )}
      </motion.div>

      {/* Ripple Rings */}
      {isAnimating && (
        <>
          {[0, 1, 2].map((i) => (
            <div
              key={i}
              className={`absolute rounded-full border pointer-events-none ${status === "listening" ? "border-white" : "border-zinc-400"}`}
              style={{
                width: `${140 + i * 30}px`,
                height: `${140 + i * 30}px`,
                animation: "pulseRing 1.8s ease-out infinite",
                animationDelay: `${i * 0.6}s`,
              }}
            />
          ))}
        </>
      )}

      {/* Ambient Glow for Idle */}
      {status === "idle" && (
        <div className="absolute w-32 h-32 rounded-full bg-white/5 blur-2xl" />
      )}
    </div>
  );
}
