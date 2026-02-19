import React, { useState, useCallback, useRef, useEffect } from "react";
import { Menu, Plus, Zap, Loader2 } from "lucide-react";
import { VoiceEqualizer } from "./components/VoiceEqualizer";
import { ConversationDisplay, Message } from "./components/ConversationDisplay";
import { Sidebar, ChatRoom } from "./components/Sidebar";
import { RecordingButton } from "./components/RecordingButton";
import { PersonaSelector, Persona, PERSONAS } from "./components/PersonaSelector";
import { DeleteConfirmationModal } from "./components/DeleteConfirmationModal";
import { motion, AnimatePresence } from "motion/react";

export default function App() {
  const [status, setStatus] = useState<"idle" | "listening" | "processing" | "speaking">("idle");
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);
  const [isPersonaSelectorOpen, setIsPersonaSelectorOpen] = useState(false);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  
  // State for rooms and personas
  const [rooms, setRooms] = useState<ChatRoom[]>([
    { id: "1", title: "Chat with Best Friend", date: "Today", persona: PERSONAS[0] },
  ]);
  const [activeRoomId, setActiveRoomId] = useState<string>("1");
  const [messagesByRoom, setMessagesByRoom] = useState<Record<string, Message[]>>({
    "1": [{ id: "1", text: PERSONAS[0].initialMessage, sender: "ai", timestamp: Date.now() }]
  });
  
  const [roomToDelete, setRoomToDelete] = useState<string | null>(null);

  // Get current room and messages
  const activeRoom = rooms.find(r => r.id === activeRoomId);
  const messages = activeRoomId ? (messagesByRoom[activeRoomId] || []) : [];

  // Recording Logic
  const startRecording = useCallback(() => {
    if (status !== "idle" || !activeRoomId) return;
    setStatus("listening");
  }, [status, activeRoomId]);

  const stopRecordingAndSend = useCallback(() => {
    if (status === "listening" && activeRoomId) {
      setStatus("processing");
      
      // Simulate Processing
      setTimeout(() => {
        // 1. Add User Message
        const userMsg: Message = {
            id: Date.now().toString(),
            text: "Can you tell me a joke?", // Shorter test string to fit better
            sender: "user",
            timestamp: Date.now(),
        };
        
        setMessagesByRoom(prev => ({
            ...prev,
            [activeRoomId]: [userMsg, ...(prev[activeRoomId] || [])]
        }));

        // 2. AI Speaks (Simulated Response)
        setTimeout(() => {
            setStatus("speaking");
            
            let responseText = "I'm not sure how to respond to that.";
            const currentPersonaId = rooms.find(r => r.id === activeRoomId)?.persona.id;

            if (currentPersonaId === "friend") responseText = "Why did the chicken cross the road? To get to the other side!";
            else if (currentPersonaId === "interviewer") responseText = "Humor is great, but let's focus. Tell me about a time you failed.";
            else if (currentPersonaId === "lover") responseText = "I love hearing your laugh.";
            else if (currentPersonaId === "mentor") responseText = "Focus on the goal.";
            else responseText = "I see.";

            const aiMsg: Message = {
                id: (Date.now() + 1).toString(),
                text: responseText,
                sender: "ai",
                timestamp: Date.now(),
            };
            
            setMessagesByRoom(prev => ({
                ...prev,
                [activeRoomId]: [aiMsg, ...(prev[activeRoomId] || [])]
            }));
            
            setTimeout(() => setStatus("idle"), 4000);
        }, 1500);

      }, 1500); // Slightly longer processing time to show the indicator
    }
  }, [status, activeRoomId, rooms]);

  // Sidebar Actions
  const handleNewChat = () => {
    setIsSidebarOpen(false);
    setIsPersonaSelectorOpen(true);
  };

  const handleSelectPersona = (persona: Persona) => {
    const newRoomId = Date.now().toString();
    const newRoom: ChatRoom = {
        id: newRoomId,
        title: `Chat with ${persona.name}`,
        date: "Just now",
        persona: persona
    };
    
    setRooms(prev => [newRoom, ...prev]);
    setMessagesByRoom(prev => ({
        ...prev,
        [newRoomId]: [{ id: Date.now().toString(), text: persona.initialMessage, sender: "ai", timestamp: Date.now() }]
    }));
    setActiveRoomId(newRoomId);
    setIsPersonaSelectorOpen(false);
  };

  const handleDeleteRoom = (roomId: string) => {
    setRoomToDelete(roomId);
    setIsDeleteModalOpen(true);
  };

  const confirmDeleteRoom = () => {
    if (roomToDelete) {
        setRooms(prev => {
            const newRooms = prev.filter(r => r.id !== roomToDelete);
            if (activeRoomId === roomToDelete) {
                setActiveRoomId(newRooms.length > 0 ? newRooms[0].id : "");
            }
            return newRooms;
        });
        
        setMessagesByRoom(prev => {
            const newMessages = { ...prev };
            delete newMessages[roomToDelete];
            return newMessages;
        });
        
        setRoomToDelete(null);
    }
  };

  return (
    <div className="relative w-full h-screen bg-zinc-950 text-white overflow-hidden select-none touch-none font-sans flex flex-col">
      
      {/* Header */}
      <header className="absolute top-0 left-0 w-full p-6 flex justify-between items-center z-30 pointer-events-none">
        <div className="flex items-center gap-4 pointer-events-auto">
            <button 
              onClick={() => setIsSidebarOpen(true)}
              className="p-3 rounded-full bg-zinc-900/40 backdrop-blur-md hover:bg-zinc-800 transition-colors border border-white/5 shadow-lg group"
            >
              <Menu className="text-white w-5 h-5 opacity-80 group-hover:scale-110 transition-transform" />
            </button>
        </div>
        
        <AnimatePresence mode="wait">
            {activeRoom && (
                <motion.div 
                    key={activeRoom.id}
                    initial={{ opacity: 0, y: -20 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -20 }}
                    className="flex flex-col items-center justify-center opacity-80"
                >
                    <div className="text-[10px] font-bold tracking-[0.3em] uppercase opacity-40 mb-1">Current Persona</div>
                    <div className={`text-xs font-semibold px-4 py-1.5 rounded-full bg-white/5 border border-white/5 flex items-center gap-2 backdrop-blur-md shadow-lg`}>
                        <div className={`w-2 h-2 rounded-full ${activeRoom.persona.color === "bg-blue-500" ? "bg-blue-400" : activeRoom.persona.color === "bg-zinc-500" ? "bg-zinc-400" : activeRoom.persona.color === "bg-rose-500" ? "bg-rose-400" : "bg-violet-400"} shadow-[0_0_10px_currentColor]`} />
                        {activeRoom.persona.name}
                    </div>
                </motion.div>
            )}
        </AnimatePresence>

        <div className="w-12" /> 
      </header>

      {/* Main Content Area */}
      <main className="flex-1 flex flex-col items-center justify-between w-full relative z-10 pt-20 pb-4">
        
        {/* Top: Equalizer */}
        <div className="flex-1 flex items-center justify-center w-full min-h-[220px] relative">
             <AnimatePresence mode="wait">
                {activeRoomId ? (
                    <motion.div 
                        key="equalizer"
                        initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
                        className="w-full flex justify-center"
                    >
                        <VoiceEqualizer status={status} />
                    </motion.div>
                ) : (
                    <motion.div 
                        key="empty"
                        initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
                        className="flex flex-col items-center text-zinc-600 space-y-4"
                    >
                        <div className="w-24 h-24 rounded-full border border-zinc-800 flex items-center justify-center bg-zinc-900/50">
                            <Zap size={32} strokeWidth={1} />
                        </div>
                        <p className="text-sm font-medium tracking-wide">No Active Chat</p>
                    </motion.div>
                )}
             </AnimatePresence>
        </div>

        {/* Middle: Processing Indicator */}
        <div className="h-12 flex items-center justify-center w-full">
            <AnimatePresence>
                {status === "processing" && (
                   <motion.div
                     initial={{ opacity: 0, scale: 0.8 }}
                     animate={{ opacity: 1, scale: 1 }}
                     exit={{ opacity: 0, scale: 0.8 }}
                     className="flex space-x-1.5 items-center bg-zinc-900/80 backdrop-blur-sm px-4 py-2 rounded-full border border-white/5 shadow-lg"
                   >
                      <div className="w-1.5 h-1.5 bg-blue-400 rounded-full animate-bounce" style={{ animationDelay: "0ms" }} />
                      <div className="w-1.5 h-1.5 bg-blue-400 rounded-full animate-bounce" style={{ animationDelay: "150ms" }} />
                      <div className="w-1.5 h-1.5 bg-blue-400 rounded-full animate-bounce" style={{ animationDelay: "300ms" }} />
                   </motion.div>
                )}
            </AnimatePresence>
        </div>

        {/* Text Display - Only visible when not processing or when processing is done? No, always visible, just pushed down slightly */}
        <div className="w-full flex justify-center mb-6 px-4 relative z-20 min-h-[240px]">
             <AnimatePresence mode="wait">
                {activeRoomId ? (
                    <motion.div key="chat" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="w-full flex justify-center">
                        <ConversationDisplay messages={messages} />
                    </motion.div>
                ) : (
                    <motion.div key="select" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="flex flex-col items-center justify-center h-full pb-8">
                        <button 
                            onClick={() => setIsPersonaSelectorOpen(true)}
                            className="flex items-center gap-2 px-6 py-3 bg-blue-600 hover:bg-blue-500 text-white rounded-full font-semibold shadow-lg shadow-blue-900/20 transition-all active:scale-95 pointer-events-auto"
                        >
                            <Plus size={18} />
                            Start New Chat
                        </button>
                    </motion.div>
                )}
             </AnimatePresence>
        </div>

        {/* Bottom: Recording Button */}
        <div className="w-full flex justify-center items-center z-50 mb-8 relative h-20">
             <AnimatePresence>
                {activeRoomId && (
                    <motion.div 
                        initial={{ opacity: 0, y: 20 }} 
                        animate={{ opacity: 1, y: 0 }} 
                        exit={{ opacity: 0, y: 20 }}
                    >
                        <RecordingButton 
                            status={status} 
                            onStart={startRecording} 
                            onEnd={stopRecordingAndSend}
                        />
                    </motion.div>
                )}
             </AnimatePresence>
        </div>

      </main>

      {/* Sidebar & Modals */}
      <Sidebar 
        isOpen={isSidebarOpen} 
        onClose={() => setIsSidebarOpen(false)} 
        onSelectRoom={(id) => { 
            setActiveRoomId(id);
            setIsSidebarOpen(false); 
        }}
        onNewChat={handleNewChat}
        onDeleteRoom={handleDeleteRoom}
        activeRoomId={activeRoomId}
        rooms={rooms}
      />

      <PersonaSelector 
        isOpen={isPersonaSelectorOpen}
        onClose={() => setIsPersonaSelectorOpen(false)}
        onSelect={handleSelectPersona}
      />

      <DeleteConfirmationModal 
        isOpen={isDeleteModalOpen}
        onClose={() => setIsDeleteModalOpen(false)}
        onConfirm={confirmDeleteRoom}
        roomName={rooms.find(r => r.id === roomToDelete)?.title || "Chat"}
      />
      
    </div>
  );
}
