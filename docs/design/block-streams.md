# HIP-1056 Block Streams

## Purpose

[HIP-1056](https://hips.hedera.com/hip/hip-1056) introduces a new output data format for consensus nodes, called block streams, that replaces the existing
record streams and signature files with one single stream. Support for block streams in the Mirror Node will be split
into two phases. This design document concerns the first phase which will transform the block streams into the existing
record stream format and parse the transformed record streams just as we do today. The second phase (to be detailed in a
separate design document) will remove the block to record transformation and parse the block streams directly and additionally
transform record streams into block streams to allow the mirror node to continue to ingest all record streams from the past.

## Goals

- Ingest block stream files downloaded from an S3/GCP bucket and transform them into record stream files.

## Non-Goals

- Refactoring the importer to support block streams natively and the removal of record stream parsing.
- Support for Block Nodes will be covered in a separate design document.

## Architecture

### Data Flow

![Data Flow](images/blockstream.png)

## Domain

### Interfaces and Classes

#### BlockItem

```java
package com.hedera.mirror.common.domain.transaction;

// Multiple protobuf BlockItems will be combined into a single BlockItem
public record BlockItem(Transaction transaction,
                        TransactionResult transactionResult,
                        List<TransactionOutput> transactionOutput, // Note: List may be empty
                        Optional<StateChanges> stateChanges) implements StreamItem {}
```

#### BlockFile

```java
package com.hedera.mirror.common.domain.transaction;

public class BlockFile implements StreamFile<BlockItem> {
}
```

## Importer

Update the handling of the topic message `runningHashVersion`:

- Change TopicMessage.runningHashVersion from `int` to `Integer` to allow for null values.
- Update `ConsensusSubmitMessageTransactionHandler` to account for block streams no longer sending the runningHashVersion value:
  - If the TransactionReceipt runningHashVersion is 3 (the current value) set the runningHashVersion to null.
  - If the TransactionReceipt runningHashVersion is not 3 set the runningHashVersion to that value.

### Interfaces and Classes

#### BlockFileReader

```java
package com.hedera.mirror.importer.reader.block;

public interface BlockFileReader extends StreamFileReader<BlockFile, BlockItem> {
}
```

#### ProtoBlockFileReader

```java
package com.hedera.mirror.importer.reader.block;

public class ProtoBlockFileReader implements BlockFileReader {
    // Generates a BlockFile from a StreamFileData.
    // Converts the protobuf BlockItems into mirror node BlockItems.
    // Protobuf BlockItems that do not represent a transaction will be filtered out here.
    // Note that a protobuf BlockFile with no transactions will still produce a mirror node BlockFile and persist that block to the database.
    public BlockFile read(StreamFileData streamFileData);
}
```

#### StreamFileTransformer

```java
package com.hedera.mirror.importer.downloader;

public interface StreamFileTransformer<T extends StreamFile<?>, S extends StreamFile<?>> {
    // Used for transforming a block file into a record file in phase one and for transforming a record file into a block file in phase two.
    T transform(S s);
}
```

#### BlockFileTransformer

```java
package com.hedera.mirror.importer.downloader.block;

public class BlockFileTransformer implements StreamFileTransformer<RecordFile, BlockFile> {

    /**
     *   Transforms the block file into a record file and calculates the block hash
     *   The transformation uses a mapping of block fields to record file fields
     *   Block items are only iterated once in the transform method
     *   State changes are accumulated and used for calculating the block hash
     *
     *   If the Block File contains a Wrapped Record File, then convert the Wrapped Record File to a Record File
     */
    @Override
    public RecordFile transform(BlockFile block);

    // Block hashes are not included in the block. They must be calculated from the state changes within the block.
    private byte[] calculateBlockHash(byte[] previousBlockHash, List<StateChanges> stateChanges);

    // The transaction hash will not be included in the block stream output so we will need to calculate it
    private byte[] calculateTransactionHash(EventTransaction transaction);
}
```

#### StreamPoller

```java
package com.hedera.mirror.importer.downloader;

public interface StreamPoller<StreamFile> {
    void poll();
}
```

#### BlockStreamPoller

```java
package com.hedera.mirror.importer.downloader.block;

public class BlockStreamPoller extends StreamPoller<BlockFile> {
    private final BlockFileReader blockFileReader;
    private final BlockStreamVerifier blockStreamVerifier;
    private final StreamFileProvider streamFileProvider;

    /**
     * Polls and downloads block stream files from an S3/GCP bucket
     * Uses the previous block number to derive the name of the next block file to download and should not use any bucket file list operations
     * Uses streamFileProvider to download the block file
     * Passes block files on to the blockStreamVerifier
     *
     * Also of note is that block file names are left padded:
     *    000000000000000000000000000000000001.blk.gz
     *
     * So we will want an efficient means of incrementing the block number
     */
    @Scheduled
    public void poll();
}
```

#### StreamFileNotifier

-Rename `verified` to `notify` as blocks will not be verifiable until each state change has been processed by the BlockStreamVerifier.

#### BlockStreamVerifier

```java
package com.hedera.mirror.importer.downloader.block;

public class BlockStreamVerifier {
    private final StreamFileNotifier streamFileNotifier;
    private final StreamFileTransformer<RecordFile, BlockFile> blockFileTransformer;
    private final RecordFileParser recordFileParser;

    /**
     * Transforms the block file into a record file, verifies the hash chain and then parses it
     * Does not parse Block N until Block N+1 has been downloaded and used to verify Block N
     */
    public void notify(@Nonnull StreamFile<?> streamFile);

    /**
     * The hash chain cannot be verified for Block N until Block N+1 has been downloaded.
     * For Block N the hash must be verified to match the previousBlockHash protobuf value provided by Block N+1
     */
    private void verifyHashChain(String expected, String actual);

    // Verifies that the number of the block file contained in its file name matches the block number within the block file
    private void verifyBlockNumber(String expected, String actual);
}
```

### Database

- Add `software_version` to the `record_file` table. This is the consensus node version that generated the block.
- Add `congestionPricingMultiplier`, `round_start`, `round_end` to the `record_file` table, these come from the block.
- Add a migration that sets the `software_version` to the hapi version to populate those fields for previous records. The `software_version` is always the `hapi_version` for records prior to the introduction of block streams. Also add `software_version` to the existing RecordFileReaders at the same time.
- Update the `topic_message` table to allow for a null `running_hash_version`.
- Rename `record_file` to `block`. This will be a low priority task near the end of implementing phase one, as it requires a large number of changes.

```sql
alter table if exists record_file
    add column if not exists software_version_major        int          null,
    add column if not exists software_version_minor        int          null,
    add column if not exists software_version_patch        int          null,
    add column if not exists congestion_pricing_multiplier bigint       null,
    add column if not exists round_start                   bigint       null,
    add column if not exists round_end                     bigint       null;

alter table if exists topic_message
    alter column if exists running_hash_version drop not null;
```

### Block to Record File Transformation

Blocks are composed of block items. A record item may be transformed from a set of multiple block items.
Beginning from an `EventTransaction` block item, a record item is composed of one `TransactionResult` block item, zero to N `TransactionOutput` block items and zero to N `StateChange` block items.

![Transformation](images/transformation.png)

## Block protobuf to mirror node database mapping

### Record File

| Database           | Block Item                                                                                                                                       |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| bytes              | raw bytes that comprise the blk file                                                                                                             |
| consensus_start    | block_header.first_transaction_consensus_time                                                                                                    |
| consensus_end      | consensus_timestamp of last transaction_output or transaction_result in the blk file. If block is empty, set to first_transaction_consensus_time |
| count              | Sum of the EventTransaction typed block items in the blk file                                                                                    |
| digest_algorithm   | block_header.hash_algorithm. Currently SHA2_384                                                                                                  |
| gas_used           | Sum of the gas_used of all block items of type 'transaction_output'                                                                              |
| hapi_version_major | block_header.hapi_proto_version.major                                                                                                            |
| hapi_version_minor | block_header.hapi_proto_version.minor                                                                                                            |
| hapi_version_patch | block_header.hapi_proto_version.patch                                                                                                            |
| hash               | Generated by the new mirror node class `BlockFileTransformer`. This calculated value must match the next block's previous_block_hash             |
| index              | block_header.number                                                                                                                              |
| load_start         | System.currentTimeMillis() at beginning of parsing                                                                                               |
| load_end           | System.currentTimeMillis() at end of parsing                                                                                                     |
| logs_bloom         | Aggregate calculated by mirror node                                                                                                              |
| name               | Name of the blk file                                                                                                                             |
| node_id            |                                                                                                                                                  |
| prev_hash          | block_header.previous_block_hash                                                                                                                 |
| sidecar_count      | Set to 0 as sidecar data is being integrated into the block                                                                                      |
| size               | byte size of the blk file                                                                                                                        |
| version            | Set to 7                                                                                                                                         |

### Contract Create Transaction

| Database                                      | Block Item                                                                                  |
| --------------------------------------------- | ------------------------------------------------------------------------------------------- |
| contract.id                                   | transaction_output.contract_create.bytecode.contract_id                                     |
| contract.initcode                             | transaction_output.contract_create.bytecode.initcode                                        |
| contract.runtime_bytecode                     | transaction_output.contract_create.bytecode.runtime_bytecode                                |
| contract_action.call_depth                    | transaction_output.contract_create.contract_actions.call_depth                              |
| contract_action.call_operation_type           | transaction_output.contract_create.contract_actions.call_operation_type                     |
| contract_action.call_type                     | transaction_output.contract_create.contract_actions.call_type                               |
| contract_action.caller                        | transaction_output.contract_create.contract_actions.caller                                  |
| contract_action.caller_type                   | transaction_output.contract_create.contract_actions.caller_case                             |
| contract_action.consensus_timestamp           | transaction_result.consensus_timestamp                                                      |
| contract_action.gas                           | transaction_output.contract_create.contract_actions[j].gas                                  |
| contract_action.gas_used                      | transaction_output.contract_create.contract_actions[j].gas_used                             |
| contract_action.index                         | Index j of transaction_output.contract_create.contract_actions[j]                           |
| contract_action.input                         | transaction_output.contract_create.contract_actions.input                                   |
| contract_action.recipient_account             | transaction_output.contract_create.contract_actions[j].recipient_account                    |
| contract_action.recipient_address             | transaction_output.contract_create.contract_actions[j].targeted_address                     |
| contract_action.recipient_contract            | transaction_output.contract_create.contract_actions[j].recipient_contract                   |
| contract_action.result_data                   | transaction_output.contract_create.contract_actions[j].result_data                          |
| contract_action.result_data_type              | transaction_output.contract_create.contract_actions[j].result_data_case                     |
| contract_action.value                         | transaction_output.contract_create.contract_actions[j].value                                |
| contract_log.bloom                            | transaction_output.contract_create.contract_function_result.log_info[i].bloom               |
| contract_log.consensus_timestamp              | transaction_result.consensus_timestamp                                                      |
| contract_log.contract_id                      | transaction_output.contract_create.contract_function_result.log_info[i].contract_id         |
| contract_log.data                             | transaction_output.contract_create.contract_function_result.log_info[i].data                |
| contract_log.index                            | Index i of transaction_output.contract_create.contract_function_result.log_info[i]          |
| contract_log.root_contract_id                 | transaction_output.contract_create.contract_function_result.contractID                      |
| contract_log.topic0                           | transaction_output.contract_create.contract_function_result.log_info[i].topic[0]            |
| contract_log.topic1                           | transaction_output.contract_create.contract_function_result.log_info[i].topic[1]            |
| contract_log.topic2                           | transaction_output.contract_create.contract_function_result.log_info[i].topic[2]            |
| contract_log.topic3                           | transaction_output.contract_create.contract_function_result.log_info[i].topic[3]            |
| contract_log.transaction_hash                 |                                                                                             |
| contract_log.transaction_index                |                                                                                             |
| contract_result.amount                        | transaction_output.contract_create.contract_function_result.amount                          |
| contract_result.bloom                         | transaction_output.contract_create.contract_function_result.bloom                           |
| contract_result.call_result                   | transaction_output.contract_create.contract_function_result.contract_call_result            |
| contract_result.consensus_timestamp           | transaction_result.consensus_timestamp                                                      |
| contract_result.contract_id                   | transaction_output.contract_create.contract_function_result.contractID                      |
| contract_result.created_contract_ids          | transaction_output.contract_create.contract_function_result.created_contract_ids            |
| contract_result.error_message                 | transaction_output.contract_create.contract_function_result.error_message                   |
| contract_result.failed_initcode               | transaction_output.contract_create.bytecode.initcode                                        |
| contract_result.function_parameters           | transaction_output.contract_create.contract_function_result.function_parameters             |
| contract_result.function_result               | transaction_output.contract_create.contract_function_result                                 |
| contract_result.gas_consumed                  | Calculated by mirror node                                                                   |
| contract_result.gas_limit                     | transaction_output.contract_create.contract_function_result.gas                             |
| contract_result.gas_used                      | transaction_output.contract_create.contract_function_result.gas_used                        |
| contract_result.sender_id                     | transaction_output.contract_create.contract_function_result.sender_id                       |
| contract_result.transaction_hash              |                                                                                             |
| contract_result.transaction_index             |                                                                                             |
| contract_result.transaction_nonce             | transaction_output.contract_create.contract_function_result.contract_nonces[i].nonce        |
| contract_result.transaction_result            |                                                                                             |
| constract_state.contract_id                   | transaction_output.contract_create.state_changes[j].contract_state_changes[k].contract_id   |
| constract_state.created_timestamp             |                                                                                             |
| constract_state.modified_timestamp            | transaction_output.contract_create.consensus_timestamp                                      |
| constract_state.slot                          | transaction_output.contract_create.state_changes[j].contract_state_changes[k].slot          |
| constract_state.value                         | transaction_output.contract_create.state_changes[j].contract_state_changes[k].value_written |
| contract_state_change.consensus_timestamp     | transaction_output.contract_create.consensus_timestamp                                      |
| contract_state_change.contract_id             | transaction_output.contract_create.state_changes[j].contract_state_changes[k].contract_id   |
| contract_state_change.migration               | transaction_output.contract_create.migration                                                |
| contract_state_change.slot                    | transaction_output.contract_create.state_changes[j].contract_state_changes[k].slot          |
| contract_state_change.value_read              | transaction_output.contract_create.state_changes[j].contract_state_changes[k].value_read    |
| contract_state_change.value_written           | transaction_output.contract_create.state_changes[j].contract_state_changes[k].value_written |
| contract_transaction_hash.consensus_timestamp |                                                                                             |
| contract_transaction_hash.hash                |                                                                                             |
| contract_transaction_hash.entity_id           |                                                                                             |
| contract_transaction_hash.transaction_result  |                                                                                             |
| entity.alias                                  | state_changes[i].state_change.map_update.value.account_value.alias                          |
| entity.auto_renew_account_id                  | state_changes[i].state_change.map_update.value.account_value.auto_renew_account_id          |
| entity.auto_renew_period                      | state_changes[i].state_change.map_update.value.account_value.auto_renew_seconds             |
| entity.balance                                | state_changes[i].state_change.map_update.value.account_value.tinybar_balance                |
| entity.balance_timestamp                      | state_changes.consensus_timestamp                                                           |
| entity.created_timestamp                      |                                                                                             |
| entity.decline_reward                         | state_changes[i].state_change.map_update.value.account_value.decline_reward                 |
| entity.deleted                                | state_changes[i].state_change.map_delete.key.accountId                                      |
| entity.ethereum_nonce                         | state_changes[i].state_change.map_update.value.account_value.ethereum_nonce                 |
| entity.evm_address                            | transaction_output.contract_create.contract_function_result.evm_address                     |
| entity.expiration_timestamp                   | state_changes[i].state_change.map_update.value.account_value.expiration_second              |
| entity.id                                     | state_changes[i].state_change.map_update.value.account_id_value                             |
| entity.key                                    | state_changes[i].state_change.map_update.value.account_value.key                            |
| entity.max_automatic_token_associations       | state_changes[i].state_change.map_update.value.account_value.max_auto_associations          |
| entity.memo                                   | state_changes[i].state_change.map_update.value.account_value.memo                           |
| entity.num                                    | state_changes[i].state_change.map_update.value.account_id_value.accountNum                  |
| entity.proxy_account_id                       |                                                                                             |
| entity.public_key                             |                                                                                             |
| entity.realm                                  | state_changes[i].state_change.map_update.value.account_id_value.realmNum                    |
| entity.receiver_sig_required                  | state_changes[i].state_change.map_update.value.account_value.receiver_sig_required          |
| entity.shard                                  | state_changes[i].state_change.map_update.value.account_id_value.shardNum                    |
| entity.staked_account_id                      | state_changes[i].state_change.map_update.value.account_value.staked_account_id              |
| entity.staked_node_id                         | state_changes[i].state_change.map_update.value.account_value.staked_node_id                 |
| entity.stake_period_start                     | state_changes[i].state_change.map_update.value.account_value.stake_period_start             |
| entity.submit_key                             | state_changes[i].state_change.map_update.value.account_value.submit_key                     |
| entity.type                                   | state_changes[i].state_change.map_update.value: hasAccountValue/hasTokenValue               |

### Contract Call Transaction

| Database                                | Block Item                                                                                                          |
| --------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| contract.id                             | state_changes[i].state_change.map_update.value.account_value -Note: This is the contract id from TransactionReceipt |
| Updates to contract_action              | Similar to contract create, accessed at transaction_output.contract_call.contract_actions                           |
| Updates to contract_log/contract_result | Similar to contract create, accessed at transaction_output.contract_call.contract_function_result                   |

### CryptoCreate Transaction

| Database           | Block Item                                                         |
| ------------------ | ------------------------------------------------------------------ |
| entity.id          | state_changes[i].state_change.map_update.value.account_id_value    |
| entity.evm_address | state_changes[i].state_change.map_update.value.account_value.alias |

### CryptoTransfer Transaction

| Database                                        | Block Item                                                                                       |
| ----------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| assessed_custom_fee.amount                      | transaction_output.crypto_transfer.assessedCustomFees[i].amount                                  |
| assessed_custom_fee.collector_account_id        | transaction_output.crypto_transfer.assessedCustomFees[i].feeCollectorAccountId                   |
| assessed_custom_fee.consensus_timestamp         | transaction_result.consensus_timestamp                                                           |
| assessed_custom_fee.effective_payer_account_ids | transaction_output.crypto_transfer.assessedCustomFees[i].effectivePayerAccountId                 |
| assessed_custom_fee.token_id                    | transaction_output.crypto_transfer.assessedCustomFees[i].tokenId                                 |
| token_account.account_id                        | transaction_output.crypto_transfer.automatic_token_associations[i].accountId                     |
| token_account.token_id                          | transaction_output.crypto_transfer.automatic_token_associations[i].tokenId                       |
| token_transfer.account_id                       | transaction_output.crypto_transfer.token_transfer_lists[i].transfers[j].accountAmount.accountID  |
| token_transfer.amount                           | transaction_output.crypto_transfer.token_transfer_lists[i].transfers[j].accountAmount.amount     |
| token_transfer.consensus_timestamp              | transaction_result.consensus_timestamp                                                           |
| token_transfer.is_approval                      | transaction_output.crypto_transfer.token_transfer_lists[i].transfers[j].accountAmount.isApproval |
| token_transfer.token_id                         | transaction_output.crypto_transfer.token_transfer_lists[i].token.tokenID                         |

### CryptoUpdate Transaction

| Database  | Block Item                                                      |
| --------- | --------------------------------------------------------------- |
| entity.id | state_changes[i].state_change.map_update.value.account_id_value |

### Ethereum Transaction

| Database                  | Block Item                                                      |
| ------------------------- | --------------------------------------------------------------- |
| ethereum_transaction.hash | transaction_output.ethereum.ethereum_hash                       |
| entity.id                 | transaction_output.ethereum.contract_function_result.contractId |

### File Create/Update

| Database                      | Block Item                                             |
| ----------------------------- | ------------------------------------------------------ |
| file_data.consensus_timestamp | transaction_result.consensus_timestamp                 |
| file_data.entity_id           | state_changes[i].state_change.map_update.key.fileIdKey |

### Node Create Transaction

| Database     | Block Item                                                     |
| ------------ | -------------------------------------------------------------- |
| node.node_id | state_changes[i].state_change.map_update.key.entity_number_key |

### Schedule Create Transaction

| Database    | Block Item                                                 |
| ----------- | ---------------------------------------------------------- |
| schedule.id | state_changes[i].state_change.map_update.key.scheduleIdKey |

### Schedule Sign Transaction

| Database  | Block Item                                                |
| --------- | --------------------------------------------------------- |
| entity.id | transaction_output.schedule_sign.scheduled_transaction_id |

### Token Airdrop

| Database                           | Block Item                                                                                     |
| ---------------------------------- | ---------------------------------------------------------------------------------------------- |
| token_airdrop.amount               | state_changes[i].state_change.map_update.value.accountPendingAirdropValue.pendingAirdropValue  |
| token_airdrop.receiver_account_id  | state_changes[i].state_change.map_update.key.receiverId                                        |
| token_airdrop.sender_account_id    | state_changes[i].state_change.map_update.key.senderId                                          |
| token_airdrop.serial_number        | state_changes[i].state_change.map_update.key.tokenReference.serial                             |
| token_airdrop.token_id             | state_changes[i].state_change.map_update.key.tokenReference.tokenId                            |
| Updates accessed_custom_fee        | Similar to crypto_transfer, accessed at transaction_output.token_airdrop                       |
| token_account.account_id           | transaction_output.token_airdrop.automatic_token_associations[i].accountId                     |
| token_account.token_id             | transaction_output.token_airdrop.automatic_token_associations[i].tokenId                       |
| token_transfer.account_id          | transaction_output.token_airdrop.token_transfer_lists[i].transfers[j].accountAmount.accountID  |
| token_transfer.amount              | transaction_output.token_airdrop.token_transfer_lists[i].transfers[j].accountAmount.amount     |
| token_transfer.consensus_timestamp | transaction_result.consensus_timestamp                                                         |
| token_transfer.is_approval         | transaction_output.token_airdrop.token_transfer_lists[i].transfers[j].accountAmount.isApproval |
| token_transfer.token_id            | transaction_output.token_airdrop.token_transfer_lists[i].token.tokenID                         |

### Token Create Transaction

| Database  | Block Item                                              |
| --------- | ------------------------------------------------------- |
| entity.id | state_changes[i].state_change.map_update.key.tokenIdKey |

### Token Mint Transaction

| Database            | Block Item                                                           |
| ------------------- | -------------------------------------------------------------------- |
| token.serial_number | state_changes[i].state_change.map_update.key.nft_id_key.serialNumber |

### Token Update Transaction

| Database                 | Block Item                                                                   |
| ------------------------ | ---------------------------------------------------------------------------- |
| token_account.account_id | state_changes[i].state_change.map_update.value.token_value.treasuryAccountId |
| token_account.token_id   | state_changes[i].state_change.map_update.key.tokenIdKey                      |

### Token Wipe Transaction

| Database           | Block Item                                                                                             |
| ------------------ | ------------------------------------------------------------------------------------------------------ |
| token.total_supply | Existing supply minus the sum of state_changes[i].state_change.map_delete.key.nft_id_key.serial_number |

### Topic Create Transaction

| Database                          | Block Item                                                    |
| --------------------------------- | ------------------------------------------------------------- |
| topic_message.consensus_timestamp | state_changes.consensus_timestamp                             |
| topic_message.running_hash        | state_changes[i].state_change.map_update.value.runningHash    |
| topic_message.sequence_number     | state_changes[i].state_change.map_update.value.sequenceNumber |
| topic_message.topic_id            | state_changes[i].state_change.map_update.value.topicId        |

### Topic Delete Transaction

| Database       | Block Item                                             |
| -------------- | ------------------------------------------------------ |
| entity.id      | state_changes[i].state_change.map_update.value.topicId |
| entity.deleted | state_changes[i].state_change.map_delete.key.topicId   |

### Topic Submit Message

| Database                           | Block Item                                                                                                                                                    |
| ---------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| topic_message.consensus_timestamp  | state_changes.consensus_timestamp                                                                                                                             |
| topic_message.running_hash         | state_changes[i].state_change.map_update.value.runningHash                                                                                                    |
| topic_message.running_hash_version | Map this value to the current value of 3. This value is no longer submitted by the block stream: This is a fixed value that is only updated with a HIP change |
| topic_message.sequence_number      | state_changes[i].state_change.map_update.value.sequenceNumber                                                                                                 |

### Transaction

| Database                               | Block Item                                              |
| -------------------------------------- | ------------------------------------------------------- |
| transaction.consensus_timestamp        | transaction_result.consensus_timestamp                  |
| transaction.nft_transfer               | transaction_result.token_transfer_list[i].nft_transfers |
| transaction.parent_consensus_timestamp | transaction_result.parent_consensus_timestamp           |
| transaction.result                     | transaction_result.status                               |
| transaction.scheduled                  | transaction_result.hasScheduleRef                       |

### UtilPrng Transaction

| Database            | Block Item                                       |
| ------------------- | ------------------------------------------------ |
| consensus_timestamp | transaction_result.consensus_timestamp           |
| prng_bytes          | transaction_output.util_prng.entropy.prng_bytes  |
| prng_number         | transaction_output.util_prng.entropy.prng_number |

## REST API

- Deprecate the REST API's State Proof Alpha. The record files and signature files will no longer be provided in the cloud buckets.

## Testing

- Add a BlockFile to RecordFile comparer class to the importer along with a configuration option to ingest both record and block files together and to compare the transformed record file to the corresponding record file and to log differences. This configuration can be run in test environments.
- Add block file to record file transform metrics to the Grafana dashboard.
- Add a performance test for the Performance environment that ingests block files instead of record files.
- Add `BlockFileBuilder` and `BlockItemBuilder` domain classes.
- Add downloader tests for block files similar to the existing downloader tests for record files in `com.hedera.mirror.importer.downloader.record`.
- Add parsing tests for block files similar to the record file tests in `com.hedera.mirror.importer.parser.record`.
