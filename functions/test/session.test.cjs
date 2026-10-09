const { test } = require('node:test');
const assert = require('node:assert/strict');
const { mintClientSecret, validateRequest } = require('../lib/session');
const { nextCounter } = require('../lib/rateLimit');

const expectCode = (code) => (error) => error.code === code;

test('requires sign-in, correct attested app, unused token and empty data', () => {
  const auth = { uid: 'test-user' };
  const app = { appId: 'allowed' };
  assert.throws(() => validateRequest(undefined, app, 'allowed', {}), expectCode('unauthenticated'));
  assert.throws(() => validateRequest(auth, undefined, 'allowed', {}), expectCode('permission-denied'));
  assert.throws(() => validateRequest(auth, { appId: 'other' }, 'allowed', {}), expectCode('permission-denied'));
  assert.throws(() => validateRequest(auth, { ...app, alreadyConsumed: true }, 'allowed', {}), expectCode('permission-denied'));
  for (const data of [null, [], { model: 'arbitrary' }, 'text']) {
    assert.throws(() => validateRequest(auth, app, 'allowed', data), expectCode('invalid-argument'));
  }
  assert.equal(validateRequest(auth, app, 'allowed', {}), auth.uid);
});

test('uses official client_secrets schema and returns only temporary auth data', async () => {
  const result = await mintClientSecret('test-only-placeholder', 'gpt-realtime-2.1-mini', 'test-user', async (url, options) => {
    assert.equal(url, 'https://api.openai.com/v1/realtime/client_secrets');
    assert.equal(options.method, 'POST');
    assert.equal(options.headers.Authorization, 'Bearer test-only-placeholder');
    assert.notEqual(options.headers['OpenAI-Safety-Identifier'], 'test-user');
    const body = JSON.parse(options.body);
    assert.deepEqual(body.expires_after, { anchor: 'created_at', seconds: 60 });
    assert.equal(body.session.type, 'realtime');
    assert.equal(body.session.model, 'gpt-realtime-2.1-mini');
    return Response.json({ value: 'test-ephemeral', expires_at: 1060, session: { model: 'gpt-realtime-2.1-mini' }, ignored: 'private' });
  }, () => 1000);
  assert.deepEqual(result, { clientSecret: 'test-ephemeral', expiresAt: 1060, serverTime: 1000, model: 'gpt-realtime-2.1-mini' });
});

test('maps upstream HTTP errors without forwarding raw bodies', async () => {
  for (const [status, code] of [[400, 'failed-precondition'], [401, 'failed-precondition'],
    [403, 'failed-precondition'], [404, 'failed-precondition'], [429, 'resource-exhausted'], [500, 'unavailable']]) {
    await assert.rejects(mintClientSecret('test-only', 'gpt-realtime-2.1-mini', 'user', async () =>
      new Response('sensitive upstream body', { status })), error =>
      error.code === code && !error.message.includes('sensitive'));
  }
});

test('rejects missing server key, network errors, malformed and expired credentials', async () => {
  await assert.rejects(mintClientSecret('', 'model', 'user'), expectCode('failed-precondition'));
  await assert.rejects(mintClientSecret('test-only', 'model', 'user', async () => { throw new Error('network'); }), expectCode('unavailable'));
  const responses = [null, {}, { value: '', expires_at: 1060 }, { value: 'token', expires_at: 1004 },
    { value: 'token', expires_at: '1060' }];
  for (const response of responses) {
    await assert.rejects(mintClientSecret('test-only', 'model', 'user', async () => Response.json(response), () => 1000), expectCode('internal'));
  }
  await assert.rejects(mintClientSecret('test-only', 'model', 'user', async () => new Response('not-json')), expectCode('internal'));
});

test('limits attempts, resets time buckets, preserves cooldown across minute boundaries', () => {
  const first = nextCounter(undefined, 50_000, 3, 20, 20_000);
  assert.equal(first.dayCount, 1);
  assert.throws(() => nextCounter(first, 60_000, 3, 20, 20_000), expectCode('resource-exhausted'));
  const next = nextCounter(first, 70_000, 3, 20, 20_000);
  assert.equal(next.minuteCount, 1);
  assert.equal(next.dayCount, 2);
  assert.throws(() => nextCounter({ ...next, minuteCount: 3 }, 90_000, 3, 20, 0), expectCode('resource-exhausted'));
  assert.throws(() => nextCounter({ ...next, dayCount: 20 }, 120_000, 3, 20, 0), expectCode('resource-exhausted'));
  assert.equal(nextCounter({ ...next, dayCount: 20 }, 86_400_000, 3, 20, 0).dayCount, 1);
});

test('rate-limit errors include exact retry time and limiting period', () => {
  const first = nextCounter(undefined, 50_000, 3, 20, 20_000);
  assert.throws(() => nextCounter(first, 60_000, 3, 20, 20_000), error =>
    error.details.source === 'session-limit' && error.details.limitPeriod === 'interval' && error.details.retryAfterSeconds === 10);
  assert.throws(() => nextCounter({ ...first, minute: 1, minuteCount: 3 }, 90_000, 3, 20, 0), error =>
    error.details.limitPeriod === 'minute' && error.details.retryAfterSeconds === 30);
  assert.throws(() => nextCounter({ ...first, dayCount: 20 }, 120_000, 3, 20, 0), error =>
    error.details.limitPeriod === 'day' && error.details.retryAfterSeconds === 86280);
});
