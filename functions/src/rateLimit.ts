import { createHash } from "node:crypto";
import { Firestore, Timestamp } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";

export interface Counter {
  minute: number;
  minuteCount: number;
  day: number;
  dayCount: number;
  lastAttempt: number;
}

export function nextCounter(previous: Partial<Counter> | undefined, now: number,
  minuteLimit: number, dayLimit: number, cooldownMs: number): Counter {
  const minute = Math.floor(now / 60_000);
  const day = Math.floor(now / 86_400_000);
  const minuteCount = previous?.minute === minute ? previous.minuteCount ?? 0 : 0;
  const dayCount = previous?.day === day ? previous.dayCount ?? 0 : 0;
  if ((previous?.lastAttempt !== undefined && now - previous.lastAttempt < cooldownMs) ||
      minuteCount >= minuteLimit || dayCount >= dayLimit) {
    throw new HttpsError("resource-exhausted", "Session request limit reached.");
  }
  return { minute, minuteCount: minuteCount + 1, day, dayCount: dayCount + 1, lastAttempt: now };
}

export async function reserveSessionAttempt(db: Firestore, uid: string): Promise<void> {
  const userKey = createHash("sha256").update(uid).digest("hex");
  const userRef = db.collection("_sessionLimits").doc(userKey);
  const globalRef = db.collection("_sessionLimits").doc("global");
  await db.runTransaction(async transaction => {
    const [user, global] = await transaction.getAll(userRef, globalRef);
    const now = Date.now();
    const nextUser = nextCounter(user.data(), now, 3, 20, 20_000);
    const nextGlobal = nextCounter(global.data(), now, 20, 200, 0);
    // Count attempts before contacting OpenAI, including failed requests. Fail closed.
    transaction.set(userRef, { ...nextUser, expiresAt: Timestamp.fromMillis(now + 172_800_000) });
    transaction.set(globalRef, nextGlobal);
  });
}
