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

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.restjava.common.EntityIdRangeParameter;
import com.hedera.mirror.restjava.common.RangeOperator;
import com.hedera.mirror.restjava.repository.NftAllowanceRepository;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.util.CollectionUtils;

@Named
@RequiredArgsConstructor
public class NftAllowanceServiceImpl implements NftAllowanceService {

    private final NftAllowanceRepository repository;
    private final EntityService entityService;

    public Collection<NftAllowance> getNftAllowances(NftAllowanceRequest request) {

        var ownerOrSpenderId = request.getOwnerOrSpenderId();
        var token = request.getTokenId();
        var id = entityService.lookup(request.getAccountId());

        checkOwnerSpenderParamValidity(ownerOrSpenderId, token);

        if (!CollectionUtils.isEmpty(ownerOrSpenderId)) {
            var bounds = getRange(ownerOrSpenderId);

            // Shortcutting when the range is invalid
            if (bounds.lower > bounds.upper) {
                return Collections.emptyList();
            } else if (bounds.lower == bounds.upper) {
                var optimizedRangeParam = new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(bounds.lower));
                request.setOwnerOrSpenderId(List.of(optimizedRangeParam));
            }
        }

        return repository.findAll(request, id);
    }

    @SuppressWarnings({"java:S131"})
    private Bound getRange(List<EntityIdRangeParameter> ownerOrSpenderIds) {
        long lowerBound = 0;
        long upperBound = Long.MAX_VALUE;
        for (EntityIdRangeParameter param : ownerOrSpenderIds) {
            switch (param.operator()) {
                case LT -> upperBound = param.value().getId() - 1;
                case GT -> lowerBound = param.value().getId() + 1;
                case LTE -> upperBound = param.value().getId();
                case GTE -> lowerBound = param.value().getId();
            }
        }

        return new Bound(lowerBound, upperBound);
    }

    private static List<RangeOperator> verifyRangeId(List<EntityIdRangeParameter> idParams) {
        int countGtOperator = 0;
        int countLtOperator = 0;
        int countEqOperator = 0;

        List<RangeOperator> operators = new ArrayList<>();
        for (EntityIdRangeParameter param : idParams) {

            if (param.operator() == RangeOperator.NE) {
                throw new IllegalArgumentException("Invalid range operator ne. This operator is not supported");
            }

            if (param.operator() == RangeOperator.GT || param.operator() == RangeOperator.GTE) {
                countGtOperator++;
            } else if (param.operator() == RangeOperator.LT || param.operator() == RangeOperator.LTE) {
                countLtOperator++;
            } else if (param.operator() == RangeOperator.EQ) {
                countEqOperator++;
            }
            operators.add(param.operator());
        }
        if (countGtOperator > 1 || countLtOperator > 1 || countEqOperator > 1) {
            throw new IllegalArgumentException("Single occurrence only supported.");
        }
        if (countEqOperator == 1 && (countGtOperator != 0 || countLtOperator != 0)) {
            throw new IllegalArgumentException("Can't support both range and equal for this parameter.");
        }
        return operators;
    }

    private static void checkOwnerSpenderParamValidity(
            List<EntityIdRangeParameter> ownerOrSpenderParams, List<EntityIdRangeParameter> tokenParams) {

        if (CollectionUtils.isEmpty(ownerOrSpenderParams)) {
            if (!CollectionUtils.isEmpty(tokenParams)) {
                throw new IllegalArgumentException("token.id parameter must have account.id present");
            } else {
                return;
            }
        }
        var accountOperators = verifyRangeId(ownerOrSpenderParams);

        if (!CollectionUtils.isEmpty(tokenParams)) {
            var tokenOperators = verifyRangeId(tokenParams);

            if ((tokenOperators.contains(RangeOperator.LTE) || tokenOperators.contains(RangeOperator.LT))
                    && !accountOperators.contains(RangeOperator.EQ)
                    && !accountOperators.contains(RangeOperator.LTE)) {
                throw new IllegalArgumentException(
                        "Single occurrence only supported.Requires the presence of an lte or eq account.id query");
            }
            if ((tokenOperators.contains(RangeOperator.GTE) || tokenOperators.contains(RangeOperator.GT))
                    && !accountOperators.contains(RangeOperator.EQ)
                    && !accountOperators.contains(RangeOperator.GTE)) {
                throw new IllegalArgumentException(
                        "Single occurrence only supported.Requires the presence of an gte or eq account.id query");
            }
        }
    }

    private record Bound(long lower, long upper) {}
}
