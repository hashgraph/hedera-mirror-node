# REST API

The REST API is the main API to retrieve data from the mirror node. Further documentation is available
on [docs.hedera.com](https://docs.hedera.com/guides/docs/mirror-node-api/cryptocurrency-api) and via
our [Swagger UI](https://mainnet-public.mirrornode.hedera.com/api/v1/docs/#/).

## Database

This section documents the tables used for different endpoints and query parameters. This information is useful for
debugging purposes and for understanding the update cadence of the underlying data.

| Endpoint                                              | Tables                                                                                                           | Notes                                                        |
|-------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------|
| `/api/v1/accounts`                                    | `entity`, `token_balance`                                                                                        | Entity tables first used to filter, then joined w/ balances  |
| `/api/v1/accounts?balance=false`                      | `entity`                                                                                                         | Balance tables skipped                                       |
| `/api/v1/accounts/:idOrAlias`                         | `crypto_transfer`, `entity`, `token_balance`, `token_transfer`, `transaction`                                    | Transfers & transactions are present only for legacy reasons |
| `/api/v1/accounts/:id/allowances/crypto`              | `crypto_allowance`                                                                                               |                                                              |
| `/api/v1/accounts/:id/allowances/tokens`              | `token_allowance`                                                                                                |                                                              |
| `/api/v1/accounts/:alias/allowances/crypto`           | `crypto_allowance`, `entity`                                                                                     | Separate alias lookup first                                  |
| `/api/v1/accounts/:alias/allowances/tokens`           | `token_allowance`, `entity`                                                                                      | Separate alias lookup first                                  |
| `/api/v1/accounts/:id/nfts`                           | `nft`                                                                                                            |                                                              |
| `/api/v1/accounts/:alias/nfts`                        | `entity`, `nft`                                                                                                  | Separate alias lookup first                                  |
| `/api/v1/balances`                                    | `account_balance`, `token_balance`                                                                               |                                                              |
| `/api/v1/balances?account.publickey`                  | `account_balance`, `contract`, `entity`, `token_balance`                                                         | Entity tables used to find by public key                     |
| `/api/v1/contracts`                                   | `contract`, `entity`                                                                                             |                                                              |
| `/api/v1/contracts/:idOrAddress`                      | `contract`, `entity`, `file_data`                                                                                | `file_data` used to get init bytecode                        |
| `/api/v1/contracts/:idOrAddress?timestamp=`           | `contract`, `entity`, `entity_history`, `file_data`                                                              | Union both contract tables to find latest timestamp in range |
| `/api/v1/contracts/:idOrAddress/results`              | `contract_result`, `ethereum_transaction`, `transaction`                                                         | `ethereum_transaction` for hash and `transaction` for index  |
| `/api/v1/contracts/:idOrAddress/results?block.hash`   | `contract_result`, `ethereum_transaction`, `record_file`, `transaction`                                          | Separate block lookup by hash first                          |
| `/api/v1/contracts/:idOrAddress/results?block.number` | `contract_result`, `ethereum_transaction`, `record_file`, `transaction`                                          | Separate block lookup by number first                        |
| `/api/v1/contracts/:idOrAddress/results/:timestamp`   | `contract_log`, `contract_result`, `contract_state_change`, `ethereum_transaction`, `record_file`, `transaction` |                                                              |
| `/api/v1/contracts/:idOrAddress/results/logs`         | `contract_log`                                                                                                   |                                                              |
| `/api/v1/contracts/results`                           | `contract_log`, `contract_result`, `contract_state_change`, `record_file`, `transaction`                         |                                                              |
| `/api/v1/contracts/results/:transactionIdOrHash`      | `contract_log`, `contract_result`, `contract_state_change`, `ethereum_transaction`, `record_file`, `transaction` |                                                              |
| `/api/v1/contracts/results/logs`                      | `contract_log`                                                                                                   |                                                              |
| `/api/v1/network/nodes`                               | `address_book`, `address_book_entry`, `address_book_service_endpoint`                                            |                                                              |
| `/api/v1/network/stake`                               | `network_stake`                                                                                                  |                                                              |
| `/api/v1/network/supply`                              | `entity`, `record_file`                                                                                          |                                                              |
| `/api/v1/network/supply?timestamp=`                   | `account_balance`, `account_balance_file`                                                                        |                                                              |
| `/api/v1/schedules`                                   | `entity`, `schedule`, `transaction_signature`                                                                    |                                                              |
| `/api/v1/schedules/:id`                               | `entity`, `schedule`, `transaction_signature`                                                                    |                                                              |
| `/api/v1/tokens`                                      | `entity`, `token`                                                                                                |                                                              |
| `/api/v1/tokens?account.id`                           | `entity`, `token`, `token_account`                                                                               |                                                              |
| `/api/v1/tokens/:id`                                  | `custom_fee`, `entity`, `token`                                                                                  |                                                              |
| `/api/v1/tokens/:id/balances`                         | `token_balance`                                                                                                  |                                                              |
| `/api/v1/tokens/:id/balances?account.publickey`       | `entity`, `token_balance`                                                                                        |                                                              |
| `/api/v1/tokens/:id/nfts`                             | `entity`, `nft`                                                                                                  |                                                              |
| `/api/v1/tokens/:id/nfts/:serial`                     | `entity`, `nft`                                                                                                  |                                                              |
| `/api/v1/tokens/:id/nfts/:serial/transactions`        | `nft_transfer`, `transaction`                                                                                    |                                                              |
| `/api/v1/topics/:id/messages`                         | `topic_message`                                                                                                  |                                                              |
| `/api/v1/topics/:id/messages/:number`                 | `topic_message`                                                                                                  |                                                              |
| `/api/v1/topics/messages/:timestamp`                  | `topic_message`                                                                                                  |                                                              |
| `/api/v1/transactions`                                | `crypto_transfer`, `token_transfer`, `transaction`                                                               | Transfers are present only for legacy reasons                |
| `/api/v1/transactions/:idOrHash`                      | `assessed_custom_fee`, `crypto_transfer`, `nft_transfer`, `token_transfer`, `transaction`                        |                                                              |
| `/api/v1/transactions/:id/stateproof`                 | `address_book`, `address_book_entry`, `record_file`, `transaction`                                               | Also downloads RCD files from S3                             |
