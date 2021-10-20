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

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.hedera.mirror.importer.converter.AccountIdConverter;

@Builder(toBuilder = true)
@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
public class RecordFileBase {

    private byte[] bytes;

    private Long consensusStart;

    @Id
    private Long consensusEnd;

    private Long count;

    @Enumerated
//    @JsonSerialize(using = DigestAlgorithmSerializer.class)
    private DigestAlgorithm digestAlgorithm;

    private String fileHash;

    private Integer hapiVersionMajor;

    private Integer hapiVersionMinor;

    private Integer hapiVersionPatch;

    private String hash;

    private Long index;

    private Long loadEnd;

    private Long loadStart;

    private String name;

    @Convert(converter = AccountIdConverter.class)
    private EntityId nodeAccountId;

    private String prevHash;

    private int version;
}
