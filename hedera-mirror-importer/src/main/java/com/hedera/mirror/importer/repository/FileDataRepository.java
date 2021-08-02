package com.hedera.mirror.importer.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.importer.domain.FileData;

public interface FileDataRepository extends CrudRepository<FileData, Long> {
    @Query(value = "select * from file_data where consensus_timestamp between ?1 and ?2 and entity_id " +
            "= ?3 and transaction_type = ?4 order by consensus_timestamp asc", nativeQuery = true)
    List<FileData> findFilesInRange(long start, long end, long encodedEntityId, int transactionType);

    @Query(value = "select * from file_data where consensus_timestamp < ?1 and entity_id = ?2 and transaction_type in" +
            " (?3) order by consensus_timestamp desc limit 1", nativeQuery = true)
    Optional<FileData> findLatestMatchingFile(long consensusTimestamp, long encodedEntityId,
                                              List<Integer> transactionTypes);

    @Query(value = "select * from file_data where consensus_timestamp > ?1 and consensus_timestamp < ?2 and " +
            "entity_id in (101, 102) order by consensus_timestamp asc limit ?3", nativeQuery = true)
    List<FileData> findAddressBooksBetween(long startConsensusTimestamp, long endConsensusTimestamp, long limit);
}
