/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hedera.mirror.common.domain.DigestAlgorithm;
import com.hedera.mirror.common.domain.StreamFile;
import com.hedera.mirror.common.domain.StreamType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
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
@Entity
@AllArgsConstructor
@NoArgsConstructor
public class RecordFile implements StreamFile<RecordItem> {

    public static final Version HAPI_VERSION_NOT_SET = new Version(0, 0, 0);
    public static final Version HAPI_VERSION_0_23_0 = new Version(0, 23, 0);
    public static final Version HAPI_VERSION_0_27_0 = new Version(0, 27, 0);
    public static final Version HAPI_VERSION_0_47_0 = new Version(0, 47, 0);

    @ToString.Exclude
    private byte[] bytes;

    private Long consensusStart;

    @Id
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
    private Collection<RecordItem> items = List.of();

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

    private int sidecarCount;

    @Builder.Default
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    @ToString.Exclude
    @Transient
    private Collection<SidecarFile> sidecars = List.of();

    private Integer size;

    private int version;

    @Override
    public void clear() {
        StreamFile.super.clear();
        setLogsBloom(null);
        setSidecars(List.of());
    }

    @Override
    public StreamFile<RecordItem> copy() {
        return this.toBuilder().build();
    }

    @Override
    @JsonIgnore
    public StreamType getType() {
        return StreamType.RECORD;
    }

    private Version hapiVersion() {
        if (hapiVersionMajor == null || hapiVersionMinor == null || hapiVersionPatch == null) {
            return HAPI_VERSION_NOT_SET;
        }

        return new Version(hapiVersionMajor, hapiVersionMinor, hapiVersionPatch);
    }
}
