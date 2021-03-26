-------------------
-- New transaction result codes from revised scheduled transaction design. Also add missing result codes
-------------------

delete from t_transaction_results where proto_id >= 207;
insert into t_transaction_results (proto_id, result) values
  (197, 'TOKEN_ID_REPEATED_IN_TOKEN_LIST'),
  (198, 'TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED'),
  (199, 'EMPTY_TOKEN_TRANSFER_BODY'),
  (200, 'EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS'),
  (207, 'SCHEDULED_TRANSACTION_NOT_IN_WHITELIST'),
  (208, 'SOME_SIGNATURES_WERE_INVALID'),
  (209, 'TRANSACTION_ID_FIELD_NOT_ALLOWED'),
  (210, 'IDENTICAL_SCHEDULE_ALREADY_CREATED'),
  (211, 'INVALID_ZERO_BYTE_IN_STRING'),
  (212, 'SCHEDULE_ALREADY_DELETED'),
  (213, 'SCHEDULE_ALREADY_EXECUTED'),
  (214, 'MESSAGE_SIZE_TOO_LARGE');
