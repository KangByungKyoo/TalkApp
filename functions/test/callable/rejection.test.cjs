const { test } = require('node:test');
const assert = require('node:assert/strict');

test('callable HTTP endpoint rejects unattested unauthenticated requests', async () => {
  const response = await fetch('http://127.0.0.1:5001/demo-englishtalk/asia-northeast3/createRealtimeSession', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ data: {} })
  });
  assert.equal(response.status, 401);
  const body = await response.json();
  assert.equal(body.error.status, 'UNAUTHENTICATED');
  assert.equal(body.result, undefined);
});

test('Firebase ID token alone cannot bypass App Check', async () => {
  const signup = await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=test-only', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ returnSecureToken: true })
  });
  assert.equal(signup.status, 200);
  const { idToken } = await signup.json();
  assert.ok(idToken);
  const response = await fetch('http://127.0.0.1:5001/demo-englishtalk/asia-northeast3/createRealtimeSession', {
    method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${idToken}` },
    body: JSON.stringify({ data: {} })
  });
  assert.equal(response.status, 401);
  const body = await response.json();
  assert.equal(body.error.status, 'UNAUTHENTICATED');
});
