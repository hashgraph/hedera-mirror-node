/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.domain.entity.EntityHistory;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public interface EntityHistoryRepository extends CrudRepository<EntityHistory, Long>, RetentionRepository {

    @Modifying
    @Override
    @Query(nativeQuery = true, value = "delete from entity_history where timestamp_range << int8range (?1, null)")
    int prune(long consensusTimestamp);

    @Modifying
    @Query(
            value = "update entity_history set type = 'CONTRACT' where id in (:ids) and type <> 'CONTRACT'",
            nativeQuery = true)
    int updateContractType(Iterable<Long> ids);
}
