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
import javax.transaction.Transactional;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import com.hedera.mirror.importer.config.CacheConfiguration;
import com.hedera.mirror.importer.domain.AddressBook;
import com.hedera.mirror.importer.domain.EntityId;

@CacheConfig(cacheNames = "addressbook", cacheManager = CacheConfiguration.NEVER_EXPIRE_LARGE)
@Transactional
public interface AddressBookRepository extends CrudRepository<AddressBook, Long> {
    @Query("from AddressBook where consensusTimestamp <= ?1 and fileId = ?2 order by " +
            "consensusTimestamp asc")
    List<AddressBook> findCompleteAddressBooks(long consensusTimestamp, EntityId fileId);

    @Cacheable(key = "{#p0, #p1.entityNum}", sync = true)
    Optional<AddressBook> findTopByConsensusTimestampBeforeAndFileIdOrderByConsensusTimestampDesc(long consensusTimestamp, EntityId fileId);

    @Cacheable(key = "{#p0.entityNum}", sync = true)
    Optional<AddressBook> findTopByFileIdOrderByConsensusTimestampDesc(EntityId fileId);

    @Modifying
    @Query("update AddressBook set endConsensusTimestamp = :end where consensusTimestamp = :timestamp")
    void updateEndConsensusTimestamp(@Param("timestamp") long consensusTimestamp,
                                     @Param("end") long endConsensusTimestamp);

    @Modifying
    @Query("update AddressBook set startConsensusTimestamp = :start where consensusTimestamp = :timestamp")
    void updateStartConsensusTimestamp(@Param("timestamp") long consensusTimestamp,
                                       @Param("start") long startConsensusTimestamp);
}
