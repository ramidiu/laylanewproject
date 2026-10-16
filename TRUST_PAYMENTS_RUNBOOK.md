# Trust Payments (Secure Trading) — Card Pay-In Runbook

Last updated: 2026-10-16. Site reference (live): `laylalondo147951`. Sandbox: `test_laylalondo147950`.
Hosted page URL: `https://payments.securetrading.net/process/payments/details`.

This document captures everything we diagnosed and fixed for the Layla card pay-in flow, what is
still outstanding (Trust/MyST side), and how to diagnose future issues.

---

## 1. How the card flow works

1. Customer creates a transaction (status `PENDING`) in the customer Send-Money page
   (`laylamoneytransfernew/src/app/customer/send-money/send-money.page.ts`, method `payWithCard()`).
2. The page builds a hidden HTML form and **POSTs it to the Trust hosted payment page**.
3. Customer enters card → 3-D Secure → Trust authorises (AUTH).
4. Trust returns the browser via a **form POST** to `successfulurlredirect` / `declinedurlredirect`.
5. Our backend applies the result and advances the transaction `PENDING → PROCESSING → FUNDS_RECEIVED`.
6. A 5-minute scheduler (`TrustSettlementScheduler`) reconciles any missed settlements via
   Trust's `TRANSACTIONQUERY`.

Key backend file: `layla-backend/src/main/java/com/remitz/modules/payin/trust/controller/TrustPaymentController.java`
Endpoints (all under `/api/trust-payment`, `permitAll`):
- `POST /confirm`   — browser confirm (frontend-driven)
- `POST|GET /callback` — server-to-server webhook (reliable source of truth)
- `POST|GET /return`  — browser RETURN target (applies result, then 302-redirects to the SPA status page)
- `POST /status-check`     — admin: query Trust `TRANSACTIONQUERY` by `orderreference` (needs `settlement:manage`)
- `POST /settlement-status`— admin: reconcile one transaction

---

## 2. Issues found & FIXES ALREADY DEPLOYED (code side) ✅

### 2.1 `shipAddrCountry` error 60010 — FIXED
- **Symptom:** Trust portal error `60010 "Bank System Error — ISO code not valid, Detail: shipAddrCountry"`
  on THREEDQUERY; the hosted page shows **"There has been a problem with your payment — verify your
  details"**; transaction never reaches AUTH; `status-check` shows `found=0` or no AUTH.
- **Root cause:** the card form sent the **remittance beneficiary's** country into the `customer*`
  (= delivery / 3-DS `shipAddr`) fields. `customercountryiso2a` must be ISO 3166-1 **alpha-2**. For
  Sudan it was sent as a name/3-letter code → invalid → Trust rejects.
- **Fix:** send the **cardholder's own** country (billing, GB) as the delivery party, and harden
  `toIso2()` so it only ever emits a valid 2-letter code (added `SDN → SD`). The hosted page now
  shows Delivery = cardholder / United Kingdom, matching billing. Verified live (TXN5823).

### 2.2 Blank page after a successful payment (POST → 405) — FIXED
- **Symptom:** after paying, the customer lands on a **blank/white page**.
- **Root cause:** Trust returns via **POST** to `/home/trust-callback`, which is a static Angular
  (SPA) route. nginx can only serve static files for **GET**, so a **POST returns HTTP 405**.
- **Fix:** the redirect now targets the backend **`/api/trust-payment/return`** (accepts POST),
  which applies the result and **302-redirects (GET)** to `/home/trust-callback?errorcode=…`. On the
  **native app** (`isNativeShell`) it keeps the SPA route (there is no backend at `localhost`).

### 2.3 Sticky sandbox routing — FIXED
- **Symptom:** some devices silently sent LIVE payments to the Trust **sandbox** (invisible in the
  live portal).
- **Root cause:** a `?trusttest=1` URL flag persisted in `localStorage` forever, routing that device
  to `test_laylalondo147950`.
- **Fix:** removed the sticky flag; card payments always use the live reference `laylalondo147951`.

### 2.4 DO NOT re-add `STR-8` URL-notification rule (caused RISKDEC2 block)
- We briefly added `ruleidentifier=STR-8` (for a server-to-server webhook). In THIS Trust account
  `STR-8` coincided with a **RISKDEC2 risk-decision** step that **blocked AUTH** — card form looped
  back blank with no error. **It was removed.** Do not re-add STR-8 without confirming what it maps
  to in MyST.

---

## 3. OUTSTANDING — needs Trust / MyST action (not code) ⚠️

These cause the **"some clients succeed, some don't"** pattern. The integration works (many
transactions reach `AUTH` + `settlestatus=100`); the remaining failures are **per-card at Trust**.

### 3.1 AVS / billing-address mismatch  ← most likely remaining cause
- **What it is:** Trust AVS compares the billing **house number + postcode** against what the card
  **issuer** has on file. We **pre-fill billing from the customer's Layla profile**, which may not
  match the **card's** registered address → AVS mismatch → decline → "verify your details."
- **Confirm it first:** in MyST open a declined transaction and look at the security response —
  e.g. `securityresponseaddress = NOTMATCHED` or `securityresponsepostcode = NOTMATCHED`. If present,
  it is AVS.
- **FIX A (recommended, MyST — you/Trust support):** set AVS (address/postcode) mismatch to
  **accept-and-flag / review**, NOT hard-decline. Rule is tied to **STR-11** (the address rule).
  Settings → Rule Manager / Fraud control. Ticket wording for Trust support:
  > "Please set AVS (address/postcode) mismatches to NOT decline on site `laylalondo147951` — our
  > remitters' billing address often differs from the card's registered address."
- **FIX B (optional, code — can be implemented on request):** stop pre-filling the profile address;
  leave the billing address blank so the customer enters the **card's** billing address on the Trust
  page (keep name prefilled). Reduces mismatches without a MyST change.

### 3.2 RISKDEC2 risk-decision blocks
- Some transactions reach Trust and stop at a **`RISKDEC2`** record with **no AUTH** (e.g. TXN5809,
  TXN5791). This is Trust's **risk-decisioning** declining before authorisation.
- **FIX (Trust support only):** ask Trust what the RISKDEC2 decision outcome is and which rule blocks
  AUTH; request it be adjusted for legitimate remittance traffic. Example refs: TXN5791
  (transactionreference `57-102-88887475`), TXN5809, page ref `P56-wHmmc13e/p`.

### 3.3 Genuine card declines
- Insufficient funds, wrong CVV/expiry, blocked card → nothing to fix on our side.

---

## 4. How to diagnose (read-only)

### 4.1 Query Trust for a transaction's real status (admin)
```bash
TOKEN=$(curl -sS -X POST https://laylamoneytransfer.co.uk/api/auth/admin/login \
  -H "Content-Type: application/json" \
  --data '{"email":"<ADMIN_EMAIL>","password":"<ADMIN_PASSWORD>"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin).get('accessToken',''))")

curl -sS -X POST https://laylamoneytransfer.co.uk/api/trust-payment/status-check \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  --data '{"orderreference":"TXN5823"}'
```
Interpret the latest record:
- `found=0`                         → never reached Trust (abandoned, or rejected pre-record).
- `requesttype=AUTH, errorcode=0, settlestatus=100` → ✅ authorised + settled.
- `requesttype=AUTH, errorcode!=0`  → card declined at auth.
- `requesttype=RISKDEC2, no AUTH`   → blocked at risk decision (see 3.2).

> The admin account needs the `settlement:manage` permission (superadmin). Admin credentials are NOT
> stored in this repo — keep them out of source control.

### 4.2 Backend logs
```bash
ssh <PROD> "cd /opt/layla-live && docker compose -f docker-compose.production.yml logs --since 24h backend \
  | grep -iE 'Trust card confirm|Trust payment callback|Trust return|-> PROCESSING|errorcode=false|CANCELLED|RISKDEC'"
```
- `Trust card confirm: errorcode=true` → `true` means errorcode was `"0"` = success.
- `0` declines reaching the backend + failures only on Trust's page = the failure is **at Trust**
  (AVS / risk / card decline), not in our code.

---

## 5. Sandbox testing toggle (for manual testing)

The code currently defaults to **live**. If a session-only sandbox toggle is needed for testing with
Trust test cards, it can be re-enabled (open Send page with `?trusttest=1`, stored in `sessionStorage`
so it clears on tab close). Sandbox site reference: `test_laylalondo147950`. Use Trust test cards from
MyST → Test card details. A successful sandbox payment but failing live = confirms a **live-side**
Trust rule (AVS / RISKDEC2), not a code bug.

---

## 6. Deploy mechanics (reference)

- Frontend (static): rebuild the `layla-frontend-new` container, then
  `bash deploy-trust-removetest-prod.sh` (backs up the web root, ships the bundle, reloads nginx).
- Backend (single file): back up the prod controller, `scp` the changed `.java`, then on the server
  `docker compose -f docker-compose.production.yml build --no-cache backend && up -d backend`.
- Deploy **backend before frontend** when the frontend references a new backend endpoint.
- Prod web is served `no-cache` on `index.html`, so clients auto-pick-up new bundles on next load.

See `TRUST_MYST_ACTIONS.md` for the Trust-portal checklist.
