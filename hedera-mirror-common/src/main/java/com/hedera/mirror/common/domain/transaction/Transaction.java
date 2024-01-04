/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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
import com.hedera.mirror.common.converter.ObjectToStringSerializer;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.NftTransfer;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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

    private EntityId entityId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ErrataType errata;

    private Integer index;

    private Long initialBalance;

    @JsonSerialize(using = ObjectToStringSerializer.class)
    @JdbcTypeCode(SqlTypes.JSON)
    private List<ItemizedTransfer> itemizedTransfer;

    @ToString.Exclude
    private byte[] memo;

    private Long maxFee;

    @JsonSerialize(using = ObjectToStringSerializer.class)
    @JdbcTypeCode(SqlTypes.JSON)
    private List<NftTransfer> nftTransfer;

    private EntityId nodeAccountId;

    private Integer nonce;

    private Long parentConsensusTimestamp;

    private EntityId payerAccountId;

    private Integer result;

    private boolean scheduled;

    @ToString.Exclude
    private byte[] transactionBytes;

    @ToString.Exclude
    private byte[] transactionHash;

    @ToString.Exclude
    private byte[] transactionRecordBytes;

    private Integer type;

    private Long validDurationSeconds;

    private Long validStartNs;

    public void addItemizedTransfer(@NonNull ItemizedTransfer itemizedTransfer) {
        if (this.itemizedTransfer == null) {
            this.itemizedTransfer = new ArrayList<>();
        }

        this.itemizedTransfer.add(itemizedTransfer);
    }

    public void addNftTransfer(@NonNull NftTransfer nftTransfer) {
        if (this.nftTransfer == null) {
            this.nftTransfer = new ArrayList<>();
        }

        this.nftTransfer.add(nftTransfer);
    }

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
