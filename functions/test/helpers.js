export class FakeHttpsError extends Error {
  constructor(code, message, details) {
    super(message);
    this.code = code;
    this.details = details;
  }
}

export const NOTE_SENTINEL = 'SECRET-NOTE-SENTINEL-4471';

export const validData = (overrides = {}) => ({
  note: `lunch 850 at keells, card ${NOTE_SENTINEL}`,
  today: '2026-10-21',
  accounts: [
    { id: 'boc', name: 'BOC Savings', type: 'BANK', currency: 'LKR' },
    { id: 'cash', name: 'Cash wallet', type: 'CASH', currency: 'LKR' },
  ],
  ...overrides,
});

export const authedRequest = (data = validData(), extra = {}) => ({
  data, auth: { uid: 'user-1', token: {} }, app: { appId: 'app-1' }, ...extra,
});

export function memoryLogger() {
  const entries = [];
  return {
    entries,
    info: (message, fields) => entries.push({ level: 'info', message, fields }),
    warn: (message, fields) => entries.push({ level: 'warn', message, fields }),
  };
}

export function fakeProvider(result) {
  const calls = [];
  return {
    calls,
    async generate(input) {
      calls.push(input);
      if (result instanceof Error) throw result;
      return typeof result === 'function' ? result(input) : result;
    },
  };
}

export const answer = (obj) => ({ text: JSON.stringify(obj), usage: { promptTokens: 900, outputTokens: 150 } });

/** A fetch stub that records the call and answers with a Gemini-shaped body. */
export function fakeFetch({ status = 200, body, throws } = {}) {
  const calls = [];
  const impl = async (url, init) => {
    calls.push({ url, init });
    if (throws) throw throws;
    return { ok: status >= 200 && status < 300, status, json: async () => body };
  };
  impl.calls = calls;
  return impl;
}

export const geminiBody = (text, extra = {}) => ({
  candidates: [{ content: { parts: [{ text }] }, finishReason: 'STOP' }],
  usageMetadata: { promptTokenCount: 887, candidatesTokenCount: 163 },
  ...extra,
});
