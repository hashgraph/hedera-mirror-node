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
import com.hedera.mirror.restjava.dto.NftAllowanceRequest;
import com.hedera.mirror.restjava.repository.NftAllowanceRepository;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.Collections;

@Named
@RequiredArgsConstructor
public class NftAllowanceServiceImpl implements NftAllowanceService {

    private final NftAllowanceRepository repository;
    private final EntityService entityService;

    public Collection<NftAllowance> getNftAllowances(NftAllowanceRequest request) {

        var ownerOrSpenderId = request.getOwnerOrSpenderIds();
        var token = request.getTokenIds();

        checkOwnerSpenderParamValidity(ownerOrSpenderId, token);

        if (ownerOrSpenderId.getLowerBound() != null
                && ownerOrSpenderId.getUpperBound() != null
                && (ownerOrSpenderId.getLowerBound().value().getId()
                        > ownerOrSpenderId.getUpperBound().value().getId())) {
            return Collections.emptyList();
        }

        var id = entityService.lookup(request.getAccountId());

        return repository.findAll(request, id);
    }

    @SuppressWarnings({"java:S131"})
    private static void verifyRangeId(Bound ids) {
        if (ids.getCardinality(RangeOperator.NE) > 0) {
            throw new IllegalArgumentException("Invalid range operator ne. This operator is not supported");
        }

        if (singleOperatorCheck(ids)) {
            throw new IllegalArgumentException("Single occurrence only supported.");
        }

        if (ids.getCardinality(RangeOperator.EQ) == 1 && (getCountGtGte(ids) != 0 || getCountLtLte(ids) != 0)) {
            throw new IllegalArgumentException("Can't support both range and equal for this parameter.");
        }
    }

    private static boolean singleOperatorCheck(Bound ids) {
        return getCountGtGte(ids) > 1 || getCountLtLte(ids) > 1 || ids.getCardinality(RangeOperator.EQ) > 1;
    }

    private static int getCountGtGte(Bound ids) {
        return ids.getCardinality(RangeOperator.GT) + ids.getCardinality(RangeOperator.GTE);
    }

    private static int getCountLtLte(Bound ids) {
        return ids.getCardinality(RangeOperator.LT) + ids.getCardinality(RangeOperator.LTE);
    }

    private static void checkOwnerSpenderParamValidity(Bound ownerOrSpenderParams, Bound tokenParams) {

        if (ownerOrSpenderParams.getLowerBound() == null
                && ownerOrSpenderParams.getUpperBound() == null
                && (tokenParams.getLowerBound() != null || tokenParams.getUpperBound() != null)) {
            throw new IllegalArgumentException("token.id parameter must have account.id present");
        }

        verifyRangeId(ownerOrSpenderParams);
        verifyRangeId(tokenParams);

        if (getCountLtLte(tokenParams) > 0
                && ownerOrSpenderParams.getCardinality(RangeOperator.EQ) == 0
                && ownerOrSpenderParams.getCardinality(RangeOperator.LTE) == 0) {
            throw new IllegalArgumentException(
                    "Single occurrence only supported. Requires the presence of an lte or eq account.id parameter");
        }
        if (getCountGtGte(tokenParams) > 0
                && ownerOrSpenderParams.getCardinality(RangeOperator.EQ) == 0
                && ownerOrSpenderParams.getCardinality(RangeOperator.GTE) == 0) {
            throw new IllegalArgumentException(
                    "Single occurrence only supported. Requires the presence of an gte or eq account.id parameter");
        }
    }
}
