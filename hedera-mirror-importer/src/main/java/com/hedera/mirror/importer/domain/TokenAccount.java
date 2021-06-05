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
import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@NoArgsConstructor
public class TokenAccount {
    @Access(AccessType.FIELD)
    @EmbeddedId
    @JsonUnwrapped
    private TokenAccountId id;

    private Boolean associated;

    private Long createdTimestamp;

    @Enumerated(EnumType.ORDINAL)
    private TokenFreezeStatusEnum freezeStatus;

    @Enumerated(EnumType.ORDINAL)
    private TokenKycStatusEnum kycStatus;

    private long modifiedTimestamp;

//    @Getter
//    @Transient
//    private EntityId accountId;
//
//    @Getter
//    @Transient
//    private EntityId tokenId;

    public TokenAccount(EntityId tokenId, EntityId accountId) {
        id = new TokenAccountId(tokenId, accountId);
    }

//    @Data
//    @AllArgsConstructor
//    @NoArgsConstructor
//    @Embeddable
//    public static class Id implements Serializable {
//        private static final long serialVersionUID = -4069569824910871771L;
//
//        @Convert(converter = TokenIdConverter.class)
//        @JsonSerialize(using = EntityIdSerializer.class)
//        private EntityId tokenId;
//
//        @Convert(converter = AccountIdConverter.class)
//        @JsonSerialize(using = EntityIdSerializer.class)
//        private EntityId accountId;
//    }
}
