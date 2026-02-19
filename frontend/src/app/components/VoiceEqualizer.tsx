import { motion } from "motion/react";
import { useEffect, useState } from "react";

interface VoiceEqualizerProps {
  status: "idle" | "listening" | "processing" | "speaking";
}

export function VoiceEqualizer({ status }: VoiceEqualizerProps) {
  const [loudness, setLoudness] = useState<number[]>(new Array(5).fill(1));

  useEffect(() => {
    let interval: NodeJS.Timeout;

    if (status === "speaking" || status === "listening") {
      interval = setInterval(() => {
        setLoudness(
          new Array(5).fill(0).map(() => Math.random() * (status === "speaking" ? 1.5 : 0.8) + 0.5)
        );
      }, 100);
    } else {
      setLoudness(new Array(5).fill(1));
    }

    return () => clearInterval(interval);
  }, [status]);

  return (
    <div className="relative flex items-center justify-center w-64 h-64">
      {/* Central Core */}
      <motion.div
        animate={{
          scale: status === "processing" ? [1, 0.9, 1] : status === "listening" ? 1.1 : 1,
          rotate: status === "processing" ? 180 : 0,
        }}
        transition={{
          duration: status === "processing" ? 2 : 0.3,
          repeat: status === "processing" ? Infinity : 0,
          ease: "easeInOut",
        }}
        className={`z-10 w-24 h-24 rounded-full backdrop-blur-md border border-white/10 transition-all duration-500 flex items-center justify-center
          ${status === "idle" ? "bg-white/5 shadow-[0_0_20px_rgba(255,255,255,0.05)]" : ""}
          ${status === "listening" ? "bg-white/20 shadow-[0_0_40px_rgba(255,255,255,0.2)] border-white/30" : ""}
          ${status === "processing" ? "bg-white/10 border-white/20" : ""}
          ${status === "speaking" ? "bg-white/20 shadow-[0_0_50px_rgba(255,255,255,0.1)]" : ""}
        `}
      >
        {/* Inner Indicator for Processing */}
        {status === "processing" && (
            <motion.div 
                className="w-12 h-12 border-t-2 border-white/50 rounded-full"
                animate={{ rotate: 360 }}
                transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
            />
        )}
      </motion.div>

      {/* Ripples / Equalizer Rings - Monochrome/Subtle Style */}
      {status !== "idle" && status !== "processing" && (
        <>
          {[0, 1, 2].map((i) => (
            <motion.div
              key={i}
              className={`absolute rounded-full border opacity-20 pointer-events-none
                ${status === "listening" ? "border-white" : "border-zinc-400"}
              `}
              animate={{
                width: `${loudness[i] * 120 + i * 30}px`,
                height: `${loudness[i] * 120 + i * 30}px`,
                opacity: [0.05, 0.15, 0.05],
                scale: status === "listening" ? 1.05 : 1,
              }}
              transition={{
                duration: 0.2,
                ease: "linear",
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
