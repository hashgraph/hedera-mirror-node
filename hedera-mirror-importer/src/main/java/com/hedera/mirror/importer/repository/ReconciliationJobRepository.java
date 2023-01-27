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
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.common.domain.job.ReconciliationJob;

public interface ReconciliationJobRepository extends CrudRepository<ReconciliationJob, Long>, RetentionRepository {

    @Query(nativeQuery = true, value = "select * from reconciliation_job order by timestamp_start desc limit 1")
    Optional<ReconciliationJob> findLatest();

    @Modifying
    @Override
    @Query("delete from ReconciliationJob where consensusTimestamp <= ?1")
    int prune(long consensusTimestamp);
}
