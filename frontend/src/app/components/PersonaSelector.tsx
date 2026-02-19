import { motion, AnimatePresence } from "motion/react";
import { Briefcase, UserRound, X } from "lucide-react";

export interface Persona {
  id: string;
  name: string;
  role: string;
  description: string;
  icon: React.ReactNode;
  initialMessage: string;
  color: string;
}

export const PERSONAS: Persona[] = [
  {
    id: "maid",
    name: "메이드 리리아",
    role: "친절한 메이드",
    description: "따뜻하고 세심한 말투로 대화를 이어갑니다.",
    icon: <UserRound size={24} />,
    initialMessage: "안녕하세요, 주인님. 오늘은 어떤 이야기를 나눌까요?",
    color: "bg-blue-500",
  },
  {
    id: "interviewer",
    name: "기술 면접관",
    role: "전문 면접 모드",
    description: "실전 중심 질문으로 면접 답변을 점검합니다.",
    icon: <Briefcase size={24} />,
    initialMessage: "안녕하세요. 기술 면접을 시작하겠습니다. 간단히 자기소개해 주세요.",
    color: "bg-zinc-500",
  },
];

interface PersonaSelectorProps {
  isOpen: boolean;
  onClose: () => void;
  onSelect: (persona: Persona) => void;
}

export function PersonaSelector({ isOpen, onClose, onSelect }: PersonaSelectorProps) {
  return (
    <AnimatePresence>
      {isOpen && (
        <>
          {/* Backdrop */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="fixed inset-0 bg-black/80 backdrop-blur-sm z-50 flex items-center justify-center p-4"
          >
            {/* Modal */}
            <motion.div
              initial={{ scale: 0.95, opacity: 0, y: 20 }}
              animate={{ scale: 1, opacity: 1, y: 0 }}
              exit={{ scale: 0.95, opacity: 0, y: 20 }}
              onClick={(e) => e.stopPropagation()}
              className="bg-zinc-900 border border-zinc-800 w-full max-w-lg rounded-2xl shadow-2xl overflow-hidden flex flex-col max-h-[80vh]"
            >
              {/* Header */}
              <div className="p-6 border-b border-zinc-800 flex justify-between items-center bg-zinc-900 sticky top-0 z-10">
                <div>
                  <h2 className="text-xl font-bold text-white tracking-tight">Choose Persona</h2>
                  <p className="text-zinc-400 text-sm mt-1">Who do you want to talk to today?</p>
                </div>
                <button 
                  onClick={onClose}
                  className="p-2 rounded-full hover:bg-zinc-800 text-zinc-400 hover:text-white transition-colors"
                >
                  <X size={20} />
                </button>
              </div>

              {/* List */}
              <div className="overflow-y-auto p-4 space-y-3">
                {PERSONAS.map((persona) => (
                  <motion.button
                    key={persona.id}
                    onClick={() => onSelect(persona)}
                    whileHover={{ scale: 1.02 }}
                    whileTap={{ scale: 0.98 }}
                    className="w-full flex items-center gap-4 p-4 rounded-xl bg-zinc-800/50 hover:bg-zinc-800 border border-zinc-700/50 hover:border-zinc-600 transition-all group text-left"
                  >
                    <div className={`w-12 h-12 rounded-full flex items-center justify-center text-white shadow-lg ${persona.color}`}>
                      {persona.icon}
                    </div>
                    <div className="flex-1">
                      <div className="flex justify-between items-center mb-1">
                        <span className="font-semibold text-white text-lg">{persona.name}</span>
                        <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-white/10 text-zinc-300 border border-white/5">
                            {persona.role}
                        </span>
                      </div>
                      <p className="text-sm text-zinc-400 leading-snug group-hover:text-zinc-300 transition-colors">
                        {persona.description}
                      </p>
                    </div>
                  </motion.button>
                ))}
              </div>
            </motion.div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
