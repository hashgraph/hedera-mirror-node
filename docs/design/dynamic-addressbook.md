# HIP-869 Dynamic Address Book

## Purpose

[HIP-869](https://hips.hedera.com/hip/hip-869) implements the HAPI endpoints for Address Book management within the
current Address Book implementation.
In this phase, the mirror node processes the new transactions and service_endpoint information,
then returns that information through its existing APIs.

## Goals

- Ingest the following transactions and persist them to the database:

  - `NodeCreate`
  - `NodeDelete`
  - `NodeUpdate`

- Expose `domain_name` in the `/network/nodes` REST endpoint
- Expose `domain_name` in the grpc network service

## Architecture

### Database

```sql

alter table if exists address_book_service_endpoint drop constraint if exists address_book_service_endpoint_pkey;

alter table if exists address_book_service_endpoint
    alter column ip_address_v4 set default '',
    alter column port drop default,
    add column if not exists domain_name varchar(253) not null default '';

create index if not exists address_book_service_endpoint__timestamp_node_id
  on address_book_service_endpoint (consensus_timestamp , node_id);

create table if not exists node
(
  admin_key              bytea           not null,
  created_timestamp      bigint          not null,
  deleted                boolean         default false not null,
  node_id                bigint          not null,
  timestamp_range        int8range       not null
);

-- add node index
alter table if exists node
  add constraint node__pk primary key (node_id);

create table if not exists node_history
(
  like node including defaults
);


create index if not exists node_history__node_id_lower_timestamp
  on node_history (node_id, lower(timestamp_range));

```

### Importer

#### Node Transactions Parsing

When parsing node transactions,

- Persist `transaction_bytes` and `transaction_record_bytes` to the `transaction` table for `NodeUpdate`,`NodeCreate` and `NodeDelete`.
- Persist `Node` domain objects.

Update the `AddressBookServiceImpl` to persist the `domain_name` in `address_book_service_endpoint`

#### Domain

- Modify `AddressBookServiceEndpoint` domain object to add `domain_name`.
- Add `AbstractNode`, `Node` and `NodeHistory` domain objects in the common module.

#### EntityListener

- Add `EntityListener.onNode` method and `CompositeEntityListener.onNode` to handle inserts to the `node` table.

#### Transaction Handlers

Write `transaction_bytes` and `transaction_record_bytes` in the following handlers:

- Add `NodeCreateTransactionHandler`
- Add `NodeUpdateTransactionHandler`
- Add `NodeDeleteTransactionHandler`

Update `node` table with the latest `admin_key`

### GRPC API

- Update the `NetworkController` to add `domain_name` to the service endpoint and `admin_key`.

### REST API

- Update the `/api/v1/network/nodes` endpoint to return `domain_name` and `admin_key`

Response:

```json
{
  "nodes": [
    {
      "admin_key": {
        "_type": "ED25519",
        "key": "308201a2300d06092a864886f70d01010105000382018f003082018a028100e0"
      },
      "description": "address book 1",
      "file_id": "0.0.102",
      "max_stake": 50000,
      "memo": "0.0.4",
      "min_stake": 1000,
      "node_account_id": "0.0.4",
      "node_cert_hash": "0x01d173753810c0aae794ba72d5443c292e9ff962b01046220dd99f5816422696e0569c977e2f169e1e5688afc8f4aa16",
      "node_id": 1,
      "public_key": "0x4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f",
      "reward_rate_start": 1000000,
      "service_endpoints": [
        {
          "domain_name": "examplenode.com",
          "ip_address_v4": "128.0.0.6",
          "port": 50216
        }
      ],
      "stake": 20000,
      "stake_not_rewarded": 19900,
      "stake_rewarded": 100,
      "staking_period": {
        "from": "1655164800.000000000",
        "to": "1655251200.000000000"
      },
      "timestamp": {
        "from": "187654.000123457",
        "to": null
      }
    }
  ],
  "links": {
    "next": null
  }
}
```

## Non-Functional Requirements

- Ingest new transaction types at the same rate as consensus nodes
