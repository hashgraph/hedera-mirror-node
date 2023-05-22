/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.codec.EncodingFacade.SUCCESS_RESULT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FEE_SUBMITTED;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.REVERT;

import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * Extracted from hedera-services
 */
public interface Precompile {

    // Construct the synthetic transaction
    TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver);

    // Customize fee charging
    long getMinimumFeeInTinybars(Timestamp consensusTime);

    // Change the world state through the given frame
    void run(MessageFrame frame);

    long getGasRequirement(long blockTimestamp);

    Set<Integer> getFunctionSelectors();

    default void handleSentHbars(final MessageFrame frame) {
        if (!Objects.equals(Wei.ZERO, frame.getValue())) {
            final String INVALID_TRANSFER_MSG = "Transfer of Value to Hedera Precompile";
            frame.setRevertReason(Bytes.of(INVALID_TRANSFER_MSG.getBytes(StandardCharsets.UTF_8)));
            frame.setState(REVERT);
            throw new InvalidTransactionException(INVALID_FEE_SUBMITTED, "", "");
        }
    }

    default Bytes getSuccessResultFor() {
        return SUCCESS_RESULT;
    }

    default Bytes getFailureResultFor(final ResponseCodeEnum status) {
        return EncodingFacade.resultFrom(status);
    }
}
