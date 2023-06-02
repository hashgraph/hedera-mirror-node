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
import com.hedera.mirror.common.domain.Upsertable;
import com.hedera.mirror.common.util.DomainUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Entity
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@IdClass(ContractState.Id.class)
@NoArgsConstructor
@Upsertable
public class ContractState {

    private static final int SLOT_BYTE_LENGTH = 32;

    @jakarta.persistence.Id
    private long contractId;

    @Column(updatable = false)
    private long createdTimestamp;

    private long modifiedTimestamp;

    @jakarta.persistence.Id
    @ToString.Exclude
    private byte[] slot;

    @ToString.Exclude
    private byte[] value;

    @JsonIgnore
    public ContractState.Id getId() {
        ContractState.Id id = new ContractState.Id();
        id.setContractId(contractId);
        id.setSlot(slot);
        return id;
    }

    public void setSlot(byte[] slot) {
        this.slot = DomainUtils.leftPadBytes(slot, SLOT_BYTE_LENGTH);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id implements Serializable {
        private static final long serialVersionUID = 6192377810161178246L;
        private long contractId;
        private byte[] slot;
    }
}
