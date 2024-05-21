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

        verifyRangeId(token);
        verifyRangeId(ownerOrSpenderId);

        return repository.findAll(request, id);
    }

    private static void verifyRangeId(EntityIdRangeParameter idParam) {
        if (idParam != null && idParam.operator() == RangeOperator.NE) {
            throw new IllegalArgumentException("Invalid range operator ne. This operator is not supported");
        }
    }

    private static void checkOwnerSpenderParamValidity(
            EntityIdRangeParameter ownerOrSpenderId, EntityIdRangeParameter token) {
        if (ownerOrSpenderId == null && token != null) {
            throw new IllegalArgumentException("token.id parameter must have account.id present");
        }
    }
}
