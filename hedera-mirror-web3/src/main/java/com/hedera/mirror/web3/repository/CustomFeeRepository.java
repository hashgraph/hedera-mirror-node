package com.hedera.mirror.web3.repository;

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

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.common.domain.transaction.CustomFee;
import com.hedera.mirror.common.domain.transaction.CustomFee.Id;

public interface CustomFeeRepository extends CrudRepository<CustomFee, Id> {

    @Query(value = "select * from custom_fee where token_id = ?1 and created_timestamp = (select created_timestamp from custom_fee where token_id = ?1 order by created_timestamp desc limit 1)",
            nativeQuery = true)
    List<CustomFee> findByTokenId(final Long tokenId);
}
