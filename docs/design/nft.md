# Non-Fungible Tokens (NFTs)

## Purpose

NFTs allow users to create tokens that each have unique data and can be individually tracked. This document explains how
the mirror node can be updated to add support for NFTs.

## Goals

- Support whole, singleton, and fractional NFTs
- Ingest NFT transactions and persist to the database
- Expose NFT info and transfers via REST APIs
- Monitor NFTs

## Non Goals

- Support hybrid NFTs (NFTs with a fungible child token to represent a fungible component)
- Provide an NFT gRPC API

## Architecture

### Database

- Update `t_transaction_results` with new response codes

- Add to `token` table fields `type` (enum, values `FUNGIBLE_COMMON` and `NON_FUNGIBLE_UNIQUE`), `supply_type` (
  enum, values `INFINITE` and `FINITE`), and `max_supply` (bigint).
  - Default values will be `FUNGIBLE_COMMON`, `INFINITE`, and max long, respectively.

- Add a new `nft` table

```sql
create table if not exists nft
(
  account_id            bigint                  not null,
  created_timestamp     bigint                  not null,
  deleted               boolean default false   not null,
  modified_timestamp    bigint                  not null,
  metadata              bytea   default ''      not null,
  serial_number         bigint                  not null,
  token_id              bigint                  not null
);

create unique index if not exists nft__token_id_serial_num
  on nft (token_id desc, serial_number desc);
```

- Add a unique constraint to `nft` for `token_id` and `serial_number`, desc

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

create unique index if not exists nft_transfer__timestamp_token_id_serial_num
  on nft_transfer (consensus_timestamp desc, token_id desc, serial_number desc);
```

- Add a unique constraint to `nft_transfer` for `consensus_timestamp`, `token_id`, and `serial_number`, desc

### Importer

#### Domain

- Add new fields to `Token` domain object from schema changes
- Add an `Nft` domain object with the same fields as the schema.
- Add an `NftTransfer` domain object with the same fields as the schema.

#### Entity Listener

- Add `onNft`
- Add `onNftTransfer`

#### Entity Record Item Listener

- If transaction is successful, persist any NFT Transfers.
- `insertTokenCreate()` must be updated to set `tokenType`, `tokenSupplyType` and `maxSupply`
- `insertTokenMint()` must be updated to handle the new field `metadata` and create and persist the `NFTs` if type
  is `NON_FUNGIBLE_UNIQUE`. Multiple `NFTs` can be minted in one transaction.
- `insertTokenBurn` must be updated to handle the new field `serialNumbers`, mark the `NFTs` as deleted, and
  update `modifiedTimestamp` if the `serialNumbers` list is set.
- `insertTokenWipe` must be updated to handle the new field `serialNumbers`, mark the `NFTs` as deleted and
  update `modifiedTimestamp` if the `serialNumbers` list is set.

### REST API

#### Get Transaction

- Update `/api/v1/transactions/{id}` response to add nft_transfers
  - `/api/v1/transactions` should not contain nft_transfers, as moving forward we wish to not add more joins to this
    endpoint.

```json
{
  "transactions": [
    {
      "consensus_timestamp": "1234567890.000000001",
      "charged_tx_fee": 7,
      "max_fee": "33",
      "memo_base64": null,
      "name": "CRYPTOTRANSFER",
      "nft_transfers": [
        {
          "receiver_account_id": "0.0.121",
          "sender_account_id": "0.0.122",
          "serial_number": 124,
          "token_id": "0.0.123"
        }
      ],
      "node": "0.0.3",
      "result": "SUCCESS",
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

#### List Tokens

- Update `/api/v1/tokens` response to show NFTs by adding the `type` field.

```json
{
  "tokens": [
    {
      "token_id": "0.0.1000",
      "symbol": "F",
      "type": "FUNGIBLE_COMMON",
      "admin_key": {
        "_type": "ED25519",
        "key": "31c4647554640c464c854337570217269a1fc0f8bc30591c349a410269090920"
      }
    },
    {
      "token_id": "0.0.10001",
      "symbol": "N",
      "type": "NON_FUNGIBLE_UNIQUE",
      "admin_key": {
        "_type": "ED25519",
        "key": "31c4647554640c464c854337570217269a1fc0f8bc30591c349a410269090920"
      }
    }
  ]
}
```

Add optional filters

- `/api/v1/tokens?type=FUNGIBLE_COMMON` - All fungible tokens (other values are `NON_FUNGIBLE_UNIQUE` and `ALL` (
  default))

#### Get Token by id

- Update `/api/v1/tokens/{id}` response to show NFTs by adding the `type` field. Also display the new `supply_type`
  and `max_supply` fields.

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
  "initial_supply": "2",
  "kyc_key": {
    "_type": "ProtobufEncoded",
    "key": "9c2233222c2233222c2233227d"
  },
  "max_supply": "10",
  "name": "FOO COIN TOKEN",
  "supply_key": {
    "_type": "ProtobufEncoded",
    "key": "9c2233222c2233222c2233227d"
  },
  "supply_type": "FINITE",
  "symbol": "FOOCOIN",
  "token_id": "0.15.3",
  "type": "NON_FUNGIBLE_UNIQUE",
  "total_supply": "2",
  "treasury_account": "0.15.10",
  "wipe_key": {
    "_type": "ProtobufEncoded",
    "key": "9c2233222c2233222c2233227d"
  }
}
```

#### List NFTs

- GET `/api/v1/tokens/{id}/nfts` will list basic information of all NFTs for a given token.
  - This endpoint should return a 409 for tokens that are not of type `NON_FUNGIBLE_UNIQUE` with a message that
    indicates that this endpoint is not valid for this token type.
  - `metadata` should be base64 encoded before returning.
  - `deleted` should be true if either the nft or the token is deleted

```json
{
  "nfts": [
    {
      "account_id": "0.0.111",
      "created_timestamp": "1610682445.003266000",
      "deleted": false,
      "metadata": "VGhpcyBpcyBhIHRlc3QgTkZU",
      "modified_timestamp": "1610682445.003266001",
      "serial_number": 124,
      "token_id": "0.0.222"
    }
  ],
  "links": {
    "next": null
  }
}
```

Optional Filters

- `/api/v1/tokens/{id}/nfts?account.id=0.0.111` - All NFTs belonging to a given account id.
- `/api/v1/tokens/{id}/nfts?limit=x` - All NFTs taking the first `x` number of NFTs
- `/api/v1/tokens/{id}/nfts?order=desc` - All NFTs in descending order of `serial_number`
- `/api/v1/tokens/{id}/nfts?serialNumber=gt:1001` - All serial numbers in range

#### Get NFT by id

- GET `/api/v1/tokens/{id}/nfts/{serialNumber}` will show information about an individual NFT.
  - `metadata` should be base64 encoded before returning.
  - `deleted` should be true if either the nft or the token is deleted

```json
{
  "account_id": "0.0.111",
  "created_timestamp": "1610682445.003266000",
  "deleted": false,
  "metadata": "VGhpcyBpcyBhIHRlc3QgTkZU",
  "modified_timestamp": "1610682445.003266000",
  "serial_number": 124,
  "token_id": "0.0.222"
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
    - Creating an NFT requires the `tokenType` field to be set to `NON_FUNGIBLE_UNIQUE`, as well as not
      setting `decimals` and `initialSupply`.
  - Update the `token` expression logic to set the new fields (`tokenType`, `tokenSupplyType`, and `maxSupply`).

- Add logic to support list properties for scenarios. Currently a YAML list is read in as separate properties,
  e.g., `transferTypes.0`, `transferTypes.1`.

- Update the `TransactionSuppliers`
  - `TokenCreateTransactionSupplier` will need an enum `type` attribute (with values `FUNGIBLE_COMMON` and
    `NON_FUNGIBLE_UNIQUE`), an enum `supplyType` (with values `INFINITE` and `FINITE`), and a long `maxSupply`
    attribute.
    - Some fields will now have stricter requirements now, such as for NFTs `decimals` has to be 0, as
      does `initialSupply`. Add logic to enforce these based on the `type` and `supplyType` enums.
  - `TokenMintTransactionSupplier` will need an enum `type` attribute, as well as a `metadata` string and
    a `metadataSize` int (to generate metadata if not set, similar to how `ConsensusSubmitMessageTransactionSupplier`
    works). The supplier should only set `amount` for fungible tokens, and only `metadata` for NFTs.
  - `TokenBurnTransactionSupplier` and `TokenWipeTransactionSupplier` will need an enum `type` attribute, and
    a `serialNumber` AtomicLong field that the supplier will start at and increment as serial numbers are wiped/burned.

- Add support for transferring NFTs in `CryptoTransferTransactionSupplier`.
  - Add a new `transferType` `NFT` that will allow for transferring NFTs, and remove `BOTH`.
  - Change the `transferTypes`property to be an `EnumSet` that will allow for any combination of transfers.
  - Add a `transferNftTokenId` field so that a `TOKEN` and `NFT` scenario can occur with two different tokens.
  - Add a `serialNumber` AtomicLong field, and write logic to increment the value every time a transfer is added.

### Acceptance Tests

Add acceptance tests that verify all transactions are handled appropriately. This includes tests for

1. Basic NFT transfer flow

- Create a token with type `NON_FUNGIBLE_UNIQUE`
- Mint a serial number
- Associate the token to an account
- Transfer the serial number to the account.
- Verify response codes and data from `/tokens`, `/tokens/{id}/nfts`,
  `/tokens/{id}/nfts/{serialNumber}/transactions`, and `/transactions`,

2. NFT Burn/Wipe/Delete flow

- Create a token with type `NON_FUNGIBLE_UNIQUE`
- Mint three serial numbers, associate the token with
- Associate the token to an account
- Transfer one serial number to the account.
- Wipe the serial number from the account.
- Burn a serial number from the treasury account.
- Delete the token
- Verify response codes and data from `/tokens`, `/tokens/{id}/nfts`,
  `/tokens/{id}/nfts/{serialNumber}/transactions`, and `/transactions`

3. Scheduled NFT transfer flow

- Create a token with type `NON_FUNGIBLE_UNIQUE`
- Mint a serial number
- Create an account with receiverSigRequired=true
- Associate the token to an account
- Create a schedule of CRYTOTRANSFER to transfer the serial number
- Submit a ScheduleSign from the receiving account
- Verify response codes and data from /tokens, /tokens/{id}/nfts, /tokens/{id}/nfts/{serialNumber}/transactions,
  /schedules/{id}, and /transactions
