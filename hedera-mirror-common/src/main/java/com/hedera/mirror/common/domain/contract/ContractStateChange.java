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
import com.hedera.mirror.common.domain.entity.EntityId;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import java.io.Serial;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.domain.Persistable;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For Builder
@Builder(toBuilder = true)
@Data
@Entity
@IdClass(ContractStateChange.Id.class)
@NoArgsConstructor
public class ContractStateChange implements Persistable<ContractStateChange.Id> {

    @jakarta.persistence.Id
    private long consensusTimestamp;

    @Convert(converter = ContractIdConverter.class)
    @jakarta.persistence.Id
    private long contractId;

    private boolean migration;

    @Convert(converter = AccountIdConverter.class)
    private EntityId payerAccountId;

    @jakarta.persistence.Id
    @ToString.Exclude
    private byte[] slot;

    @ToString.Exclude
    private byte[] valueRead;

    @ToString.Exclude
    private byte[] valueWritten;

    @Override
    @JsonIgnore
    public Id getId() {
        Id id = new Id();
        id.setConsensusTimestamp(consensusTimestamp);
        id.setContractId(contractId);
        id.setSlot(slot);
        return id;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true;
    }

    public void setContractId(EntityId contractId) {
        this.contractId = contractId.getId();
    }

    @AllArgsConstructor
    @Data
    @NoArgsConstructor
    public static class Id implements Serializable {
        @Serial
        private static final long serialVersionUID = -3677350664183037811L;

        private long consensusTimestamp;
        private long contractId;
        private byte[] slot;
    }
}
