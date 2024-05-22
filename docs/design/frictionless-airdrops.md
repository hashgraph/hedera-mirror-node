# HIP-904 Frictionless Airdrops

## Purpose

[HIP-904](https://hips.hedera.com/hip/hip-904) enables frictionless airdrops of both fungible and non-fungible tokens by removing the requirement to pre-associate tokens with the receiver’s account.

## Goals

- Ingest the following transactions and persist to the database:
  - `TokenAirdrop`
  - `TokenCancelAirdrop`
  - `TokenClaimAirdrop`
  - `TokenReject`
- Expose Pending Airdrop information via the Java REST API

## Non-Goals

- Enhance gRPC APIs with Pending Airdrop information
- Enhance Web3 APIs with Pending Airdrop information

## Architecture

### Database

#### Pending Airdrops

```sql
create type airdrop_state as enum ('PENDING', 'CANCELLED', 'CLAIMED');

create table if not exists token_airdrop
(
    amount              bigint,
    receiver_account_id bigint         not null,
    sender_account_id   bigint         not null,
    serial_number       bigint,
    state               airdrop_state  not null default 'PENDING',
    timestamp_range     int8range      not null,
    token_id            bigint         not null
);

create unique index if not exists token_airdrop__sender_id on token_airdrop (sender_account_id, receiver_account_id, token_id, serial_number);
create index if not exists token_airdrop__receiver_id on token_airdrop (receiver_account_id, sender_account_id, token_id, serial_number);

create table if not exists token_airdrop_history
(
  like token_airdrop including defaults
);

create index if not exists token_airdrop_history__token_serial_lower_timestamp
  on token_airdrop_history using gist (timestamp_range);
```

### Importer

#### Pending Airdrop Parsing

When parsing pending airdrops,

- Persist airdrops to the `token_airdrop` table.
- If a `TokenAirdrop` transaction occurs for a fungible token that already exists in the `token_airdrop` table then the existing entry should have the timestamp range closed, and it should be moved into the `token_airdrop_history` table. If the entry was in the `PENDING` state, then the new entry should be updated with `amount` set to the sum of the existing amount and the new amount. If the existing entry is in any other state, then the `amount` should be set to the amount of the new entry.
- If a `TokenAirdrop` transaction occurs for a nft that already exists in the `token_airdrop` table i.e matching sender, receiver, token_id and serial_number, then the existing entry should have the timestamp range closed, and it should be moved into the `token_airdrop_history` table.

#### Domain

- Add an `TokenAirdrop` domain object with the same fields as the schema.

#### Entity Listener

- Add `onTokenAirdrop` to handle inserts to the `token_airdrop` table.

#### Transaction Handlers

- Add `TokenAirdropTransactionHandler` which will add a new entry to the `token_airdrop` table with the default state of `PENDING`.
- Add `TokenCancelAirdropTransactionHandler` which will set the state of the airdrop to `CANCELLED`
- Add `TokenClaimAirdropTransactionHandler` which will set the state of the airdrop to `CLAIMED`
- Add `TokenRejectTransactionHandler` which will be similar to `CryptoTransferTransactionHandler`.

#### Support unlimited Max Automatic Token Associations

- Add test(s) for the `-1` value for `maxAutomaticTokenAssociations`.

### Web3 API

#### Support unlimited Max Automatic Token Associations

- Update Web3 `CryptoOpsUsage` and `SyntheticTxnFactory` to handle `-1` values for `maxAutomaticTokenAssociations` for protobuf versions greater than or equal to the version that implements HIP-904. If the HAPI protobuf version is less than that version then the methods should use the existing behavior.

### REST API

Add the new endpoints to Java REST. Going forward new endpoints will be added to the Rest-Java module rather than the Javascript module. Queries used by the API need to be performant on v1 and also on v2 with consideration for Citus partitioning.

#### List Outstanding Airdrops

`/api/v1/accounts/{senderIdOrEvmAddress}/airdrops/outstanding`

List of outstanding airdrops in the `PENDING` state. The primary use case is for the sender of the airdrop. All information returned to the requester will be queried from the `token_airdrop` table and should be performant based on the `token_airdrop__sender_id` index.
Input validation will be applied to the `senderIdOrEvmAddress` path parameter and the optional query parameters. An error code response of `400` will be returned if any parameters fail validation.

Response:

```json
{
  "airdrops": [
    {
      "amount": 333,
      "receiver_id": "0.0.999",
      "sender_id": "0.0.222",
      "serial_number": null,
      "timestamp": {
        "from": "1111111111.111111111",
        "to": null
      },
      "token_id": "0.0.111"
    },
    {
      "amount": 555,
      "receiver_id": "0.0.999",
      "sender_id": "0.0.222",
      "serial_number": null,
      "timestamp": {
        "from": "1111111111.111111112",
        "to": null
      },
      "token_id": "0.0.444"
    },
    {
      "amount": null,
      "receiver_id": "0.0.999",
      "sender_id": "0.0.222",
      "serial_number": 888,
      "timestamp": {
        "from": "1111111111.111111113",
        "to": null
      },
      "token_id": "0.0.666"
    }
  ],
  "links": {
    "next": null
  }
}
```

Optional Filters

- `limit` - The maximum number of airdrops to return in the response. Defaults to `25` with a max of `100`.
- `order` - The direction to sort the items in the response. Sorted by the `token_airdrop__sender_id` index. Can be `asc` or `desc` with a default of `asc`.
- `receiver.id` - The receiver account the outstanding airdrop was intended for. Supports `eq`, `gt`, `gte`, `lt`, and `lte` operators. Only one occurrence is allowed.
- `serialnumber` - The specific serial number associated with airdrop. Supports `eq`, `gt`, `gte`, `lt`, and `lte` operators. Only one occurrence is allowed.
- `token.id` - The token ID this airdrop is associated with. Supports `eq`, `gt`, `gte`, `lt`, and `lte` operators. Only one occurrence is allowed.

#### List Unclaimed Airdrops

`/api/v1/accounts/{receiverIdOrEvmAddress}/airdrops/pending`

List of airdrops in the `PENDING` state. The primary use case is for the receiver of the airdrop. All information returned to the requester will be queried from the `token_airdrop` table and should be performant based on the `token_airdrop__receiver_id` index.
Input validation will be applied to the `receiverIdOrEvmAddress` path parameter and the optional query parameters. An error code response of `400` will be returned if any parameters fail validation.

Response:

```json
{
  "airdrops": [
    {
      "amount": 333,
      "receiver_id": "0.0.999",
      "sender_id": "0.0.222",
      "serial_number": null,
      "timestamp": {
        "from": "1111111111.111111111",
        "to": null
      },
      "token_id": "0.0.111"
    },
    {
      "amount": 555,
      "receiver_id": "0.0.999",
      "sender_id": "0.0.222",
      "serial_number": null,
      "timestamp": {
        "from": "1111111111.111111112",
        "to": null
      },
      "token_id": "0.0.444"
    },
    {
      "amount": null,
      "receiver_id": "0.0.999",
      "sender_id": "0.0.222",
      "serial_number": 888,
      "timestamp": {
        "from": "1111111111.111111113",
        "to": null
      },
      "token_id": "0.0.666"
    }
  ],
  "links": {
    "next": null
  }
}
```

Optional Filters

- `limit` - The maximum number of airdrops to return in the response. Defaults to `25` with a max of `100`.
- `order` - The direction to sort the items in the response. Sorting by the `token_airdrop__receiver_id` index. Can be `asc` or `desc` with a default of `asc`.
- `sender.id` - The sender account that initiated the pending airdrop. Supports `eq`, `gt`, `gte`, `lt`, and `lte` operators. Only one occurrence is allowed.
- `serialnumber` - The specific serial number associated with airdrop. Supports `eq`, `gt`, `gte`, `lt`, and `lte` operators. Only one occurrence is allowed.
- `token.id` - The token ID this airdrop is associated with. Supports `eq`, `gt`, `gte`, `lt`, and `lte` operators. Only one occurrence is allowed.

## Non-Functional Requirements

- Ingest new transaction types at the same rate as consensus nodes

## Acceptance Tests

Add acceptance tests for the token feature that use an existing set of accounts and a token_id from the existing acceptance tests and performs the following:

- Send two airdrops, a fungible and nft airdrop to an account that has been associated with the tokens and verify that the airdrops are transferred to the account and that the two REST APIs do not list the airdrops. Verify that related endpoints for the account (`/accounts/{id}/nfts`, `/accounts/{id}/tokens`, and `/tokens/{id}/nfts/{serial}`) show the account as the owner of the tokens.
- Send a fungible and nft airdrop to an account that has not been associated with the tokens and has no open slots for automatic associations and verify that the airdrops are listed by the two REST APIs. Verify that related endpoints for the account (`/accounts/{id}/nfts`, `/accounts/{id}/tokens`, and `/tokens/{id}/nfts/{serial}`) do not show the account as the owner of the tokens.
- Cancel the pending airdrops and verify that they are no longer listed by the two REST APIs. Verify that the receiver does not own the tokens and that the sender does own them (`/accounts/{receiver/senderId}/nfts`, `/accounts/{receiver/senderId}/tokens`, and `/tokens/{receiver/senderId}/nfts/{serial}`).
- Send a fungible and nft airdrop to an account that has not been associated with the tokens and has no open slots for automatic associations and verify that the airdrop is listed in the two REST APIs. Verify that related endpoints for the account (`/accounts/{id}/nfts`, `/accounts/{id}/tokens`, and `/tokens/{id}/nfts/{serial}`) do not show the account as the owner of the tokens.
- Claim the airdrops and verify that they are no longer listed in the two REST APIs. Verify that related endpoints for the account (`/accounts/{id}/nfts`, `/accounts/{id}/tokens`, and `/tokens/{id}/nfts/{serial}`) show the account as the owner of the tokens.
- Reject the tokens that were just claimed and verify that related endpoints for the account (`/accounts/{id}/nfts`, `/accounts/{id}/tokens` and `/tokens/{id}/nfts/{serial}`) do not show the account as the owner of the tokens. Verify that the tokens have been returned to their treasury account (`/accounts/{treasury account id}/nfts`, `/accounts/{treasury account id}/tokens`, and `/tokens/{id}/nfts/{serial}`).

## K6 Tests

- Setup and ensure the staging environment has a pending airdrop sent by one of the configurable default accounts and received by another.
- Add the two new endpoints to the K6 test suite.
