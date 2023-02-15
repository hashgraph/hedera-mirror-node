package com.hedera.mirror.web3.service.model;

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

import lombok.Builder;
import lombok.Value;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;

@Value
@Builder
public class CallServiceParameters {
    HederaEvmAccount sender;
    Address receiver;
    long providedGasLimit;
    long value;
    Bytes callData;
    boolean isStatic;
    CallType callType;

    public enum CallType {
        ETH_CALL,
        ETH_ESTIMATE_GAS,
        ERROR
    }
}
