/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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
import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.History;
import com.hedera.mirror.common.domain.Upsertable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.IdClass;
import jakarta.persistence.MappedSuperclass;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@IdClass(AbstractTokenAirdrop.Id.class)
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public class AbstractTokenAirdrop implements History {

    private Long amount;

    @jakarta.persistence.Id
    private long receiverAccountId;

    @jakarta.persistence.Id
    private long senderAccountId;

    @jakarta.persistence.Id
    private long serialNumber;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TokenAirdropStateEnum state;

    private Range<Long> timestampRange;

    @jakarta.persistence.Id
    private long tokenId;

    @JsonIgnore
    public Id getId() {
        Id id = new Id();
        id.setReceiverAccountId(receiverAccountId);
        id.setSenderAccountId(senderAccountId);
        id.setSerialNumber(serialNumber);
        id.setTokenId(tokenId);
        return id;
    }

    @Data
    public static class Id implements Serializable {
        @Serial
        private static final long serialVersionUID = -8165098238647325621L;

        private long receiverAccountId;
        private long senderAccountId;
        private long serialNumber;
        private long tokenId;
    }
}
