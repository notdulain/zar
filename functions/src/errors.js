// Safe failure reasons shared by the callable and the Android client. Messages never carry user or provider text.
export const Reason = Object.freeze({
  UNAUTHENTICATED: 'UNAUTHENTICATED',
  APP_CHECK: 'APP_CHECK',
  INVALID_INPUT: 'INVALID_INPUT',
  RATE_LIMITED: 'RATE_LIMITED',
  TIMEOUT: 'TIMEOUT',
  PROVIDER_UNAVAILABLE: 'PROVIDER_UNAVAILABLE',
  BAD_RESPONSE: 'BAD_RESPONSE',
});

/** A failure whose [reason] is safe to log and return. Carries no note, prompt or provider text. */
export class DraftError extends Error {
  constructor(reason, cause) {
    super(reason);
    this.name = 'DraftError';
    this.reason = reason;
    // Only the provider status/type is kept; never the provider message, which may echo the request.
    this.cause = cause;
  }
}

export const HTTPS_CODE = Object.freeze({
  [Reason.UNAUTHENTICATED]: 'unauthenticated',
  [Reason.APP_CHECK]: 'failed-precondition',
  [Reason.INVALID_INPUT]: 'invalid-argument',
  [Reason.RATE_LIMITED]: 'resource-exhausted',
  [Reason.TIMEOUT]: 'deadline-exceeded',
  [Reason.PROVIDER_UNAVAILABLE]: 'unavailable',
  [Reason.BAD_RESPONSE]: 'internal',
});

export const SAFE_MESSAGE = Object.freeze({
  [Reason.UNAUTHENTICATED]: 'Sign in to use quick notes.',
  [Reason.APP_CHECK]: 'This app could not be verified.',
  [Reason.INVALID_INPUT]: 'The note or account details are not valid.',
  [Reason.RATE_LIMITED]: 'Too many requests. Try again shortly.',
  [Reason.TIMEOUT]: 'The request took too long.',
  [Reason.PROVIDER_UNAVAILABLE]: 'Quick notes are unavailable right now.',
  [Reason.BAD_RESPONSE]: 'The note could not be interpreted.',
});
