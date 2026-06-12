# KYC document handling — changes (2026-06-12)

Two batches of changes to the KYC document flow (backend `layla-backend` + frontend
`laylamoneytransfernew`). Triggered by production data issues where users had duplicate /
mistyped KYC documents, could upload while a review was pending, could submit a single
document, and stayed VERIFIED after a document was rejected.

---

## Batch 1 — one current document per type, block-while-pending, require both docs
*(committed in `f404dbd "layla claude changes from 955"`)*

### 1. Re-upload REPLACES instead of appending (one current document per type)
- **`db/migration/V545__add_superseded_at_to_kyc_documents.sql`** (new) — adds
  `superseded_at DATETIME NULL` to `kyc_documents`, backfills existing duplicates (keeps the
  latest row per `user_id`+`document_type`, flags the rest as superseded), adds index
  `idx_kyc_docs_user_active`.
- **`KycDocumentEntity`** — new `supersededAt` field.
- **`KycDocumentRepository`** — `findByUserIdAndSupersededAtIsNull(userId)` and
  `findByUserIdAndDocumentTypeAndSupersededAtIsNull(userId, type)`.
- **`KycService.uploadDocument`** — before saving a new upload, supersedes any existing active
  document(s) of the same type (kept for audit, hidden from the active list).
- **`KycService.getDocuments` / `getKycStatus` / `triggerVerification`** — now read active
  (non-superseded) documents only, so the admin list and the per-type counter show exactly one
  current document per type.

### 2. Block uploads while a document is pending review
- **`KycService.uploadDocument`** — a customer self-upload throws `400` if the user already has
  a `PENDING` document. Admin (auto-approve) uploads bypass this.
- **`kyc.page.html`** — the customer "Upload / Re-submit" button is hidden when
  `kycStatus === 'PENDING'`, replaced by an "under review" note.
- **i18n** — new key `KYC.UPLOAD_LOCKED_PENDING` (en + ar).

### 3. "My Documents" requires BOTH identity AND address
- **`kyc.page.ts` `isDocUploadValid`** — the documents-only upload path now requires both an
  identity document and a proof of address (was: "at least one section"). This closes the gap
  that let a user submit ID only with no address proof.

---

## Batch 2 — rejecting a document revokes verification
*(uncommitted at time of writing — this commit)*

**Policy:** a rejected customer is "not verified / pending" and must re-submit ALL documents.

### Backend — `KycService.java`
- **`reviewDocument`** — on `REJECTED`, calls new `revokeVerificationOnRejection(userId, docId)`.
- **`revokeVerificationOnRejection`** (new) — drops the user to `TIER_0` and, if currently
  `ACTIVE`, back to `PENDING_VERIFICATION`; writes a `VERIFICATION_REVOKED` audit log.
  `SUSPENDED` / `CLOSED` / deletion states are left untouched.
- **`computeOverallStatus`** — a genuine (app-uploaded, has `file_hash`) rejected document now
  returns `REJECTED` *before* the `tier → VERIFIED` shortcut. This is the fix for "reject then
  still VERIFIED online", and it also corrects already-rejected users on first read. Imported
  placeholder docs (no `file_hash`) are excluded, so the migrated cohort is unaffected.

### Frontend
- No new change — the "require both identity + address" rule from Batch 1 already forces a
  rejected user through a full re-submission.

---

## Root causes that drove these (from prod investigation)
- `uploadDocument` was INSERT-only → duplicate rows per type; the admin list/counter counted
  stale duplicates.
- No pending guard → users re-uploaded (even the same image twice) during review.
- `isDocUploadValid` accepted a single section → ID-only submissions with no address proof.
- `reviewDocument` never touched `kyc_tier`, and `evaluateAndUpgrade` is upgrade-only, so the
  `tier != TIER_0 → VERIFIED` rule in `computeOverallStatus` swallowed genuine rejections.

## Deploy notes
- Backend builds from source on prod (`docker compose -f docker-compose.production.yml build
  backend`); `V545` is applied automatically by Flyway on backend restart — **do not** run a
  manual ALTER.
- Frontend: `ng build --configuration production` → tar → extract into
  `/var/www/layla/data/www/laylamoneytransfer.co.uk/`.
- **Prod-wide effects:** V545 dedupes every user's KYC docs (latest per type); the
  `computeOverallStatus` rejection guard flips any user with a real rejected active document
  from VERIFIED to REJECTED on first read. Deploy during low traffic.
