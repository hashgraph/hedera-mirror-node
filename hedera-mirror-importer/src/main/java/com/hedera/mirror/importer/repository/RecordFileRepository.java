package com.hedera.mirror.importer.repository;

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

import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.hedera.mirror.common.domain.transaction.RecordFile;

public interface RecordFileRepository extends StreamFileRepository<RecordFile, Long>, RetentionRepository {

    @Override
    @Query(value = "select * from record_file order by consensus_end desc limit 1", nativeQuery = true)
    Optional<RecordFile> findLatest();

    @Query(nativeQuery = true, value = "select * from record_file where consensus_end < " +
            "(select max(consensus_end) from record_file) - ?1 order by consensus_end desc limit 1")
    Optional<RecordFile> findLatestWithOffset(long offset);

    @Query(value = "select * from record_file where consensus_end < ?1 and gas_used = -1 order by consensus_end desc " +
            "limit 1",
            nativeQuery = true)
    Optional<RecordFile> findLatestMissingGasUsedBefore(long consensusTimestamp);

    @Query(value = "select * from record_file where consensus_end > ?1 and consensus_end <= ?2 " +
            "order by consensus_end asc limit 1", nativeQuery = true)
    Optional<RecordFile> findNextBetween(long minTimestampExclusive, long maxTimestampInclusive);

    @Modifying
    @Override
    @Query("delete from RecordFile where consensusEnd <= ?1")
    int prune(long consensusTimestamp);

    @Modifying
    @Query(nativeQuery = true, value = "update record_file set index = index + ?1")
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 7200)
    int updateIndex(long offset);
}
