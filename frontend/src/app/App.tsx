import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Menu, Plus, Send, Loader2, Mic } from "lucide-react";
import { VoiceEqualizer } from "./components/VoiceEqualizer";
import { Sidebar, ChatRoom } from "./components/Sidebar";
import { RecordingButton } from "./components/RecordingButton";
import { PersonaSelector, Persona } from "./components/PersonaSelector";
import { DeleteConfirmationModal } from "./components/DeleteConfirmationModal";
import { CreditBadge } from "./components/CreditBadge";
import { useCreditBalance } from "./hooks/useCreditBalance";
import { motion, AnimatePresence } from "motion/react";

import { Message, AppStatus, ToastKind, ChatRoomState } from "./types";
import { CONVERSATION_CREDIT_COST } from "./constants";
import {
  buildApiUrl,
  createSession,
  streamText,
  streamAudioAndPlay,
} from "./services/api";
import { useThrottledCallback } from "./hooks/useThrottledCallback";
import { useRecording } from "./hooks/useRecording";

function formatRoomDate(timestamp: number): string {
  const diff = Date.now() - timestamp;
  if (diff < 60_000) return "방금 전";
  if (diff < 3_600_000) return `${Math.max(1, Math.floor(diff / 60_000))}분 전`;
  if (diff < 86_400_000) return `${Math.max(1, Math.floor(diff / 3_600_000))}시간 전`;
  return new Date(timestamp).toLocaleDateString("ko-KR");
}

export default function App() {
  const [status, setStatus] = useState<AppStatus>("idle");
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);
  const [isPersonaSelectorOpen, setIsPersonaSelectorOpen] = useState(false);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [isVoiceOutputEnabled, setIsVoiceOutputEnabled] = useState(true);
  const [inputText, setInputText] = useState("");
  const [toast, setToast] = useState<{ message: string; kind: ToastKind } | null>(null);
  const [isBusy, setIsBusy] = useState(false);

  const {
    balance: creditBalance,
    isLoading: creditLoading,
    refresh: refreshCredit,
    applyOptimisticDelta: applyCreditDelta,
    lastDelta: creditLastDelta,
    changeId: creditChangeId,
  } = useCreditBalance(buildApiUrl);

  const [rooms, setRooms] = useState<ChatRoomState[]>([]);
  const [activeRoomId, setActiveRoomId] = useState<string>("");
  const [messagesByRoom, setMessagesByRoom] = useState<Record<string, Message[]>>({});
  const [roomToDelete, setRoomToDelete] = useState<string | null>(null);

  const audioRef = useRef<HTMLAudioElement | null>(null);
  const toastTimerRef = useRef<number | null>(null);

  const activeRoom = useMemo(
    () => rooms.find((room) => room.id === activeRoomId),
    [rooms, activeRoomId],
  );

  const showToast = useCallback((message: string, kind: ToastKind = "info", duration = 2200) => {
    setToast({ message, kind });

    if (toastTimerRef.current !== null) {
      window.clearTimeout(toastTimerRef.current);
    }

    toastTimerRef.current = window.setTimeout(() => {
      setToast(null);
      toastTimerRef.current = null;
    }, duration);
  }, []);

  const pushMessage = useCallback((roomId: string, message: Message) => {
    setMessagesByRoom((prev) => ({
      ...prev,
      [roomId]: [message, ...(prev[roomId] || [])],
    }));
  }, []);

  const patchMessage = useCallback((roomId: string, messageId: string, nextText: string) => {
    setMessagesByRoom((prev) => {
      const target = prev[roomId] || [];
      return {
        ...prev,
        [roomId]: target.map((msg) =>
          msg.id === messageId
            ? {
                ...msg,
                text: nextText,
              }
            : msg,
        ),
      };
    });
  }, []);

  const patchMessageThrottled = useThrottledCallback(
    (roomId: string, messageId: string, nextText: string) => {
      patchMessage(roomId, messageId, nextText);
    },
    [patchMessage],
  );

  const touchRoom = useCallback((roomId: string) => {
    const now = Date.now();
    setRooms((prev) => {
      const current = prev.find((room) => room.id === roomId);
      if (!current) return prev;

      const touched: ChatRoomState = {
        ...current,
        createdAt: now,
        date: formatRoomDate(now),
      };

      return [touched, ...prev.filter((room) => room.id !== roomId)];
    });
  }, []);

  const runDialogue = useCallback(
    async (query: string, options?: { appendUser?: boolean }) => {
      if (!activeRoom) {
        showToast("활성화된 대화방이 없습니다.", "error");
        setIsPersonaSelectorOpen(true);
        return;
      }

      const normalized = query.trim();
      if (!normalized) return;

      if (creditBalance !== null && creditBalance < CONVERSATION_CREDIT_COST) {
        showToast("크레딧이 부족합니다. 크레딧을 충전하세요.", "error");
        return;
      }

      const roomId = activeRoom.id;
      const sessionId = activeRoom.sessionId;
      const appendUser = options?.appendUser ?? true;

      if (appendUser) {
        pushMessage(roomId, {
          id: `${Date.now()}-user`,
          text: normalized,
          sender: "user",
          timestamp: Date.now(),
        });
      }

      touchRoom(roomId);
      applyCreditDelta(-CONVERSATION_CREDIT_COST);
      setStatus("processing");
      setIsBusy(true);

      try {
        if (isVoiceOutputEnabled) {
          const speakingMessageId = `${Date.now()}-ai-audio`;
          pushMessage(roomId, {
            id: speakingMessageId,
            text: "음성 응답을 재생하고 있습니다...",
            sender: "ai",
            timestamp: Date.now(),
          });

          if (!audioRef.current) {
            throw new Error("오디오 재생기 초기화에 실패했습니다");
          }

          setStatus("speaking");

          await streamAudioAndPlay(sessionId, normalized, audioRef.current);

          patchMessage(roomId, speakingMessageId, "음성 응답 재생이 완료되었습니다.");
        } else {
          const aiMessageId = `${Date.now()}-ai`;
          pushMessage(roomId, {
            id: aiMessageId,
            text: "",
            sender: "ai",
            timestamp: Date.now(),
          });

          const fullText = await streamText(sessionId, normalized, (nextText) => {
            patchMessageThrottled(roomId, aiMessageId, nextText);
          });

          patchMessage(roomId, aiMessageId, fullText || "응답이 비어 있습니다.");
        }
      } catch (error) {
        console.error(error);
        showToast(error instanceof Error ? error.message : "요청 처리 중 오류가 발생했습니다.", "error");
      } finally {
        setStatus("idle");
        setIsBusy(false);
        void refreshCredit();
      }
    },
    [
      activeRoom,
      applyCreditDelta,
      creditBalance,
      isVoiceOutputEnabled,
      patchMessage,
      patchMessageThrottled,
      pushMessage,
      refreshCredit,
      showToast,
      touchRoom,
    ],
  );

  const handleRecordingComplete = useCallback(
    async (transcription: string) => {
      if (!activeRoomId) {
        showToast("활성 대화방이 없어 요청을 취소했습니다.", "error");
        setStatus("idle");
        setIsBusy(false);
        return;
      }

      pushMessage(activeRoomId, {
        id: `${Date.now()}-stt-user`,
        text: transcription,
        sender: "user",
        timestamp: Date.now(),
      });

      setInputText(transcription);
      await runDialogue(transcription, { appendUser: false });
    },
    [activeRoomId, pushMessage, runDialogue, showToast],
  );

  const handleRecordingError = useCallback(
    (error: Error) => {
      showToast(error.message, "error");
    },
    [showToast],
  );

  const { startRecording, stopRecording, cleanupRecording } = useRecording({
    onComplete: handleRecordingComplete,
    onError: handleRecordingError,
    onStatusChange: setStatus,
    isBusy,
    status,
  });

  const handleNewChat = useCallback(() => {
    setIsSidebarOpen(false);
    setIsPersonaSelectorOpen(true);
  }, []);

  const handleSelectPersona = useCallback(
    async (persona: Persona) => {
      setIsPersonaSelectorOpen(false);
      setStatus("processing");
      setIsBusy(true);

      try {
        const session = await createSession(persona.id);
        const now = Date.now();
        const roomId = `${now}`;

        const newRoom: ChatRoomState = {
          id: roomId,
          title: `${persona.name}와의 대화`,
          date: formatRoomDate(now),
          persona,
          sessionId: session.sessionId,
          userId: session.userId,
          createdAt: now,
        };

        setRooms((prev) => [newRoom, ...prev]);
        setMessagesByRoom((prev) => ({
          ...prev,
          [roomId]: [
            {
              id: `${now}-welcome`,
              text: persona.initialMessage,
              sender: "ai",
              timestamp: now,
            },
          ],
        }));

        setActiveRoomId(roomId);
        setStatus("idle");
        showToast("대화를 시작하려면 길게 눌러 말하세요.");
        void refreshCredit();
      } catch (error) {
        console.error(error);
        showToast(error instanceof Error ? error.message : "세션 생성 중 오류가 발생했습니다.", "error");
        setStatus("idle");
        setIsPersonaSelectorOpen(true);
      } finally {
        setIsBusy(false);
      }
    },
    [refreshCredit, showToast],
  );

  const handleDeleteRoom = useCallback((roomId: string) => {
    setRoomToDelete(roomId);
    setIsDeleteModalOpen(true);
  }, []);

  const confirmDeleteRoom = useCallback(() => {
    if (!roomToDelete) return;

    setRooms((prev) => {
      const next = prev.filter((room) => room.id !== roomToDelete);
      if (activeRoomId === roomToDelete) {
        setActiveRoomId(next[0]?.id || "");
      }
      return next;
    });

    setMessagesByRoom((prev) => {
      const copied = { ...prev };
      delete copied[roomToDelete];
      return copied;
    });

    setRoomToDelete(null);
  }, [activeRoomId, roomToDelete]);

  const sendTypedMessage = useCallback(async () => {
    const text = inputText.trim();
    if (!text || isBusy) return;

    setInputText("");
    await runDialogue(text);
  }, [inputText, isBusy, runDialogue]);

  useEffect(() => {
    return () => {
      if (toastTimerRef.current !== null) {
        window.clearTimeout(toastTimerRef.current);
      }
      cleanupRecording();
    };
  }, [cleanupRecording]);

  return (
    <div className="relative w-full h-screen bg-zinc-950 text-white overflow-hidden select-none touch-none font-sans flex flex-col">
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
              <div className="text-xs font-semibold px-4 py-1.5 rounded-full bg-white/5 border border-white/5 flex items-center gap-2 backdrop-blur-md shadow-lg">
                <div
                  className={`w-2 h-2 rounded-full ${
                    activeRoom.persona.color === "bg-blue-500"
                      ? "bg-blue-400"
                      : activeRoom.persona.color === "bg-zinc-500"
                        ? "bg-zinc-400"
                        : activeRoom.persona.color === "bg-rose-500"
                          ? "bg-rose-400"
                          : "bg-violet-400"
                  } shadow-[0_0_10px_currentColor]`}
                />
                {activeRoom.persona.name}
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        <CreditBadge
          balance={creditBalance}
          isLoading={creditLoading}
          lastDelta={creditLastDelta}
          changeId={creditChangeId}
        />
      </header>

      <main className="flex-1 flex flex-col items-center justify-between w-full relative z-10 pt-20 pb-4">
        <div className="flex-1 flex items-center justify-center w-full min-h-[220px] relative">
          <AnimatePresence mode="wait">
            {activeRoomId ? (
              <motion.div
                key="equalizer"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                className="w-full flex justify-center"
              >
                <VoiceEqualizer status={status} />
              </motion.div>
            ) : (
              <motion.div
                key="empty"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                className="flex flex-col items-center text-zinc-600 space-y-4"
              >
                <div className="w-24 h-24 rounded-full border border-zinc-800 flex items-center justify-center bg-zinc-900/50">
                  <Plus size={32} strokeWidth={1} />
                </div>
                <p className="text-sm font-medium tracking-wide">새 대화를 시작해 주세요</p>
              </motion.div>
            )}
          </AnimatePresence>
        </div>

        {!activeRoomId && (
          <div className="w-full flex justify-center mb-4 px-4 relative z-20 min-h-[96px]">
            <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="flex flex-col items-center justify-center h-full pb-4">
              <button
                onClick={() => setIsPersonaSelectorOpen(true)}
                className="flex items-center gap-2 px-6 py-3 bg-blue-600 hover:bg-blue-500 text-white rounded-full font-semibold shadow-lg shadow-blue-900/20 transition-all active:scale-95 pointer-events-auto"
              >
                <Plus size={18} />
                Start New Chat
              </button>
            </motion.div>
          </div>
        )}

        <div className="w-full max-w-2xl px-4 md:px-6 space-y-3 mb-5 pointer-events-auto">
          <div className="flex items-center gap-2 bg-zinc-900/85 border border-white/10 rounded-2xl px-3 py-2 backdrop-blur">
            <input
              value={inputText}
              onChange={(event) => setInputText(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter" && !event.shiftKey) {
                  event.preventDefault();
                  void sendTypedMessage();
                }
              }}
              placeholder={activeRoomId ? "메시지를 입력하세요" : "먼저 페르소나를 선택하세요"}
              disabled={!activeRoomId || isBusy}
              className="flex-1 bg-transparent text-sm text-white placeholder:text-zinc-500 outline-none px-1"
            />

            <button
              type="button"
              onClick={() => {
                if (status === "listening") {
                  stopRecording();
                  return;
                }
                void startRecording();
              }}
              disabled={!activeRoomId || (isBusy && status !== "listening")}
              className={`w-9 h-9 rounded-full flex items-center justify-center border transition-colors disabled:opacity-40 disabled:cursor-not-allowed ${
                status === "listening"
                  ? "bg-white text-zinc-950 border-white"
                  : "bg-zinc-800 text-zinc-300 border-white/10 hover:bg-zinc-700 hover:text-white"
              }`}
              title={status === "listening" ? "다시 누르면 전송" : "누르면 음성 입력 시작"}
              aria-label={status === "listening" ? "음성 입력 종료" : "음성 입력 시작"}
            >
              {status === "listening" ? <Loader2 size={16} className="animate-spin" /> : <Mic size={16} />}
            </button>

            <button
              onClick={() => void sendTypedMessage()}
              disabled={!activeRoomId || isBusy || !inputText.trim()}
              className="w-9 h-9 rounded-full bg-white text-zinc-900 flex items-center justify-center disabled:opacity-40 disabled:cursor-not-allowed"
            >
              {isBusy ? <Loader2 size={16} className="animate-spin" /> : <Send size={16} />}
            </button>
          </div>
        </div>

        <div className="w-full flex justify-center items-center z-50 mb-8 relative h-20">
          <AnimatePresence>
            {activeRoomId && (
              <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: 20 }}>
                <RecordingButton status={status} onStart={() => void startRecording()} onEnd={stopRecording} disabled={!activeRoomId || isBusy} />
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </main>

      <Sidebar
        isOpen={isSidebarOpen}
        onClose={() => setIsSidebarOpen(false)}
        onSelectRoom={(roomId) => {
          setActiveRoomId(roomId);
          setIsSidebarOpen(false);
        }}
        onNewChat={handleNewChat}
        onDeleteRoom={handleDeleteRoom}
        activeRoomId={activeRoomId}
        rooms={rooms}
        creditBalance={creditBalance}
        isVoiceOutputEnabled={isVoiceOutputEnabled}
        onVoiceOutputChange={setIsVoiceOutputEnabled}
      />

      <PersonaSelector
        isOpen={isPersonaSelectorOpen}
        onClose={() => setIsPersonaSelectorOpen(false)}
        onSelect={(persona) => void handleSelectPersona(persona)}
      />

      <DeleteConfirmationModal
        isOpen={isDeleteModalOpen}
        onClose={() => {
          setIsDeleteModalOpen(false);
          setRoomToDelete(null);
        }}
        onConfirm={confirmDeleteRoom}
        roomName={roomToDelete ? rooms.find((room) => room.id === roomToDelete)?.title || "이 채팅" : ""}
      />

      <AnimatePresence>
        {toast && (
          <motion.div
            key={`${toast.kind}-${toast.message}`}
            initial={{ opacity: 0, y: 12, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 10, scale: 0.98 }}
            transition={{ duration: 0.18 }}
            className={`fixed left-1/2 -translate-x-1/2 bottom-36 z-[120] px-4 py-2 rounded-full text-xs md:text-sm border backdrop-blur-md shadow-lg pointer-events-none ${
              toast.kind === "error"
                ? "bg-red-500/15 border-red-400/40 text-red-100"
                : "bg-zinc-900/90 border-white/15 text-zinc-100"
            }`}
          >
            {toast.message}
          </motion.div>
        )}
      </AnimatePresence>

      <audio ref={audioRef} style={{ display: "none" }} />
    </div>
  );
}
