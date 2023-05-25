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

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class MockPrecompile implements Precompile {

    @Override
    public TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver) {
        // Dummy logic to simulate decoding of invalid input
        if (10 == input.size()) {
            return TransactionBody.newBuilder();
        } else {
            return null;
        }
    }

    @Override
    public long getMinimumFeeInTinybars(Timestamp consensusTime) {
        return 0;
    }

    @Override
    public void run(MessageFrame frame) {
        // Dummy logic to mimic invalid behaviour
        if (Address.ZERO.equals(frame.getSenderAddress())) {
            throw new InvalidTransactionException(ResponseCodeEnum.INVALID_ACCOUNT_ID);
        }
    }

    @Override
    public long getGasRequirement(long blockTimestamp) {
        return 0;
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(0x00000000);
    }

    @Override
    public Bytes getSuccessResultFor() {
        return SUCCESS_RESULT;
    }
}
