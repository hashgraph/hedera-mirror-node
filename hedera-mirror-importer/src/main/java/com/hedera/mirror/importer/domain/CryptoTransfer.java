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
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.io.Serializable;
import javax.persistence.Convert;
import javax.persistence.Embeddable;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import com.hedera.mirror.importer.converter.AccountIdConverter;

@Data
@Entity
@NoArgsConstructor
public class CryptoTransfer implements Persistable<CryptoTransfer.Id> {

    /*
     * It used to be that crypto transfers could have multiple amounts for the same account, so all fields were used for
     * uniqueness. Later a change was made to aggregate amounts by account making the unique key
     * (consensusTimestamp, entityId). Since we didn't migrate the old data to aggregate we have to treat all fields as
     * the key still.
     */
    @EmbeddedId
    @JsonUnwrapped
    private Id id;

    @Convert(converter = AccountIdConverter.class)
    private EntityId payerAccountId;

    public CryptoTransfer(long consensusTimestamp, long amount, EntityId entityId) {
        id = new CryptoTransfer.Id(amount, consensusTimestamp, entityId);
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }

    @Data
    @Embeddable
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id implements Serializable {

        private static final long serialVersionUID = 6187276796581956587L;

        private long amount;

        private long consensusTimestamp;

        @Convert(converter = AccountIdConverter.class)
        private EntityId entityId;
    }
}
