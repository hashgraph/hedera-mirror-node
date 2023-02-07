package com.hedera.mirror.web3.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_10MIN;
import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_500MS;

import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

import com.hedera.mirror.common.domain.transaction.RecordFile;

public interface RecordFileRepository extends PagingAndSortingRepository<RecordFile, Long> {

    @Cacheable(cacheNames = "record_file.latest_index", cacheManager = CACHE_MANAGER_500MS, unless = "#result == null")
    @Query("select max(r.index) from RecordFile r")
    Optional<Long> findLatestIndex();

    @Cacheable(cacheNames = "record_file.index", cacheManager = CACHE_MANAGER_10MIN, unless = "#result == null")
    @Query("select r.hash from RecordFile r where r.index = ?1")
    Optional<String> findHashByIndex(long index);

    @Cacheable(cacheNames = "record_file.latest", cacheManager = CACHE_MANAGER_500MS, unless = "#result == null")
    @Query(value = "select * from record_file order by consensus_end desc limit 1", nativeQuery = true)
    Optional<RecordFile> findLatest();
}
