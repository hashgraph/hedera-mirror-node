# Nonfungible Tokens (NFT)

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
  - TBA

- Add to `token` table fields `maxSupply` (long) and `fungible` (boolean)
- Add a new `nft` table

```sql
create table if not exists nft
(
  created_timestamp     bigint  primary key not null,
  deleted               boolean             not null,
  hash                  bytea               not null,
  modified_timestamp    bigint              not null,
  memo                  text    default ''  not null,
  serial_number         bigint              not null,
  token_id              bigint              not null,
);

```

- Add a unique constraint to `nft` for `serial_number` and `created_timestamp`, desc

- Add a new `nft_transfer` table

```sql
create table if not exists nft_transfer
(
  consensus_timestamp   bigint  not null,
  receiver_account_id   bigint  not null,
  sender_account_id     bigint  not null,
  serial_number         bigint  not null,
  token_id              bigint  not null,
);
```

- Add a unique constraint to `nft_transfer` for `serial_number` and `consensus_timestamp`, desc

- Add a new `nft_balance` table

```sql
create table if not exists nft_balance
(
  account_id          bigint    not null,
  consensus_timestamp bigint    not null,
  serial_number       bigint    not null,
  token_id            bigint    not null,
);
```

- Add a unique constraint to `nft_transfer` for `serial_number` and `consensus_timestamp`, desc

### Importer

#### Converter

Add an `NftIdConverter`.

#### Domain

- Add new fields to `Token` domain object from schema changes
- Add an `Nft` domain object with the same fields as the schema.
- Add an `NftTransfer` domain object with the same fields as the schema.
- Add an `NftBalance` domain object with the same fields as the schema.
- Add an `NFT` enum value to `EntityTypeEnum`.
- Add `NftBalance` list to `AccountBalances` domain object

#### Balance Parsing

Need information on file format. Effectively envision:

- Update `ProtoBalanceFileReader` to handle new NFT transfer list
  - Either deprecate use of the csv file or also add support for new CSV `BalanceFileReader` based on new format
  - Add `NftBalance` to the `AccountBalance` object as they are read.
- Update `AccountBalanceFileParser` to persist the `NftBalance`

#### Entity Listener

- Add `onNft`
- Add `onNftTransfer`

#### Entity Record Item Listener

- If transaction is successful, persist any NFT Transfers.
- `insertTokenCreate()` must be updated to create and persist the `NFT` objects and entities.
- `insertTokenMint()` must be updated to create and persist the `NFT` objects and entities.
- `insertTokenBurn` must be updated to mark the NFT entities as deleted.
- `insertTokenDelete` must be added to mark the NFT entities as deleted.
- `insertTokenWipe` must be updated to mark the NFT entities as deleted.

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
      "name": "TOKENTRANSFER",
      "node": "0.0.3",
      "nft_transfers": [
        {
          "sender_account": "0.0.122",
          "receiver_account": "0.0.121",
          "serial_number": "0.0.124",
          "token_id": "0.0.123"
        }
      ],
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
            "token_id": "0.15.3"
          },
          {
            "balance": 50,
            "token_id": "0.2.5"
          },
          {
            "token_id": "0.2.9",
            "serial_numbers": [
              "0.2.10",
              "0.2.11"
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
          "token_id": "0.15.3",
          "balance": 80
        },
        {
          "token_id": "0.2.9",
          "serial_numbers": [
            "0.2.10",
            "0.2.11"
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
        {
          "token_id": "0.15.6",
          "serial_numbers": [
            "0.15.7"
          ]
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

### Token Supply distribution

- Update `/api/v1/tokens/{id}/balances` response to show `serial_numbers` when the token is an NftType token.

```json
    {
  "timestamp": "0.000002345",
  "balances": [
    {
      "account": "0.15.10",
      "serial_numbers": [
        "0.0.1002",
        "0.0.1003"
      ]
    },
    {
      "account": "0.15.9",
      "serial_numbers": [
        "0.0.1004",
        "0.0.1005"
      ]
    }
  ],
  "links": {
    "next": null
  }
}
```

#### List NFTs

- GET `/api/v1/nfts` will list basic information of all NFTs, including the NftType they belong to.

```json
{
  "nfts": [
    {
      "token_id": "0.0.123",
      "serial_number": "0.0.124",
      "memo": "NFT",
      "created_timestamp": "1610682445.003266000"
    }
  ],
  "links": {
    "next": null
  }
}
```

Optional Filters

- `/api/v1/nfts?serialNumber.id=gt:0.0.1001` - All serial numbers in range
- `/api/v1/nfts?token.id=0.0.1000` - All NFTs belonging to the given token type.
- `/api/v1/nfts?order=desc` - All NFTs in descending order of `serial_number`
- `/api/v1/nfts?limit=x` - All NFTs taking the first `x` number of NFTs

#### Get NFT by id

- GET `/api/v1/nfts/{serialNumber}` will show more detailed information about an individual NFT.

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
  "hash": "123123123123A",
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
    https: //hedera.zoom.us/j/8826097631?pwd=VUxmeW13Y0luREJuWmwxRFpHZzQvUT09
    "_type"
    :
    "ProtobufEncoded",
    "key": "9c2233222c2233222c2233227d"
  },
  "modified_timestamp": "1618510697.682451000"
}
```

#### Get NFT transaction history

- GET `/api/v1/nfts/{serialNumber}/transactions` will return minimal transaction information for each tranasction
  involving a given NFT.

```json
{
  "transactions": [
    {
      "consensus_timestamp": "1618591023.997420020",
      "transaction_id": "0.0.19789-1618805680-742097947",
      "transaction_type": "TOKENBURN"
    },
    {
      "consensus_timestamp": "1618591023.997420021",
      "receiver_account": "0.0.11",
      "sender_account": "0.0.10",
      "transaction_id": "0.0.19789-1618805680-742097948",
      "transaction_type": "CRYPTOTRANSFER"
    },
    {
      "consensus_timestamp": "1618591023.997420022",
      "transaction_id": "0.0.19789-1618805680-742097949",
      "transaction_type": "TOKENCREATION"
    }
  ],
  "links": {
    "next": null
  }
}
```

### Monitor

- Make changes to the `ExpressionConverter`
  - Burning and minting NFT tokens will require both the Token id and the serial numbers to execute. This could also be
    true for transfering the NFTs. This requires the `convertedProperties` to know which serial numbers belong to which
    Tokens when creating them. Two different approaches could be take for this.
    - Create two new expressions, `nft` that would create a new token with the fungible flag set to true , and a
      compound `nft.serial` expression (e.g. `nft.1.serial.1`) that would mint a new NFT serial number for the
      equivalent `nft` in the map.
      - This could be problematic if the `nft.serial` expression is processed before the `nft` expression. It may
        require some restructuring of how `ExpressionConverter` works.
    - Create an `nft` expression to create the token type and a preset set of serial numbers in one transaction. Change
      the map to hold Objects so that the serial numbers and the token id can be tied together, and the transaction
      suppliers would have logic to use the objects tied to that field (in this case, `TokenBurnTransactionSupplier`
      would have to unpack the Object's token id and serial numbers).
      - This option would be less customizable, since users cannot specify how many serial numbers to mint.
- Update `TokenCreateTransactionSupplier`, `TokenBurnTransactionSupplier`, and `TokenMintTransactionSupplier` to support
  NFT creation and deletion
  - All will need to support an optional list of serial numbers.
  - `TokenCreateTransactionSupplier`and `TokenMintTransactionSupplier` may need more depending on the final SDK design.
- Add support for transfering NFTs in `CryptoTransferTransactionSupplier`.
  - Add a new `fungible` boolean attribute to be used when doing a TOKEN or BOTH transfer, that determines whether to
    use the `amount` attribute or the `serial numbers` list.
  - Because a serial number can only be transfered once out of a given account (unless it is transfered back), custom
    logic will be needed for performant NFT transfers.
    - Initial thought is to have the supplier swap the sender and receiver each time to transfer the NFT back and forth.
      This could be done only on the NFT transfer or on the entire transfer. This would likely limit performance as the
      swap has to be synchronized (or we just let the transactions fail when double-transfers occur).
      - This would also require different signatures on the transaction, which would require reworking how we handle the
        transaction signing.
    - Alernatively, the `ExpressionConverter` could just generate a large amount of serial numbers, and
      the `CryptoTransferTransactionSupplier` could just transfer one at a time via a counter until it runs out. This
      would be a much simpler approach, but it has obvious limitations.

### Acceptance Tests

Add acceptance tests that verify all transactions are handled appropriately. This includes tests for

- createToken with an NFT
- mintToken with an NFT
- burnToken with an NFT
- deleteToken with an NFT
- wipeTokenAccount with an NFT
- cryptoTransfer with an NFT

# Outstanding Questions and Concerns:

1. General protobuf questions.
