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
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hedera.mirror.common.converter.AccountIdConverter;
import com.hedera.mirror.common.converter.EntityIdSerializer;
import com.hedera.mirror.common.converter.UnknownIdConverter;
import com.hedera.mirror.common.domain.entity.EntityId;
import io.hypersistence.utils.hibernate.type.basic.PostgreSQLEnumType;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Type;
import org.springframework.data.domain.Persistable;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For builder
@Builder
@Data
@Entity
@NoArgsConstructor
public class Transaction implements Persistable<Long> {

    @Id
    private Long consensusTimestamp;

    private Long chargedTxFee;

    @Convert(converter = UnknownIdConverter.class)
    @JsonSerialize(using = EntityIdSerializer.class)
    private EntityId entityId;

    @Enumerated(EnumType.STRING)
    @Type(PostgreSQLEnumType.class)
    private ErrataType errata;

    private Integer index;

    private Long initialBalance;

    @ToString.Exclude
    private byte[] memo;

    private Long maxFee;

    @Convert(converter = AccountIdConverter.class)
    @JsonSerialize(using = EntityIdSerializer.class)
    private EntityId nodeAccountId;

    private Integer nonce;

    private Long parentConsensusTimestamp;

    @Convert(converter = AccountIdConverter.class)
    @JsonSerialize(using = EntityIdSerializer.class)
    private EntityId payerAccountId;

    private Integer result;

    private boolean scheduled;

    @ToString.Exclude
    private byte[] transactionBytes;

    @ToString.Exclude
    private byte[] transactionHash;

    private Integer type;

    private Long validDurationSeconds;

    private Long validStartNs;

    @JsonIgnore
    @Override
    public Long getId() {
        return consensusTimestamp;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }

    public TransactionHash toTransactionHash() {
        return TransactionHash.builder()
                .consensusTimestamp(consensusTimestamp)
                .hash(transactionHash)
                .payerAccountId(payerAccountId.getId())
                .build();
    }
}
