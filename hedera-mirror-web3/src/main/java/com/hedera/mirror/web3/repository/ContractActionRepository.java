/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.domain.contract.ContractAction;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface ContractActionRepository extends CrudRepository<ContractAction, Long> {

    @Query(
            value =
                    "select * from contract_action where consensus_timestamp = ?1 and result_data_type = 12 and call_type = 4 order by index asc",
            nativeQuery = true)
    List<ContractAction> findFailedSystemActionsByConsensusTimestamp(long consensusTimestamp);
}
