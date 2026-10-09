import { createHash } from "node:crypto";
import { HttpsError } from "firebase-functions/v2/https";
import { warn } from "firebase-functions/logger";

export interface SessionResult {
  clientSecret: string;
  expiresAt: number;
  serverTime: number;
  model: string;
}

export function validateRequest(auth: { uid: string } | undefined,
  app: { appId: string; alreadyConsumed?: boolean } | undefined,
  expectedAppId: string, data: unknown): string {
  if (!auth?.uid) throw new HttpsError("unauthenticated", "Sign-in required.");
  if (!app || app.alreadyConsumed || app.appId !== expectedAppId) {
    throw new HttpsError("permission-denied", "Valid application attestation required.");
  }
  if (!data || typeof data !== "object" || Array.isArray(data) || Object.keys(data).length !== 0) {
    throw new HttpsError("invalid-argument", "No client configuration is accepted.");
  }
  return auth.uid;
}

export async function mintClientSecret(apiKey: string, model: string, uid: string,
  fetcher: typeof fetch = fetch, nowSeconds: () => number = () => Math.floor(Date.now() / 1000)
): Promise<SessionResult> {
  if (!apiKey.trim()) throw new HttpsError("failed-precondition", "Server credential is not configured.");
  let response: Response;
  try {
    response = await fetcher("https://api.openai.com/v1/realtime/client_secrets", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiKey}`,
        "Content-Type": "application/json",
        "OpenAI-Safety-Identifier": createHash("sha256").update(uid).digest("hex")
      },
      body: JSON.stringify({
        expires_after: { anchor: "created_at", seconds: 60 },
        session: {
          type: "realtime", model,
          instructions: "You are Emma, a friendly English conversation teacher. Use clear English, ask one short question at a time, and gently help the learner.",
          audio: { output: { voice: "marin" } }
        }
      }),
      signal: AbortSignal.timeout(15_000)
    });
  } catch {
    throw new HttpsError("unavailable", "OpenAI request failed or timed out.");
  }
  // Never forward upstream bodies/headers: they can contain sensitive details.
  if (!response.ok) {
    warn("OpenAI Realtime request rejected", { upstreamStatus: response.status });
  }
  if (response.status === 429) throw new HttpsError("resource-exhausted", "Upstream request limit reached.");
  if ([400, 401, 403, 404].includes(response.status)) {
    throw new HttpsError("failed-precondition", "Check server API credentials and model availability.");
  }
  if (!response.ok) throw new HttpsError("unavailable", "OpenAI service unavailable.");
  let body: unknown;
  try { body = await response.json(); }
  catch { throw new HttpsError("internal", "Invalid upstream response."); }
  const result = body as { value?: unknown; expires_at?: unknown; session?: { model?: unknown } } | null;
  const serverTime = nowSeconds();
  if (!result || typeof result.value !== "string" || !result.value.trim() ||
      typeof result.expires_at !== "number" || !Number.isSafeInteger(result.expires_at) ||
      result.expires_at <= serverTime + 5) {
    throw new HttpsError("internal", "Invalid or expired upstream credential.");
  }
  return { clientSecret: result.value, expiresAt: result.expires_at, serverTime, model };
}
