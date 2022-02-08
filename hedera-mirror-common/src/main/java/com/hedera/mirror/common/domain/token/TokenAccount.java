package com.hedera.mirror.common.domain.token;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

import com.hedera.mirror.common.domain.Upsertable;
import com.hedera.mirror.common.domain.entity.EntityId;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@NoArgsConstructor
@Upsertable
public class TokenAccount {

    @EmbeddedId
    @JsonUnwrapped
    private TokenAccountId id;

    private Boolean associated;

    private Boolean automaticAssociation;

    private Long createdTimestamp;

    @Enumerated(EnumType.ORDINAL)
    private TokenFreezeStatusEnum freezeStatus;

    @Enumerated(EnumType.ORDINAL)
    private TokenKycStatusEnum kycStatus;

    public TokenAccount(EntityId tokenId, EntityId accountId, long modifiedTimestamp) {
        id = new TokenAccountId(tokenId, accountId, modifiedTimestamp);
    }
}
