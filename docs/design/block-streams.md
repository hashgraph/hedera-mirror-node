# HIP-1056 Block Streams

## Purpose

[HIP-1056](https://hips.hedera.com/hip/hip-1056) introduces a new output data format for consensus nodes, called block streams, that replaces the existing
record streams and signature files with one single stream. Support for block streams in the Mirror Node will be split
into two phases. This design document concerns the first phase which will translate the block streams into the existing
record stream format and parse the translated record streams just as we do today. The second phase (to be detailed in a
separate design document) will remove the translation and parse the block streams directly and translate record streams
into block streams to allow the mirror node to continue to ingest all record streams from the past.

## Goals

- Ingest block stream files downloaded from an S3/GCP bucket and translate them into record stream files.

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

public class BlockItem implements StreamItem {
}
```

#### BlockFile

```java
package com.hedera.mirror.common.domain.transaction;

public class BlockFile implements StreamFile<BlockItem> {
}
```

## Importer

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
    // Generates a BlockFile from a StreamFileData
    public BlockFile read(StreamFileData streamFileData);
}
```

#### StreamFileTranslator

```java
package com.hedera.mirror.importer.downloader;

public interface StreamFileTranslator<T extends StreamFile<?>, S extends StreamFile<?>> {
    // Used for translating a block file into a record file in phase one and for translating a record file into a block file in phase two.
    T translate(S s);
}
```

#### BlockFileTranslator

```java
package com.hedera.mirror.importer.downloader.block;

public class BlockFileTranslator implements StreamFileTranslator<RecordFile, BlockFile> {

    /**
     *   Translates the block file into a record file and calculates the hash chain
     *   The translation uses a mapping of block fields to record file fields
     *   Block items are only iterated once in the translate method.
     *   State changes are accumulated and used for calculating the block hash at the end of the block.
     *
     *   If the Block File contains a Wrapped Record File, then convert the Wrapped Record File to a Record File.
     */
    @Override
    public RecordFile translate(BlockFile block);

    // Block hashes are not included in the block. They must be calculated from the state changes within the block.
    private byte[] calculateBlockHash(byte[] previousBlockHash, List<StateChanges> stateChanges);
}
```

#### StreamPoller

```java
package com.hedera.mirror.importer.downloader;

public abstract class StreamPoller<StreamFile> {
    public abstract void poll();
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

public class BlockStreamVerifier implements StreamFileNotifier, Closable {
    private final StreamFileTranslator<RecordFile, BlockFile> blockFileTranslator;
    private final RecordFileParser recordFileParser;

    // Translates the block file into a record file, verifies the hash chain and then parses it
    public void notify(@Nonnull StreamFile<?> streamFile);

    /**
     * The hash chain cannot be verified for block N until block N-1 has been translated by the blockFileTranslator.
     * For Block N, the previousBlockHash (a value included in the Block protobuf) must be verified to match the hash calculated by the blockFileTranslator for Block N-1.
     */
    private void verifyHashChain(String expected, String actual);

    // Verifies that the number of the block file contained in its file name matches the block number within the block file
    private void verifyBlockNumber(String expected, String actual);
}
```

### Database

Add `software_version` to the `record_file` table. This is the consensus node version that generated the block.
Add `congestionPricingMultiplier` to the `record_file` table.
Rename `record_file` to `block_file`.

## Block protobuf to mirror node database mapping

### Record File

| Database           | Block Item                                                                                                                          |
| ------------------ | ----------------------------------------------------------------------------------------------------------------------------------- |
| bytes              | raw bytes that comprise the blk file                                                                                                |
| consensus_start    | block_header.first_transaction_consensus_time                                                                                       |
| consensus_end      | consensus_timestamp of last transaction_output or transaction_result in the blk file                                                |
| count              | Sum of the transaction_output and transaction_result typed block items in the blk file                                              |
| digest_algorithm   | block_header.hash_algorithm. Currently SHA2_384                                                                                     |
| gas_used           | Sum of the gas_used of all block items of type 'transaction_output'                                                                 |
| hapi_version_major | block_header.hapi_proto_version.major                                                                                               |
| hapi_version_minor | block_header.hapi_proto_version.minor                                                                                               |
| hapi_version_patch | block_header.hapi_proto_version.patch                                                                                               |
| hash               | Generated by the new mirror node class `BlockFileTranslator`. This calculated value must match the next block's previous_block_hash |
| index              | block_header.number                                                                                                                 |
| load_start         | System.currentTimeMillis() at beginning of parsing                                                                                  |
| load_end           | System.currentTimeMillis() at end of parsing                                                                                        |
| logs_bloom         | Aggregate calculated by mirror node                                                                                                 |
| name               | Name of the blk file                                                                                                                |
| node_id            |                                                                                                                                     |
| prev_hash          | block_header.previous_block_hash                                                                                                    |
| sidecar_count      | Count of all transaction*output.\_transaction type*.sidecars in the blk file                                                        |
| size               | byte size of the blk file                                                                                                           |
| version            | Record stream version - Perhaps a new version should be added to indicate "Translated from Block"                                   |

### Contract Create Transaction

| Database                                      | Block Item                                                                                              |
| --------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| contract.id                                   | transaction_output.contract_create.sidecars[i].bytecode.contract_id                                     |
| contract.file_id                              | state_changes[i].state_change.map_update.value.file_value.file_id                                       |
| contract.initcode                             | transaction_output.contract_create.sidecars[i].bytecode.initcode                                        |
| contract.runtime_bytecode                     | transaction_output.contract_create.sidecars[i].bytecode.runtime_bytecode                                |
| contract_action.call_depth                    | transaction_output.contract_create.sidecars[i].actions.contract_actions.call_depth                      |
| contract_action.call_operation_type           | transaction_output.contract_create.sidecars[i].actions.contract_actions.call_operation_type             |
| contract_action.call_type                     | transaction_output.contract_create.sidecars[i].actions.contract_actions.call_type                       |
| contract_action.caller                        | transaction_output.contract_create.sidecars[i].actions.contract_actions.caller                          |
| contract_action.caller_type                   | transaction_output.contract_create.sidecars[i].actions.contract_actions.caller_case                     |
| contract_action.consensus_timestamp           | transaction_output.contract_create.sidecars[i].consensus_timestamp                                      |
| contract_action.gas                           | transaction_output.contract_create.sidecars[i].actions.contract_actions[j].gas                          |
| contract_action.gas_used                      | transaction_output.contract_create.sidecars[i].actions.contract_actions[j].gas_used                     |
| contract_action.index                         | Index j of transaction_output.contract_create.sidecars[i].actions.contract_actions[j]                   |
| contract_action.input                         | transaction_output.contract_create.sidecars[i].actions.contract_actions.input                           |
| contract_action.payer_account_id              | state_changes[i].state_change.map_update.value.account_value.account_id                                 |
| contract_action.recipient_account             | transaction_output.contract_create.sidecars[i].actions.contract_actions[j].recipient_account            |
| contract_action.recipient_address             | transaction_output.contract_create.sidecars[i].actions.contract_actions[j].targeted_address             |
| contract_action.recipient_contract            | transaction_output.contract_create.sidecars[i].actions.contract_actions[j].recipient_contract           |
| contract_action.result_data                   | transaction_output.contract_create.sidecars[i].actions.contract_actions[j].result_data                  |
| contract_action.result_data_type              | transaction_output.contract_create.sidecars[i].actions.contract_actions[j].result_data_case             |
| contract_action.value                         | transaction_output.contract_create.sidecars[i].actions.contract_actions[j].value                        |
| contract_log.bloom                            | transaction_output.contract_create.contract_create_result.log_info[i].bloom                             |
| contract_log.consensus_timestamp              | transaction_result.consensus_timestamp                                                                  |
| contract_log.contract_id                      | transaction_output.contract_create.contract_create_result.log_info[i].contract_ID                       |
| contract_log.data                             | transaction_output.contract_create.contract_create_result.log_info[i].data                              |
| contract_log.index                            | Index i of transaction_output.contract_create.contract_create_result.log_info[i]                        |
| contract_log.payer_account_id                 |                                                                                                         |
| contract_log.root_contract_id                 | transaction_output.contract_create.contract_create_result.contractID                                    |
| contract_log.topic0                           | transaction_output.contract_create.contract_create_result.log_info[i].topic[0]                          |
| contract_log.topic1                           | transaction_output.contract_create.contract_create_result.log_info[i].topic[1]                          |
| contract_log.topic2                           | transaction_output.contract_create.contract_create_result.log_info[i].topic[2]                          |
| contract_log.topic3                           | transaction_output.contract_create.contract_create_result.log_info[i].topic[3]                          |
| contract_log.transaction_hash                 |                                                                                                         |
| contract_log.transaction_index                |                                                                                                         |
| contract_result.amount                        | transaction_output.contract_create.contract_create_result.amount                                        |
| contract_result.bloom                         | transaction_output.contract_create.contract_create_result.bloom                                         |
| contract_result.call_result                   | transaction_output.contract_create.contract_create_result.contract_call_result                          |
| contract_result.consensus_timestamp           | transaction_result.consensus_timestamp                                                                  |
| contract_result.contract_id                   | transaction_output.contract_create.contract_create_result.contractID                                    |
| contract_result.created_contract_ids          | transaction_output.contract_create.contract_create_result.created_contract_ids                          |
| contract_result.error_message                 | transaction_output.contract_create.contract_create_result.error_message                                 |
| contract_result.failed_initcode               | transaction_output.contract_create.sidecars[i].bytecode.initcode                                        |
| contract_result.function_parameters           | transaction_output.contract_create.contract_create_result.function_parameters                           |
| contract_result.function_result               | transaction_output.contract_create.contract_create_result                                               |
| contract_result.gas_consumed                  | Calculated by mirror node                                                                               |
| contract_result.gas_limit                     | transaction_output.contract_create.contract_create_result.gas                                           |
| contract_result.gas_used                      | transaction_output.contract_create.contract_create_result.gas_used                                      |
| contract_result.payer_account_id              |                                                                                                         |
| contract_result.sender_id z                   | transaction_output.contract_create.contract_create_result.sender_id                                     |
| contract_result.transaction_hash              |                                                                                                         |
| contract_result.transaction_index             |                                                                                                         |
| contract_result.transaction_nonce             | transaction_output.contract_create.contract_create_result.contract_nonces[i].nonce                      |
| contract_result.transaction_result            |                                                                                                         |
| constract_state.contract_id                   | transaction_output.contract_create.sidecars[i].state_changes[j].contract_state_changes[k].contract_id   |
| constract_state.created_timestamp             |                                                                                                         |
| constract_state.modified_timestamp            | transaction_output.contract_create.sidecars[i].consensus_timestamp                                      |
| constract_state.slot                          | transaction_output.contract_create.sidecars[i].state_changes[j].contract_state_changes[k].slot          |
| constract_state.value                         | transaction_output.contract_create.sidecars[i].state_changes[j].contract_state_changes[k].value_written |
| contract_state_change.consensus_timestamp     | transaction_output.contract_create.sidecars[i].consensus_timestamp                                      |
| contract_state_change.contract_id             | transaction_output.contract_create.sidecars[i].state_changes[j].contract_state_changes[k].contract_id   |
| contract_state_change.migration               | transaction_output.contract_create.sidecars[i].migration                                                |
| contract_state_change.payer_account_id        |                                                                                                         |
| contract_state_change.slot                    | transaction_output.contract_create.sidecars[i].state_changes[j].contract_state_changes[k].slot          |
| contract_state_change.value_read              | transaction_output.contract_create.sidecars[i].state_changes[j].contract_state_changes[k].value_read    |
| contract_state_change.value_written           | transaction_output.contract_create.sidecars[i].state_changes[j].contract_state_changes[k].value_written |
| contract_transaction_hash.consensus_timestamp |                                                                                                         |
| contract_transaction_hash.hash                |                                                                                                         |
| contract_transaction_hash.payer_account_id    |                                                                                                         |
| contract_transaction_hash.entity_id           |                                                                                                         |
| contract_transaction_hash.transaction_result  |                                                                                                         |
| entity.alias                                  | state_changes[i].state_change.map_update.value.account_value.alias                                      |
| entity.auto_renew_account_id                  | state_changes[i].state_change.map_update.value.account_value.auto_renew_account_id                      |
| entity.auto_renew_period                      | state_changes[i].state_change.map_update.value.account_value.auto_renew_seconds                         |
| entity.balance                                | state_changes[i].state_change.map_update.value.account_value.tinybar_balance                            |
| entity.balance_timestamp                      | state_changes.consensus_timestamp                                                                       |
| entity.created_timestamp                      |                                                                                                         |
| entity.decline_reward                         | state_changes[i].state_change.map_update.value.account_value.decline_reward                             |
| entity.deleted                                | state_changes[i].state_change.map_update.value.account_value.deleted                                    |
| entity.ethereum_nonce                         | state_changes[i].state_change.map_update.value.account_value.ethereum_nonce                             |
| entity.evm_address                            | transaction_output.contract_create.contract_create_result.evm_address                                   |
| entity.expiration_timestamp                   | state_changes[i].state_change.map_update.value.account_value.expiration_second                          |
| entity.id                                     | state_changes[i].state_change.map_update.value.account_id_value                                         |
| entity.key                                    | state_changes[i].state_change.map_update.value.account_value.key                                        |
| entity.max_automatic_token_associations       | state_changes[i].state_change.map_update.value.account_value.max_auto_associations                      |
| entity.memo                                   | state_changes[i].state_change.map_update.value.account_value.memo                                       |
| entity.num                                    | state_changes[i].state_change.map_update.value.account_id_value.accountNum                              |
| entity.proxy_account_id                       |                                                                                                         |
| entity.public_key                             |                                                                                                         |
| entity.realm                                  | state_changes[i].state_change.map_update.value.account_id_value.realmNum                                |
| entity.receiver_sig_required                  | state_changes[i].state_change.map_update.value.account_value.receiver_sig_required                      |
| entity.shard                                  | state_changes[i].state_change.map_update.value.account_id_value.shardNum                                |
| entity.staked_account_id                      | state_changes[i].state_change.map_update.value.account_value.staked_account_id                          |
| entity.staked_node_id                         | state_changes[i].state_change.map_update.value.account_value.staked_node_id                             |
| entity.stake_period_start                     | state_changes[i].state_change.map_update.value.account_value.stake_period_start                         |
| entity.submit_key                             | state_changes[i].state_change.map_update.value.account_value.submit_key                                 |
| entity.type                                   | state_changes[i].state_change.map_update.value: hasAccountValue/hasTokenValue                           |

### Topic Create Transaction

| Database                            | Block Item                                                    |
| ----------------------------------- | ------------------------------------------------------------- |
| topic_message.consensus_timestamp   | state_changes.consensus_timestamp                             |
| topic_message.payer_account_id      |                                                               |
| topic_message.running_hash          | state_changes[i].state_change.map_update.value.runningHash    |
| topic_message.sequence_number       | state_changes[i].state_change.map_update.value.sequenceNumber |
| topic_message.topic_id              | state_changes[i].state_change.map_update.value.topicId        |
| topic_message.valid_start_timestamp |                                                               |

### Topic Delete Transaction

| Database       | Block Item                                             |
| -------------- | ------------------------------------------------------ |
| entity.id      | state_changes[i].state_change.map_update.value.topicId |
| entity.deleted | state_changes[i].state_change.map_update.value.deleted |

### UtilPrng Transaction

| Database            | Block Item                                 |
| ------------------- | ------------------------------------------ |
| consensus_timestamp | transaction_result.consensus_timestamp     |
| payer_account_id    |                                            |
| prng_bytes          | transaction_output.util_prng.entropy.value |
| prng_number         | transaction_output.util_prng.entropy.value |

## REST API

-Deprecate the REST API's State Proof Alpha. The record files and signature files will no longer be provided in the cloud buckets.
