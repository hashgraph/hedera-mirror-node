package com.hedera.mirror.importer.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import java.util.List;
import java.util.Optional;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

import com.hedera.mirror.importer.config.CacheConfiguration;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.FileData;

@CacheConfig(cacheNames = "filedata", cacheManager = CacheConfiguration.EXPIRE_AFTER_30M)
public interface FileDataRepository extends CrudRepository<FileData, Long> {
    @Cacheable(key = "{#p0}", sync = true)
    @Transactional(readOnly = true)
    @Query("from FileData where consensusTimestamp > ?1 order by consensusTimestamp asc")
    List<FileData> findLatest(long consensusTimestamp, Pageable pageable);

    @Cacheable(key = "{#p0}", sync = true)
    @Transactional(readOnly = true)
    @Query("from FileData where consensusTimestamp > ?1 and entityId = ?2 order by consensusTimestamp asc")
    List<FileData> findLatestFilesById(long consensusTimestamp, EntityId entityId, Pageable pageable);

    @Transactional(readOnly = true)
    @Query(value = "from FileData where consensusTimestamp > ?1 and entityId = ?2 and transactionType = ?3 order by " +
            "consensusTimestamp asc")
    List<FileData> findLatestFiles(long consensusTimestamp, EntityId entityId, int transactionType);

    //    @Cacheable(key = "{#p0.entityNum}", sync = true)
    @Transactional(readOnly = true)
    @Query(value = "from  file_data where entity_id = ?1 and transaction_type in ?2 order by " +
            "consensus_timestamp desc limit 1", nativeQuery = true)
    Optional<FileData> findLastNonAppendFileData(EntityId entityId, List<Integer> transactionTypes);

    @Cacheable(key = "{#p0.entityNum}", sync = true)
    @Transactional(readOnly = true)
    Optional<FileData> findTopByEntityIdAndTransactionTypeInOrderByConsensusTimestampDesc(EntityId entityId,
                                                                                          List<Integer> transactionTypes);

    @CachePut(key = "{#p0.transactionType, #p0.entityId }")
    @Override
    <S extends FileData> S save(S fileData);
}
