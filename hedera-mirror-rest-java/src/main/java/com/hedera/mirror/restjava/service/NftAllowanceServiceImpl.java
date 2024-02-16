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

package com.hedera.mirror.restjava.service;

import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.restjava.common.RangeOperator;
import com.hedera.mirror.restjava.repository.NftAllowanceRepository;
import jakarta.inject.Named;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Named
@RequiredArgsConstructor
public class NftAllowanceServiceImpl implements NftAllowanceService {

    public static final String TOKEN_ID = "token_id";
    public static final String OWNER = "owner";
    public static final String SPENDER = "spender";
    private static final Sort OWNER_TOKEN_ASC_ORDER =
            Sort.by(Sort.Direction.ASC, OWNER).and(Sort.by(Sort.Direction.ASC, TOKEN_ID));
    private static final Sort OWNER_TOKEN_DESC_ORDER =
            Sort.by(Sort.Direction.DESC, OWNER).and(Sort.by(Sort.Direction.DESC, TOKEN_ID));
    private static final Sort SPENDER_TOKEN_ASC_ORDER =
            Sort.by(Sort.Direction.ASC, SPENDER).and(Sort.by(Sort.Direction.ASC, TOKEN_ID));
    private static final Sort SPENDER_TOKEN_DESC_ORDER =
            Sort.by(Sort.Direction.DESC, SPENDER).and(Sort.by(Sort.Direction.DESC, TOKEN_ID));

    private final NftAllowanceRepository repository;

    public Collection<NftAllowance> getNftAllowances(NftAllowanceRequest request) {

        var accountIdOperator = request.getAccountIdOperator();
        var ownerId = request.getOwnerId();
        var limit = request.getLimit();
        var order = request.getOrder();
        var spenderId = request.getSpenderId();
        var tokenId = request.getTokenId();
        var tokenIdOperator = request.getAccountIdOperator();

        //  LT,LTE,EQ,NE are not supported right now. Default is GT.
        tokenId = getUpdatedEntityId(tokenIdOperator, tokenId);

        // Set the value depending on the owner flag   99
        if (request.isOwner()) {

            spenderId = getUpdatedEntityId(accountIdOperator, spenderId);
            var pageable =
                    PageRequest.of(0, limit, order.isAscending() ? SPENDER_TOKEN_ASC_ORDER : SPENDER_TOKEN_DESC_ORDER);
            return repository.findByOwnerAndFilterBySpenderAndToken(ownerId, spenderId, tokenId, pageable);

        } else {

            ownerId = getUpdatedEntityId(accountIdOperator, ownerId);
            var pageable =
                    PageRequest.of(0, limit, order.isAscending() ? OWNER_TOKEN_ASC_ORDER : OWNER_TOKEN_DESC_ORDER);
            return repository.findBySpenderAndFilterByOwnerAndToken(spenderId, ownerId, tokenId, pageable);
        }
    }

    private static long getUpdatedEntityId(RangeOperator accountIdOperator, long entityId) {
        if (accountIdOperator == RangeOperator.GTE) {
            entityId = entityId > 0 ? entityId - 1 : entityId;
        }
        return entityId;
    }
}
