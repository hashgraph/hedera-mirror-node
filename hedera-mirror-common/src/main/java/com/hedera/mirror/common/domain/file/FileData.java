package com.hedera.mirror.common.domain.file;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.domain.Persistable;

import com.hedera.mirror.common.converter.FileIdConverter;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.TransactionType;

@Builder(toBuilder = true)
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "fileData")
public class FileData implements Persistable<Long> {

    @Id
    private Long consensusTimestamp;

    @SuppressWarnings("java:S1700")
    private byte[] fileData;

    @Convert(converter = FileIdConverter.class)
    private EntityId entityId;

    private Integer transactionType;

    public boolean transactionTypeIsAppend() {
        return transactionType == TransactionType.FILEAPPEND.getProtoId();
    }

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
