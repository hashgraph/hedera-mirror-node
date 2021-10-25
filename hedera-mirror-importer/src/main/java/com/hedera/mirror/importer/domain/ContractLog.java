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

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import javax.persistence.Convert;
import javax.persistence.IdClass;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.domain.Persistable;

import com.hedera.mirror.importer.converter.ContractIdConverter;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For Builder
@Builder
@Data
@javax.persistence.Entity
@IdClass(ContractLog.Id.class)
@NoArgsConstructor
public class ContractLog implements Persistable<ContractLog.Id> {

    @ToString.Exclude
    private byte[] bloom;

    @javax.persistence.Id
    private long consensusTimestamp;

    @Convert(converter = ContractIdConverter.class)
    private EntityId contractId;

    @ToString.Exclude
    private byte[] data;

    @javax.persistence.Id
    private int index;

    private String topic0;

    private String topic1;

    private String topic2;

    private String topic3;

    @JsonIgnore
    public Id getId() {
        Id id = new Id();
        id.setConsensusTimestamp(consensusTimestamp);
        id.setIndex(index);
        return id;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true;
    }

    @Data
    public static class Id implements Serializable {
        private long consensusTimestamp;
        private int index;
    }
}
