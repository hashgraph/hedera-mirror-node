# HIP-869 Dynamic Address Book

## Purpose

[HIP-869](https://hips.hedera.com/hip/hip-869) changes the currently manually administered address book for Hedera into an HAPI managed configuration, updatable via signed Hedera transactions on a daily basis.

## Goals

- Ingest the following transactions and persist to the database:
  - `NodeCreate`
  - `NodeDelete`
  - `NodeUpdate`

## Architecture

### Database

```sql

alter table address_book_service_endpoint
    add column if not exists domain_name varchar(253) default null;

drop index if exists address_book_service_endpoint_pkey;

create index idx__address_book_service_endpoint__timestamp_then_node_id
    on address_book_service_endpoint (consensus_timestamp asc,node_id asc);

```

### Importer

#### Node Transactions Parsing

When parsing node transactions,

- Persist `transaction_bytes` and `transaction_record_bytes` with the binary proto value to the `transaction` table for `NodeUpdate`,`NodeCreate` and `NodeDelete`.

#### Domain

- Modify `AddressBookServiceEndpoint` domain object to add `domain_name`.

#### Transaction Handlers

Write `transaction_bytes` and `transaction_record_bytes` in the following handlers:

- Add `NodeCreateTransactionHandler`
- Add `NodeUpdateTransactionHandler`
- Add `NodeDeleteTransactionHandler`

### REST API

Might not need to update the REST API until a later phase.

## Non-Functional Requirements

- Ingest new transaction types at the same rate as consensus nodes
