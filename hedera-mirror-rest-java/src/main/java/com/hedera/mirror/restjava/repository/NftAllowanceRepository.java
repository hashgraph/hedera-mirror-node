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

package com.hedera.mirror.restjava.repository;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.restjava.service.NftAllowanceRequest;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;

public interface NftAllowanceRepository {

    /**
     * Find all NftAllowance matching the request parameters with the given limit, sort order, and byOwner flag
     *
     * @param request   Request object for NftAllowance
     * @param accountId
     * @return The matching nft allowances
     */
    @NotNull
    Collection<NftAllowance> findAll(NftAllowanceRequest request, EntityId accountId);
}
