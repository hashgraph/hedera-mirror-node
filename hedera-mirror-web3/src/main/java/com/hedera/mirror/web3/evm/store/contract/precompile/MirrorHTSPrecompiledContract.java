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

package com.hedera.mirror.web3.evm.store.contract.precompile;

import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.ViewGasCalculator;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import lombok.CustomLog;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

/*This class would serve as a bridge between hedera-evm and the other libraries from services that would contain the precompile logic.
Currently, the adapter implementation resides in com.hedera.services package but would be removed once we start depending on the new modules from services.*/
@CustomLog
public class MirrorHTSPrecompiledContract extends EvmHTSPrecompiledContract {

    private final HTSPrecompiledContractAdapter htsPrecompiledContractAdapter;

    public MirrorHTSPrecompiledContract(
            final EvmInfrastructureFactory infrastructureFactory,
            final HTSPrecompiledContractAdapter htsPrecompiledContractAdapter) {
        super(infrastructureFactory);
        this.htsPrecompiledContractAdapter = htsPrecompiledContractAdapter;
    }

    @Override
    public Pair<Long, Bytes> computeCosted(
            final Bytes input,
            final MessageFrame frame,
            final ViewGasCalculator viewGasCalculator,
            final TokenAccessor tokenAccessor) {
        return htsPrecompiledContractAdapter.computeCosted(input, frame, viewGasCalculator, tokenAccessor);
    }
}
