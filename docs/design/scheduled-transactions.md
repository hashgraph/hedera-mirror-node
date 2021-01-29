# Scheduled Transactions

## Purpose

Scheduled transactions allow users to submit transactions to Hedera that will execute once all required signatures are
acquired. This document explains how the mirror node can be updated to support scheduled transactions.

## Goals

- Ingest schedule related transactions and persist to the database
- Design scheduled transactions REST APIs
- Monitor scheduled transactions

## Non-Goals

- Provide a schedule gRPC API
- Support entity expiration

## Database

- Update `t_entity_types` to add new `SCHEDULE` entity.
- Update `t_transaction_types` to add new schedule transaction types.
- Update `t_transaction_results` with the new response codes.
  - `INVALID_SCHEDULE_ID = 201`
- Add a `scheduled` boolean to `transaction` table with a default of false.
- Add a new `schedule` table:

```sql
create table if not exists schedule
(
  consensus_timestamp bigint primary key not null,
  creator_account_id  bigint             not null,
  executed_timestamp  bigint             null,
  payer_account_id    bigint             not null,
  schedule_id         bigint             not null,
  transaction_body    bytea              not null
);
```

- Add a unique constraint to `schedule` for `schedule_id`.
- Add a new `schedule_signature` table that represents the signatories that have signed and are present in the `sigMap`
  field of `ScheduleCreate` or `ScheduleSign`:

```sql
create table if not exists schedule_signature
(
  consensus_timestamp bigint not null,
  public_key_prefix   bytea  not null,
  schedule_id         bigint not null,
  signature           bytea  not null
);
```

> **_Note:_** There's no unique constraint/primary key since the client can potentially sign multiple times with the same key

- Add an index to `schedule_signature` for `schedule_id`.

## Importer

### Converter

Add a `ScheduleIdConverter`.

### Domain

- Add a `Schedule` domain object with the same fields as the schema.
- Add a `ScheduleSignature` domain object with the same fields as the schema.
- Add a `SCHEDULE` enum value to `EntityTypeEnum`.

### Entity Listener

- Add `onSchedule(Schedule schedule)`
- Add `onScheduleSignature(ScheduleSignature scheduleSignature)`

### Transaction Handler

- Add `ScheduleCreateTransactionHandler` that updates the entity to set the admin key.
- Add `ScheduleSignTransactionHandler` that doesn't update the entity.

### Entity Record Item Listener

#### Schedule Create

- Insert a `Transaction` with `scheduled` set to false.
- Upsert an `Entities` for the `scheduleID` and the `payerAccountID`.
- If the `scheduleID` does not exist, insert a `Schedule`:
  - Set `consensusTimestamp` to the `consensusTimestamp` in the transaction record.
  - Set `creatorAccountId` to the payer account from the transaction ID.
  - Set `payerAccountId` to the one in the transaction body else use the payer account from the transaction ID.
  - Set `scheduleId` to the `scheduleID` in the transaction receipt.
  - Set `transactionBody` to the `transactionBody` field within the `ScheduleCreateTransactionBody`.
- Insert a `ScheduleSignature` for every entry in the `sigMap`:
  - Set `consensusTimestamp` to the `consensusTimestamp` in the transaction record.
  - Set `publicKeyPrefix` to the `sigPair.pubKeyPrefix`.
  - Set `scheduleId` to the `scheduleID` in the transaction receipt.
  - Set `signature` to the `sigPair.signature` `oneof` field. Only ed25519 is supported.

> **_Note:_** Currently a `ScheduleCreate` can potentially cause an update or a create, so don't create a schedule and
> only update signatures for an update.

#### Schedule Sign

- Insert a `Transaction` with `scheduled` set to false.
- Upsert an `Entities` for the `scheduleID`.
- Insert a `ScheduleSignature` for every entry in the `sigMap`:
  - Set `consensusTimestamp` to the `consensusTimestamp` in the transaction record.
  - Set `publicKeyPrefix` to the `sigPair.pubKeyPrefix`.
  - Set `scheduleId` to the `scheduleID` in the transaction receipt.
  - Set `signature` to the `sigPair.signature` `oneof` field. Only ed25519 is supported.

#### Scheduled Transaction

A transaction triggered by the last schedule sign or create will have a `scheduleRef` populated in the record.

- Insert a `Transaction` with `scheduled` set to true.
- Update the `Schedule` to set `executed_timestamp`.

## GRPC API

- Add a `SCHEDULE` enum value to `EntityType`.

## REST API

### Get Transaction

Add an optional boolean `scheduled` parameter to `/api/v1/transactions/:id`. Also add a `scheduled` boolean to every
transaction APIs JSON response:

- If true, return only the inner scheduled transaction
- If false, return all non-scheduled transactions matching `id` including the `ScheduleCreate` transaction
  if exists
- If not present, return all transactions matching `id`

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
      "name": "SCHEDULECREATE",
      "node": "0.0.3",
      "scheduled": false,
      "transaction_id": "0.0.10-1234567890-000000000",
      "valid_duration_seconds": "11",
      "max_fee": "33",
      "transfers": [
        {
          "account": "0.0.3",
          "amount": 10
        },
        {
          "account": "0.0.98",
          "amount": 1
        }
      ]
    },
    {
      "consensus_timestamp": "1234567890.000000002",
      "valid_start_timestamp": "1234567890.000000000",
      "charged_tx_fee": 7,
      "memo_base64": null,
      "result": "SUCCESS",
      "transaction_hash": "aGFzaA==",
      "name": "CRYPTOTRANSFER",
      "node": "0.0.3",
      "scheduled": true,
      "transaction_id": "0.0.10-1234567890-000000000",
      "valid_duration_seconds": "11",
      "max_fee": "33",
      "transfers": [
        {
          "account": "0.0.3",
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
      ]
    }
  ]
}
```

### State Proof

Add an optional boolean `scheduled` parameter to `/api/v1/transactions/:id/stateproof` with a default of false.

### List Schedules

GET `/api/v1/schedules`

```json
{
  "schedules": [
    {
      "admin_key": {
        "_type": "ProtobufEncoded",
        "key": "7b2233222c2233222c2233227d"
      },
      "consensus_timestamp": "1234567890.000000001",
      "creator_account_id": "0.0.100",
      "executed_timestamp": "1234567890.000000002",
      "memo": "Created per council decision dated 1/21/21",
      "payer_account_id": "0.0.101",
      "schedule_id": "0.0.102",
      "signatures": [
        {
          "consensus_timestamp": "1234567890.000000001",
          "public_key_prefix": "1f4be98a",
          "signature": "d28d200bba7d48f507e140fa6228aba7f29ff8b2a3f2a8eeb85742dc053cec71"
        },
        {
          "consensus_timestamp": "1234567890.000000002",
          "public_key_prefix": "1afc6e5e",
          "signature": "c3d987c874294eb95b2df9fd36b0656623315769af2ef344c35868780102b5c0"
        }
      ],
      "transaction_body": "29ccb14cc5f65c52feb74292b01d52fdcf2de645d394b53704680db6d132ec6c=="
    }
  ],
  "links": {
    "next": null
  }
}
```

#### Optional Filters

- `/api/v1/schedules?account.id=0.0.8` - All schedule transactions for the matching creator account.
- `/api/v1/schedules?executed=true` - All scheduled transactions that have collected enough signatures to execute.
- `/api/v1/schedules?limit=N` - Limit results to the first `N` schedule transactions.
- `/api/v1/schedules?order=desc` - All schedule transactions in descending order of `consensus_timestamp`.
- `/api/v1/schedules?schedule.id=gt:0.0.1001` - All scheduled transactions in range

### Get Schedule

GET `/api/v1/schedules/{scheduleId}`

```json

{
  "admin_key": {
    "_type": "ProtobufEncoded",
    "key": "7b2233222c2233222c2233227d"
  },
  "consensus_timestamp": "1234567890.000000001",
  "creator_account_id": "0.0.100",
  "executed_timestamp": "1234567890.000000002",
  "memo": "Created per council decision dated 1/21/21",
  "payer_account_id": "0.0.101",
  "schedule_id": "0.0.102",
  "signatures": [
    {
      "consensus_timestamp": "1234567890.000000001",
      "public_key_prefix": "1f4be98a",
      "signature": "d28d200bba7d48f507e140fa6228aba7f29ff8b2a3f2a8eeb85742dc053cec71"
    },
    {
      "consensus_timestamp": "1234567890.000000002",
      "public_key_prefix": "1afc6e5e",
      "signature": "c3d987c874294eb95b2df9fd36b0656623315769af2ef344c35868780102b5c0"
    }
  ],
  "transaction_body": "29ccb14cc5f65c52feb74292b01d52fdcf2de645d394b53704680db6d132ec6c=="
}
```

## Monitor

Add a separate `TransactionSupplier` and `TransactionType` enum value for each schedule transaction. These will be
dependent on support being first added to the SDK. SDK developers could choose to only add scheduled transaction support
to v2, which would force us to update our code to use that version and require a larger amount of effort.

## Acceptance Tests

Add an acceptance test that tests the schedule transaction flow end to end:

1. Alice submits a `ScheduleCreate` with an inner `CryptoTransfer` with her as payer and Bob as sender.
2. Verify outer transaction exists via mirror node REST API.
3. Verify inner `CryptoTransfer` does not exist via mirror node REST API.
4. Bob submits a `ScheduleSign`.
5. Verify transaction exists via mirror node REST API.
6. Verify inner `CryptoTransfer` exists via mirror node REST API.
