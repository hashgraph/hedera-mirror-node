/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.common.domain.contract;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hedera.mirror.common.converter.ListToStringSerializer;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(ContractTransaction.Id.class)
public class ContractTransaction implements Persistable<ContractTransaction.Id> {
    @jakarta.persistence.Id
    private Long consensusTimestamp;

    @jakarta.persistence.Id
    private Long entityId;

    @Builder.Default
    @JsonSerialize(using = ListToStringSerializer.class)
    private List<Long> contractIds = Collections.emptyList();

    private long payerAccountId;

    @Override
    @JsonIgnore
    public Id getId() {
        return new ContractTransaction.Id(consensusTimestamp, entityId);
    }

    @Override
    @JsonIgnore
    public boolean isNew() {
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id implements Serializable {
        @Serial
        private static final long serialVersionUID = -6807023295883699004L;

        private long consensusTimestamp;
        private long entityId;
    }
}
