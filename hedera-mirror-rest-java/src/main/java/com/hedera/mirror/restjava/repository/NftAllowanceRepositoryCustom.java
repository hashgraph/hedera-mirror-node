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

import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.restjava.common.Filter;
import com.hedera.mirror.restjava.common.Order;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public interface NftAllowanceRepositoryCustom {

    /**
     * Find all NftAllowance matching the filters with the given limit, sort order, and byOwner flag
     * @param byOwner  True if looking for nft allowances granted by the same owner, otherwise looking for nft
     *                 nft allowances granted to the same spender
     * @param filters The filters
     * @param limit The limit of the number of nft allowances to return
     * @param order The sort order
     * @return The matching nft allowances
     */
    @NotNull
    Collection<NftAllowance> findAll(
            boolean byOwner, @NotNull List<Filter<?>> filters, int limit, @NotNull Order order);
}
