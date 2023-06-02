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

package com.hedera.mirror.common.domain.token;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hedera.mirror.common.converter.AccountIdConverter;
import com.hedera.mirror.common.converter.EntityIdSerializer;
import com.hedera.mirror.common.converter.TokenIdConverter;
import com.hedera.mirror.common.domain.entity.EntityId;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import java.io.Serial;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For Builder
@Builder
@Data
@Entity
@NoArgsConstructor
public class TokenTransfer implements Persistable<TokenTransfer.Id> {

    @EmbeddedId
    @JsonUnwrapped
    private Id id;

    private long amount;

    @JsonIgnore
    @Transient
    private boolean deletedTokenDissociate;

    private Boolean isApproval;

    @Convert(converter = AccountIdConverter.class)
    private EntityId payerAccountId;

    public TokenTransfer(long consensusTimestamp, long amount, EntityId tokenId, EntityId accountId) {
        this(consensusTimestamp, amount, tokenId, accountId, false);
    }

    public TokenTransfer(
            long consensusTimestamp,
            long amount,
            EntityId tokenId,
            EntityId accountId,
            boolean deletedTokenDissociate) {
        id = new TokenTransfer.Id(consensusTimestamp, tokenId, accountId);
        this.amount = amount;
        this.deletedTokenDissociate = deletedTokenDissociate;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }

    @Builder(toBuilder = true)
    @Data
    @Embeddable
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id implements Serializable {

        @Serial
        private static final long serialVersionUID = 8693129287509470469L;

        private long consensusTimestamp;

        @Convert(converter = TokenIdConverter.class)
        private EntityId tokenId;

        @Convert(converter = AccountIdConverter.class)
        @JsonSerialize(using = EntityIdSerializer.class)
        private EntityId accountId;
    }
}
