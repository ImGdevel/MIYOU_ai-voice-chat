import { motion, AnimatePresence } from "motion/react";
import { Mic } from "lucide-react";

interface RecordingButtonProps {
  status: "idle" | "listening" | "processing" | "speaking";
  onStart: () => void;
  onEnd: () => void;
  disabled?: boolean;
}

export function RecordingButton({ status, onStart, onEnd, disabled = false }: RecordingButtonProps) {
  const isListening = status === "listening";

  return (
    <div className="relative flex flex-col items-center justify-center pointer-events-auto select-none touch-none">
      
      {/* Pulse Effect for Listening */}
      {isListening && (
        <span className="absolute z-0 w-16 h-16 rounded-full bg-white/10 pointer-events-none animate-ping" />
      )}

      {/* Button */}
      <motion.button
        onMouseDown={() => !disabled && onStart()}
        onMouseUp={() => !disabled && onEnd()}
        onMouseLeave={() => !disabled && onEnd()}
        onTouchStart={() => !disabled && onStart()}
        onTouchEnd={() => !disabled && onEnd()}
        disabled={disabled}
        animate={{
          scale: isListening ? 0.9 : 1,
          backgroundColor: isListening ? "#ffffff" : "rgba(255,255,255,0.08)",
          borderColor: isListening ? "rgba(255,255,255,1)" : "rgba(255,255,255,0.1)",
        }}
        whileTap={{ scale: 0.95 }}
        className={`z-20 w-16 h-16 rounded-full flex items-center justify-center border shadow-lg backdrop-blur-md transition-all duration-300 outline-none disabled:opacity-40 disabled:cursor-not-allowed
          ${isListening ? "text-zinc-950 shadow-white/20" : "text-zinc-400 hover:bg-white/15 hover:text-white hover:border-white/30"}
        `}
      >
        <Mic size={24} strokeWidth={2} className={`transition-transform duration-300 ${isListening ? "scale-110" : "scale-100"}`} />
      </motion.button>

      {/* Helper Text */}
      <div className="mt-3 pointer-events-none opacity-60 text-[10px] tracking-[0.1em] uppercase font-semibold text-white/80 h-4 whitespace-nowrap">
        <AnimatePresence mode="wait">
            {isListening ? (
                <motion.span 
                    key="release"
                    initial={{ opacity: 0, y: 5 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}
                >
                    Release to Send
                </motion.span>
            ) : (
                <motion.span 
                    key="hold"
                    initial={{ opacity: 0, y: 5 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}
                >
                    Hold to Speak
                </motion.span>
            )}
        </AnimatePresence>
      </div>
    </div>
  );
}
