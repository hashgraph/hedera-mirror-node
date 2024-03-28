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
import com.hedera.mirror.restjava.common.EntityIdRangeParameter;
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

    private final NftAllowanceRepository repository;

    private static final String TOKEN_ID = "token_id";
    private static final String OWNER = "owner";
    private static final String SPENDER = "spender";
    public static final Sort OWNER_TOKEN_ASC_ORDER = sort(Sort.Direction.ASC, OWNER);
    public static final Sort OWNER_TOKEN_DESC_ORDER = sort(Sort.Direction.DESC, OWNER);
    public static final Sort SPENDER_TOKEN_ASC_ORDER = sort(Sort.Direction.ASC, SPENDER);
    public static final Sort SPENDER_TOKEN_DESC_ORDER = sort(Sort.Direction.DESC, SPENDER);

    public Collection<NftAllowance> getNftAllowances(NftAllowanceRequest request) {

        var accountId = request.getAccountId();
        var limit = request.getLimit();
        var order = request.getOrder();
        var ownerOrSpenderId = request.getOwnerOrSpenderId();
        var token = request.getTokenId();

        checkOwnerSpenderParamValidity(ownerOrSpenderId, token);

        //  LT,LTE,EQ,NE are not supported right now. Default is GT.
        var tokenId = verifyAndGetRangeId(token);

        // Set the value depending on the owner flag
        if (request.isOwner()) {
            var spenderId = verifyAndGetRangeId(ownerOrSpenderId);
            var pageable =
                    PageRequest.of(0, limit, order.isAscending() ? SPENDER_TOKEN_ASC_ORDER : SPENDER_TOKEN_DESC_ORDER);
            return repository.findByOwnerAndFilterBySpenderAndToken(
                    accountId.value().getId(), spenderId, tokenId, pageable);

        } else {
            var ownerId = verifyAndGetRangeId(ownerOrSpenderId);
            var pageable =
                    PageRequest.of(0, limit, order.isAscending() ? OWNER_TOKEN_ASC_ORDER : OWNER_TOKEN_DESC_ORDER);
            return repository.findBySpenderAndFilterByOwnerAndToken(
                    accountId.value().getId(), ownerId, tokenId, pageable);
        }
    }

    private static long verifyAndGetRangeId(EntityIdRangeParameter idParam) {
        long id = 0;
        // Setting default to 0.static queries will return all values with ids > 0 .
        if (idParam != null) {
            if (idParam.operator() == RangeOperator.NE) {
                throw new IllegalArgumentException("Invalid range operator ne. This operator is not supported");
            }
            id = getUpdatedEntityId(idParam);
        }
        return id;
    }

    private static void checkOwnerSpenderParamValidity(
            EntityIdRangeParameter ownerOrSpenderId, EntityIdRangeParameter token) {
        if (ownerOrSpenderId == null && token != null) {
            throw new IllegalArgumentException("token.id parameter must have account.id present");
        }
    }

    private static long getUpdatedEntityId(EntityIdRangeParameter idParam) {
        long id = idParam.value().getId();
        if (idParam.operator() == RangeOperator.GTE) {
            id = id > 0 ? id - 1 : id;
        }
        return id;
    }

    private static Sort sort(Sort.Direction direction, String account) {
        return Sort.by(direction, account).and(Sort.by(direction, TOKEN_ID));
    }
}
