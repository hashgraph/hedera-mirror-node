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

package com.hedera.mirror.web3.service.model;

import com.hedera.mirror.web3.evm.contracts.execution.traceability.TracerType;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

public interface CallServiceParameters {
    BlockType getBlock();

    Bytes getCallData();

    CallType getCallType();

    long getGas();

    Address getReceiver();

    HederaEvmAccount getSender();

    TracerType getTracerType();

    long getValue();

    boolean isEstimate();

    boolean isStatic();

    public enum CallType {
        ETH_CALL,
        ETH_DEBUG_TRACE_TRANSACTION,
        ETH_ESTIMATE_GAS,
        ERROR
    }
}
