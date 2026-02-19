import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Menu, Plus, Send, Loader2, Mic, MicOff } from "lucide-react";
import { VoiceEqualizer } from "./components/VoiceEqualizer";
import type { Message } from "./components/ConversationDisplay";
import { Sidebar, ChatRoom } from "./components/Sidebar";
import { RecordingButton } from "./components/RecordingButton";
import { PersonaSelector, Persona } from "./components/PersonaSelector";
import { DeleteConfirmationModal } from "./components/DeleteConfirmationModal";
import { motion, AnimatePresence } from "motion/react";

type AppStatus = "idle" | "listening" | "processing" | "speaking";
type ToastKind = "info" | "error";

interface SessionResponse {
  sessionId: string;
  userId: string;
  personaId: string;
}

interface ChatRoomState extends ChatRoom {
  sessionId: string;
  userId: string;
  createdAt: number;
}

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/$/, "");

function buildApiUrl(path: string): string {
  if (!API_BASE_URL) return path;
  return `${API_BASE_URL}${path.startsWith("/") ? path : `/${path}`}`;
}


function formatRoomDate(timestamp: number): string {
  const diff = Date.now() - timestamp;
  if (diff < 60_000) return "방금 전";
  if (diff < 3_600_000) return `${Math.max(1, Math.floor(diff / 60_000))}분 전`;
  if (diff < 86_400_000) return `${Math.max(1, Math.floor(diff / 3_600_000))}시간 전`;
  return new Date(timestamp).toLocaleDateString("ko-KR");
}

function chooseRecordingMimeType() {
  const candidates = ["audio/webm", "audio/mp4", "audio/wav"];
  const mimeType = candidates.find((type) => MediaRecorder.isTypeSupported(type));
  return mimeType || "audio/webm";
}

async function createSession(personaId: string): Promise<SessionResponse> {
  const response = await fetch(buildApiUrl("/rag/dialogue/session"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      personaId,
    }),
  });

  if (!response.ok) {
    throw new Error(`세션 생성 실패 (${response.status})`);
  }

  const session: SessionResponse = await response.json();

  // 백엔드가 생성한 userId를 로컬스토리지에 저장
  localStorage.setItem("miyou-user-id", session.userId);

  return session;
}

async function transcribeAudio(blob: Blob, mimeType: string): Promise<string> {
  const extension = mimeType.includes("mp4") ? "mp4" : mimeType.includes("wav") ? "wav" : "webm";
  const formData = new FormData();
  formData.append("audio", blob, `recording.${extension}`);

  const url = new URL(buildApiUrl("/rag/dialogue/stt"), window.location.origin);
  url.searchParams.append("language", "ko");

  const response = await fetch(url, {
    method: "POST",
    body: formData,
  });

  if (!response.ok) {
    throw new Error(`음성 인식 실패 (${response.status})`);
  }

  const result = await response.json();
  const transcription = typeof result === "string" ? result : result?.transcription;

  if (!transcription) {
    throw new Error("STT 결과가 비어 있습니다");
  }

  return transcription;
}

async function streamText(
  sessionId: string,
  query: string,
  onToken: (nextText: string) => void,
): Promise<string> {
  const response = await fetch(buildApiUrl("/rag/dialogue/text"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      sessionId,
      text: query,
      requestedAt: new Date().toISOString(),
    }),
  });

  if (!response.ok || !response.body) {
    throw new Error(`텍스트 응답 실패 (${response.status})`);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";
  let fullText = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split("\n");
    buffer = lines.pop() || "";

    for (const rawLine of lines) {
      const line = rawLine.trimEnd();
      if (!line.startsWith("data:")) continue;

      const data = line.slice(5);
      const token = data.startsWith(" ") ? data.slice(1) : data;
      if (!token || token === "[DONE]") continue;

      fullText += token;
      onToken(fullText);
    }
  }

  return fullText;
}

async function streamAudioAndPlay(
  sessionId: string,
  query: string,
  audioElement: HTMLAudioElement,
): Promise<void> {
  if (!("MediaSource" in window) || !MediaSource.isTypeSupported("audio/mpeg")) {
    throw new Error("브라우저가 오디오 스트리밍을 지원하지 않습니다");
  }

  const mediaSource = new MediaSource();
  const objectUrl = URL.createObjectURL(mediaSource);
  audioElement.src = objectUrl;

  try {
    await new Promise<void>((resolve, reject) => {
      let sourceBuffer: SourceBuffer | null = null;
      const queue: Uint8Array[] = [];
      let streamDone = false;
      let hasAudioData = false;
      let playbackStarted = false;
      let settled = false;

      const cleanup = () => {
        if (sourceBuffer) {
          sourceBuffer.removeEventListener("updateend", onUpdateEnd);
        }
        audioElement.removeEventListener("ended", onEnded);
        audioElement.removeEventListener("error", onAudioError);
        mediaSource.removeEventListener("sourceopen", onSourceOpen);
      };

      const finish = () => {
        if (settled) return;
        settled = true;
        cleanup();
        resolve();
      };

      const fail = (error: unknown) => {
        if (settled) return;
        settled = true;
        cleanup();
        reject(error instanceof Error ? error : new Error("오디오 스트리밍 중 오류가 발생했습니다"));
      };

      const tryStartPlayback = () => {
        if (playbackStarted) return;
        playbackStarted = true;
        audioElement.play().catch((error) => {
          fail(error);
        });
      };

      const tryAppendNext = () => {
        if (!sourceBuffer || sourceBuffer.updating || queue.length === 0) return;
        const next = queue.shift();
        if (!next) return;
        hasAudioData = true;
        sourceBuffer.appendBuffer(next);
        tryStartPlayback();
      };

      const maybeCompleteStream = () => {
        if (!sourceBuffer) return;
        if (!streamDone || sourceBuffer.updating || queue.length > 0) return;

        if (!hasAudioData) {
          fail(new Error("오디오 응답 데이터가 비어 있습니다."));
          return;
        }

        if (mediaSource.readyState === "open") {
          mediaSource.endOfStream();
        }
      };

      const onEnded = () => finish();

      const onAudioError = () => {
        fail(new Error("오디오 재생 중 오류가 발생했습니다"));
      };

      const onUpdateEnd = () => {
        try {
          tryAppendNext();
          maybeCompleteStream();
        } catch (error) {
          fail(error);
        }
      };

      const onSourceOpen = async () => {
        try {
          sourceBuffer = mediaSource.addSourceBuffer("audio/mpeg");
          sourceBuffer.addEventListener("updateend", onUpdateEnd);

          const response = await fetch(buildApiUrl("/rag/dialogue/audio?format=mp3"), {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
            },
            body: JSON.stringify({
              sessionId,
              text: query,
              requestedAt: new Date().toISOString(),
            }),
          });

          if (!response.ok || !response.body) {
            throw new Error(`음성 응답 실패 (${response.status})`);
          }

          const reader = response.body.getReader();

          while (true) {
            const { done, value } = await reader.read();
            if (done) {
              streamDone = true;
              maybeCompleteStream();
              break;
            }

            if (!value || value.byteLength === 0) continue;
            queue.push(value);
            tryAppendNext();
          }
        } catch (error) {
          fail(error);
        }
      };

      audioElement.addEventListener("ended", onEnded, { once: true });
      audioElement.addEventListener("error", onAudioError, { once: true });
      mediaSource.addEventListener("sourceopen", onSourceOpen, { once: true });
    });
  } finally {
    URL.revokeObjectURL(objectUrl);
  }
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

  const [rooms, setRooms] = useState<ChatRoomState[]>([]);
  const [activeRoomId, setActiveRoomId] = useState<string>("");
  const [messagesByRoom, setMessagesByRoom] = useState<Record<string, Message[]>>({});
  const [roomToDelete, setRoomToDelete] = useState<string | null>(null);

  const audioRef = useRef<HTMLAudioElement | null>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const recordStreamRef = useRef<MediaStream | null>(null);
  const recordedChunksRef = useRef<BlobPart[]>([]);
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

  const rafPatchRef = useRef<{ rafId: number | null; latestArgs: [string, string, string] | null }>({
    rafId: null,
    latestArgs: null,
  });

  const patchMessageThrottled = useCallback(
    (roomId: string, messageId: string, nextText: string) => {
      rafPatchRef.current.latestArgs = [roomId, messageId, nextText];
      if (rafPatchRef.current.rafId !== null) return;
      rafPatchRef.current.rafId = requestAnimationFrame(() => {
        const args = rafPatchRef.current.latestArgs;
        rafPatchRef.current.rafId = null;
        rafPatchRef.current.latestArgs = null;
        if (args) patchMessage(...args);
      });
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
      }
    },
    [activeRoom, isVoiceOutputEnabled, patchMessage, patchMessageThrottled, pushMessage, showToast, touchRoom],
  );

  const startRecording = useCallback(async () => {
    if (isBusy || status !== "idle") return;

    if (!activeRoom) {
      showToast("세션이 없습니다. 페르소나를 먼저 선택하세요.", "error");
      setIsPersonaSelectorOpen(true);
      return;
    }

    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
      showToast("마이크 기능은 HTTPS 환경의 지원 브라우저에서 사용할 수 있습니다.", "error");
      return;
    }

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mimeType = chooseRecordingMimeType();
      const mediaRecorder = new MediaRecorder(stream, { mimeType });

      recordedChunksRef.current = [];
      recordStreamRef.current = stream;
      mediaRecorderRef.current = mediaRecorder;

      mediaRecorder.addEventListener("dataavailable", (event) => {
        recordedChunksRef.current.push(event.data);
      });

      mediaRecorder.addEventListener("stop", async () => {
        try {
          setStatus("processing");
          setIsBusy(true);

          const blob = new Blob(recordedChunksRef.current, { type: mimeType });
          const transcription = await transcribeAudio(blob, mimeType);

          if (!activeRoomId) {
            showToast("활성 대화방이 없어 요청을 취소했습니다.", "error");
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
        } catch (error) {
          console.error(error);
          setStatus("idle");
          setIsBusy(false);
          showToast(error instanceof Error ? error.message : "녹음 처리 중 오류가 발생했습니다.", "error");
        } finally {
          recordStreamRef.current?.getTracks().forEach((track) => track.stop());
          mediaRecorderRef.current = null;
          recordStreamRef.current = null;
          recordedChunksRef.current = [];
        }
      });

      mediaRecorder.start();
      setStatus("listening");
    } catch (error) {
      console.error(error);
      showToast("마이크 접근 권한이 필요합니다.", "error");
      setStatus("idle");
    }
  }, [activeRoom, activeRoomId, isBusy, pushMessage, runDialogue, showToast, status]);

  const stopRecordingAndSend = useCallback(() => {
    const recorder = mediaRecorderRef.current;
    if (!recorder || recorder.state === "inactive") return;

    recorder.stop();
  }, []);

  const handleNewChat = useCallback(() => {
    setIsSidebarOpen(false);
    setIsPersonaSelectorOpen(true);
  }, []);

  const handleSelectPersona = useCallback(async (persona: Persona) => {
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
    } catch (error) {
      console.error(error);
      showToast(error instanceof Error ? error.message : "세션 생성 중 오류가 발생했습니다.", "error");
      setStatus("idle");
      setIsPersonaSelectorOpen(true);
    } finally {
      setIsBusy(false);
    }
  }, [showToast]);

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
      mediaRecorderRef.current?.stop();
      recordStreamRef.current?.getTracks().forEach((track) => track.stop());
    };
  }, []);

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

        <div className="w-12" />
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
            <button
              onClick={() => setIsVoiceOutputEnabled((prev) => !prev)}
              className={`w-9 h-9 rounded-full flex items-center justify-center transition-colors ${
                isVoiceOutputEnabled ? "bg-blue-500 text-white" : "bg-zinc-800 text-zinc-400"
              }`}
              title="음성 출력 토글"
            >
              {isVoiceOutputEnabled ? <Mic size={16} /> : <MicOff size={16} />}
            </button>

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
                <RecordingButton status={status} onStart={() => void startRecording()} onEnd={stopRecordingAndSend} disabled={!activeRoomId || isBusy} />
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
