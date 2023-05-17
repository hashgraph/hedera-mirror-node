/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.evm.properties;

import static com.hedera.mirror.web3.viewmodel.ContractCallRequest.ADDRESS_LENGTH;

import com.hedera.mirror.web3.validation.Hex;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import lombok.NonNull;
import org.hyperledger.besu.evm.frame.MessageFrame.State;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "hedera.mirror.web3.evm.trace")
public class TraceProperties {

    private boolean enabled = false;

    @NonNull
    private Set<@Hex(minLength = ADDRESS_LENGTH, maxLength = ADDRESS_LENGTH) String> contract = new HashSet<>();

    @NonNull
    private Set<State> status = new HashSet<>();

    public boolean stateFilterCheck(State state) {
        return !getStatus().isEmpty() && !getStatus().contains(state);
    }

    public boolean contractFilterCheck(String contract) {
        return !getContract().isEmpty() && !getContract().contains(contract);
    }
}
