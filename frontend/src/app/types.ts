import { ChatRoom } from "./components/Sidebar";

export type AppStatus = "idle" | "listening" | "processing" | "speaking";
export type ToastKind = "info" | "error";

export interface Message {
  id: string;
  text: string;
  sender: "user" | "ai";
  timestamp: number;
}

export interface SessionResponse {
  sessionId: string;
  userId: string;
  personaId: string;
}

export interface ChatRoomState extends ChatRoom {
  sessionId: string;
  userId: string;
  createdAt: number;
}
