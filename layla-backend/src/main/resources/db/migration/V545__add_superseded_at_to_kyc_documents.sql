-- One current KYC document per type.
-- Re-uploading a document type now supersedes the prior one instead of appending a
-- second row, so the admin list and the per-type counter show exactly one current
-- document per type. Superseded rows are kept (audit history) but flagged.

ALTER TABLE kyc_documents ADD COLUMN superseded_at DATETIME NULL;

-- Backfill: collapse existing duplicates. For each (user_id, document_type) keep only
-- the latest row (highest id) active; mark all older same-type rows as superseded.
UPDATE kyc_documents k
JOIN (
    SELECT user_id, document_type, MAX(id) AS latest_id
    FROM kyc_documents
    GROUP BY user_id, document_type
) latest
  ON k.user_id = latest.user_id
 AND k.document_type = latest.document_type
SET k.superseded_at = NOW()
WHERE k.id <> latest.latest_id
  AND k.superseded_at IS NULL;

CREATE INDEX idx_kyc_docs_user_active ON kyc_documents (user_id, superseded_at);
