/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.grpc.repository;

import com.hedera.mirror.common.domain.transaction.Transaction;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface TransactionRepository extends CrudRepository<Transaction, Long> {

    @Query(
            value =
                    """
       select *
       from transaction
       where consensus_timestamp > ?1 and result = 22 and type = ?3
       order by consensus_timestamp
       limit ?2
       """,
            nativeQuery = true)
    List<Transaction> findSuccessfulTransactionsByTypeAfterTimestamp(long consensusTimestamp, int limit, int type);
}
