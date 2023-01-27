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

import com.hedera.mirror.common.domain.event.EventFile;

public interface EventFileRepository extends StreamFileRepository<EventFile, Long>, RetentionRepository {

    @Override
    @Query(value = "select * from event_file order by consensus_end desc limit 1", nativeQuery = true)
    Optional<EventFile> findLatest();

    @Modifying
    @Override
    @Query("delete from EventFile where consensusEnd <= ?1")
    int prune(long consensusTimestamp);
}
