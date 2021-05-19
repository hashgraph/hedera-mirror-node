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

- Add to `token` table fields `type` (enum, values `FUNGIBLE` and `NON_FUNGIBLE`) and `max_supply` (bigint),
  - Default values will be `FUNGIBLE` and max long, respectively.

- Add a new `nft` table

```sql
create table if not exists nft
(
  account_id            bigint                  not null
  created_timestamp     bigint  primary key     not null,
  deleted               boolean default false   not null,
  modified_timestamp    bigint                  not null,
  memo                  text    default ''      not null,
  serial_number         bigint                  not null,
  token_id              bigint                  not null
);

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
- `insertTokenCreate()` must be updated to set `tokenType` and `maxSupply`
- `insertTokenMint()` must be updated to handle the new field `amountOrMemo` create and persist the `NFTs` if the memo
  is set.
- `insertTokenBurn` must be updated to handle the new field `amountOrSerialNumbers`, mark the `NFTs` as deleted, and
  update `modifiedTimestamp` if the `serialNumbers` list is set.
- `insertTokenWipe` must be updated to handle the new field `amountOrSerialNumbers`, mark the `NFTs` as deleted and
  update `modifiedTimestamp` if the `serialNumbers` list is set.

### REST API

#### Get Transaction

- Update `/api/v1/transactions` and `/api/v1/transactions/{id}` response to add nft_transfers

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
      "type": "FUNGIBLE",
      "admin_key": {
        "_type": "ED25519",
        "key": "31c4647554640c464c854337570217269a1fc0f8bc30591c349a410269090920"
      }
    },
    {
      "token_id": "0.0.10001",
      "symbol": "N",
      "type": "NON_FUNGIBLE",
      "admin_key": {
        "_type": "ED25519",
        "key": "31c4647554640c464c854337570217269a1fc0f8bc30591c349a410269090920"
      }
    }
  ]
}
```

Add optional filters

- `/api/v1/tokens?type=FUNGIBLE` - All fungible tokens (other values are `NON_FUNGIBLE` and `ALL` (default))

#### Get Token by id

- Update `/api/v1/tokens/{id}` response to show NFTs by adding the `type` field.

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
  "symbol": "FOOCOIN",
  "token_id": "0.15.3",
  "type": "NON_FUNGIBLE",
  "total_supply": "2",
  "treasury_account": "0.15.10",
  "wipe_key": {
    "_type": "ProtobufEncoded",
    "key": "9c2233222c2233222c2233227d"
  }
}
```

### Token Supply distribution

- Update `/api/v1/tokens/{id}/balances` response to return an error code 409 for `NON_FUNGIBLE` tokens that indicates
  users should go to `/api/v1/tokens/{id}/nfts`.

#### List NFTs

- GET `/api/v1/tokens/{id}/nfts` will list basic information of all NFTs for a given token.
  - NFTs should only display if the token has not been deleted (e.g. `token.deleted` is false) Otherwise, display empty
    list.
  - `account_id` should not display when the NFT has been deleted.
  - This endpoint should return a 409 for `FUNGIBLE` tokens with a message that indicates users should go
    to `/api/v1/tokens/{id}/balances`.

```json
{
  "nfts": [
    {
      "account_id": "0.0.111",
      "created_timestamp": "1610682445.003266000",
      "deleted": false,
      "memo": "This is a test NFT",
      "modified_timestamp": "1610682445.003266001",
      "serial_number": 124
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
- `/api/v1/tokens/{id}/nfts?serialNumber=gt:0.0.1001` - All serial numbers in range

#### Get NFT by id

- GET `/api/v1/tokens/{id}/nfts/{serialNumber}` will show information about an individual NFT.
  - `account_id` should not display when the NFT or Token has been deleted.

```json
{
  "account_id": "0.0.111",
  "created_timestamp": "1610682445.003266000",
  "deleted": false,
  "memo": "This is a test NFT",
  "modified_timestamp": "1610682445.003266000",
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
  - Update the `token` expression logic to set the new fields (`tokenType` and `maxSupply`).

- Update the `TransactionSuppliers`
  - `TokenCreateTransactionSupplier` will need an enum `tokenType` attribute (with values FUNGIBLE and NON_FUNGIBLE) and
    a long `maxSupply` attribute.
    - Some fields will now have stricter requirements now, such as for NFTs `decimals` has to be 0, as
      does `initialSupply`. We could simply abide by the user config values, regardless of if they are valid, but most
      likely we should have logic to enforce those requirements based on the `tokenType`.
  - `TokenMintTransactionSupplier` will need an enum `tokenType` attribute, as well as a String `memo` attribute. The
    supplier should only set `amount` for fungible tokens, and only `memo` for NFTs
    - Alternatively, the `tokenType` can be removed, and the supplier just sets whichever of `memo` or `amount` is set,
      but this relies on users configuring things correctly.
  - `TokenBurnTransactionSupplier` and `TokenWipeTransactionSupplier` will need an enum `tokenType` attribute, and it
    will need to set the `serialNumbers` list for NFTs (hardcoded based on the `initialSupply` value used in
    the `ExpressionConverter`)
    - The list could be configurable using the compound expression mentioned earlier if desired.

- Add support for transfering NFTs in `CryptoTransferTransactionSupplier`.
  - Add a new `tokenType` enum attribute to be used when doing a TOKEN or BOTH transfer, that determines whether to use
    the `amount` attribute or the `serial numbers` list.
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
  `/tokens/{id}/nfts/{serialNumber}/transactions`, and `/transactions`,

2. NFT Burn/Wipe/Delete flow

- Create a token with type `NON-FUNGIBLE`
- Mint three serial numbers, associate the token with
- Associate the token to an account
- Transfer one serial number to the account.
- Wipe the serial number from the account.
- Burn a serial number from the treasury account.
- Delete the token
- Verify response codes and data from `/tokens`, `/tokens/{id}/nfts`,
  `/tokens/{id}/nfts/{serialNumber}/transactions`, and `/transactions`

3. Scheduled NFT transfer flow

- Create a token with type `NON-FUNGIBLE`
- Mint a serial number
- Create an account with receiverSigRequired=true
- Associate the token to an account
- Create a schedule of CRYTOTRANSFER to transfer the serial number
- Submit a ScheduleSign from the receiving account
- Verify response codes and data from /tokens, /tokens/{id}/nfts, /tokens/{id}/nfts/{serialNumber}/transactions,
  /schedules/{id}, and /transactions

# Outstanding Questions and Concerns:

1. How will NFT balances be represented in the balance file?
2. Will NFT balances be added to the csv version of the balance file, or just the proto version?
