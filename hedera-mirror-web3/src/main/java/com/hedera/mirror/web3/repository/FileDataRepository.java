package com.hedera.mirror.web3.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_30DAYS;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.common.domain.file.FileData;

public interface FileDataRepository extends CrudRepository<FileData, Long> {
    @Cacheable(cacheNames = "file_data.closest_timestamp", cacheManager = CACHE_MANAGER_30DAYS, unless = "#result == null")
    @Query(value = "select * from file_data where consensus_timestamp < ?1 and entity_id " +
            "= ?2 order by consensus_timestamp desc limit 1", nativeQuery = true)
    FileData findFileByEntityIdAndClosestPreviousTimestamp(long consensusTimestamp, long encodedEntityId);

    @Cacheable(cacheNames = "file_data.latest_timestamp", cacheManager = CACHE_MANAGER_30DAYS, unless = "#result == null")
    @Query(value = "select * from file_data where entity_id = ?1 order by consensus_timestamp desc limit 1", nativeQuery = true)
    FileData findLatestFileByEntityId(long encodedEntityId);
}
