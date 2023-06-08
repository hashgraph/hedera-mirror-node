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

import static com.hedera.services.store.contracts.precompile.codec.EncodingFacade.SUCCESS_RESULT;

import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.mirror.web3.evm.store.contract.precompile.codec.BodyParams;
import com.hedera.mirror.web3.evm.store.contract.precompile.codec.EmptyResult;
import com.hedera.mirror.web3.evm.store.contract.precompile.codec.RunResult;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;

@Named
public class MockPrecompile implements Precompile {

    @Override
    public TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver, BodyParams bodyParams) {
        return TransactionBody.newBuilder();
    }

    @Override
    public long getMinimumFeeInTinybars(Timestamp consensusTime) {
        return 0;
    }

    @Override
    public RunResult run(MessageFrame frame, StackedStateFrames<Object> stackedStateFrames) {
        // Dummy logic to mimic invalid behaviour
        if (Address.ZERO.equals(frame.getSenderAddress())) {
            throw new InvalidTransactionException(ResponseCodeEnum.INVALID_ACCOUNT_ID);
        } else if (Address.ECREC.equals(frame.getSenderAddress())) {
            return null;
        } else {
            return new EmptyResult();
        }
    }

    @Override
    public long getGasRequirement(long blockTimestamp, StackedStateFrames<Object> stackedStateFrames) {
        return 0;
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(0x00000000);
    }

    @Override
    public Bytes getSuccessResultFor(final RunResult runResult) {
        if (runResult == null) {
            return null;
        } else {
            return SUCCESS_RESULT;
        }
    }
}
