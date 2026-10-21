import { DraftError, Reason } from './errors.js';
import {
  DIRECTIONS, DRAFT_FIELDS, PROPOSAL_KINDS, SPENDING_CLASSES, TRANSACTION_TYPES,
} from './prompt.js';

export const MAX_PROPOSALS = 5;

const bad = () => new DraftError(Reason.BAD_RESPONSE);
const isPlainObject = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);

// Free text is shortened; identifiers, money and dates that are too long are treated as missing.
const FREE_TEXT = { category: 40, merchant: 80, title: 80, note: 300 };
const BOUNDED = { amount: 40, receivedAmount: 40, currency: 10, receivedCurrency: 10, accountId: 128, toAccountId: 128, date: 10, dueDate: 10 };

function nullableText(value, limit, truncate) {
  if (value === undefined || value === null) return null;
  if (typeof value !== 'string') throw bad();
  const text = value.trim();
  if (text.length === 0) return null;
  if (text.length <= limit) return text;
  return truncate ? text.slice(0, limit) : null;
}

function nullableEnum(value, allowed) {
  if (value === undefined || value === null) return null;
  if (typeof value !== 'string' || !allowed.includes(value)) throw bad();
  return value;
}

/**
 * Parses the provider's JSON text into a bounded, known-fields-only list. The model's schema is a hint, not a
 * guarantee, so shape, kinds, enums and sizes are all re-checked. Anything unexpected fails the whole response.
 */
export function parseProposals(text) {
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw bad();
  }
  if (!isPlainObject(parsed) || !Array.isArray(parsed.proposals)) throw bad();
  if (parsed.proposals.length > MAX_PROPOSALS) throw bad();
  return parsed.proposals.map(parseProposal);
}

function parseProposal(raw) {
  if (!isPlainObject(raw)) throw bad();
  if (typeof raw.kind !== 'string' || !PROPOSAL_KINDS.includes(raw.kind)) throw bad();

  const uncertain = raw.uncertainFields ?? [];
  if (!Array.isArray(uncertain) || uncertain.length > DRAFT_FIELDS.length) throw bad();
  if (uncertain.some((f) => typeof f !== 'string' || !DRAFT_FIELDS.includes(f))) throw bad();

  const proposal = {
    kind: raw.kind,
    transactionType: nullableEnum(raw.transactionType, TRANSACTION_TYPES),
    direction: nullableEnum(raw.direction, DIRECTIONS),
    spendingClass: nullableEnum(raw.spendingClass, SPENDING_CLASSES),
    uncertainFields: [...new Set(uncertain)],
  };
  for (const [field, limit] of Object.entries(BOUNDED)) proposal[field] = nullableText(raw[field], limit, false);
  for (const [field, limit] of Object.entries(FREE_TEXT)) proposal[field] = nullableText(raw[field], limit, true);
  return proposal;
}
