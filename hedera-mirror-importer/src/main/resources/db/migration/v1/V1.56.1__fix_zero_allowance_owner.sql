-- explicitly set owner to payer_account_id if it's 0
update crypto_allowance set owner = payer_account_id where owner = 0;
update crypto_allowance_history set owner = payer_account_id where owner = 0;

update nft_allowance set owner = payer_account_id where owner = 0;
update nft_allowance_history set owner = payer_account_id where owner = 0;

update token_allowance set owner = payer_account_id where owner = 0;
update token_allowance_history set owner = payer_account_id where owner = 0;
