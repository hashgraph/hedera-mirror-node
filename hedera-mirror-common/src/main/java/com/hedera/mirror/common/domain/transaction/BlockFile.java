/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.common.domain.transaction.RecordFile.HAPI_VERSION_NOT_SET;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hedera.hapi.block.stream.output.protoc.BlockHeader;
import com.hedera.hapi.block.stream.protoc.BlockProof;
import com.hedera.mirror.common.domain.DigestAlgorithm;
import com.hedera.mirror.common.domain.StreamFile;
import com.hedera.mirror.common.domain.StreamType;
import com.hederahashgraph.api.proto.java.BlockStreamInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Transient;
import java.util.Collection;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.util.Version;

@Builder(toBuilder = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BlockFile implements StreamFile<BlockItem> {

    // Contains the block number and the previous block hash
    private BlockHeader blockHeader;

    // Used to generate block hash
    private BlockProof blockProof;

    // Contained within the last StateChange of the block, contains hashes needed to generate the block hash
    private BlockStreamInfo blockStreamInfo;

    // TODO, determine fields not needed

    @ToString.Exclude
    private byte[] bytes;

    private Long consensusStart;

    private Long consensusEnd;

    private Long count;

    @Enumerated
    private DigestAlgorithm digestAlgorithm;

    @ToString.Exclude
    private String fileHash;

    @Builder.Default
    private long gasUsed = 0L;

    private Integer hapiVersionMajor;
    private Integer hapiVersionMinor;
    private Integer hapiVersionPatch;

    @Getter(lazy = true)
    @JsonIgnore
    @Transient
    private final Version hapiVersion = hapiVersion();

    @ToString.Exclude
    private String hash;

    private Long index;

    @Builder.Default
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    @ToString.Exclude
    @Transient
    private Collection<BlockItem> items = List.of();

    private Long loadEnd;

    private Long loadStart;

    @ToString.Exclude
    private byte[] logsBloom;

    @ToString.Exclude
    @JsonIgnore
    @Transient
    private String metadataHash;

    private String name;

    private Long nodeId;

    @Column(name = "prev_hash")
    @JsonProperty("prev_hash")
    @ToString.Exclude
    private String previousHash;

    // private int sidecarCount = 0;

    private Integer size;

    private int version;

    @Override
    public void clear() {
        StreamFile.super.clear();
        setLogsBloom(null);
    }

    @Override
    public StreamFile<BlockItem> copy() {
        return this.toBuilder().build();
    }

    @Override
    public void setItems(Collection<BlockItem> items) {
        this.items = items;
    }

    @Override
    @JsonIgnore
    public StreamType getType() {
        return StreamType.BLOCK;
    }

    private Version hapiVersion() {
        if (hapiVersionMajor == null || hapiVersionMinor == null || hapiVersionPatch == null) {
            return HAPI_VERSION_NOT_SET;
        }

        return new Version(hapiVersionMajor, hapiVersionMinor, hapiVersionPatch);
    }
}
