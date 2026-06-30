-- ============================================================================
-- V547: Restore identity-document FRONTS wrongly superseded by a BACK upload
-- ----------------------------------------------------------------------------
-- Before the fix, KycService.uploadDocument superseded ANY prior active document
-- of the same documentType. So uploading the BACK of a driving licence / national
-- id marked the FRONT as superseded, hiding it from the KYC review (only the BACK
-- showed). This restores those fronts.
--
-- A row is restored when: it is a superseded identity doc, it is NOT a back side
-- (document_number does not contain "back"), there is an ACTIVE back of the same
-- type for the same user, and there is NO active front of that type already
-- (so we never resurrect an older front that a genuine re-upload replaced).
-- ============================================================================

UPDATE kyc_documents f
JOIN (
    SELECT DISTINCT user_id, document_type FROM kyc_documents
    WHERE superseded_at IS NULL AND LOWER(document_number) LIKE '%back%'
) ab ON ab.user_id = f.user_id AND ab.document_type = f.document_type
LEFT JOIN (
    SELECT DISTINCT user_id, document_type FROM kyc_documents
    WHERE superseded_at IS NULL
      AND (document_number IS NULL OR LOWER(document_number) NOT LIKE '%back%')
) af ON af.user_id = f.user_id AND af.document_type = f.document_type
SET f.superseded_at = NULL
WHERE f.superseded_at IS NOT NULL
  AND f.document_type IN ('DRIVING_LICENCE', 'NATIONAL_ID')
  AND (f.document_number IS NULL OR LOWER(f.document_number) NOT LIKE '%back%')
  AND af.user_id IS NULL;
