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
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.Serializable;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.IdClass;

import com.hedera.mirror.common.domain.entity.EntityId;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.domain.Persistable;

import com.hedera.mirror.common.converter.EntityIdSerializer;
import com.hedera.mirror.common.converter.UnknownIdConverter;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For Builder
@Builder
@Data
@Entity
@IdClass(TransactionSignature.Id.class)
@NoArgsConstructor
public class TransactionSignature implements Persistable<TransactionSignature.Id> {

    @javax.persistence.Id
    private long consensusTimestamp;

    @Convert(converter = UnknownIdConverter.class)
    @JsonSerialize(using = EntityIdSerializer.class)
    private EntityId entityId;

    @javax.persistence.Id
    @ToString.Exclude
    private byte[] publicKeyPrefix;

    @ToString.Exclude
    private byte[] signature;

    private int type;

    @Override
    @JsonIgnore
    public TransactionSignature.Id getId() {
        TransactionSignature.Id transactionSignatureId = new TransactionSignature.Id();
        transactionSignatureId.setConsensusTimestamp(consensusTimestamp);
        transactionSignatureId.setPublicKeyPrefix(publicKeyPrefix);
        return transactionSignatureId;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true;
    }

    @Data
    public static class Id implements Serializable {
        private static final long serialVersionUID = -8758644338990079234L;
        private long consensusTimestamp;
        private byte[] publicKeyPrefix;
    }
}
