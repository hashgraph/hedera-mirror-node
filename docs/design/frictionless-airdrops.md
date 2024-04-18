# HIP-336 Approval and Allowance

## Purpose

[HIP-904](https://hips.hedera.com/hip/hip-904) enables frictionless airdrops of both fungible and non-fungible tokens by removing the requirement to pre-associate tokens with the receiver’s account.

## Goals

- Ingest `TokenAirdrop` transactions and persist to the database
- Ingest `PendingAirdrop` transactions and persist to the database
- Ingest `TokenCancelAirdrop` and `TokenClaimAirdrop` transactions and remove entries from the database
- Ingest `TokenReject` transactions and persist to the database
- Expose Pending Airdrop information via the Java REST API

## Non-Goals

- Support for historical airdrop information
- Enhance gRPC APIs with Pending Airdrop information
- Enhance Web3 APIs with Pending Airdrop information

## Architecture

### Database

#### Pending Airdrops

```sql
create table if not exists pending_airdrop
(
    account_id          bigint  not null,
    amount              bigint
    payer_account_id    bigint  not null,
    serial_number       bigint
    token_id            bigint  not null
    primary key (account_id, payer_account_id, token_id)
);

create index if not exists pending__airdrop on pending_airdrop (account_id, payer_account_id);
```

### Importer

#### Pending Airdrop Parsing

When parsing pending airdrops,

- Persist pending airdrops to the `pending_airdrop` table.
- If a pending airdrop entry already exists, the new amount should be added to the existing amount.

#### Domain

- Add an `TokenAirdrop` domain object with the same fields as `CryptoTransfer`.
- Add an `TokenCancelAirdrop` domain object which contains the fields `accountId`, `payerAccountId`, `tokenId`, and `serialNumber`.
- Add an `TokenClaimAirdrop` domain object which contains the fields `accountId`, `payerAccountId`, `tokenId`, and `serialNumber`.
- Add an `TokenReject` domain object which contains the fields `accountId`, `tokenId`, and `serialNumber`.
- Add an `PendingAirdrop` domain object with the same fields as the schema.

#### Entity Listener

- Add `onCancelAirdrop` to handle deletes from the `pending_airdrop` table.
- Add `onClaimAirdrop` to handle deletes from the `pending_airdrop` table.
- Add `onPendingAirdrop` to handle inserts to the `pending_airdrop` table.
- Add `onTokenReject` which will be similar to `onCryptoTransfer` except the balance will be zero.
- Add `onTokenAirdrop` which will be similar to `onCryptoTransfer`.

#### Transaction Handlers

- Add `TokenAirdropTransactionHandler` which will be similar to `CryptoTransferTransactionHandler`
- Add `TokenCancelAirdropTransactionHandler`
- Add `TokenClaimAirdropTransactionHandler`
- Add `TokenRejectTransactionHandler` which will be similar to `CryptoTransferTransactionHandler`
- Add `PendingAirdropTransactionHandler`

#### Batch Deletion

- Add a batch deletion velocity template to be used by Claim airdrop and Cancel airdrop.
- Add a `GenericDeleteQueryGenerator` to be used for batch deletion.

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
- `order` - The direction to sort the items in the response. Can be `asc` or `desc` with a default of `asc`.
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
- `order` - The direction to sort the items in the response. Can be `asc` or `desc` with a default of `asc`.
- `sender.id` - The sender account that initiated the pending airdrop.
- `serialnumber` - The specific serial number associated with airdrop. Supports `eq`, `gt`, `gte`, `lt`, and `lte` operators. Only one occurrence is allowed.
- `token.id` - The token ID this airdrop is associated with. Supports `eq`, `gt`, `gte`, `lt`, and `lte` operators. Only one occurrence is allowed.

## Non-Functional Requirements

- Ingest new transaction types at the same rate as consensus nodes
