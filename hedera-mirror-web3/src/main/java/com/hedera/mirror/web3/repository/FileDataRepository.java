/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.repository;

import com.hedera.mirror.common.domain.file.FileData;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileDataRepository extends CrudRepository<FileData, Long> {
    @Query(
            value =
                    """
                with latest_create as (
                      select max(file_data.consensus_timestamp) as consensus_timestamp
                      from file_data
                      where file_data.entity_id = ?1 and file_data.transaction_type in (17, 19)
                    )
                    select
                    string_agg(file_data.file_data, '' order by file_data.consensus_timestamp) as file_data
                    from file_data
                    join latest_create l on file_data.consensus_timestamp >= l.consensus_timestamp
                    where file_data.entity_id = ?1 and file_data.transaction_type in (16, 17, 19)
                      and ?2 >= l.consensus_timestamp""",
            nativeQuery = true)
    byte[] getFileAtTimestamp(long fileId, long timestamp);
}
