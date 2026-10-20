# Bank SMS compatibility (L01)

Which Sri Lankan bank SMS formats Zar can read, based on real messages collected in October 2026. Only formats listed as **Supported** were checked against samples. Every other bank is unknown and must not be advertised.

The parser is `features/capture/domain/parser/BankSmsParser.kt`. The anonymized samples and expected results are in `BankSmsParserTest.kt`. Run `./gradlew testDebugUnitTest` to check them.

## Summary

| Bank | Message type | Samples | Amount | Currency | Date | Time | Card/account | Status |
|---|---|---|---|---|---|---|---|---|
| Commercial Bank | Card purchase | 9 | ✅ | ✅ | ✅ | ✅ | Card last 4 | **Supported** |
| Commercial Bank | ATM withdrawal | 3 | ✅ | ✅ | ✅ | ✅ | Card last 4 | **Supported** |
| Commercial Bank | Online fund transfer | 4 | ✅ | ✅ | ✅ | ⚠️ no AM/PM | None | **Needs review** |
| Commercial Bank | Money received | 0 | | | | | | Unknown |
| Sampath Bank | Debits (POS, ATM, eCom, transfers) | 25 | ✅ | ✅ | ✅ | ✅ | Account last 4 | **Supported** |
| Sampath Bank | Credits and reversals | 5 | ✅ | ✅ | ✅ | ✅ | Account last 4 | **Supported** (type set by user) |
| DFCC Bank | Debits and fees | 6 | ✅ | ✅ | ✅ | ❌ none | Account last 4 | **Needs review** (no time) |
| DFCC Bank | Money received | 0 | | | | | | Unknown |
| BOC, People's, HNB, NSB, NDB, Seylan, Nations Trust, others | Any | 0 | | | | | | Unknown |

**Supported** means every sample extracted correctly. **Needs review** means the message parses but a field is missing or unclear, so the user confirms it. **Unknown** means no samples, so these messages go to manual review.

## Formats (anonymized examples)

**Commercial Bank: card purchase**
> Dear Cardholder, Purchase at CAFE SEVENTY THREE MALABE LK for LKR 800.00 on 11/10/26 10:49 AM has been authorised on your debit card ending #1111.

**Commercial Bank: ATM withdrawal**
> Withdrawal at MAIN STREET COLOMBO 11 LK for LKR 6,000.00 on 14/10/26 01:34 PM from card ending #1111.

**Commercial Bank: online transfer**
> Fund transfer request WT10000001 P 2026-10-06 at 09:59:34 via Combank Online to JOHN DOE for LKR 200.00 Inquiries+94112353596.

**Sampath Bank: debit or credit** (the bank is identified by the inquiry number 0112303050)
> LKR 650.00 debited from AC \*\*3333 via POS at GOLDEN MEAL RESTAURANT 80500323
> 09/10/2026 21:15:47
> To Inq Call 0112303050
> Get protected -Do not Share OTP

The word after the account shows the channel: `via POS at` is a card purchase, `via ATM at` an ATM withdrawal, `for eCom` an online purchase, `for eCom/REV` a reversal, and `for <note>` a transfer with the sender's note.

**DFCC Bank: debit** (the bank is identified by the inquiry number 0112350035)
> Your A/C No: \*\*\*\*\*\*\*\*4444 is debited with LKR2,643.69 on 09 OCT 2026 ref: Netflix.com Los Gatos. Your bal is LKR10,000.00. If unauthorized call 0112350035

## Findings

1. **Declined payments look like transactions.** Commercial Bank sends "your card was declined… amount was LKR 599.00". No money moved, so the parser ignores these.
2. **OTP messages contain amounts.** For example, "Your OTP at Merchant 'LMT GO' for LKR 500.00 is …". The parser ignores them. Sampath's footer "Do not Share OTP" is not treated as an OTP.
3. **Reversals are not income.** Sampath sends `for eCom/REV` credits when an online payment is reversed. In one Uber case, 4 debits and 3 reversals of LKR 336.66 arrived within 5 minutes. These must be linked to the original purchase and never counted as Received.
4. **Credits are not automatically income.** Sampath credits carry only the sender's note ("Food", "wifi"). The money might be income, a friend paying back, or a transfer, so the user picks the type. This matches the rule that unexplained credits stay out of Received.
5. **Commercial Bank transfer times are 12-hour without AM/PM.** A message received at 21:59 said `09:59:34`, so only the date is used. The wording is "Fund transfer request", so the user confirms it went through.
6. **DFCC has no time** and sends card fees as separate messages (`ref: POS Transaction Fee`). The parser marks these as bank fees.
7. **Merchant names are raw.** They include country codes, terminal IDs and typos (`COLK`). Merchant cleanup is future work, and the user can edit the name in review.
8. **No credit alerts were seen** from Commercial Bank or DFCC, so they are unknown, not supported.
9. **Foreign purchases show in LKR.** The only foreign sample (a declined Google One charge) was in LKR. No USD-amount message has been seen yet.

## Capture routes (to test on an Android phone with Dulain)

All samples so far came from iPhones, and iOS doesn't let apps read SMS. Fill this table on a real Android device:

| Route | Permission | User steps | Works when app closed? | Result |
|---|---|---|---|---|
| Share SMS to Zar (`ACTION_SEND`, text/plain) | None | Long-press message → Share → Zar | Not needed | Not tested yet |
| Read incoming SMS (`RECEIVE_SMS`) | Runtime SMS permission | Allow once | Yes | Not tested yet. Google Play restricts SMS permissions, so treat it as a demo only |
| Notification listener | Special notification access | Enable in system settings | Yes | Not tested yet |

Recommendation: make **share to Zar** the main route, because it needs no sensitive permission. Raw message text stays on the device and is never uploaded (see `docs/domain-contracts.md`).

## Privacy

Samples were anonymized before being committed: card and account digits, people's names, transfer references, OTP codes and balances were replaced. Amounts, dates, merchants and bank inquiry numbers were kept because the parser depends on them. Never commit an unedited bank message.

## Next steps

- Collect samples from BOC, People's Bank, HNB and NSB, plus credit messages from Commercial Bank and DFCC.
- Run the capture-route tests on an Android phone.
- Re-run the tests after adding each new sample.
