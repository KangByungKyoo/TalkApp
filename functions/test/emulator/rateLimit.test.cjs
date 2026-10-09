const { test } = require('node:test');
const assert = require('node:assert/strict');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { reserveSessionAttempt } = require('../../lib/rateLimit');

test('Firestore transaction prevents concurrent duplicate issuance and global overflow', async () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Must run with Firestore emulator');
  const app = initializeApp({ projectId: 'demo-englishtalk' });
  const db = getFirestore(app);
  const globalRef = db.collection('_sessionLimits').doc('global');
  await globalRef.delete();
  const uid = `concurrent-${Date.now()}`;
  const results = await Promise.allSettled(Array.from({ length: 5 }, () => reserveSessionAttempt(db, uid)));
  assert.equal(results.filter(result => result.status === 'fulfilled').length, 1);
  assert.equal(results.filter(result => result.status === 'rejected' && result.reason.code === 'resource-exhausted').length, 4);
  const now = Date.now();
  await globalRef.set({ minute: Math.floor(now / 60000), minuteCount: 20,
    day: Math.floor(now / 86400000), dayCount: 20, lastAttempt: now });
  await assert.rejects(reserveSessionAttempt(db, `different-${now}`), error => error.code === 'resource-exhausted');
  const documentUrl = `http://${process.env.FIRESTORE_EMULATOR_HOST}/v1/projects/demo-englishtalk/databases/(default)/documents/_sessionLimits/global`;
  assert.equal((await fetch(documentUrl)).status, 403, 'Client reads must be denied');
  assert.equal((await fetch(documentUrl, {
    method: 'PATCH', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ fields: { dayCount: { integerValue: '0' } } })
  })).status, 403, 'Client must not reset rate limits');
});
