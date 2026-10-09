import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { defineSecret, defineString } from "firebase-functions/params";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { reserveSessionAttempt } from "./rateLimit";
import { mintClientSecret, validateRequest } from "./session";

initializeApp();
const openaiApiKey = defineSecret("OPENAI_API_KEY");
const androidAppId = defineString("ALLOWED_ANDROID_APP_ID");
const realtimeModel = defineString("OPENAI_REALTIME_MODEL", { default: "gpt-realtime" });

export const createRealtimeSession = onCall({
  region: "asia-northeast3",
  secrets: [openaiApiKey],
  enforceAppCheck: true,
  consumeAppCheckToken: true,
  timeoutSeconds: 30,
  memory: "256MiB",
  minInstances: 0,
  maxInstances: 3,
  concurrency: 10
}, async request => {
  request.rawRequest.res?.set("Cache-Control", "no-store");
  const uid = validateRequest(request.auth, request.app, androidAppId.value(), request.data);
  try {
    await reserveSessionAttempt(getFirestore(), uid);
    return await mintClientSecret(openaiApiKey.value(), realtimeModel.value(), uid);
  } catch (error) {
    if (error instanceof HttpsError) throw error;
    // Do not log request data, credentials, or raw upstream errors.
    throw new HttpsError("unavailable", "Session service unavailable.");
  }
});
