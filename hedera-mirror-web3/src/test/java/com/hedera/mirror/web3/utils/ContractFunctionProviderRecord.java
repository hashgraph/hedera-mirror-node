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

package com.hedera.mirror.web3.utils;

import com.hedera.mirror.web3.viewmodel.BlockType;
import lombok.Builder;
import lombok.With;
import org.hyperledger.besu.datatypes.Address;

@Builder
public record ContractFunctionProviderRecord(
        Address contractAddress, Address sender, long value, String expectedErrorMessage, @With BlockType block) {

    public static ContractFunctionProviderRecordBuilder builder() {
        return new ContractFunctionProviderRecordBuilder()
                .block(BlockType.LATEST)
                .sender(Address.fromHexString(""));
    }
}
