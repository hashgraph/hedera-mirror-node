alter table if exists token_account
  alter column automatic_association drop default,
  alter column automatic_association drop not null,
  alter column balance_timestamp drop not null,
  alter column created_timestamp drop not null,
  alter column freeze_status drop default,
  alter column freeze_status drop not null,
  alter column kyc_status drop default,
  alter column kyc_status drop not null;


alter table if exists token_account_history
  alter column automatic_association drop default,
  alter column automatic_association drop not null,
  alter column balance_timestamp drop not null,
  alter column created_timestamp drop not null,
  alter column freeze_status drop default,
  alter column freeze_status drop not null,
  alter column kyc_status drop default,
  alter column kyc_status drop not null;