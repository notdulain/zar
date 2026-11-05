# Kavindu live Firebase demo evidence

Captured on 5 November 2026 from the running Zar debug APK on the separate `Kavindu_Demo` Android emulator (Pixel 6a profile, API 37.1), signed into the synthetic Kavindu Silva account on live Firebase project `zar-app-35602`. No Firebase emulators were used for these images. The account was populated by the explicit `KavinduDemoSeed` instrumentation fixture; its representative records were read back from the live Firestore server before the fixture passed. The app itself loaded the records after a fresh email/password sign-in. Notification permission was enabled on this demo emulator before the Freelance screenshot.

| Image | What it demonstrates |
| --- | --- |
| [Home, October](home-october.png) | LKR 190,037 received from salary, freelance, and converted AdSense; LKR 94,332 spent, split into committed, discretionary, and unknown. One USD receipt has no LKR rate, so the total is explicitly incomplete. |
| [Plans](plans-macbook.png) | MacBook Pro M4 backed by LKR 11,200 of LKR 490,000, plus weekly allowance, three bills, and two freelance projects. |
| [Crypto](crypto-profit-loss.png) | Two realized sales with net LKR 5,000 gain: Q1 ETH gain of LKR 55,000 and Q3 BNB loss of LKR 50,000; 200 USDT remain. Transfers are not counted as income. |
| [Freelance](freelance-milestones.png) | LKR 105,000 clients owe, including LKR 45,000 overdue; one LKR 45,000 milestone is matched to a received payment. |
| [Bills](bills-review.png) | Rent, gym, and Dialog Fibre schedules. The existing October expenses have not been linked to bill instances, so the screen truthfully asks for review; Netflix is suggested as a subscription. |

The current month at capture time was November 2026. Its Home received figure is correctly zero because the 25 November salary has not happened. Select October 2026 in Home for the full scenario walkthrough. These are real app screenshots using synthetic financial data, not Figma images or generated mockups. The sign-in password is stored only in a local ignored file and is not in this directory.
