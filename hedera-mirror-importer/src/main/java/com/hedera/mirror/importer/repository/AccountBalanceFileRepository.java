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

package com.hedera.mirror.importer.repository;

import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;

public interface AccountBalanceFileRepository extends StreamFileRepository<AccountBalanceFile, Long> {

    @Override
    @Query(value = "select * from account_balance_file order by consensus_timestamp desc limit 1", nativeQuery = true)
    Optional<AccountBalanceFile> findLatest();

    @Query(
            nativeQuery = true,
            value =
                    "select * from account_balance_file where consensus_timestamp < ?1 order by consensus_timestamp desc limit 1")
    Optional<AccountBalanceFile> findLatestBefore(long timestamp);

    @Query(
            nativeQuery = true,
            value = "select * from account_balance_file where consensus_timestamp >= ?1 "
                    + "and consensus_timestamp <= ?2 and consensus_timestamp <= (select max(consensus_end) from record_file) "
                    + "order by consensus_timestamp asc limit 1")
    Optional<AccountBalanceFile> findNextInRange(long startTimestamp, long endTimestamp);
}
