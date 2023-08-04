create index if not exists crypto_allowance__active
  on crypto_allowance (owner, spender) where amount_granted > 0;
create index if not exists token_allowance__active
  on token_allowance (owner, spender, token_id) where amount_granted > 0;
