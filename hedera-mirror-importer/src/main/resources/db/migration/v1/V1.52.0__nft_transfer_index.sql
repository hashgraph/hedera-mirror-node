-------------------
-- Add index to speed up nft transfer query for the combination of token id, serial number, and optional timestamp
-------------------

create index if not exists nft_transfer__token_id_serial_num_timestamp
  on nft_transfer(token_id desc, serial_number desc, consensus_timestamp desc);
