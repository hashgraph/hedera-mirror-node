package com.hedera.mirror.importer.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import java.util.ArrayList;
import java.util.List;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import com.hedera.mirror.importer.converter.AccountIdConverter;
import com.hedera.mirror.importer.parser.domain.RecordItem;

@Builder(toBuilder = true)
@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
public class RecordFile implements StreamFile {

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

    private Integer hapiVersionMajor;

    private Integer hapiVersionMinor;

    private Integer hapiVersionPatch;

    @ToString.Exclude
    private String hash;

    private Long index;

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Transient
    private List<RecordItem> items = new ArrayList<>();

    private Long loadEnd;

    private Long loadStart;

    @ToString.Exclude
    @Transient
    private String metadataHash;

    private String name;

    @Convert(converter = AccountIdConverter.class)
    private EntityId nodeAccountId;

    @Column(name = "prev_hash")
    @ToString.Exclude
    private String previousHash;

    private int version;

    @Override
    public StreamType getType() {
        return StreamType.RECORD;
    }

    @Override
    public boolean hasIndex() {
        return true;
    }
}
