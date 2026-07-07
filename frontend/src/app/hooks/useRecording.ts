import { useCallback, useRef } from "react";
import { MIN_STT_RECORDING_DURATION_MS, MIN_STT_RECORDING_BYTES } from "../constants";
import { transcribeAudio } from "../services/api";
import { AppStatus } from "../types";

function chooseRecordingMimeType() {
  const candidates = ["audio/webm", "audio/mp4", "audio/wav"];
  const mimeType = candidates.find((type) => MediaRecorder.isTypeSupported(type));
  return mimeType || "audio/webm";
}

interface UseRecordingOptions {
  onComplete: (transcription: string) => Promise<void>;
  onError: (error: Error) => void;
  onStatusChange: (status: AppStatus) => void;
  isBusy: boolean;
  status: AppStatus;
}

export function useRecording({ onComplete, onError, onStatusChange, isBusy, status }: UseRecordingOptions) {
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const recordStreamRef = useRef<MediaStream | null>(null);
  const recordedChunksRef = useRef<BlobPart[]>([]);
  const recordingStartedAtRef = useRef(0);
  const recordingStartPendingRef = useRef(false);
  const stopAfterRecordingStartRef = useRef(false);

  const startRecording = useCallback(async () => {
    if (isBusy || status !== "idle") return;

    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
      onError(new Error("마이크 기능은 HTTPS 환경의 지원 브라우저에서 사용할 수 있습니다."));
      return;
    }

    try {
      recordingStartPendingRef.current = true;
      stopAfterRecordingStartRef.current = false;
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mimeType = chooseRecordingMimeType();
      const mediaRecorder = new MediaRecorder(stream, { mimeType });

      recordedChunksRef.current = [];
      recordStreamRef.current = stream;
      mediaRecorderRef.current = mediaRecorder;
      recordingStartedAtRef.current = Date.now();

      mediaRecorder.addEventListener("dataavailable", (event) => {
        if (event.data.size > 0) {
          recordedChunksRef.current.push(event.data);
        }
      });

      mediaRecorder.addEventListener("stop", async () => {
        try {
          onStatusChange("processing");

          const blob = new Blob(recordedChunksRef.current, { type: mimeType });
          const recordingDuration = Date.now() - recordingStartedAtRef.current;

          if (recordingDuration < MIN_STT_RECORDING_DURATION_MS || blob.size < MIN_STT_RECORDING_BYTES) {
            onError(new Error("음성이 너무 짧습니다. 조금 더 길게 말한 뒤 전송해 주세요."));
            onStatusChange("idle");
            return;
          }

          const transcription = await transcribeAudio(blob, mimeType);
          await onComplete(transcription);
        } catch (error) {
          onError(error instanceof Error ? error : new Error(String(error)));
          onStatusChange("idle");
        } finally {
          recordStreamRef.current?.getTracks().forEach((track) => track.stop());
          mediaRecorderRef.current = null;
          recordStreamRef.current = null;
          recordedChunksRef.current = [];
          recordingStartedAtRef.current = 0;
        }
      });

      mediaRecorder.start();
      onStatusChange("listening");

      if (stopAfterRecordingStartRef.current) {
        mediaRecorder.stop();
      }
    } catch (error) {
      onError(new Error("마이크 접근 권한이 필요합니다."));
      onStatusChange("idle");
    } finally {
      recordingStartPendingRef.current = false;
    }
  }, [isBusy, status, onComplete, onError, onStatusChange]);

  const stopRecording = useCallback(() => {
    const recorder = mediaRecorderRef.current;
    if (!recorder) {
      if (recordingStartPendingRef.current) {
        stopAfterRecordingStartRef.current = true;
      }
      return;
    }

    if (recorder.state === "inactive") return;

    recorder.stop();
  }, []);

  const cleanupRecording = useCallback(() => {
    mediaRecorderRef.current?.stop();
    recordStreamRef.current?.getTracks().forEach((track) => track.stop());
  }, []);

  return {
    startRecording,
    stopRecording,
    cleanupRecording,
  };
}
