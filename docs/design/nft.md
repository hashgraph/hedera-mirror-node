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

- Update `t_entity_types` to add `NFTTYPE` and `NFT` entities.
- Update `t_transaction_types` to add new NFT transaction types
- Update `t_transaction_results` with new response codes
  - TBA
- Add a new `nft_type` table

```sql
create table if not exists nft_type
(
  treasury_account_id   bigint              not null,
  nft_type_id           bigint              not null,
  mint_burn_key         bytea
  created_timestamp     bigint  primary key not null,
  modified_timestamp    bigint,
  expiration?
  memo?
);
```

- Add a unique constraint to `nft_type` for `nft_type_id` and `created_timestamp`, desc
- Add a new `nft` table

```sql
create table if not exists nft
(
  serial_number         bigint              not null,
  nft_type_id           bigint              not null,
  created_timestamp     bigint  primary key not null,
  modified_timestamp    bigint
);

```

- Add a unique constraint to `nft` for `serial_number` and `created_timestamp`, desc

- Add a new `nft_transfer` table
  - //QUESTION: This could be split into 2 transfers like crypto and token, but because you can only transfer 1 token
    and there are no fees, you can always guarantee the sender/receiver relationship. Halves the number of rows.

```sql
create table if not exists nft_transfer
(
  serial_number         bigint              not null,
  consensus_timestamp   bigint  primary key not null,
  receiver_account_id   bigint              not null,
  sender_account_id     bigint              not null,
);
```

- Add a unique constraint to `nft_transfer` for `serial_number`, `consensus_timestamp`, and `receiver_account_id`, desc
- Add a unique constraint to `nft_transfer` for `serial_number`, `consensus_timestamp`, and `sender_account_id`, desc

- Add a new `nft_holder` table

```sql
create table if not exists nft
(
  nft_id              bigint             not null,
  account_id          bigint             not null,
  consensus_timestamp bigint primary key not null,
);
```

- //INDEXES

### Importer

#### Converter

Add an `NftTypeIdConverter` and an `NftIdConverter`.

### Domain

- Add an `NftType` domain object with the same fields as the schema.
- Add an `Nft` domain object with the same fields as the schema.
- Add an `NftTransfer` domain object with the same fields as the schema.
- Add an `NftHolder` domain object with the same fields as the schema.
- Add an `NFT` and an `NFTTYPE` enum value to `EntityTypeEnum`.

### Balance Parsing

Need information on file format. Effectively envision:

- For each NFT listed for account, upsert `NftHolder`.

### Entity Listener

- Add `onNftType(Schedule schedule)`
- Add `onNft(ScheduleSignature scheduleSignature)`
- Add `onNftAccount` ?Name?
- Add `onNftTransfer`

### Transaction Handler

- Add `NftTypeCreateTransactionHandler` that updates the entity to set the admin key and expiry . //Autorenew?
- Add `NftCreateTransactionHandler` that updates the entity to set memo. //Autorenew? Expiry subject to inheritance
  question.

### Entity Record Item Listener

- `TransactionBody.hasNftTypeCreation()` and parse `NftTypeCreateTransactionBody` from the record, create a
  new `NftType`
- `TransactionBody.hasNftCreation()` and parse `NftCreateTransactionBody` from the record, create a new `Nft`
- If is successful and `entityProperties.getPersist().isNfts()`, insert `NftTransfer` for each in
  record `getNftTransfers` list.
- Still need info for if delete, associate, update exist.

## REST API

### Get Transaction

- Update `/api/v1/transactions` response to add nft transfers
- Add a filter by serial number to track the history of a particular NFT
- //Should we format this as nftType lists or individual nft types.

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

### List NFTTypes

GET `/api/v1/nftTypes`

Should there be the memo field in the NFT?

```json
{
  "nftTypes": [
    {
      "id": "0.0.123",
      "adminKey": {
        "_type": "ED25519",
        "key": "KEY"
      },
      "nfts": [
        {
          "serialNumber": "0.0.124",
          "memo": "NFT"
        }
      ]
    },
    {
      "id": "0.0.125",
      "adminKey": {
        "_type": "ED25519",
        "key": "KEY"
      },
      "nfts": [
        {
          "serialNumber": "0.0.126",
          "memo": "NFT"
        },
        {
          "serialNumber": "0.0.127",
          "memo": "NFT"
        }
      ]
    }
  ]
}
```

### Get NFT by id

GET `/api/v1/nftTypes/{id}`

Should there be the memo field in the NFT?

```json
{
  "id": "0.0.123",
  "mintBurnKey": {
    "_type": "ED25519",
    "key": "KEY"
  },
  "adminKey": {
    "_type": "ED25519",
    "key": "KEY"
  },
  "expiration": 1000000000,
  "nfts": [
    {
      "tokenId": "0.0.124",
      "memo": "NFT"
    }
  ]
}
```

### List NFTs

GET `/api/v1/nfts`

```json
{
  "nfts": [
    {
      "nftTypeId": "0.0.123",
      "serialNumber": "0.0.124",
      "memo": "NFT",
      "creationDate": ""
    }
  ]
}
```

### Get NFT by id

GET `/api/v1/nfts/{serialNumber}`

// Maybe we show holder?

```json
{
  "nftTypeId": "0.0.123",
  "serialNumber": "0.0.124",
  "memo": "NFT",
  "creationDate": ""
}
```

## Monitor

## Acceptance Tests

Questions:

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
