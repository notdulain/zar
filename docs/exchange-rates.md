# Exchange rates for foreign-currency income and expenses

Issue [#46](https://github.com/notdulain/zar/issues/46). Income and expenses on an account that is not in LKR carry a rate: how many LKR one unit of the account's currency is worth. The rate is saved with the record and never changed afterwards.

## What the person sees

On the Income form and on the Expense form, when the account is not in LKR, the rate row (`1 USD = ? LKR`) has a **Use today's rate** link.

| State | What happens |
|---|---|
| Empty, today's date | The link is shown. The hint says the rate can be filled in, typed, or left empty (then the record shows "No LKR rate"). |
| Tap the link | "Getting today's rate…" while the app asks the provider. |
| Fetched | The field holds the provider's number to four decimals, for example `332.4449`. The hint says when the provider published it and that it can be changed. |
| Typed over | The number is the person's own. The hint says "You typed this rate. Today's rate is 332.4449." The link stays so today's rate can be filled in again. |
| Could not get it | "Couldn't get today's rate. Type one, or leave it empty." with **Try again**. Saving is never blocked. |
| Earlier date | The link is not offered, because today's rate could be wrong for that day. The person types the rate for that day, or leaves it empty. Moving the date away from today also takes a fetched rate out of the field; a typed number stays. |
| Editing a saved record | The saved rate is shown. If it came from the provider, the hint says "Saved from today's rate, updated …". |

Wherever the provider's rate is shown, the form shows the link **Rates By Exchange Rate API** (opens exchangerate-api.com). The provider's terms require it.

Designs: Figma file Zar UI, page Screens, section 29 (Z198 to Z210).

## What is saved

The record's `ReportingConversion` is `Converted` with an `ExchangeRate` (`base`, `quote` LKR, `rate`, `asOf`, `source`):

- A fetched rate that was not changed: `source = PROVIDER`, `asOf` = the time the provider published it.
- A typed rate, or a fetched rate the person changed: `source = MANUAL`, `asOf` = the time of saving.
- No rate: `Missing`, as before. The record is left out of the totals and counted as "no LKR rate".

A corrected amount keeps the stored rate. Changing the rate in a correction replaces the conversion.

## Where the rate comes from

ExchangeRate-API's open endpoint, `GET https://open.er-api.com/v6/latest/<currency>`, called directly from the app. It needs no key, so there is no backend and no key to protect. Nothing about the person or their records is sent, only the currency code.

- The provider publishes once a day. The app keeps the answer until the response's `time_next_update_unix` (never longer than 24 hours, and one hour when the provider gives no usable time), so a phone asks about once a day.
- The provider limits requests per address. After a "too many requests" answer (HTTP 429) the app sends nothing for 20 minutes.
- An answer is only used if it is a success for the right currency, the LKR rate is above zero and below one billion, it is not published in the future, and the body is under 256 KB. Anything else counts as "could not get it".
- No redirects are followed and a call times out after 10 seconds.
- Only fiat currencies (not LKR) are offered. A crypto asset has no rate from this provider and keeps the typed rate.

Code: `features/rates` (`domain`: `RateQuote`, `RateEntry`, `RateText`, `FetchTodaysRateUseCase`; `data`: `ExchangeRateApiRepository`, `SharedPreferencesRateCache`). The two forms share the row `RateRow`.

## Typing a rate

`RateText.parse` reads what is typed:

| Typed | Read as |
|---|---|
| `300.5`, `300` | 300.5, 300 |
| `300,5`, `300,50` | 300.5, 300.50 (a comma before one or two digits is a decimal separator) |
| `1,100`, `1,234,567` | 1100, 1234567 (digits grouped in threes are thousands) |
| `1,100.50` | 1100.50 (a comma next to a dot is a thousands separator) |
| `0`, `-5`, `abc`, `1,2,3`, `12,3456` | rejected: "Enter a valid rate above zero, or leave it empty" |

## Not covered

- The rate of an earlier date. A past income or expense needs a typed rate.
- Adding a rate to an expense that was saved without one. The expense form can now take a rate when it is created; a saved expense still cannot be corrected to add one.
- Any plausibility check on a typed rate. A rate of 3 or 0.0033 for USD is accepted.
- Recalculating old records. They keep the rate they were saved with.

## Tests

Unit tests: `RateTextTest`, `RateEntryTest`, `TodaysRateTest`, `ExchangeRateApiRepositoryTest` (against a mock server), `SharedPreferencesRateCacheTest`, and the income and expense rules and view-model tests. Evidence from a real run on the emulator: [docs/evidence/issue-46](evidence/issue-46/README.md).
