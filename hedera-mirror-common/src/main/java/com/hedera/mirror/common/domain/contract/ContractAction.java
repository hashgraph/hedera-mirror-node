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
import com.hedera.mirror.common.converter.AccountIdConverter;
import com.hedera.mirror.common.converter.ContractIdConverter;
import com.hedera.mirror.common.converter.UnknownIdConverter;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import io.hypersistence.utils.hibernate.type.basic.PostgreSQLEnumType;
import jakarta.persistence.Convert;
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
import org.hibernate.annotations.Type;
import org.springframework.data.domain.Persistable;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For Builder
@Builder
@Data
@Entity
@IdClass(ContractAction.Id.class)
@NoArgsConstructor
public class ContractAction implements Persistable<ContractAction.Id> {

    private int callDepth;

    @Convert(converter = UnknownIdConverter.class)
    private EntityId caller;

    @Enumerated(EnumType.STRING)
    @Type(PostgreSQLEnumType.class)
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

    @Convert(converter = AccountIdConverter.class)
    private EntityId payerAccountId;

    @Convert(converter = AccountIdConverter.class)
    private EntityId recipientAccount;

    @ToString.Exclude
    private byte[] recipientAddress;

    @Convert(converter = ContractIdConverter.class)
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

    public void setCaller(EntityId caller) {
        this.caller = caller;
        this.callerType = caller == null ? null : caller.getType();
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
