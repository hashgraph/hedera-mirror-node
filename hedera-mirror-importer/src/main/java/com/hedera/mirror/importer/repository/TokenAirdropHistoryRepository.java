/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.repository;

import com.hedera.mirror.common.domain.token.AbstractTokenAirdrop;
import com.hedera.mirror.common.domain.token.TokenAirdropHistory;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface TokenAirdropHistoryRepository
        extends CrudRepository<TokenAirdropHistory, AbstractTokenAirdrop.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query(value = "delete from token_airdrop_history where timestamp_range << int8range(?1, null)", nativeQuery = true)
    int prune(long consensusTimestamp);
}
