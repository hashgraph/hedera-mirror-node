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
- Add a `scheduled` boolean to `transaction` table with a default of false
- Add a new `schedule` table:

```sql
create table if not exists schedule
(
  consensus_timestamp bigint primary key not null,
  creator_account_id  int                not null,
  payer_account_id    int                not null,
  schedule_id         int                not null,
  signature_map       bytea              not null,
  transaction_body    bytea              not null
);
```

## Importer

### Converter

Add a `ScheduleIdConverter`.

### Domain

- Add a `Schedule` domain object with the same fields as the schema.
- Add a `SCHEDULE` enum value to `EntityTypeEnum`.

### Entity Listener

- Add `onSchedule(Schedule schedule)`

### Transaction Handler

- Add `ScheduleCreateTransactionHandler`
- Add `ScheduleSignTransactionHandler`

### Entity Record Item Listener

Update `EntityRecordItemListener` to:

- For every `ScheduleCreate` transaction, insert a `Transaction`, `Entities` and `Schedule` object into the database
- For every `ScheduleSign` transaction, insert a `Transaction` and create the `Entities` if it doesn't exist

## GRPC API

- Add a `SCHEDULE` enum value to `EntityType`

## REST API

### Get Transaction

Add an optional boolean `scheduled` parameter to `/api/v1/transactions/:id`:

- If true, return the inner scheduled transaction
- If false, return the `ScheduleCreate` transaction
- If not present, return both

### State Proof

Add an optional boolean `scheduled` parameter to `/api/v1/transactions/:id/stateproof` with a default of false

### List Schedules

GET `/api/v1/schedule`

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
      "payer_account_id": "0.0.101",
      "schedule_id": "0.0.102",
      "signature_map": "9c2233222c2233222c2233227d"
    }
  ],
  "links": {
    "next": null
  }
}
```

### Get Schedule

GET `/api/v1/schedule/{scheduleId}`

```json
{
  "admin_key": {
    "_type": "ProtobufEncoded",
    "key": "7b2233222c2233222c2233227d"
  },
  "consensus_timestamp": "1234567890.000000001",
  "creator_account_id": "0.0.100",
  "payer_account_id": "0.0.101",
  "schedule_id": "0.0.102",
  "signature_map": "9c2233222c2233222c2233227d"
}
```

## Monitor

Add a separate `TransactionSupplier` and `TransactionType` enum value for each schedule transaction. These will be
dependent on support being first added to the SDK. SDK developers could choose to only add scheduled transaction support
to v2, which would force us to update our code to use that version and require a larger amount of effort.

## Acceptance Tests

Add an acceptance test that tests the schedule transaction flow end to end:

1. Alice submits a `ScheduleCreate` with an inner `CryptoTransfer` with her as payer and Bob as sender.
2. Verify transaction via mirror node REST API.
3. Verify inner `CryptoTransfer` does not exist via mirror node REST API.
4. Bob submits a `ScheduleSign`.
5. Verify transaction via mirror node REST API.
6. Verify inner `CryptoTransfer` via mirror node REST API.

## Open Questions

- [ ] Will there be a separate `ScheduleUpdate` transaction?
