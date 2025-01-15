/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.common.domain.transaction;

import com.hedera.hapi.block.stream.output.protoc.BlockHeader;
import com.hedera.hapi.block.stream.protoc.BlockProof;
import com.hedera.hapi.block.stream.protoc.RecordFileItem;
import com.hedera.mirror.common.domain.DigestAlgorithm;
import com.hedera.mirror.common.domain.StreamFile;
import com.hedera.mirror.common.domain.StreamType;
import java.util.Collection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Singular;
import lombok.ToString;

@Builder(toBuilder = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BlockFile implements StreamFile<BlockItem> {

    // Contains the block number and the previous block hash
    private BlockHeader blockHeader;

    // Used to generate block hash
    private BlockProof blockProof;

    @ToString.Exclude
    private byte[] bytes;

    private Long consensusStart;

    private Long consensusEnd;

    private Long count;

    private DigestAlgorithm digestAlgorithm;

    @ToString.Exclude
    private String hash;

    private Long index;

    @EqualsAndHashCode.Exclude
    @Singular
    @ToString.Exclude
    private Collection<BlockItem> items;

    private Long loadEnd;

    private Long loadStart;

    private String name;

    private Long nodeId;

    @ToString.Exclude
    private String previousHash;

    private RecordFileItem recordFileItem;

    private Long roundEnd;

    private Long roundStart;

    private Integer size;

    private int version;

    @Override
    public StreamFile<BlockItem> copy() {
        return this.toBuilder().build();
    }

    @Override
    public String getFileHash() {
        return null;
    }

    @Override
    public StreamType getType() {
        return StreamType.BLOCK;
    }

    public static class BlockFileBuilder {

        public BlockFileBuilder onNewRound(long roundNumber) {
            if (roundStart == null) {
                roundStart = roundNumber;
            }

            roundEnd = roundNumber;
            return this;
        }

        public BlockFileBuilder onNewTransaction(long consensusTimestamp) {
            if (consensusStart == null) {
                consensusStart = consensusTimestamp;
            }

            consensusEnd = consensusTimestamp;
            return this;
        }
    }
}
