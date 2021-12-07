package com.hedera.mirror.common.domain.balance;

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
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embeddable;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

import com.hedera.mirror.common.domain.entity.EntityId;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import com.hedera.mirror.common.converter.AccountIdConverter;
import com.hedera.mirror.common.converter.TokenIdConverter;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class TokenBalance implements Persistable<TokenBalance.Id> {

    private long balance;

    @EmbeddedId
    @JsonUnwrapped
    private TokenBalance.Id id;

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update balances and use a natural ID, avoid Hibernate querying before insert
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Embeddable
    public static class Id implements Serializable {

        private static final long serialVersionUID = -8547332015249955424L;

        @Column(nullable = false, updatable = false) // set updatable = false to prevent additional hibernate query
        private long consensusTimestamp;

        @Column(nullable = false, updatable = false) // set updatable = false to prevent additional hibernate query
        @Convert(converter = AccountIdConverter.class)
        private EntityId accountId;

        @Convert(converter = TokenIdConverter.class)
        private EntityId tokenId;
    }
}
