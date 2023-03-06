package com.hedera.mirror.common.domain.transaction;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.persistence.Convert;
import javax.persistence.Embeddable;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.springframework.data.domain.Persistable;

import com.hedera.mirror.common.converter.AccountIdConverter;
import com.hedera.mirror.common.converter.LongListToStringSerializer;
import com.hedera.mirror.common.converter.TokenIdConverter;
import com.hedera.mirror.common.domain.entity.EntityId;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@Entity
@NoArgsConstructor
public class AssessedCustomFee implements Persistable<AssessedCustomFee.Id> {

    @EmbeddedId
    @JsonUnwrapped
    private Id id;

    private long amount;

    @Builder.Default
    @Type(type = "com.vladmihalcea.hibernate.type.array.ListArrayType")
    @JsonSerialize(using = LongListToStringSerializer.class)
    private List<Long> effectivePayerAccountIds = Collections.emptyList();

    @Convert(converter = TokenIdConverter.class)
    private EntityId tokenId;

    @Convert(converter = AccountIdConverter.class)
    private EntityId payerAccountId;

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }

    public void setEffectivePayerEntityIds(List<EntityId> effectivePayerEntityIds) {
        effectivePayerAccountIds = effectivePayerEntityIds.stream()
                .map(AccountIdConverter.INSTANCE::convertToDatabaseColumn)
                .toList();
    }

    @Data
    @Embeddable
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id implements Serializable {

        private static final long serialVersionUID = -636368167561206418L;

        @Convert(converter = AccountIdConverter.class)
        private EntityId collectorAccountId;

        private long consensusTimestamp;
    }
}
