# Database

# Indexes

The table below documents the database indexes with the usage in APIs / services.

| Table        | Indexed Columns                              | Component   | Service                    | Description                                                                       |
|--------------|----------------------------------------------|-------------|----------------------------|-----------------------------------------------------------------------------------|
| nft_transfer | consensus_timestamp                          | REST API    | `/api/v1/transactions/:id` | Used to join `nft_transfer` and the `tlist` CTE on `consensus_timestamp` equality |
| nft_transfer | token_id, serial_number, consensus_timestamp | REST API    | `/api/v1/tokens/:id/nfts/:serialNumber/transactions` | Used to query the transfer consensus timestamps of a NFT (token_id, serial_number) with optional timestamp filter |
| nft_transfer | consensus_timestamp                          | Rosetta API | `/account/balance`         | Used to calculate an account's nft token balance including serial numbers at a block |
| nft_transfer | consensus_timestamp                          | Rosetta API | `/block`                   | Used to join `nft_transfer` and `transaction` on `consensus_timestamp` equality   |
| nft_transfer | consensus_timestamp                          | Rosetta API | `/block/transaction`       | Used to join `nft_transfer` and `transaction` on `consensus_timestamp` equality   |
