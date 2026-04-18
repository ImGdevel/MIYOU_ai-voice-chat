import { motion, AnimatePresence } from "motion/react";
import { X, MessageSquare, Plus, Settings, User, Trash2, Coins, Volume2, VolumeX } from "lucide-react";
import { Persona } from "./PersonaSelector";

export interface ChatRoom {
  id: string;
  title: string;
  date: string;
  persona: Persona; // Store the persona associated with this room
}

interface SidebarProps {
  isOpen: boolean;
  onClose: () => void;
  onSelectRoom: (roomId: string) => void;
  onNewChat: () => void;
  onDeleteRoom: (roomId: string) => void;
  activeRoomId?: string;
  rooms: ChatRoom[];
  creditBalance?: number | null;
  isVoiceOutputEnabled: boolean;
  onVoiceOutputChange: (enabled: boolean) => void;
}

export function Sidebar({
  isOpen,
  onClose,
  onSelectRoom,
  onNewChat,
  onDeleteRoom,
  activeRoomId,
  rooms,
  creditBalance,
  isVoiceOutputEnabled,
  onVoiceOutputChange,
}: SidebarProps) {
  return (
    <AnimatePresence>
      {isOpen && (
        <>
          {/* Backdrop */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 0.5 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="fixed inset-0 bg-black z-40 backdrop-blur-sm"
          />
          
          {/* Drawer */}
          <motion.div
            initial={{ x: "-100%" }}
            animate={{ x: 0 }}
            exit={{ x: "-100%" }}
            transition={{ type: "spring", damping: 25, stiffness: 200 }}
            className="fixed left-0 top-0 bottom-0 w-80 bg-zinc-900 border-r border-zinc-800 z-50 shadow-2xl flex flex-col"
            onMouseDown={(e) => e.stopPropagation()}
            onTouchStart={(e) => e.stopPropagation()}
          >
            <div className="p-6 flex items-center justify-between border-b border-zinc-800">
              <h2 className="text-xl font-semibold text-white tracking-tight">Chats</h2>
              <button 
                onClick={onClose}
                className="p-2 rounded-full hover:bg-zinc-800 transition-colors text-zinc-400 hover:text-white"
              >
                <X size={20} />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto p-4 space-y-2 scrollbar-thin scrollbar-thumb-zinc-700 scrollbar-track-transparent">
              <button 
                onClick={onNewChat}
                className="w-full flex items-center gap-3 p-3 rounded-xl bg-blue-600 hover:bg-blue-500 text-white transition-all shadow-lg shadow-blue-900/20 mb-6 group active:scale-95 duration-100"
              >
                <div className="bg-white/20 p-2 rounded-lg group-hover:bg-white/30 transition-colors">
                    <Plus size={18} />
                </div>
                <div className="flex flex-col text-left">
                    <span className="font-bold text-sm">New Chat</span>
                    <span className="text-[10px] opacity-70 font-medium">Select a Persona</span>
                </div>
              </button>

              <div className="text-xs font-semibold text-zinc-500 uppercase tracking-wider mb-2 px-2 flex justify-between items-center">
                <span>Recent Conversations</span>
                <span className="bg-zinc-800 px-1.5 py-0.5 rounded text-[10px] text-zinc-400">{rooms.length}</span>
              </div>
              
              <AnimatePresence mode="popLayout">
                {rooms.length === 0 ? (
                    <motion.div 
                        initial={{ opacity: 0 }} animate={{ opacity: 1 }}
                        className="text-center py-8 text-zinc-600 text-sm"
                    >
                        No conversations yet.
                    </motion.div>
                ) : (
                    rooms.map((room) => (
                        <motion.div
                          key={room.id}
                          layout
                          initial={{ opacity: 0, scale: 0.9 }}
                          animate={{ opacity: 1, scale: 1 }}
                          exit={{ opacity: 0, height: 0, marginBottom: 0 }}
                          className="relative group"
                        >
                          <button
                            onClick={() => onSelectRoom(room.id)}
                            className={`w-full flex items-center gap-3 p-3 rounded-lg text-left transition-all duration-200 border border-transparent
                              ${activeRoomId === room.id 
                                ? "bg-zinc-800 text-white shadow-md border-zinc-700/50" 
                                : "text-zinc-400 hover:bg-zinc-800/50 hover:text-zinc-200"
                              }
                            `}
                          >
                            <div className={`p-2 rounded-full text-white ${activeRoomId === room.id ? "bg-blue-500/20 text-blue-400" : "bg-zinc-800 text-zinc-500"}`}>
                                <MessageSquare size={16} />
                            </div>
                            <div className="flex flex-col flex-1 min-w-0">
                                <span className="text-sm font-medium truncate">{room.title}</span>
                                <div className="flex items-center gap-2">
                                    <span className="text-[10px] text-zinc-500 bg-zinc-800/50 px-1.5 rounded truncate max-w-[80px]">
                                        {room.persona.name}
                                    </span>
                                    <span className="text-[10px] text-zinc-600">{room.date}</span>
                                </div>
                            </div>
                          </button>
                          
                          {/* Delete Button - Appears on hover */}
                          <button
                            onClick={(e) => {
                                e.stopPropagation();
                                onDeleteRoom(room.id);
                            }}
                            className="absolute right-2 top-1/2 -translate-y-1/2 p-2 rounded-full text-zinc-500 hover:text-red-400 hover:bg-red-500/10 transition-all opacity-0 group-hover:opacity-100"
                            title="Delete Chat"
                          >
                            <Trash2 size={16} />
                          </button>
                        </motion.div>
                      ))
                )}
              </AnimatePresence>
            </div>

            <div className="p-4 border-t border-zinc-800 bg-zinc-900/50">
                {creditBalance != null && (
                  <div className="flex items-center gap-2 px-3 py-2.5 mb-2 rounded-lg bg-amber-500/8 border border-amber-500/15">
                    <Coins size={15} className="text-amber-400 shrink-0" />
                    <span className="text-xs text-zinc-400 flex-1">크레딧 잔액</span>
                    <span className="text-sm font-bold text-amber-400 tabular-nums">
                      {creditBalance.toLocaleString()}
                    </span>
                  </div>
                )}
                <div className="rounded-lg bg-zinc-950/40 border border-zinc-800/80 overflow-hidden">
                  <div className="flex items-center gap-3 px-3 py-3 text-zinc-300">
                    <Settings size={18} />
                    <span className="text-sm font-medium">Settings</span>
                  </div>
                  <button
                    type="button"
                    onClick={() => onVoiceOutputChange(!isVoiceOutputEnabled)}
                    className="w-full flex items-center gap-3 px-3 py-3 border-t border-zinc-800 text-left text-zinc-400 hover:text-white hover:bg-zinc-800/70 transition-colors"
                    aria-pressed={isVoiceOutputEnabled}
                  >
                    <span
                      className={`w-8 h-8 rounded-full flex items-center justify-center shrink-0 ${
                        isVoiceOutputEnabled ? "bg-blue-500/20 text-blue-300" : "bg-zinc-800 text-zinc-500"
                      }`}
                    >
                      {isVoiceOutputEnabled ? <Volume2 size={16} /> : <VolumeX size={16} />}
                    </span>
                    <span className="flex-1 min-w-0">
                      <span className="block text-sm font-medium text-inherit">TTS 음성 응답</span>
                      <span className="block text-[10px] text-zinc-500">
                        {isVoiceOutputEnabled ? "켜짐" : "꺼짐"}
                      </span>
                    </span>
                    <span
                      className={`relative w-10 h-6 rounded-full transition-colors ${
                        isVoiceOutputEnabled ? "bg-blue-500" : "bg-zinc-700"
                      }`}
                    >
                      <span
                        className={`absolute top-1 w-4 h-4 rounded-full bg-white transition-transform ${
                          isVoiceOutputEnabled ? "translate-x-5" : "translate-x-1"
                        }`}
                      />
                    </span>
                  </button>
                </div>
                <div
                  className="flex items-center gap-3 p-3 rounded-lg text-zinc-600 mt-1 cursor-not-allowed"
                  aria-disabled="true"
                >
                    <User size={18} />
                    <span className="text-sm font-medium flex-1">Profile</span>
                    <span className="text-[10px] font-semibold text-zinc-600 border border-zinc-800 rounded-full px-2 py-0.5">
                      준비 중
                    </span>
                </div>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
