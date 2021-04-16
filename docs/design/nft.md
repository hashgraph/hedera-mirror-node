- How do we want to represent nft
  - NftType goes with Tokens because same idea
  - NftType associate can stay with Token Associate becausse no new info
  - NftTransfer separate because serial number
  - Separate NFT entity/table because it can be independently deleted
  - Separate NFT balance because it has to have a serial number
  -
  - How do we want to handle token burns?

Separate NFT REST API

In /tokens, should we just mix NftType with TokenType?

- Create Token
  - Have to determine if NFT or not
  - Now creates a Token and a list of NFTs
    - Should an NFT balance be created for treasury?


- Update Token
  - No change, unclear if NFT can be updated

Mint Token

- Create new NFT
  - Should an NFT balance be created for treasury?

Burn Token

- Have to delete NFT if present
- Unclear on how balance will be affected, if the serial number just disappears from the next balance file we may need
  to delete that entry as part of the burn

- Delete Token
  - NFTs are also deleted, this CANNOT be inherited because an NFT can be independently deleted

- Burn Token
  - Have to remove from balance, as file will not show

- Rename token_transfer to fungible_token_transfer
- Create non_fungible_token_transfer table
-
- Get tokens
  -

# Nonfungible Tokens (NFT)

## Purpose

NFTs allow users to create tokens that are indivisible, ensuring that each token is unique. This document explains how
the mirror node can be updated to add support for NFTs.

## Goals

- Ingest NFT transactions and persist to the database
- Expose NFT info and transfers via REST APIs
- Monitor NFTs

## Non Goals

- Provide an NFT gRPC API

## Architecture

### Database

- Update `t_entity_types` to add`NFT`
- Update `t_transaction_results` with new response codes
  - TBA

- Add to `token` table fields `maxSupply` (long) and `fungible` (boolean)
- Add a new `nft` table

```sql
create table if not exists nft
(
  serial_number         bigint              not null,
  nft_type_id           bigint              not null,
  hash                  bytea               not null,
  created_timestamp     bigint  primary key not null,
  modified_timestamp    bigint              not null,
  memo
);

```

- Add a unique constraint to `nft` for `serial_number` and `created_timestamp`, desc

- Add a new `nft_transfer` table

```sql
create table if not exists nft_transfer
(
  serial_number         bigint  not null,
  consensus_timestamp   bigint  not null,
  receiver_account_id   bigint  not null,
  sender_account_id     bigint  not null,
);
```

- Add a unique constraint to `nft_transfer` for `serial_number` and `consensus_timestamp`, desc

- Add a new `nft_owner` table

```sql
create table if not exists nft_owner
(
  nft_id              bigint    not null,
  account_id          bigint    not null,
  consensus_timestamp bigint    not null,
);
```

- Add a unique constraint to `nft_transfer` for `serial_number` and `consensus_timestamp`, desc

### Importer

#### Converter

Add an `NftIdConverter`.

### Domain

- Add new fields to `Token` domain object from schema changes
- Add an `Nft` domain object with the same fields as the schema.
- Add an `NftTransfer` domain object with the same fields as the schema.
- Add an `NftHolder` domain object with the same fields as the schema.
- Add an `NFT` enum value to `EntityTypeEnum`.
- Add `nftHolder` list to `AccountBalances` domain object

### Balance Parsing

Need information on file format. Effectively envision:

- Update `ProtoBalanceFileReader` to handle new NFT transfer list
  - Either deprecate use of the csv file or also add support for new CSV `BalanceFileReader` based on new format
  - Add `NftHolder` to the `AccountBalance` object as they are read.
- Update `AccountBalanceFileParser` to persist the `NftHolder`

### Entity Listener

- Add `onNft`
- Add `onNftTransfer`

### Entity Record Item Listener

- If transaction is successful, persist any NFT Transfers.
- `insertTokenCreate()` must be updated to create and persist the `NFT` objects and entities.
- `insertTokenMint()` must be updated to create and persist the `NFT` objects and entities.
- `insertTokenBurn` must be updated to mark the NFT entities as deleted.
- `insertTokenDelete` must be added to mark the NFT entities as deleted.
- `insertTokenWipe` must be updated to mark the NFT entities as deleted.
- `TransactionBody.hasNftCreation()` and parse `NftCreateTransactionBody` from the record, create a new `Nft`

## REST API

### Get Transaction

- Update `/api/v1/transactions` response to add nft transfers

```json
    {
  "transactions": [
    {
      "consensus_timestamp": "1234567890.000000001",
      "valid_start_timestamp": "1234567890.000000000",
      "charged_tx_fee": 7,
      "memo_base64": null,
      "result": "SUCCESS",
      "transaction_hash": "aGFzaA==",
      "name": "TOKENTRANSFER",
      "node": "0.0.3",
      "transaction_id": "0.0.10-1234567890-000000000",
      "valid_duration_seconds": "11",
      "max_fee": "33",
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
      "token_transfers": [
        {
          "account": "0.0.200",
          "amount": 200,
          "token_id": "0.0.90000"
        },
        {
          "account": "0.0.300",
          "amount": -1200,
          "token_id": "0.0.90000"
        },
        {
          "account": "0.0.400",
          "amount": 1000,
          "token_id": "0.0.90000"
        }
      ],
      "nftTransfers": [
        {
          "senderAccount": "0.0.122",
          "receiverAccount": "0.0.121",
          "serialNumber": "0.0.124"
        }
      ]
    }
  ]
}
```

### Get Accounts

- Update `/api/v1/accounts` response to add nfts

```json
{
  "accounts": [
    {
      "balance": {
        "timestamp": "0.000002345",
        "balance": 80,
        "tokens": [
          {
            "token_id": "0.15.3",
            "balance": 80
          },
          {
            "token_id": "0.2.5",
            "balance": 50
          }
        ],
        "nftTypes": [
          {
            "id": "0.0.123",
            "nfts": [
              {
                "serialNumber": "0.0.124",
                "memo": "NFT1"
              },
              {
                "serialNumber": "0.0.124",
                "memo": "NFT2"
              }
            ]
          }
        ]
      },
      "account": "0.0.8",
      "expiry_timestamp": null,
      "auto_renew_period": null,
      "key": null,
      "deleted": false
    }
  ],
  "links": {
    "next": null
  }
}
```

### Get Balances

- Update `/api/v1/balances` response to add token balances

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
          "token_id": "0.15.3",
          "balance": 80
        }
      ],
      "nftTypes": [
        {
          "id": "0.0.123",
          "nfts": [
            {
              "serialNumber": "0.0.124",
              "memo": "NFT1"
            },
            {
              "serialNumber": "0.0.124",
              "memo": "NFT2"
            }
          ]
        }
      ]
    },
    {
      "account": "0.0.13",
      "balance": 100,
      "tokens": [
        {
          "token_id": "0.15.3",
          "balance": 80
        },
        {
          "token_id": "0.2.4",
          "balance": 50
        }
      ]
    }
  ],
  "links": {
    "next": null
  }
}
```

### List Tokens

GET `/api/v1/tokens`

```json
{
  "tokens": [
    {
      "token_id": "0.0.1000",
      "symbol": "F",
      "fungible": true
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
        "0.0.1002",
        "0.0.1003"
      ],
      "admin_key": {
        "_type": "ED25519",
        "key": "31c4647554640c464c854337570217269a1fc0f8bc30591c349a410269090920"
      }
    }
  ]
}
```

Add boolean filter `fungible` to only show NFTs or FTs

### Get Token by id

GET `/api/v1/tokens/{id}`

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
  "serial_numbers": [
    "0.0.1002",
    "0.0.1003"
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

### List NFTs

GET `/api/v1/nfts`

```json
{
  "nfts": [
    {
      "token_id": "0.0.123",
      "serial_number": "0.0.124",
      "memo": "NFT",
      "created_timestamp": "1610682445.003266000"
    }
  ]
}
```

Optional Filters

- `/api/v1/nfts?serialNumber.id=gt:0.0.1001` - All serial numbers in range
- `/api/v1/nfts?token.id=0.0.1000` - All NFTs belonging to the given token type.
- `/api/v1/nfts?order=desc` - All NFTs in descending order of `serial_number`
- `/api/v1/nfts?limit=x` - All NFTs taking the first `x` number of NFTs

### Get NFT by id

GET `/api/v1/nfts/{serialNumber}`

// Maybe we show holder?

```json
{
  "auto_renew_account": "0.0.6",
  "auto_renew_period": null,
  "expiry_timestamp": null,
  "freeze_default": false,
  "freeze_key": {
    "_type": "ProtobufEncoded",
    "key": "9c2233222c2233222c2233227d"
  },
  "kyc_key": {
    "_type": "ProtobufEncoded",
    "key": "9c2233222c2233222c2233227d"
  },
  "memo": "MY FIRST NFT",
  "supply_key": {
    "_type": "ProtobufEncoded",
    "key": "9c2233222c2233222c2233227d"
  },
  "tokenId": "0.0.123",
  "serialNumber": "0.0.124",
  "memo": "NFT",
  "created_timestamp": "1610682445.003266000",
  "wipe_key": {
    "_type": "ProtobufEncoded",
    "key": "9c2233222c2233222c2233227d"
  },
  "modified_timestamp": "1618510697.682451000"
}
```

### Get NFT transaction history

GET `/api/v1/nfts/{serialNumber}/transactions`

```json
{
  "history": [
    {
      "consensus_timestamp": "1618591023.997420020",
      "transaction_type": "TOKENBURN"
    },
    {
      "consensus_timestamp": "1618591023.997420010",
      "receiver_account": "0.0.11",
      "sender_account": "0.0.10",
      "transaction_type": "CRYPTOTRANSFER"
    },
    {
      "consensus_timestamp": "1618591023.997420000",
      "transaction_type": "TOKENCREATION"
    }
  ]
}
```

## Monitor

- Update
- Add support for NFT Create (new `TransactionSupplier` and `TransactionType`), which will be similar to NftType Create,
  but it will require an NftType to be created beforehand via an expression pattern.
- Add support for transfering NFTs in `CryptoTransferTransactionSupplier`. This will require custom logic, as a user can
  only transfer an NFT once unless it is transfered back to them.
  - Initial thought is to have the supplier swap the sender and receiver each time to transfer the NFT back and forth.
    This would likely limit performance as the swap has to be synchronized (or we just let the transactions fail when
    double-transfers occur).
    - It may benefit to have a list of NFTs so that the swap happens less frequently, but this would require logic to
      pick the NFT from the list and when to swap (would probably still need to all be synchronized)
  - Alternatively we could create a new NFT for each transaction. This seems like an even worse performance hit however.
- Add new expression patterns for NftType and NFT. NftType should create a new NftType, NFT should create a new NftType
  and then a new NFT with that NftType.

// Still need clarification on update, delete, association.

## Acceptance Tests

Add an acceptance test that tests the schedule transaction flow end to end:

# Questions:

1. NFTs are no longer created as part of NftType creation, they are only created after in a separate transaction,
   correct?

2. Are NftType ids and serial numbers both entity ids?

3. Is the NFT expiration still inherited from the NftType?

4. Is it required to associate an NFT to an account through an association transaction?

5. How will transferring an NFT out of an account be shown in the ensuing balance file?

6. Are bullet 2 and 3 in the `Transactions` section two different transaction types? I'm not sure
   if/how `create a new NFT for a given NftType` and `Create a new NFT in the treasury account of a given NftType`
   differ.

7. Does an NftType and NFT update exist? A delete?

8. Autorenew period on any of this? Memo on NftType?

Associate token type

1. Balance file, what happens on a burn?
2.
