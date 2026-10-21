import { DraftError, Reason } from './errors.js';

export const MAX_NOTE_LENGTH = 500;
export const MAX_ACCOUNTS = 30;
const ACCOUNT_TYPES = ['BANK', 'CASH', 'FREELANCE', 'CRYPTO'];
const REQUEST_KEYS = ['note', 'today', 'accounts'];
const ACCOUNT_KEYS = ['id', 'name', 'type', 'currency'];
const ID_PATTERN = /^[A-Za-z0-9_.:-]{1,128}$/;
const CURRENCY_PATTERN = /^[A-Z0-9]{2,10}$/;
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

const invalid = () => new DraftError(Reason.INVALID_INPUT);
const isPlainObject = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);

export function isCalendarDate(text) {
  if (typeof text !== 'string' || !DATE_PATTERN.test(text)) return false;
  const [y, m, d] = text.split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  return date.getUTCFullYear() === y && date.getUTCMonth() === m - 1 && date.getUTCDate() === d;
}

/**
 * Strictly validates the callable payload and returns only what the provider needs.
 * Unknown keys (for example balances or raw messages) are rejected rather than forwarded.
 */
export function validateRequest(data) {
  if (!isPlainObject(data)) throw invalid();
  if (Object.keys(data).some((k) => !REQUEST_KEYS.includes(k))) throw invalid();

  const { note, today, accounts } = data;
  if (typeof note !== 'string' || note.trim().length === 0 || note.length > MAX_NOTE_LENGTH) throw invalid();
  if (!isCalendarDate(today)) throw invalid();
  if (!Array.isArray(accounts) || accounts.length > MAX_ACCOUNTS) throw invalid();

  const seen = new Set();
  const cleanAccounts = accounts.map((account) => {
    if (!isPlainObject(account)) throw invalid();
    if (Object.keys(account).some((k) => !ACCOUNT_KEYS.includes(k))) throw invalid();
    const { id, name, type, currency } = account;
    if (typeof id !== 'string' || !ID_PATTERN.test(id) || seen.has(id)) throw invalid();
    if (typeof name !== 'string' || name.trim().length === 0 || name.length > 80) throw invalid();
    if (!ACCOUNT_TYPES.includes(type)) throw invalid();
    if (typeof currency !== 'string' || !CURRENCY_PATTERN.test(currency)) throw invalid();
    seen.add(id);
    return { id, name: name.trim(), type, currency };
  });

  return { note: note.trim(), today, accounts: cleanAccounts };
}
