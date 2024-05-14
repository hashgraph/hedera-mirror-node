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
import com.hedera.mirror.restjava.dto.NftAllowanceDto;
import com.hedera.mirror.restjava.dto.NftAllowanceRequest;
import com.hedera.mirror.restjava.repository.NftAllowanceRepository;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Named
@RequiredArgsConstructor
public class NftAllowanceServiceImpl implements NftAllowanceService {

    private final NftAllowanceRepository repository;
    private final EntityService entityService;

    public Collection<NftAllowance> getNftAllowances(NftAllowanceRequest request) {

        var ownerOrSpenderId = request.getOwnerOrSpenderIds();
        var token = request.getTokenIds();

        checkOwnerSpenderParamValidity(ownerOrSpenderId, token);

        if (!CollectionUtils.isEmpty(ownerOrSpenderId)) {
            var bounds = getRange(ownerOrSpenderId);
            long lowerBound = getLowerBound(bounds);
            long upperBound = getUpperBound(bounds);

            // Shortcutting when the range is invalid
            if (lowerBound > upperBound) {
                return Collections.emptyList();
            } else if (lowerBound == upperBound) {
                var optimizedRangeParam = new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(lowerBound));
                request.setOwnerOrSpenderIds(List.of(optimizedRangeParam));
            }
        }

        var id = entityService.lookup(request.getAccountId());

        var dto = NftAllowanceDto.builder()
                .accountId(id)
                .isOwner(request.isOwner())
                .limit(request.getLimit())
                .order(request.getOrder())
                .ownerOrSpenderIdBounds(getRange(request.getOwnerOrSpenderIds()))
                .tokenIdBounds(getRange(request.getTokenIds()))
                .build();

        return repository.findAll(dto);
    }

    private static long getUpperBound(Bound bounds) {
        long upperBound = Long.MAX_VALUE;

        if (bounds.upperBound != null) {
            upperBound = bounds.upperBound.value().getId();
            if (bounds.upperBound.operator() == RangeOperator.LT) {
                upperBound--;
            }
        }
        return upperBound;
    }

    private static long getLowerBound(Bound bounds) {
        long lowerBound = 0;
        if (bounds.lowerBound != null) {
            lowerBound = bounds.lowerBound.value().getId();
            if (bounds.lowerBound.operator() == RangeOperator.GT) {
                lowerBound++;
            }
        }
        return lowerBound;
    }

    @SuppressWarnings({"java:S131"})
    private Bound getRange(List<EntityIdRangeParameter> idRangeParameters) {
        EntityIdRangeParameter lowerBoundParam = null;
        EntityIdRangeParameter upperBoundParam = null;

        if (idRangeParameters != null) {
            for (EntityIdRangeParameter param : idRangeParameters) {
                if (param != null) {
                    // Considering EQ in the same category as GT,GTE as an assumption
                    if (param.operator() == RangeOperator.GT
                            || param.operator() == RangeOperator.GTE
                            || param.operator() == RangeOperator.EQ) {
                        lowerBoundParam = param;
                    } else if (param.operator() == RangeOperator.LT || param.operator() == RangeOperator.LTE) {
                        upperBoundParam = param;
                    }
                }
            }
        }

        return new Bound(lowerBoundParam, upperBoundParam);
    }

    @SuppressWarnings({"java:S131"})
    private static Operators verifyRangeId(List<EntityIdRangeParameter> idParams) {
        boolean hasGt = false;
        boolean hasGte = false;
        boolean hasLt = false;
        boolean hasLte = false;
        int countGtGte = 0;
        int countLtLte = 0;
        int countEq = 0;

        for (EntityIdRangeParameter param : idParams) {

            if (param.operator() == RangeOperator.NE) {
                throw new IllegalArgumentException("Invalid range operator ne. This operator is not supported");
            }

            switch (param.operator()) {
                case RangeOperator.GT -> {
                    hasGt = true;
                    countGtGte++;
                }
                case RangeOperator.GTE -> {
                    hasGte = true;
                    countGtGte++;
                }
                case RangeOperator.LT -> {
                    hasLt = true;
                    countLtLte++;
                }
                case RangeOperator.LTE -> {
                    hasLte = true;
                    countLtLte++;
                }
                case RangeOperator.EQ -> countEq++;
            }
        }

        if (countGtGte > 1 || countLtLte > 1 || countEq > 1) {
            throw new IllegalArgumentException("Single occurrence only supported.");
        }
        if (countEq == 1 && (countGtGte != 0 || countLtLte != 0)) {
            throw new IllegalArgumentException("Can't support both range and equal for this parameter.");
        }
        return new Operators(countEq > 0, hasGt, hasGte, hasLt, hasLte);
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

            if (tokenOperators.hasLtLte() && !accountOperators.hasEq && !accountOperators.hasLte) {
                throw new IllegalArgumentException(
                        "Single occurrence only supported.Requires the presence of an lte or eq account.id query");
            }
            if (tokenOperators.hasGtGte() && !accountOperators.hasEq && !accountOperators.hasGte) {
                throw new IllegalArgumentException(
                        "Single occurrence only supported.Requires the presence of an gte or eq account.id query");
            }
        }
    }

    public record Bound(EntityIdRangeParameter lowerBound, EntityIdRangeParameter upperBound) {}

    private record Operators(boolean hasEq, boolean hasGt, boolean hasGte, boolean hasLt, boolean hasLte) {
        public boolean hasGtGte() {
            return hasGt || hasGte;
        }

        public boolean hasLtLte() {
            return hasLt || hasLte;
        }
    }
}
