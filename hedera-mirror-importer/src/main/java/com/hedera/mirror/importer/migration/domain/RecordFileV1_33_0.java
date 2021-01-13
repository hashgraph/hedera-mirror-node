package com.hedera.mirror.importer.migration.domain;

/*-
 *
 * Hedera Mirror Node
 *  ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 *  ​
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
 *
 */

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.hedera.mirror.importer.converter.AccountIdConverter;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.StreamFile;

@Builder
@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "record_file")
public class RecordFileV1_33_0 implements StreamFile {

    private Long consensusStart;

    private Long consensusEnd;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private Long loadStart;

    private Long loadEnd;

    private String fileHash;

    @Column(name = "prev_hash")
    private String previousHash;

    @Convert(converter = AccountIdConverter.class)
    private EntityId nodeAccountId;

    private Long count;

    @Transient
    private int recordFormatVersion;

    @Override
    public String getCurrentHash() {
        return fileHash;
    }
}
