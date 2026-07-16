-- Delete the 8 test pay-in transactions + their linked regular transactions + dependents.
-- Scoped by UUID so it only ever touches these specific rows.
SET SESSION sql_mode='';
SET @uuids := "b206239d-7c77-4e86-926b-513548f73812,fb2aa3f6-d420-48c2-b2ed-740ffa333a7a,ec19a800-6725-45a3-a3ec-6207085c2e03,770da427-e192-412b-9a06-6655ffa84e30,f266c93f-eea9-4b5f-8f5b-4b8e3420cb57,cc8b29cd-212b-472e-867f-4357777bde27,7517075c-e6c6-4c5a-8715-aa760c8ba54c,991ca8f1-f297-4a17-be83-39c522b9eead";

-- dependents of the linked regular transactions
DELETE FROM ledger_entries WHERE transaction_id IN
  (SELECT linked_transaction_id FROM payin_transactions WHERE FIND_IN_SET(transaction_id,@uuids) AND linked_transaction_id IS NOT NULL);
DELETE FROM transaction_status_history WHERE transaction_id IN
  (SELECT linked_transaction_id FROM payin_transactions WHERE FIND_IN_SET(transaction_id,@uuids) AND linked_transaction_id IS NOT NULL);
DELETE FROM payments WHERE transaction_id IN
  (SELECT linked_transaction_id FROM payin_transactions WHERE FIND_IN_SET(transaction_id,@uuids) AND linked_transaction_id IS NOT NULL);
-- the linked regular transactions
DELETE FROM transactions WHERE id IN
  (SELECT linked_transaction_id FROM payin_transactions WHERE FIND_IN_SET(transaction_id,@uuids) AND linked_transaction_id IS NOT NULL);
-- the pay-in transactions themselves
DELETE FROM payin_transactions WHERE FIND_IN_SET(transaction_id,@uuids);
