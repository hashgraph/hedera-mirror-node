/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
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
import com.google.common.primitives.Shorts;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.data.domain.Persistable;

@Data
@Entity
@NoArgsConstructor
public class TransactionHash implements Persistable<byte[]> {
    public static final int V1_SHARD_COUNT = 32;

    private long consensusTimestamp;

    @Setter(value = AccessLevel.NONE)
    private short distributionId;

    @Id
    private byte[] hash;

    private long payerAccountId;

    @Builder
    public TransactionHash(long consensusTimestamp, byte[] hash, long payerAccountId) {
        this.consensusTimestamp = consensusTimestamp;
        setHash(hash);
        this.payerAccountId = payerAccountId;
    }

    @JsonIgnore
    @Override
    public byte[] getId() {
        return hash;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }

    public int calculateV1Shard() {
        return Math.floorMod(hash[0], V1_SHARD_COUNT);
    }

    public boolean hashIsValid() {
        return this.hash != null && hash.length > 0;
    }

    public void setHash(byte[] hash) {
        this.hash = hash;
        if (ArrayUtils.isNotEmpty(hash) && hash.length >= 2) {
            this.distributionId = Shorts.fromByteArray(hash);
        }
    }
}
