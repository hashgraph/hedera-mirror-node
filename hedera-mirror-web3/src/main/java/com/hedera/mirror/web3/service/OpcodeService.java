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

package com.hedera.mirror.web3.service;

import com.hedera.mirror.rest.model.OpcodesResponse;
import com.hedera.mirror.web3.common.TransactionIdOrHashParameter;
import com.hedera.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.model.ContractCallDebugServiceParameters;
import org.springframework.lang.NonNull;

public interface OpcodeService {

    /**
     * @param transactionIdOrHash the {@link TransactionIdOrHashParameter}
     * @return the {@link ContractCallDebugServiceParameters} for the given transaction id or hash
     */
    ContractCallDebugServiceParameters buildCallServiceParameters(@NonNull TransactionIdOrHashParameter transactionIdOrHash);

    /**
     * @param opcodesProcessingResult the {@link OpcodesProcessingResult}
     * @return the {@link OpcodesResponse} for the given {@link OpcodesProcessingResult}
     */
    OpcodesResponse buildOpcodesResponse(@NonNull OpcodesProcessingResult opcodesProcessingResult);
}