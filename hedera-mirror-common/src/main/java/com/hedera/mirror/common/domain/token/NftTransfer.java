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

package com.hedera.mirror.common.domain.token;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import com.hedera.mirror.common.converter.AccountIdConverter;
import com.hedera.mirror.common.converter.AccountIdDeserializer;
import com.hedera.mirror.common.converter.EntityIdSerializer;
import com.hedera.mirror.common.converter.TokenIdConverter;
import com.hedera.mirror.common.converter.TokenIdDeserializer;
import com.hedera.mirror.common.domain.entity.EntityId;

import jakarta.persistence.Convert;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // for Builder
@Builder
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@NoArgsConstructor
public class NftTransfer {
    public static final long WILDCARD_SERIAL_NUMBER = -1;

    private Boolean isApproval;

    @JsonDeserialize(using = AccountIdDeserializer.class)
    @JsonSerialize(using = EntityIdSerializer.class)
    private EntityId receiverAccountId;

    @JsonDeserialize(using = AccountIdDeserializer.class)
    @JsonSerialize(using = EntityIdSerializer.class)
    private EntityId senderAccountId;

    private Long serialNumber;

    @JsonDeserialize(using = TokenIdDeserializer.class)
    @JsonSerialize(using = EntityIdSerializer.class)
    private EntityId tokenId;
}
