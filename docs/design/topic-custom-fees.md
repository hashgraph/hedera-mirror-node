# Topic Custom Fees Design

## Purpose

[HIP-991](https://github.com/hashgraph/hedera-improvement-proposal/blob/master/HIP/hip-991.md) proposes to add a fixed
fee mechanism to the Hedera Consensus Service (HCS) for topic messages, similar to the custom fee structures in the
Hedera Token Service (HTS). This document explains how the mirror node can be updated to support it.

## Goals

- Update / add ingestion logic for the following transactions and persist new information to the database
  - `ConsensusCreateTopic`: custom fees, fee exempt key list, and fee schedule key
  - `ConsensusUpdateTopic`: custom fees, fee exempt key list, and fee schedule key
  - `ConsensusSubmitMessage`: update remaining crypto / token fee schedule allowances from assessed custom fees
  - `ConsensusApproveAllowance`: consensus crypto / token fee schedule allowances
- Verify assessed custom fees are externalized in transaction record for applicable ConsensusSubmitMessage transactions,
  persisted to the database, and exposed via JS REST API
- Expose the following topic custom fee information
  - Expose topic custom fees, fee exempt key list, and fee schedule key via JAVA REST API
  - Expose consensus crypto fee schedule allowances and consensus token fee schedule allowances via JS REST API

## Non Goals

## Architecture

### Database

- Add new tables `topic` and `topic_history`

  ```sql
  create table if not exists topic
  (
    created_timestamp   bigint    null,
    id                  bigint    primary key,
    fee_exempt_key_list bytea     null,
    fee_schedule_key    bytea     null,
    submit_key          bytea     null,
    timestamp_range     int8range not null
  );

  create table topic_history
  (
      like topic including defaults
  );

  -- v2 only, distribution column
  select create_distributed_table('topic', 'id', colocate_with => 'entity');
  select create_distributed_table('topic_history', 'id', colocate_with => 'topic');

  create index if not exists topic_history__timestamp_range on topic_history using gist (timestamp_range);
  create index if not exists topic_history__id_lower_timestamp_range
      on topic_history (id, lower(timestamp_range));
  ```

  Add a database migration to backfill `topic` table from `entity` table. Note for consistency, for all existing topics,
  add a `custom_fee` row with empty custom fees and lower timestamp set to topic's created timestamp.

  The `submit_key` column needs to be dropped from `entity` and `entity_history` table since it's moved to the new
  table.

- Add column `amount_per_message` to `crypto_allowance` and `token_allowance` tables

  ```sql
  alter table if exists crypto_allowance
    add column amount_per_message bigint null;
  alter table if exists crypto_allowance_history
    add column amount_per_message bigint null;
  alter table if exists token_allowance
    add column amount_per_message bigint null;
  alter table if exists token_allowance_history
    add column amount_per_message bigint null;
  ```

- Rename column `token_id` to `entity_id` in `custom_fee` and `custom_fee_history` tables
  ```sql
  alter table if exists custom_fee
    rename column token_id to entity_id;
  alter table if exists custom_fee_history
    rename column token_id to entity_id;
  ```

## Importer

### Domain

- Add an abstract `AbstractTopic` class. The class implements `History` interface, and has the following fields

  - `createdTimestamp`
  - `id`
  - `feeExemptKeyList`
  - `feeScheduleKey`
  - `timestampRange`

  Add class `Topic` and `TopicHistory` that extends `AbstractTopic` class

- Add field `amountPerMessage` to `CryptoAllowance` and `TokenAllowance` classes

### Topic Custom Fee Parsing

#### EntityListener

- Add `onTopic()` to handle inserts and updates on `topic` and `topic_history` tables
  ```java
    default void onTopic(Topic topic) throws ImporterException {}
  ```

#### EntityRecordItemListener

Make the following changes to `insertAssessedCustomFees()`

- Process assessed custom fees in transaction record if tokens is enabled in `PersistProperties` or if it's a
  `ConsensusSubmitMessage` transaction
- If it's a `ConsensusSubmitMessage` transaction, for each assessed custom fee, call `onCryptoAllowance()` with a
  `CryptoAllowance` object to deduct the amount of allowance by the paid fee if it's charged in HBAR, or call
  `onTokenAllowance()` with a `TokenAllowance` object to deduct the amount of allowance by the paid fee if it's charged
  in a fungible token

#### Transaction Handlers

- In `ConsensusCreateTopicTransactionHandler`, add logic to add a new entry to the `topic` table and a new entry to
  the `custom_fee` table
- In `ConsensusUpdateTopicTransactionHandler`, add logic to create a partial update to the `topic` table only when fee
  exempt key list or fee schedule key or submit key is updated; when custom fees are updated, also add a new entry to
  the `custom_fee` table. It's worth noting that an empty custom fees list in the transaction body clears the custom fee
  schedule, and the handler should insert an entry with empty custom fees to the `custom_fee` table to reflect so.
- Add `ConsensusApproveAllowanceTransactionHandler` to process consensus crypto / token fee schedule allowances

## REST API

- `/api/v1/accounts/{idOrAliasOrEvmAddress}/allowances/crypto`

  - The endpoint lists allowances set by both `ConsensusApproveAllowance` and `CryptoApproveAllowance` transactions
  - Topic IDs are valid parameters for the `spender.id` field
  - The response will include the new `amount_per_message` field, for allowances created by `CryptoApproveAllowance` the
    value will be `null`

  ```json
  {
    "allowances": [
      {
        "amount": 75,
        "amount_granted": 100,
        "amount_per_message": 5,
        "owner": "0.0.200",
        "spender": "0.0.300",
        "timestamp": {
          "from": "1586567700.453054000",
          "to": null
        }
      },
      {
        "amount": 300,
        "amount_granted": 300,
        "amount_per_message": null,
        "owner": "0.0.201",
        "spender": "0.0.305",
        "timestamp": {
          "from": "1586567800.453054000",
          "to": null
        }
      }
    ],
    "links": {
      "next": null
    }
  }
  ```

- `/api/v1/accounts/{idOrAliasOrEvmAddress}/allowances/tokens`

  - The endpoint lists allowances set by both `ConsensusApproveAllowance` and `CryptoApproveAllowance` transactions
  - Topic IDs are valid parameters for the `spender.id` field
  - The response will include the new `amount_per_message` field, for allowances created by `CryptoApproveAllowance` the
    value will be `null`

  ```json
  {
    "allowances": [
      {
        "amount": 75,
        "amount_granted": 100,
        "amount_per_message": 5,
        "owner": "0.0.200",
        "spender": "0.0.300",
        "timestamp": {
          "from": "1586567700.453054000",
          "to": null
        },
        "token_id": "0.0.500"
      },
      {
        "amount": 300,
        "amount_granted": 300,
        "amount_per_message": null,
        "owner": "0.0.200",
        "spender": "0.0.305",
        "timestamp": {
          "from": "1586567800.453054000",
          "to": null
        },
        "token_id": "0.0.500"
      }
    ],
    "links": {
      "next": null
    }
  }
  ```

- `/api/v1/topics/{topicId}`
  - Add topic custom fee fields `custom_fees`, `fee_exempt_key_list`, and `fee_schedule_key` to the response body.
  ```json
  {
    "admin_key": {
      "_type": "ProtobufEncoded",
      "key": "421050820e1485acdd59726088e0e4a2130ebbbb70009f640ad95c78dd5a7b38"
    },
    "auto_renew_account": "0.0.2",
    "auto_renew_period": 7776000,
    "created_timestamp": "1586567700.453054000",
    "custom_fees": {
      "created_timestamp": "1586567700.453054000",
      "fixed_fees": [
        {
          "amount": 100,
          "collector_account_id": "0.0.190",
          "denominating_token_id": "0.0.5870"
        }
      ]
    },
    "deleted": false,
    "fee_exempt_key_list": [
      {
        "_type": "ProtobufEncoded",
        "key": "421050820e1485acdd59726088e0e4a2130ebbbb70009f640ad95c78dd5a7b38"
      },
      {
        "_type": "ProtobufEncoded",
        "key": "421050820e1485acdd59726088e0e4a2130ebbbb70009f640ad95c78dd5a7b39"
      }
    ],
    "fee_schedule_key": {
      "_type": "ProtobufEncoded",
      "key": "421050820e1485acdd59726088e0e4a2130ebbbb70009f640ad95c78dd5a7b38"
    },
    "memo": "topic memo",
    "submit_key": {
      "_type": "ProtobufEncoded",
      "key": "421050820e1485acdd59726088e0e4a2130ebbbb70009f640ad95c78dd5a7b38"
    },
    "timestamp": {
      "from": "1586567700.453054000",
      "to": null
    },
    "topic_id": "0.0.2"
  }
  ```

## Non-Functional Requirements

## Acceptance Tests

Refactor the existing test scenario `Validate Topic message submission` with the following additional steps:

1. Create a fungible token as the custom fee denominating token and validate the token is created successfully
2. Create a topic with custom fees (a fixed fee in HBAR and a fixed fee in fungible token), fee exempt key list,
   and fee schedule key. Validate the topic is created successfully and mirrornode REST API returns correct information
3. ALICE (whose signing key is fee exempted) submits a message to the topic. Validate mirrornode REST API returns the
   transaction with no assessed custom fee, Validate mirrornode REST API returns the topic message
4. Transfer some fungible token to BOB (BOB has unlimited token auto association slots). Validate mirrornode REST API
   returns the crypto transfer transaction
5. BOB (whose signing key is not fee exempted) approves consensus crypto and token fee schedule allowances. Validate
   mirrornode REST API returns the correct allowances
6. BOB submits a message to the topic. Validate mirrornode REST API returns the transaction with correct assessed
   custom fees and the allowances granted by BOB to the topic have updated `amount`. Validate mirrornode REST API
   returns the topic message
7. Update the topic with empty custom fees, fee exempt key list, and fee schedule key. Validate mirrornode REST API returns
   the updated topic information
8. BOB submits another message to the topic. Validate mirrornode REST API returns the transaction with no assessed
   custom fee and the allowances granted by BOB to the topic haven't changed. Validate mirrornode REST API returns the
   topic message

## K6 Tests

Change the topic id in the `topicsId` rest-java k6 test case to one with topic custom fee related properties set.
