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
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.hedera.mirror.common.converter.AccountIdConverter;
import com.hedera.mirror.common.converter.TokenIdConverter;
import com.hedera.mirror.common.domain.entity.EntityId;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@Entity
@NoArgsConstructor
public class CustomFee implements Persistable<CustomFee.Id> {

    @EmbeddedId
    @JsonUnwrapped
    private Id id;

    private boolean allCollectorsAreExempt;

    private Long amount;

    private Long amountDenominator;

    @Convert(converter = AccountIdConverter.class)
    private EntityId collectorAccountId;

    @Convert(converter = TokenIdConverter.class)
    private EntityId denominatingTokenId;

    private Long maximumAmount;

    private long minimumAmount;

    private Boolean netOfTransfers;

    private Long royaltyDenominator;

    private Long royaltyNumerator;

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

        private static final long serialVersionUID = 2713612586558952011L;

        private long createdTimestamp;

        @Convert(converter = TokenIdConverter.class)
        private EntityId tokenId;
    }
}
