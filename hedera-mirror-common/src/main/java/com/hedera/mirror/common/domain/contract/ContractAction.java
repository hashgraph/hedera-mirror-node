/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.IdClass;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For Builder
@Builder
@Data
@Entity
@IdClass(ContractAction.Id.class)
@NoArgsConstructor
public class ContractAction implements Persistable<ContractAction.Id> {

    private int callDepth;

    private EntityId caller;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EntityType callerType;

    private int callOperationType;

    private Integer callType;

    @jakarta.persistence.Id
    private long consensusTimestamp;

    private long gas;

    private long gasUsed;

    @jakarta.persistence.Id
    private int index;

    @ToString.Exclude
    private byte[] input;

    private EntityId payerAccountId;

    private EntityId recipientAccount;

    @ToString.Exclude
    private byte[] recipientAddress;

    private EntityId recipientContract;

    @ToString.Exclude
    private byte[] resultData;

    private int resultDataType;

    private long value;

    @Override
    @JsonIgnore
    public ContractAction.Id getId() {
        ContractAction.Id id = new ContractAction.Id();
        id.setConsensusTimestamp(consensusTimestamp);
        id.setIndex(index);
        return id;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id implements Serializable {
        private static final long serialVersionUID = -6192177810161178246L;
        private long consensusTimestamp;
        private int index;
    }
}
