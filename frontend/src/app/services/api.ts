import { getMiyouUserId } from "../utils/userIdentity";
import { API_BASE_URL, API_PREFIX } from "../constants";
import { SessionResponse } from "../types";

export function buildApiUrl(path: string): string {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  return `${API_BASE_URL}${API_PREFIX}${normalizedPath}`;
}

export function responseError(prefix: string, response: Response): Error {
  if (response.status === 402) {
    return new Error("크레딧이 부족합니다. 크레딧을 충전하세요.");
  }
  return new Error(`${prefix} (${response.status})`);
}

export async function createSession(personaId: string): Promise<SessionResponse> {
  const userId = await getMiyouUserId();
  const response = await fetch(buildApiUrl("/rag/dialogue/session"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      personaId,
      userId,
    }),
  });

  if (!response.ok) {
    throw new Error(`세션 생성 실패 (${response.status})`);
  }

  const session: SessionResponse = await response.json();
  return session;
}

export async function transcribeAudio(blob: Blob, mimeType: string): Promise<string> {
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

export async function streamText(
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
    throw responseError("텍스트 응답 실패", response);
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

export async function streamAudioAndPlay(
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
            throw responseError("음성 응답 실패", response);
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
