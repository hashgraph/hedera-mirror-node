package com.hedera.mirror.importer.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import java.util.List;

import com.hedera.mirror.common.domain.contract.ContractLog;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.contract.ContractStateChange;
import com.hedera.mirror.common.domain.transaction.RecordItem;

/**
 * This service is used to centralize the conversion logic from record stream items to its internal ContractResult
 * related representations.
 */
public interface ContractResultService {
    ContractResult getContractResult(RecordItem recordItem);

    List<ContractLog> getContractLogs(ContractFunctionResult functionResult, ContractResult contractResult);

    List<ContractStateChange> getContractStateChanges(ContractFunctionResult functionResult,
                                                      ContractResult contractResult);
}
