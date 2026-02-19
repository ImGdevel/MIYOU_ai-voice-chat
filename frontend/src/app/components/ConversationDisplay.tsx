import { motion, AnimatePresence } from "motion/react";
import { User, Sparkles } from "lucide-react";

export interface Message {
  id: string;
  text: string;
  sender: "user" | "ai";
  timestamp: number;
}

interface ConversationDisplayProps {
  messages: Message[];
}

export function ConversationDisplay({ messages }: ConversationDisplayProps) {
  // Show more context, but focus on the latest few
  const visibleMessages = messages.slice(0, 4);

  return (
    <div 
      className="relative flex flex-col items-center justify-start w-full max-w-lg h-[240px] overflow-hidden px-4"
      style={{ maskImage: "linear-gradient(to bottom, black 60%, transparent 100%)", WebkitMaskImage: "linear-gradient(to bottom, black 60%, transparent 100%)" }}
    >
      <div className="flex flex-col items-center w-full space-y-3 pt-2">
        <AnimatePresence mode="popLayout" initial={false}>
          {visibleMessages.map((msg, index) => {
            const isLatest = index === 0;
            const isPrevious = index === 1;
            
            // Visual styles based on sender
            const isUser = msg.sender === "user";
            
            return (
              <motion.div
                key={msg.id}
                layout
                initial={{ opacity: 0, y: -10, scale: 0.95, filter: "blur(2px)" }}
                animate={{
                  opacity: isLatest ? 1 : isPrevious ? 0.5 : 0.15,
                  y: 0,
                  scale: isLatest ? 1 : isPrevious ? 0.98 : 0.95,
                  filter: isLatest ? "blur(0px)" : isPrevious ? "blur(0px)" : "blur(1.5px)",
                }}
                exit={{ opacity: 0, y: 10, filter: "blur(4px)" }}
                transition={{
                  type: "spring",
                  stiffness: 350,
                  damping: 30,
                  opacity: { duration: 0.3 }
                }}
                className={`w-full flex flex-col items-center text-center transition-all duration-300 relative
                  ${isUser ? "text-zinc-400" : "text-zinc-100"}
                `}
              >
                {/* Message Content */}
                <div className="relative w-full px-2">
                    <div className="flex items-center justify-center gap-2 mb-1 opacity-60">
                         {isUser ? <User size={10} className="text-zinc-500"/> : <Sparkles size={10} className="text-blue-400"/>}
                         <span className="text-[9px] font-bold tracking-widest uppercase font-mono">
                            {isUser ? "You" : "Assistant"}
                         </span>
                    </div>

                    <p className={`font-medium tracking-tight leading-normal text-balance break-words w-full
                      ${isLatest ? (isUser ? "text-base italic font-serif" : "text-lg md:text-xl font-sans") : "text-sm"}
                      ${!isLatest && "truncate px-8 opacity-70"}
                    `}>
                      "{msg.text}"
                    </p>
                </div>

                {/* Separator for clarity */}
                {isLatest && <div className="w-8 h-[1px] bg-white/10 mt-3 rounded-full" />}
              </motion.div>
            );
          })}
        </AnimatePresence>
      </div>
    </div>
  );
}
