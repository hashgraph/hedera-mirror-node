/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.domain.token.CustomFee;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface CustomFeeRepository extends CrudRepository<CustomFee, Long> {
    @Query(
            value =
                    """
            select * from (
                select * from custom_fee where token_id = ?1
                union all
                select * from custom_fee_history where token_id = ?1
            ) as cf
            where timestamp_range < int8_range(?2, null)
            order by timestamp_range desc
            limit 1;
            """,
            nativeQuery = true)
    Optional<CustomFee> findByIdAndTimestamp(long id, long timestamp);
}
