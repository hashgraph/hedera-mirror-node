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

package com.hedera.mirror.common.domain.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hedera.mirror.common.converter.AccountIdConverter;
import com.hedera.mirror.common.domain.entity.EntityId;
import io.hypersistence.utils.hibernate.type.basic.PostgreSQLEnumType;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
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
import org.hibernate.annotations.Type;
import org.springframework.data.domain.Persistable;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@Entity
@IdClass(CryptoTransfer.Id.class)
@NoArgsConstructor
public class CryptoTransfer implements Persistable<CryptoTransfer.Id> {

    @jakarta.persistence.Id
    private long amount;

    @jakarta.persistence.Id
    private long consensusTimestamp;

    @jakarta.persistence.Id
    private long entityId;

    @Enumerated(EnumType.STRING)
    @Type(PostgreSQLEnumType.class)
    private ErrataType errata;

    private Boolean isApproval;

    @Convert(converter = AccountIdConverter.class)
    private EntityId payerAccountId;

    @JsonIgnore
    @Override
    public Id getId() {
        Id id = new Id();
        id.setConsensusTimestamp(consensusTimestamp);
        id.setAmount(amount);
        id.setEntityId(entityId);
        return id;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }

    /*
     * It used to be that crypto transfers could have multiple amounts for the same account, so all fields were used for
     * uniqueness. Later a change was made to aggregate amounts by account making the unique key
     * (consensusTimestamp, entityId). Since we didn't migrate the old data to aggregate we have to treat all fields as
     * the key still.
     */
    @Data
    @Embeddable
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id implements Serializable {

        private static final long serialVersionUID = 6187276796581956587L;

        private long amount;
        private long consensusTimestamp;
        private long entityId;
    }
}
