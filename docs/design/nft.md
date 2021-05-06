# Non-Fungible Tokens (NFTs)

## Purpose

NFTs allow users to create tokens that each have unique data and can be individually tracked. This document explains how
the mirror node can be updated to add support for NFTs.

## Goals

- Ingest NFT transactions and persist to the database
- Expose NFT info and transfers via REST APIs
- Monitor NFTs

## Non Goals

- Provide an NFT gRPC API

## Architecture

### Database

- Update `t_transaction_results` with new response codes

- Add to `token` table fields `fungible` (boolean) and `maxSupply` (long)
  - Default values will be `true` and `null` (null `maxSupply` will represent an infinite amount of tokens are possible)

- Add a new `nft` table

```sql
create table if not exists nft
(
  created_timestamp     bigint  primary key not null,
  deleted               boolean             not null,
  modified_timestamp    bigint              not null,
  memo                  text    default ''  not null,
  serial_number         bigint              not null,
  token_id              bigint              not null
);

```

- Add a unique constraint to `nft` for `token_id`, `serial_number`, and `created_timestamp`, desc

- Add a new `nft_transfer` table

```sql
create table if not exists nft_transfer
(
  consensus_timestamp   bigint  not null,
  receiver_account_id   bigint  not null,
  sender_account_id     bigint  not null,
  serial_number         bigint  not null,
  token_id              bigint  not null
);
```

- Add a unique constraint to `nft_transfer` for `token_id`, `serial_number` and `consensus_timestamp`, desc

- Add a new `nft_balance` table

```sql
create table if not exists nft_balance
(
  account_id          bigint    not null,
  consensus_timestamp bigint    not null,
  serial_number       bigint    not null,
  token_id            bigint    not null
);
```

- Add a unique constraint to `nft_balance` for `token_id`, `serial_number`, `account_id`, and `consensus_timestamp`,
  desc

### Importer

#### Domain

- Add new fields to `Token` domain object from schema changes
- Add an `Nft` domain object with the same fields as the schema.
- Add an `NftTransfer` domain object with the same fields as the schema.
- Add an `NftBalance` domain object with the same fields as the schema.
- Add `NftBalance` list to `AccountBalances` domain object

#### Balance Parsing

Need information on file format. Effectively envision:

- Update `ProtoBalanceFileReader` to handle new NFT balance list
  - If the NFT balance list is added to the CSV version (see [questions](#outstanding-questions-and-concerns)), add
    support for new CSV `BalanceFileReader` based on new format
  - Add `NftBalance` to the `AccountBalance` object as they are read.
- Update `AccountBalanceFileParser` to persist the `NftBalance`

#### Entity Listener

- Add `onNft`
- Add `onNftTransfer`

#### Entity Record Item Listener

- If transaction is successful, persist any NFT Transfers.
- `insertTokenCreate()` must be updated to set `fungible` and `maxSupply`
- `insertTokenMint()` must be updated to handle the new field `amountOrMemo` create and persist the `NFTs` if the memo
  is set.
- `insertTokenBurn` must be updated to handle the new field `amountOrSerialNumbers` and mark the `NFTs` as deleted if
  the `serialNumbers` list is set.
- `insertTokenDelete` must be added to mark the `NFTs` as deleted if the `fungible` field is false for the token.
- `insertTokenWipe` must be must be updated to handle the new field `amountOrSerialNumbers` and mark the `NFTs` as
  deleted if the `serialNumbers` list is set.

### REST API

#### Get Transaction

- Update `/api/v1/transactions` response to add nft transfers

```json
{
  "transactions": [
    {
      "consensus_timestamp": "1234567890.000000001",
      "charged_tx_fee": 7,
      "max_fee": "33",
      "memo_base64": null,
      "name": "CRYPTOTRANSFER",
      "node": "0.0.3",
      "result": "SUCCESS",
      "token_transfers": [
        {
          "account": "0.0.200",
          "amount": 200,
          "fungible": true,
          "token_id": "0.0.90000"
        },
        {
          "account": "0.0.300",
          "amount": -1200,
          "fungible": true,
          "token_id": "0.0.90000"
        },
        {
          "account": "0.0.400",
          "amount": 1000,
          "fungible": true,
          "token_id": "0.0.90000"
        },
        {
          "fungible": false,
          "receiver_account_id": "0.0.121",
          "sender_account_id": "0.0.122",
          "serial_number": 124,
          "token_id": "0.0.123"
        }
      ],
      "transaction_hash": "aGFzaA==",
      "transaction_id": "0.0.10-1234567890-000000000",
      "transfers": [
        {
          "account": "0.0.9",
          "amount": 10
        },
        {
          "account": "0.0.10",
          "amount": -11
        },
        {
          "account": "0.0.98",
          "amount": 1
        }
      ],
      "valid_duration_seconds": "11",
      "valid_start_timestamp": "1234567890.000000000"
    }
  ]
}
```

#### Get Accounts

- Update `/api/v1/accounts` response to add NFTs to the tokens list, with serial numbers instead of balance.

```json
{
  "accounts": [
    {
      "balance": {
        "account": "0.0.8",
        "auto_renew_period": null,
        "balance": 80,
        "deleted": false,
        "expiry_timestamp": null,
        "key": null,
        "timestamp": "0.000002345",
        "tokens": [
          {
            "balance": 80,
            "fungible": true,
            "token_id": "0.15.3"
          },
          {
            "balance": 50,
            "fungible": true,
            "token_id": "0.2.5"
          },
          {
            "token_id": "0.2.9",
            "fungible": false,
            "serial_numbers": [
              210,
              211
            ]
          }
        ]
      }
    }
  ],
  "links": {
    "next": null
  }
}
```

#### Get Balances

- Update `/api/v1/balances` response to add NFTs to the tokens list, with serial numbers instead of balance.

```json
{
  "timestamp": "0.000002345",
  "balances": [
    {
      "account": "0.0.8",
      "balance": 100,
      "tokens": []
    },
    {
      "account": "0.0.10",
      "balance": 100,
      "tokens": [
        {
          "balance": 80,
          "fungible": true,
          "token_id": "0.15.3"
        },
        {
          "fungible": false,
          "serial_numbers": [
            210,
            211
          ],
          "token_id": "0.2.9"
        }
      ]
    },
    {
      "account": "0.0.13",
      "balance": 100,
      "tokens": [
        {
          "balance": 80,
          "fungible": true,
          "token_id": "0.15.3"
        },
        {
          "balance": 50,
          "fungible": true,
          "token_id": "0.2.4"
        },
        {
          "fungible": false,
          "serial_numbers": [
            157
          ],
          "token_id": "0.15.6"
        }
      ]
    }
  ],
  "links": {
    "next": null
  }
}
```

#### List Tokens

- Update `/api/v1/tokens` response to show NFTs by adding the `fungible` field and the `serial_numbers` list.

```json
{
  "tokens": [
    {
      "token_id": "0.0.1000",
      "symbol": "F",
      "fungible": true,
      "admin_key": {
        "_type": "ED25519",
        "key": "31c4647554640c464c854337570217269a1fc0f8bc30591c349a410269090920"
      }
    },
    {
      "token_id": "0.0.10001",
      "symbol": "N",
      "fungible": false,
      "serial_numbers": [
        1002,
        1003
      ],
      "admin_key": {
        "_type": "ED25519",
        "key": "31c4647554640c464c854337570217269a1fc0f8bc30591c349a410269090920"
      }
    }
  ]
}
```

Add optional filters

- `/api/v1/tokens?type=fungible` - All fungible tokens (other values are `nft` and `both` (default))
- `/api/v1/tokens?serial.number=1001` - All tokens that contain a serial number `1001` (implied that `type` will
  be `nft`)

#### Get Token by id

- Update `/api/v1/tokens/{id}` response to show NFTs by adding the `fungible` field and the `serial_numbers` list.

```json
{
  "admin_key": {
    "_type": "ProtobufEncoded",
    "key": "9c2233222c2233222c2233227d"
  },
  "auto_renew_account": "0.0.6",
  "auto_renew_period": null,
  "decimals": "1000",
  "expiry_timestamp": null,
  "freeze_default": false,
  "freeze_key": {
    "_type": "ProtobufEncoded",
    "key": "9c2233222c2233222c2233227d"
  },
  "fungible": false,
  "initial_supply": "2",
  "kyc_key": {
    "_type": "ProtobufEncoded",
    "key": "9c2233222c2233222c2233227d"
  },
  "max_supply": "10",
  "name": "FOO COIN TOKEN",
  "nfts": [
    {
      "serial_number": 1002,
      "memo": "This is a test NFT"
    },
    {
      "serial_number": 1003,
      "memo": "This is another test NFT"
    }
  ],
  "supply_key": {
    "_type": "ProtobufEncoded",
    "key": "9c2233222c2233222c2233227d"
  },
  "symbol": "FOOCOIN",
  "token_id": "0.15.3",
  "total_supply": "2",
  "treasury_account": "0.15.10",
  "wipe_key": {
    "_type": "ProtobufEncoded",
    "key": "9c2233222c2233222c2233227d"
  }
}
```

### Token Supply distribution

- Update `/api/v1/tokens/{id}/balances` response to show `serial_numbers` when the token is an NftType token.

```json
    {
  "timestamp": "0.000002345",
  "balances": [
    {
      "account": "0.15.10",
      "nfts": [
        {
          "serial_number": 1002,
          "memo": "This is a test NFT"
        },
        {
          "serial_number": 1003,
          "memo": "This is another test NFT"
        }
      ]
    },
    {
      "account": "0.15.9",
      "nfts": [
        {
          "serial_number": 1004,
          "memo": "This is a third test NFT"
        },
        {
          "serial_number": 1005,
          "memo": "This is a fourth test NFT"
        }
      ]
    }
  ],
  "links": {
    "next": null
  }
}
```

#### List NFTs

- GET `/api/v1/tokens/{id}/nfts` will list basic information of all NFTs for a given token.

```json
{
  "nfts": [
    {
      "created_timestamp": "1610682445.003266000",
      "memo": "This is a test NFT",
      "serial_number": 124
    }
  ],
  "links": {
    "next": null
  }
}
```

Optional Filters

- `/api/v1/nfts?serial.number=gt:0.0.1001` - All serial numbers in range
- `/api/v1/nfts?order=desc` - All NFTs in descending order of `serial_number`
- `/api/v1/nfts?limit=x` - All NFTs taking the first `x` number of NFTs

#### Get NFT by id

- GET `/api/v1/tokens/{id}/nfts/{serialNumber}` will show information about an individual NFT.

```json
{
  "created_timestamp": "1610682445.003266000",
  "memo": "This is a test NFT",
  "serial_number": 124
}
```

#### Get NFT transaction history

- GET `/api/v1/tokens/{id}/nfts/{serialNumber}/transactions` will return minimal transaction information for each
  transaction involving a given NFT.

```json
{
  "transactions": [
    {
      "consensus_timestamp": "1618591023.997420022",
      "id": "0.0.19789-1618805680-742097949",
      "type": "TOKENBURN"
    },
    {
      "consensus_timestamp": "1618591023.997420021",
      "id": "0.0.19789-1618805680-742097948",
      "receiver_account_id": "0.0.11",
      "sender_account_id": "0.0.10",
      "type": "CRYPTOTRANSFER"
    },
    {
      "consensus_timestamp": "1618591023.997420020",
      "id": "0.0.19789-1618805680-742097947",
      "treasury_account_id": "0.0.5",
      "type": "TOKENMINT"
    }
  ],
  "links": {
    "next": null
  }
}
```

### Monitor

- Make changes to the `ExpressionConverter`
  - Add a new `nft` expression
    - Creating an NFT requires the `tokenType` field to be set to `NON-FUNGIBLE`. `TokenCreate` for an NFT also cannot
      create serial numbers as part of the initial supply, so a `TokenMint` should follow to have serial numbers created
      for the transaction suppliers.
    - Serial numbers are auto-incremented per token, so minting a hardcoded or configured (one universal value) number
      of serial numbers per NFT should give the transaction suppliers a guaranteed set of serial numbers to use without
      having to save the actual serial numbers anywhere. The drawback to this is the NFT scenarios will be less
      customizable.
      - Only one NFT can be minted per transaction, so to reduce startup time this number should be low, possibly just
        one.
    - Alternatively, a second, compound expression could be added, such as `nft.1.serialNo.1`, that would indicate the
      need to mint a serial number for `nft.1` (after the creation of `nft.1`). This would require more work to refactor
      the `ExpressionConverter`, but it would allow for more customization.
  - Update the `token` expression logic to set the new fields (`tokenType` and `maxSupply`).

- Update the `TransactionSuppliers`
  - `TokenCreateTransactionSupplier` will need a boolean `fungible` attribute and a long `maxSupply` attribute.
    - Some fields will now have stricter requirements now, such as for NFTs `decimals` has to be 0, as
      does `initialSupply`. We could simply abide by the user config values, regardless of if they are valid, but most
      likely we should have logic to enforce those requirements based on the `fungible` flag.
  - `TokenMintTransactionSupplier` will need a boolean `fungible` attribute, as well as a String `memo` attribute. The
    supplier should only set `amount` for fungible tokens, and only `memo` for NFTs
    - Alternatively, the `fungible` flag can be removed, and the supplier just sets whichever of `memo` or `amount` is
      set, but this relies on users configuring things correctly.
  - `TokenBurnTransactionSupplier` and `TokenWipeTransactionSupplier` will need a boolean `fungible` attribute, and it
    will need to set the `serialNumbers` list for NFTs (hardcoded based on the `initialSupply` value used in
    the `ExpressionConverter`)
    - The list could be configurable using the compound expression mentioned earlier if desired.

- Add support for transfering NFTs in `CryptoTransferTransactionSupplier`.
  - Add a new `fungible` boolean attribute to be used when doing a TOKEN or BOTH transfer, that determines whether to
    use the `amount` attribute or the `serial numbers` list.
  - Because a serial number can only be transferred once out of a given account (unless it is transferred back), custom
    logic will be needed for performant NFT transfers.
    - One approach would be to have the supplier swap the sender and receiver each time to transfer the NFT back and
      forth. This could be done only on the NFT transfer or on the entire transfer. This would likely limit performance
      as the swap has to be synchronized (or we just let the transactions fail when double-transfers occur).
      - This would also require different signatures on the transaction, which would require reworking how we handle the
        transaction signing.
    - Alernatively, the `ExpressionConverter` could just generate a large amount of serial numbers, and
      the `CryptoTransferTransactionSupplier` could just transfer one at a time via a counter until it runs out. This
      would be a much simpler approach, but it would cap the number of transactions possible in a scenario, and it would
      require a longer startup time to generate all the serial nunmbers.

### Acceptance Tests

Add acceptance tests that verify all transactions are handled appropriately. This includes tests for

1. Basic NFT transfer flow

- Create a token with type `NON-FUNGIBLE`
- Mint a serial number
- Associate the token to an account
- Transfer the serial number to the account.
- Verify response codes and data from `/tokens`, `/tokens/{id}/nfts`,
  `/tokens/{id}/nfts/{serialNumber}/transactions`, `/balances`, and `/transactions`,

2. NFT Burn/Wipe/Delete flow

- Create a token with type `NON-FUNGIBLE`
- Mint three serial numbers, associate the token with
- Associate the token to an account
- Transfer one serial number to the account.
- Wipe the serial number from the account.
- Burn a serial number from the treasury account.
- Delete the token
- Verify response codes and data from `/tokens`, `/balances`, `/tokens/{id}/nfts`,
  `/tokens/{id}/nfts/{serialNumber}/transactions`, and `/transactions`

# Outstanding Questions and Concerns:

1. How will NFT balances be represented in the balance file?
2. Will NFT balances be added to the csv version of the balance file, or just the proto version?
