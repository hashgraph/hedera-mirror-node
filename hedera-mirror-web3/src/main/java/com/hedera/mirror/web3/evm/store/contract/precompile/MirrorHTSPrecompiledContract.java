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
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class MirrorHTSPrecompiledContract extends EvmHTSPrecompiledContract {

    public MirrorHTSPrecompiledContract(EvmInfrastructureFactory infrastructureFactory) {
        super(infrastructureFactory);
    }

    @Override
    public Pair<Long, Bytes> computeCosted(
            final Bytes input,
            final MessageFrame frame,
            final ViewGasCalculator viewGasCalculator,
            final TokenAccessor tokenAccessor) {
        // We need to check if a precompile call was made with preceding non-static frame. This would mean there might
        // be an operation
        // which modifies state. Currently, we do not support speculative writes, so we should throw na error.
        if (frameContainsNonStaticFrameInStack(frame)) {
            throw new UnsupportedOperationException("Precompile not supported for non-static frames");
        }

        return super.computeCosted(input, frame, viewGasCalculator, tokenAccessor);
    }

    private boolean frameContainsNonStaticFrameInStack(final MessageFrame messageFrame) {
        if (!messageFrame.isStatic()) {
            return true;
        }

        for (final var frame : messageFrame.getMessageFrameStack()) {
            if (!frame.isStatic()) {
                return true;
            }
        }

        return false;
    }
}
