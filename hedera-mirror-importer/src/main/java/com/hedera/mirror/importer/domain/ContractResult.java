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
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Collections;
import java.util.List;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Type;
import org.springframework.data.domain.Persistable;

import com.hedera.mirror.importer.converter.ContractIdConverter;
import com.hedera.mirror.importer.converter.LongListToStringSerializer;

@Data
@Entity
@NoArgsConstructor
@SuperBuilder
public class ContractResult implements Persistable<Long> {

    private Long amount;

    @ToString.Exclude
    private byte[] bloom;

    @ToString.Exclude
    private byte[] callResult;

    @Id
    private Long consensusTimestamp;

    @Convert(converter = ContractIdConverter.class)
    private EntityId contractId;

    @Type(type = "com.vladmihalcea.hibernate.type.array.ListArrayType")
    @JsonSerialize(using = LongListToStringSerializer.class)
    private List<Long> createdContractIds = Collections.emptyList();

    private String errorMessage;

    @ToString.Exclude
    private byte[] functionParameters;

    private Long gasLimit;

    private Long gasUsed;

    @JsonIgnore
    @Override
    public Long getId() {
        return consensusTimestamp;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }
}
