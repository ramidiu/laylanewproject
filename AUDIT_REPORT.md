# Layla Money Transfer — Code Audit Report

**Date:** 2026-06-10
**Scope:** `laylamoneytransfernew/` (Angular/Ionic frontend, 211 TS files) + `layla-backend/` (Spring Boot, package `com.remitz.*`, 503 Java files)
**Method:** Four parallel automated audits — frontend memory leaks, backend resource/concurrency leaks, frontend correctness bugs, backend security/correctness bugs. Every finding cites a real `file:line`; suspected-but-unconfirmed items were dropped.

## Summary

| Area | HIGH | MEDIUM | LOW |
|---|---|---|---|
| Backend security / correctness | **7** | 4 | 1 |
| Frontend correctness (money/payment) | **3** | 4 | 4 |
| Frontend memory leaks | **4** (one root cause) | 2 | 2 |
| Backend resource / memory leaks | 0 | 3 | 3 |
| **Total** | **14** | **13** | **10** |

> ⚠️ **The backend security findings are the urgent ones.** Two are exploitable by a completely unauthenticated attacker over the internet: minting wallet money (H1) and downloading any customer's passport/ID (H2). These should be treated as production incidents and fixed before anything else.

> ✅ **Good news from the audit:** money math correctly uses `BigDecimal` with explicit `RoundingMode` throughout (no `double`/`float` on currency). File/stream handling (KYC, PDF, email, sanctions ingest) uses try-with-resources correctly. The Volume payment webhook is signature-verified and idempotent. The previously-flagged hardcoded Brevo `xkeysib` key is **no longer in source** (secrets are externalized).

---

## 🔴 CRITICAL — Backend security (fix first)

### H1 — Unauthenticated arbitrary wallet credit/debit (money minting)
- `layla-backend/.../config/SecurityConfig.java:98-99` marks `/api/wallet/credit` and `/api/wallet/debit` as `permitAll`.
- `layla-backend/.../modules/user/controller/WalletController.java:51-81` reads `userId` and `amount` from the request body with no auth and no ownership check.
- **Impact:** Anyone on the internet can `POST {userId, amount}` to credit any wallet (create money) or drain it.
- **Fix:** Remove lines 98-99 from SecurityConfig; require authentication; derive `userId` from the JWT principal, not the body; add `@PreAuthorize` (these are internal/admin ops).

### H2 — Public KYC document download (passport / ID leak, IDOR)
- `SecurityConfig.java:95` `permitAll` on `/api/users/*/kyc/documents/*/file`.
- `modules/user/controller/KycController.java:164-186` checks only that the doc belongs to the path `userId` — not that the caller *is* that user.
- **Impact:** `GET /api/users/{victimId}/kyc/documents/{docId}/file` returns any user's passport/ID image, unauthenticated. Sequential ids make enumeration trivial.
- **Fix:** Remove the permitAll; require auth; verify `doc.getUserId()` == authenticated principal (or admin authority).

### H3 — Unauthenticated read/update of any user profile
- `SecurityConfig.java:93` `permitAll` on `/api/users/*`; `modules/user/controller/UserController.java:26-48` has no authorization check.
- **Impact:** `GET/PUT /api/users/{uuid}` lets anyone read or modify any user's profile.
- **Fix:** Remove/narrow the permitAll; bind to the authenticated principal or admin permission.

### H4 — KYC endpoints lack ownership checks (IDOR)
- `modules/user/controller/KycController.java`: `GET /documents` (l.79), `POST /documents` (l.55), `DELETE /documents/{id}` (l.92), `POST /verify` (l.127), `GET /status` (l.139), `POST /screening` (l.151) all take `{userId}` from the path with no caller match. Only `reviewDocument` has `@PreAuthorize`.
- **Impact:** Any authenticated user can read/alter another user's KYC.
- **Fix:** Add an ownership guard (path `userId` == principal) or admin check on each method.

### H5 — Forgeable Trust Payments confirmation (mark funded without paying)
- `modules/payin/trust/controller/TrustPaymentController.java:34-113`, `permitAll` via `SecurityConfig.java:112`.
- `POST /api/trust-payment/confirm` accepts `{errorcode:"0", referenceNumber:<any tx ref>}` with **no signature/HMAC verification and no amount check**, then drives the tx to `FUNDS_RECEIVED`.
- **Impact:** Attacker marks any pending transaction as funds-received without paying. (The Volume webhook *does* verify a signature — Trust does not.)
- **Fix:** Verify a Trust Payments signature server-side; validate amount/currency against the transaction; ideally confirm via Trust's server API rather than client-posted params.

### H6 — Admin wallet endpoints have no authorization
- `modules/user/controller/WalletController.java:85-134` (`adminCredit/adminDebit/getAllWallets/getAdminTransactions`) have no `@PreAuthorize` — only `authenticated()`.
- **Impact:** Any logged-in non-admin user can credit/debit any wallet and list all wallets.
- **Fix:** Add `@PreAuthorize` with an admin/ledger permission.

### H7 — Predictable default password for payin-customer logins
- `modules/payin/customer/service/BackendCustomerLoginProvisioner.java:35-77` provisions accounts with password = `FIRSTNAME + first 4 phone digits`, `emailVerified=true`, KYC `TIER_2`, no OTP gate.
- **Impact:** Password is guessable from data a partner/attacker often knows. `passwordChangeRequired=true` doesn't help — the account is fully loginable before the change.
- **Fix:** Generate a random password, deliver out-of-band, block authentication until the customer sets their own; don't auto-set `emailVerified`/TIER_2 on provisioning. (See repo memory `no-password-resets` for the security-care pattern.)

### MEDIUM (backend security)
- **M1 — Transaction reads lack ownership checks (IDOR/PII):** `modules/transaction/controller/TransactionController.java` `GET /{id}` (l.81), `/{id}/history` (l.160), `/{id}/receipt.pdf` (l.33), `/{id}/receipt.html` (l.50) — any authenticated user can read any transaction by id. (`cancelTransaction` l.891 *does* check ownership.) Fix: enforce `sender == principal` or `transaction:view_all`.
- **M2 — Wallet credit/debit accept negative amounts:** `modules/user/service/WalletService.java:56-127` never validate `amount > 0`; a negative debit becomes a credit. Fix: validate `amount.signum() > 0`.
- **M3 — `/internal/**` publicly reachable:** `SecurityConfig.java:73` `permitAll` with no secret/IP allowlist; `InternalKycController` / `InternalComplianceController` exposed. Fix: restrict to internal network or service token.
- **M4 — Weak amount validation on create:** `common/dto/CreateTransactionRequest.java:32-33` `sendAmount` is `@NotNull` only (no `@Positive`); service tolerates ±£1 vs quote (`TransactionService.java:252-255`). Fix: add `@DecimalMin("0.01")` + max; tighten tolerance to a few pence.

### LOW
- **L1 — Client-spoofable `X-User-UUID`:** `security/JwtAuthenticationFilter.java:79-91` only overrides `X-User-*` headers when a JWT is present; on `permitAll` paths a client-sent header passes through. Auth-gated today but fragile with H1. Fix: never trust `X-User-*` for identity.

---

## 🟠 HIGH — Frontend correctness (money / payment)

### H8 — Volume Pay shows "success" with **zero** backend verification
- `app/customer/send-money/send-money.page.ts:932-946` (`handleVolumeEvent`) + `app/customer/volume-callback/volume-callback.page.ts:25-27` + `volume-callback.page.html:10`.
- The SDK event handler matches with `status.includes(s)` against `['COMPLETED','SETTLED','SUCCESS','PAID','PAYMENT_COMPLETED']` then navigates with a **hardcoded `status:'COMPLETED'`**. The callback renders "Payment Successful!" from that client-supplied param alone (`isSuccess = status === 'COMPLETED'`). There is **no `/volume/confirm` backend call** (unlike trust-callback).
- **Impact:** A substring match (e.g. `PAYMENT_NOT_COMPLETED`) or a spoofed URL shows a success screen for an unpaid/failed transfer.
- **Fix:** On the callback page, fetch authoritative status from the backend by `merchantPaymentId`/`txnRef` and derive `isSuccess` from that. Use strict equality (not `includes()`) in `handleVolumeEvent`.

### H9 — Trust-callback treats backend-unreachable as success
- `app/customer/trust-callback/trust-callback.page.ts:83-87`: when `/trust-payment/confirm` errors, the fallback is `isSuccess = errorCode === '0'` — declaring success purely from a client-trusted URL param.
- **Impact:** Backend down/timeout → user sees "success" and `layla_last_txn` is cleared for an unconfirmed transfer.
- **Fix:** On confirm failure show a "pending" state, never `isSuccess=true` from the URL param alone.

### H10 — Client-side quote mutation diverges from backend amounts
- `app/customer/send-money/send-money.page.ts:387-407` (`applyReferralBoostToQuote`) and `561-573` (`onReceiveAmountChange`) overwrite `quote.appliedRate` / `quote.receiveAmount` / `quote.totalCost` in place. `confirmSend` (l.801) re-fetches a fresh quote and sends only `quoteId`, so the backend computes its own numbers.
- **Impact:** Customer sees a boosted receive amount / locally-computed total that the actual transaction may not match (over-promising the recipient amount); card charge derives from the stale `totalCost`.
- **Fix:** Keep the boost as a display-only field; render and charge from the fresh quote's authoritative `receiveAmount`/`totalCost`.

### MEDIUM (frontend correctness)
- **M5 — Card charge uses stale `effectiveTotal`:** `send-money.page.ts:973-975,1007` computes `payWithCard` `mainamount` from `quote.totalCost` (possibly mutated), not the just-created `txn`. With wallet applied the card can be charged the wrong amount. Fix: use the authoritative `txn` response.
- **M6 — Register phone validator hardcoded to 10 digits:** `app/customer/register/register.page.ts:74` ignores per-country `phoneDigits` (SG=8, AU=9, DE=11…). Users in those countries can't register. Fix: dynamic validator keyed off `selectedCountry.phoneDigits`.
- **M7 — PayIn `submit()` has no in-flight guard:** `app/payin-partner/create-transaction/create-transaction.page.ts:1477-1479` lacks `if (this.submitting) return;`; rapid double-click can create two transactions. Fix: add the guard + a client idempotency key.
- (Reviewed & OK: KYC document-types response is array-guarded — `kyc.page.ts:274-303`.)

### LOW (frontend correctness)
- **L2** — `loadCorridors` proceeds on `getActiveCountries()` error, bypassing the unavailable-currency guard (`send-money.page.ts:443`).
- **L3** — Trust `sitereference` hardcoded `'laylalondo147951'` (`send-money.page.ts:990`) — public ref, but can't vary sandbox/prod.
- **L4** — `getInitials` produces garbage on double-spaced names (`send-money.page.ts:1046`).
- **L5** — `volume-pay.page.ts:87` dead-ends after payment (legacy page, only `console.log`s).

---

## 🟡 Memory leaks — Frontend

### H11–H14 — `NotificationService` polling interval never torn down (one root cause, 4 sites)
- **Root cause:** `app/core/services/notification.service.ts:47-54` — `interval(30000).pipe(startWith(0), switchMap(...)).subscribe(...)` is created with no stored `Subscription`, no `takeUntil`, and no `stopPolling()`. The service is `providedIn:'root'` (singleton), so it polls `/unread-count` every 30s forever.
- **Amplifier:** Three callers re-invoke `startPolling()` on every mount, stacking a *new* permanent interval each time:
  - `app/customer/tabs/tabs.page.ts:68`
  - `app/admin/layout/admin-layout.component.ts:41`
  - `app/superadmin/layout/superadmin-layout.component.ts:100`
- **Impact:** Navigating between portals (customer → admin → superadmin) accumulates concurrent 30s polling loops that never stop — growing network + memory over a session.
- **Fix:** In the service, store the subscription, guard against double-start, and add `stopPolling()` that unsubscribes. Best: expose `unreadCount$` consumed via async pipe, started once.

### MEDIUM
- **M8 — `form.valueChanges.subscribe()` without cleanup** on auth pages (no `OnDestroy`): `login.page.ts:48`, `admin-login.page.ts:45`, `register.page.ts:83`, `agent-send-money.page.ts:136`. Small real-world impact (Angular destroys the FormGroup with the component) but an uncleaned subscription on high-traffic pages. Fix: `takeUntil(destroy$)`.

### LOW
- **M9 — `route.queryParamMap.subscribe()` without cleanup** on one-shot callback pages: `trust-callback.page.ts:53`, `volume-callback.page.ts:37`, `volume-pay.page.ts:35`. Low risk (no polling; route destroys with component). Fix: use `route.snapshot.queryParamMap` since they read once.

**Verified clean:** `otp-verify.page.ts` (intervals cleared), `send-money.page.ts` rate-lock timer (cleared l.272), `beneficiaries.page.ts` (exemplary `destroy$`/`takeUntil`), all `setTimeout` uses (one-shot), `invoice-generator.ts` listeners (transient script element).

---

## 🟡 Resource / memory leaks — Backend (no HIGH)

- **M10 — Unbounded `findAll()` on the transactions table:** `modules/payin/transaction/service/PayinTransactionServiceImpl.java:437` loads every transaction row into memory + maps to DTOs on an admin path. Grows unbounded → OOM. Fix: paginate (`Pageable`/`Slice`) or restrict by date window.
- **M11 — `@Async` with default unbounded-queue executor:** `RemitzApplication.java:9` (`@EnableAsync`) with no `TaskExecutor` bean and no `spring.task.execution.*` config → auto `applicationTaskExecutor` has an **unbounded** queue. Consumed by `common/audit/AuditService.java:17,42,66` + email/screening `@Async` methods. If DB/SMTP slows, tasks pile up and grow heap. Fix: define a bounded `ThreadPoolTaskExecutor` with `CallerRunsPolicy`.
- **M12 — Whole-table `findAll()` on users / refresh tokens:** `modules/auth/service/AuthService.java:893,987` and `modules/payin/customer/service/PayinCustomerServiceImpl.java:70,135,181` load-then-filter in app. Fix: push predicates into derived queries.

### LOW
- **L6** — `new RestTemplate()` per request with **no timeouts**: `VolumePaymentServiceImpl.java:60`, `VolumeSignatureService.java:29`, `RemitOneServiceImpl.java:190`. Not a leak, but a slow upstream ties up the thread indefinitely. Fix: shared, timeout-configured `RestTemplate` bean.
- **L7** — Per-call `new StringRedisTemplate(...)` + `new ObjectMapper()`: `modules/transaction/service/TransactionService.java:218,221`, `modules/fx/service/QuoteService.java:166`. Allocation churn. Fix: inject shared beans.
- **L8** — `HttpClient.newHttpClient()` per SMS send: `modules/notification/service/SmsService.java:112` — each carries its own selector thread pool. Fix: hold one `HttpClient` field.

**Verified clean:** KYC upload/serve, PDF/receipt generation, OpenSanctions ingest, support-ticket downloads (all try-with-resources); schedulers use fixedDelay/single-thread (no pile-up); static collections are immutable constants; no manual JDBC; caching is Redis-backed (no unbounded in-memory maps).

---

## Recommended fix order

1. **Today (security incident):** H1, H2 — unauthenticated money minting + passport leak. Then H3, H4, H6 (auth/IDOR), H5 (forgeable Trust confirm), M1, M2, M3.
2. **This week (money correctness):** H8, H9, H10 — payment success without verification + quote divergence; then M4, M5, M7 (double-submit).
3. **Soon (stability):** H11–H14 notification polling leak (single service fix); M10/M11 (`findAll` + async queue) before traffic grows; H7 password scheme.
4. **Cleanup:** remaining MEDIUM/LOW.

*Findings are static-analysis results; validate each fix against current behavior and add a regression test (especially for the auth/IDOR and payment-confirmation paths) before deploying.*
