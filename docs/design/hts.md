# Hedera Token Service Design

## Purpose

The Hedera Token Service (HTS) builds upon the Cryptocurrency Service to provide decentralized issuance of custom tokens on the Hedera Network.
The behavior will be similar to that of the native HBAR token and as such the Mirror Node will persist token balances and transfer lists and support the retrieval of information through the API's.

## Goals
-   Ingest HTS related transactions and persist to the database
-   Ingest account balances with additional token details from the mainnet and persist to the database
-   Provide a HTS REST API to show token balance for account(s)
-   Provide a HTS REST API to return all tokens (Token Discovery)
-   Provide a HTS REST API to return all accounts holding a specific token (Token Supply Distribution)
-   Provide a HTS REST API to return all accounts h
-   Provide a streaming GRPC API to subscribe to TCS balance changes

## Non Goals

## Architecture

1. Downloader retrieves transactions and balances from stream files in cloud bucket and validates them
2. Parser persists to database
3. Client queries API token details
    - REST API retrieves token details and balances from database
    - GRPC API retrieves the current balances from database and returns to client
4. (If #3 was GRPC API) GRPC API is notified of new transfers via database Notify/streaming logic

## Alternatives

## Database

-   Update `account_balance` table with `tokens` column to store token balance lists e.g.
```json
{
  "tokens": [{
    "token": {
      "symbol": "FOOBARS",
        "balance": 100
    },
    "token": {
        "symbol": "FOOBARS",
        "balance": 100
      }
    }
  ]
}
```

or

```json
{
  "tokens": [{
    "symbol": "FOOBARS",
    "balance": 100
  },
  {
    "symbol": "FOOBARS",
    "balance": 100
  }]
}
```

```sql
alter table if exists account_balance
    add column tokens   json;
```

-   Add new `t_entity_types` row with name `token`

-   Add new `t_transaction_types`:
    -   `TokenCreate=56`
    -   `TokenCreate=56`
    -   `TokenTransact=57`
    -   `TokenGetInfo=58`
    -   `TokenFreezeAccount=59`
    -   `TokenUnfreezeAccount=60`
    -   `TokenGrantKycToAccount=61`
    -   `TokenRevokeKycFromAccount=62`
    -   `TokenDelete=63`
    -   `TokenUpdate=64`
    -   `TokenMint=65`
    -   `TokenBurn=66`
    -   `TokenAccountWipe=67`

-   Add new `t_transaction_result`:
    -   `ACCOUNT_FROZEN_FOR_TOKEN=165`
    -   `TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED=166`
    -   `INVALID_TOKEN_ID=167`
    -   `INVALID_TOKEN_DIVISIBILITY=168	`
    -   `INVALID_TOKEN_FLOAT=169`
    -   `INVALID_TREASURY_ACCOUNT_FOR_TOKEN=170`
    -   `INVALID_TOKEN_SYMBOL=171`
    -   `TOKEN_HAS_NO_FREEZE_KEY=172`
    -   `TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN=173`
    -   `MISSING_TOKEN_SYMBOL=174`
    -   `TOKEN_SYMBOL_TOO_LONG=175`
    -   `TOKEN_SYMBOL_ALREADY_IN_USE=176`
    -   `INVALID_TOKEN_REF=177`
    -   `ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN=178`
    -   `TOKEN_HAS_NO_KYC_KEY=179`
    -   `INSUFFICIENT_TOKEN_BALANCE=180`
    -   `TOKEN_WAS_DELETED=181`
    -   `TOKEN_HAS_NO_SUPPLY_KEY=182`
    -   `TOKEN_HAS_NO_WIPE_KEY=183`
    -   `INVALID_TOKEN_MINT_AMOUNT=184`
    -   `INVALID_TOKEN_BURN_AMOUNT=185`
    -   `ACCOUNT_HAS_NO_TOKEN_RELATIONSHIP=186`
    -   `CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT=187`
    -   `INVALID_KYC_KEY=188`
    -   `INVALID_WIPE_KEY=189`
    -   `INVALID_FREEZE_KEY=190`
    -   `INVALID_SUPPLY_KEY=191`
    -   `INVALID_TOKEN_EXPIRY=192`
    -   `TOKEN_HAS_EXPIRED=193`
    -   `TOKEN_IS_IMMUTABlE=194`

-   Add columns to `t_entities` table. Opting for a denormalized format to avoid costly joins
```sql
alter table if exists t_entities
    add column float            bigint
    add column divisibility     bigint
    add column treasury         entity_id
    add column kyc_key          bytea
    add column freeze_key       bytea
    add column wipe_key         bytea
    add column supply_key       bytea
    add column freeze_default   boolean
    add column kyc_default      boolean
    add column symbol           character varying(96)
    add column freeze_default   boolean;
```

-   Add columns to `crypto_transfer` table. Symbol can be pulled from a local cache since `TransactionRecord.tokenTransferLists` doesn't include symbol
```sql
alter table if exists crypto_transfer
    add column token_id entity_id
    add column symbol   character varying(96);
```

## GRPC API

### Protobuf

```proto
message TokenQuery {
    .proto.TokenID tokenID = 1; // A required token ID to retrieve transfers for.
    .proto.AccountID accountID = 2; // An account ID to retrieve transfers for.
    .proto.Timestamp consensusStartTime = 3; // Include messages which reached consensus on or after this time. Defaults to current time if not set.
    .proto.Timestamp consensusEndTime = 4; // Include messages which reached consensus before this time. If not set it will receive indefinitely.
    uint64 limit = 5; // The maximum number of messages to receive before stopping. If not set or set to zero it will return messages indefinitely.
}

message TokenResponse {
    .proto.Timestamp consensusTimestamp = 1; // The time at which the transaction reached consensus
    .proto.TokenTransfers transfer = 2; // Multiple list of AccountAmount pairs, each of which has an account and an amount to transfer into it (positive) or out of it (negative)
    string memo = 3; // The transaction memo. Any notes or descriptions that should be put into the record (max length 100)
    uint64 sequenceNumber = 4; // Starts at 1 for first submitted message. Incremented on each submitted message.
}

service TokenService {
    rpc subscribeTokenTransfers (TokenQuery) returns (stream TokenResponse);
}
```

## Importer

### Converter
-   Add `TokenBalanceSerializer` converter to handle object to JSON string serialization
```java
   @Named
   public class TokenBalanceSerializer extends JsonSerializer<List<TokenBalance>> {
       @Override
       public void serialize(List<TokenBalance> tokens, JsonGenerator gen, SerializerProvider serializers) throws IOException {
           if (value != null) {
               gen.writeStartArray();
               for (TokenBalance token: tokens) {
                   gen.writeStartObject();
                   gen.writeObjectField("token", token);
                   gen.writeEndObject();
               }
               gen.writeEndArray();
           }
       }
   }
   ```

or

```java
@Named
public class TokenBalanceSerializer extends JsonSerializer<List<TokenBalance>> {
    @Override
    public void serialize(List<TokenBalance> tokens, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value != null) {
            gen.writeStartArray();
            for (TokenBalance token: tokens) {
                gen.writeObject(token);
            }
            gen.writeEndArray();
        }
    }
}
```

### Domain

-   Update `AccountBalance` with `tokens` private class member
```java
public class AccountBalance {
    @JsonSerialize(using = TokenBalanceSerializer.class)
    private List<TokenBalance> tokens;
}
```

-   Update `CryptoTransfer` to have a `token_id` and `symbol` class members, to allow it to represent both HBARs and Tokens

-   Update `Entities` with private class members
```java
public class Entities {
    private Long initialSupply;
    private Long  divisibility;
    @Convert(converter = AccountIdConverter.class)
    private EntityId treasury;
    private byte[] kycKey;
    private byte[] freezeKey;
    private byte[] wipeKey;
    private byte[] supplyKey;
    private boolean freezeDefault;
    private boolean kycDefault;
    private String symbol;
}
```

-   Update `EntityTypeEnum` with `Token` type
```java
public enum EntityTypeEnum {

    ACCOUNT(1),
    CONTRACT(2),
    FILE(3),
    TOPIC(4),
    TOKEN(5);

    private final int id;
}
```

-   Add `TokenBalance` class
```java
public class TokenBalance implements Serializable {
    private String symbol;
    private Long balance;
}
```

### Balance Persistence

-   Update `AccountBalanceLineParser.parse` to parse additional token columns. Pull in symbols from header and create a list of `TokenBalance` objects and add to `AccountBalance.tokens`.
-   Update `AccountBalancesFileLoader` `INSERT_BALANCE_STATEMENT` with `tokens`
```java
private static final String INSERT_BALANCE_STATEMENT = "insert into account_balance " +
            "(consensus_timestamp, account_realm_num, account_num, balance, tokens) values (?, ?, ?, ?, ?) on conflict do " +
            "nothing;";
```

### Token Transfer Parsing

Modify `EntityRecordItemListener` to handle parsing HTS transactions

-   Modify `OnItem()` to check for `TransactionBody.hasTokenCreation`
-   Add `insertTokenCreateTransferList()` and `insertTokenTransferList()` to parse out `TransactionBody.tokenTransferLists` and call `entityListener.onEntityId` and `entityListener.onCryptoTransfer` on each account and `TokenTransferList`
-   Update `insertTransferList(...)` to take in `tokenID` from `TransactionReceipt.tokenId` and allow it to be shared by `insertTokenTransferList()` to parse out `TransactionBody.tokenTransferLists` and call `entityListener.onEntityId` and `entityListener.onCryptoTransfer` on each account and `TokenTransferList`

> _Note:_ There's an opportunity to refactor the `OnItem()` to focus better on different TransactionBody types but not necessarily within scope

## REST API

### Token Supply distribution

### Token Discovery

### Identify holders

### Transactions Endpoint
For easy integration with existing users the /transactions endpoint will be updated to expose tokens in `tokenTransfers`
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
      "name": "CRYPTOTRANSFER",
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
      "tokenTransfers": [
        {
          "currency":"0.0.5555",
          "transfers": [
            {"account": "0.0.1111", "amount": -10},
            {"account": "0.0.2222", "amount": 10}
          ]
        },
        {
          "currency": "0.0.6666",
          "transfers": [
            {"account": "0.0.3333", "amount": -10},
            {"account": "0.0.4444", "amount": 10}
           ]
        }
      ]
    }
  ]
}
```

To achieve this
-   Update `transactions.js` sql queries for `crypto_transfer` to pull `token_id` and `symbol`
-   Update `createTransferLists()` in `transactions.js` to build a `tokenTransfers` list if `symbol` isn't HBAR or if `token_id` is empty

## Non-Functional Requirements

## Open Questions
-   Will a `token_id` and `symbol` be assigned a value for HBARs?
-   Should the GRPC API be update to handle token transfers?

