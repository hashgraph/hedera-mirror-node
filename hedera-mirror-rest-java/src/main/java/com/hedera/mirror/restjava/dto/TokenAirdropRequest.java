/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.restjava.dto;

import static com.hedera.mirror.restjava.jooq.domain.Tables.TOKEN_AIRDROP;

import com.hedera.mirror.restjava.common.EntityIdParameter;
import com.hedera.mirror.restjava.service.Bound;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jooq.Field;
import org.springframework.data.domain.Sort;

@Data
@Builder
public class TokenAirdropRequest {

    // Sender Id for Outstanding Airdrops, Receiver Id for Pending Airdrops
    private EntityIdParameter accountId;

    @Builder.Default
    private int limit = 25;

    @Builder.Default
    private Sort.Direction order = Sort.Direction.ASC;

    // Receiver Id for Outstanding Airdrops, Sender Id for Pending Airdrops
    private Bound entityIds;

    private Bound tokenIds;

    @Builder.Default
    private AirdropRequestType type = AirdropRequestType.OUTSTANDING;

    @Getter
    @RequiredArgsConstructor
    public enum AirdropRequestType {
        OUTSTANDING(TOKEN_AIRDROP.SENDER_ACCOUNT_ID, TOKEN_AIRDROP.RECEIVER_ACCOUNT_ID),
        PENDING(TOKEN_AIRDROP.RECEIVER_ACCOUNT_ID, TOKEN_AIRDROP.SENDER_ACCOUNT_ID);

        // The base field is the conditional clause for the base DB query.
        // The base field is the path parameter accountId, which is Sender Id for Outstanding Airdrops and Receiver Id
        // for Pending Airdrops
        private final Field<Long> baseField;

        // The primary field is the primary sort field for the DB query.
        // The primary field is the optional query parameter 'entityIds', which is Receiver Id for Outstanding Airdrops
        // and Sender Id for Pending Airdrops
        private final Field<Long> primaryField;
    }
}
