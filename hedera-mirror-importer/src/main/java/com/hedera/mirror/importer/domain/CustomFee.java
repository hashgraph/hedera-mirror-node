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

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.io.Serializable;
import javax.persistence.Convert;
import javax.persistence.Embeddable;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import com.hedera.mirror.importer.converter.AccountIdConverter;
import com.hedera.mirror.importer.converter.TokenIdConverter;

@Data
@Entity
@NoArgsConstructor
public class CustomFee implements Persistable<CustomFee.Id> {

    public CustomFee(long amount, Long amountDenominator, EntityId collectorAccountId, long createdTimestamp,
                     EntityId denominatingTokenId, Long maximumAmount, Long minimumAmount, EntityId tokenId) {
        id = new Id(createdTimestamp, tokenId);
        this.amount = amount;
        this.amountDenominator = amountDenominator;
        this.collectorAccountId = collectorAccountId;
        this.denominatingTokenId = denominatingTokenId;
        this.maximumAmount = maximumAmount;
        this.minimumAmount = minimumAmount;
    }

    @EmbeddedId
    @JsonUnwrapped
    private Id id;

    private Long amount;

    private Long amountDenominator;

    @Convert(converter = AccountIdConverter.class)
    private EntityId collectorAccountId;

    @Convert(converter = TokenIdConverter.class)
    private EntityId denominatingTokenId;

    @Getter(AccessLevel.NONE)
    private boolean hasCustomFee;

    private Long maximumAmount;

    private Long minimumAmount;

    public boolean getHashCustomFee() {
        return hasCustomFee;
    }

    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }

    @Data
    @Embeddable
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id implements Serializable {

        private static final long serialVersionUID = 2713612586558952011L;

        private long createdTimestamp;

        @Convert(converter = TokenIdConverter.class)
        private EntityId tokenId;
    }
}
