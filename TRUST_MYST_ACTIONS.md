# Trust / MyST portal — action checklist

Things that must be done in the **Trust Payments MyST portal** (or via Trust support) — these are
**NOT** code changes and cannot be fixed from our side. Live site reference: `laylalondo147951`.

See `TRUST_PAYMENTS_RUNBOOK.md` for the full technical context.

---

## ☐ 1. Relax AVS (address) so mismatches do NOT hard-decline  — HIGH PRIORITY

**Why:** we pre-fill the billing address from the customer's Layla profile, which often does not match
the **card's** registered address. Trust AVS then declines with *"There has been a problem with your
payment — verify your details."* This is the main remaining cause of intermittent failures.

**Confirm first:** open a declined transaction in MyST and check the security response:
- `securityresponseaddress = NOTMATCHED`  → address mismatch
- `securityresponsepostcode = NOTMATCHED` → postcode mismatch
If you see these, it is AVS.

**Do:** Settings → **Rule Manager / Fraud control** → the rule tied to **STR-11** (address rule) →
change the action on AVS/postcode mismatch from **Decline** to **Allow / flag (review)**.

**If you can't find it, raise a Trust support ticket:**
> "On site `laylalondo147951`, please set AVS (address + postcode) mismatches to NOT decline — they
> should be accept-and-flag. Our customers are remittance senders whose billing address frequently
> differs from the card's registered address."

---

## ☐ 2. RISKDEC2 — find out why it blocks AUTH  — MEDIUM PRIORITY

**Why:** some transactions reach Trust and stop at a **`RISKDEC2`** (risk-decision) record with **no
AUTH**, so the card form loops back blank. This is Trust's risk engine declining before authorisation.

**Raise a Trust support ticket:**
> "On site `laylalondo147951`, some card payments run THREEDQUERY → RISKDEC2 but never reach AUTH;
> customers are returned to a blank card form with no error. Please tell us the RISKDEC2 decision
> outcome and which risk rule is blocking AUTH, and adjust it for legitimate remittance traffic."

**Reference transactions:** TXN5791 (transactionreference `57-102-88887475`), TXN5809, page ref
`P56-wHmmc13e/p`.

---

## ☐ 3. Confirm 3-D Secure v2 is fully enabled & the account is live-ready

Ask Trust to confirm 3-D Secure v2 is correctly configured on `laylalondo147951` and the account is
fully enabled to **authorise live card payments** (not stuck at risk/3DS).

---

## ☐ 4. Do NOT enable STR-8 on our side

In this account `STR-8` coincided with the RISKDEC2 block. The server-to-server URL-notification
webhook must NOT be enabled via `ruleidentifier=STR-8` until Trust confirms what STR-8 maps to.

---

## Notes
- Card payments **do work** for many customers (multiple transactions reach `AUTH` + `settlestatus=100`).
  The items above address the **remaining per-card failures**, not a total outage.
- Admin credentials for the `status-check` endpoint must be kept **out of source control** and rotated
  if ever shared.
