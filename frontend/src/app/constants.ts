export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/$/, "");
// 백엔드의 spring.webflux.base-path와 맞춰야 한다 (backend/bootstrap/src/main/resources/application.yml).
export const API_PREFIX = "/api/v1";
export const CONVERSATION_CREDIT_COST = 100;
export const MIN_STT_RECORDING_DURATION_MS = 700;
export const MIN_STT_RECORDING_BYTES = 1024;
