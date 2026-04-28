const USER_ID_STORAGE_KEY = "miyou-user-id";
const USER_ID_PREFIX = "miyou-device-v1";
const USER_ID_SALT = "miyou.ai-voice-chat.user.v1";

export async function getMiyouUserId(): Promise<string> {
  const fingerprint = collectDeviceFingerprint();
  const digest = await sha256Hex(`${USER_ID_SALT}|${fingerprint}`);
  const userId = `${USER_ID_PREFIX}-${digest.slice(0, 32)}`;

  try {
    localStorage.setItem(USER_ID_STORAGE_KEY, userId);
  } catch {
    // Storage can be blocked in private or restricted browser contexts.
  }

  return userId;
}

function collectDeviceFingerprint(): string {
  const nav = window.navigator;
  const screenInfo = window.screen;
  const languages = nav.languages?.length ? nav.languages : [nav.language];
  const normalizedLanguages = Array.from(
    new Set(languages.map((language) => language.toLowerCase()).filter(Boolean)),
  ).sort();
  const screenDimensions = [screenInfo.width || 0, screenInfo.height || 0].sort((a, b) => a - b);

  return JSON.stringify({
    version: 1,
    platform: normalizePlatform(nav.platform, nav.userAgent),
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || "",
    languages: normalizedLanguages.join(","),
    hardwareConcurrency: nav.hardwareConcurrency || 0,
    maxTouchPoints: nav.maxTouchPoints || 0,
    screen: {
      size: `${screenDimensions[0]}x${screenDimensions[1]}`,
      colorDepth: screenInfo.colorDepth || 0,
      pixelDepth: screenInfo.pixelDepth || 0,
    },
  });
}

function normalizePlatform(platform: string, userAgent: string): string {
  const source = `${platform} ${userAgent}`.toLowerCase();
  if (source.includes("android")) return "android";
  if (source.includes("iphone") || source.includes("ipad") || source.includes("ipod")) return "ios";
  if (source.includes("win")) return "windows";
  if (source.includes("mac")) return "macos";
  if (source.includes("linux")) return "linux";
  return "unknown";
}

async function sha256Hex(value: string): Promise<string> {
  if (window.crypto?.subtle) {
    const encoded = new TextEncoder().encode(value);
    const digest = await window.crypto.subtle.digest("SHA-256", encoded);
    return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
  }

  return fallbackHashHex(value);
}

function fallbackHashHex(value: string): string {
  let hash = 0x811c9dc5;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193);
  }
  return (hash >>> 0).toString(16).padStart(8, "0").repeat(8);
}
