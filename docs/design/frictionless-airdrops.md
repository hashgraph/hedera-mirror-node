# HIP-336 Approval and Allowance

## Purpose

[HIP-904](https://hips.hedera.com/hip/hip-904) enables frictionless airdrops of both fungible and non-fungible tokens by removing the requirement to pre-associate tokens with the receiver’s account.

## Goals

- Ingest the following transactions and persist to the database:
  - `PendingAirdrop`
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
create type airdrop_resolution_state as enum ('PENDING', 'CANCEL', 'CLAIM');

create table if not exists token_pending_airdrop
(
    account_id          bigint                   not null,
    amount              bigint,
    resolution_state    airdrop_resolution_state not null default 'PENDING',
    payer_account_id    bigint                   not null,
    serial_number       bigint,
    timestamp_range     int8range                not null,
    token_id            bigint                   not null
);

create index if not exists token_pending__airdrop on token_pending_airdrop (account_id, payer_account_id);
```

### Importer

#### Pending Airdrop Parsing

When parsing pending airdrops,

- Persist pending airdrops to the `token_pending_airdrop` table.
- If an entry already exists for a fungible token, the timestamp range of the existing entry should be closed and a new entry should be added with `amount` set to the sum of the existing amount and the new amount.

#### Domain

- Add an `TokenAirdrop` domain object with the same fields as `accountId`, `amount`, `payerAccountId`, `tokenId`, and `serialNumber`.
- Add an `TokenCancelAirdrop` domain object which contains the fields `accountId`, `payerAccountId`, `tokenId`, and `serialNumber`.
- Add an `TokenClaimAirdrop` domain object which contains the fields `accountId`, `payerAccountId`, `tokenId`, and `serialNumber`.
- Add an `TokenReject` domain object which contains the fields `accountId`, `tokenId`, and `serialNumber`.
- Add an `TokenPendingAirdrop` domain object with the same fields as the schema.

#### Entity Listener

- Add `onCancelAirdrop` to handle setting the `resolution_state` and closing the `timestamp_range` on the `token_pending_airdrop` table.
- Add `onClaimAirdrop` to handle setting the `resolution_state` flag and closing the `timestamp_range` on the `token_pending_airdrop` table.
- Add `onTokenPendingAirdrop` to handle inserts to the `token_pending_airdrop` table.
- Add `onTokenReject` which will be similar to `onCryptoTransfer` except the balance will be zero.
- Add `onTokenAirdrop` which will be similar to `onCryptoTransfer`.

#### Transaction Handlers

- Add `TokenAirdropTransactionHandler` which will be similar to `CryptoTransferTransactionHandler`
- Add `TokenCancelAirdropTransactionHandler` which will set the resolution state of the pending airdrop to `CANCEL`
- Add `TokenClaimAirdropTransactionHandler` which will set the resolution state of the pending airdrop to `CLAIM`
- Add `TokenPendingAirdropTransactionHandler`
- Add `TokenRejectTransactionHandler` which will be similar to `CryptoTransferTransactionHandler`

#### Support unlimited Max Automatic Token Associations

- Add test(s) for the `-1` value for `maxAutomaticTokenAssociations`.

### WEB3

#### Support unlimited Max Automatic Token Associations

- Update Web3 `CryptoOpsUsage` and `SyntheticTxnFactory` to handle `-1` values for `maxAutomaticTokenAssociations`.

### REST API

- Add the new endpoints to Java REST.

#### List of outstanding airdrops sent by senderIdOrEvmAddress which have not been claimed by recipients

`/api/v1/accounts/{senderIdOrEvmAddress}/airdrops/outstanding`

```json
{
  "airdrops": [
    {
      "amount": 333,
      "receiver_id": "0.0.999",
      "sender_id": "0.0.222",
      "serial_number": null,
      "token_id": "0.0.111"
    },
    {
      "amount": 555,
      "receiver_id": "0.0.999",
      "sender_id": "0.0.222",
      "serial_number": null,
      "token_id": "0.0.444"
    },
    {
      "amount": null,
      "receiver_id": "0.0.999",
      "sender_id": "0.0.222",
      "serial_number": 888,
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
- `order` - The direction to sort the items by `token_id` in the response. Can be `asc` or `desc` with a default of `asc`.
- `receiver.id` - The receiver account the outstanding airdrop was intended for.
- `serialnumber` - The specific serial number associated with airdrop. Supports `eq`, `gt`, `gte`, `lt`, and `lte` operators. Only one occurrence is allowed.
- `token.id` - The token ID this airdrop is associated with. Supports `eq`, `gt`, `gte`, `lt`, and `lte` operators. Only one occurrence is allowed.

#### List of pending airdrops that receiverIdOrEvmAddress has not yet claimed

`/api/v1/accounts/{receiverIdOrEvmAddress}/airdrops/pending`

```json
{
  "airdrops": [
    {
      "amount": 333,
      "receiver_id": "0.0.999",
      "sender_id": "0.0.222",
      "serial_number": null,
      "token_id": "0.0.111"
    },
    {
      "amount": 555,
      "receiver_id": "0.0.999",
      "sender_id": "0.0.222",
      "serial_number": null,
      "token_id": "0.0.444"
    },
    {
      "amount": null,
      "receiver_id": "0.0.999",
      "sender_id": "0.0.222",
      "serial_number": 888,
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
- `order` - The direction to sort the items by `token_id` in the response. Can be `asc` or `desc` with a default of `asc`.
- `sender.id` - The sender account that initiated the pending airdrop.
- `serialnumber` - The specific serial number associated with airdrop. Supports `eq`, `gt`, `gte`, `lt`, and `lte` operators. Only one occurrence is allowed.
- `token.id` - The token ID this airdrop is associated with. Supports `eq`, `gt`, `gte`, `lt`, and `lte` operators. Only one occurrence is allowed.

## Non-Functional Requirements

- Ingest new transaction types at the same rate as consensus nodes
