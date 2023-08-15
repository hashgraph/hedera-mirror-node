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

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * Extracted interface from hedera-services
 *
 * Differences from the original:
 *  1. Added record types for input arguments and return types, so that the Precompile implementation could achieve statless behaviour
 *  2. Added dependency to a {@link Store} interface that will hide the details of the state used for read/write operations
 */
public interface Precompile {

    // Construct the synthetic transaction
    TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver, BodyParams bodyParams);

    // Customize fee charging
    long getMinimumFeeInTinybars(Timestamp consensusTime, TransactionBody transactionBody);

    // Change the world state through the given frame
    RunResult run(MessageFrame frame, TransactionBody transactionBody);

    long getGasRequirement(
            long blockTimestamp,
            TransactionBody.Builder transactionBody,
            Store store,
            HederaEvmContractAliases mirrorEvmContractAliases);

    Set<Integer> getFunctionSelectors();

    default void handleSentHbars(final MessageFrame frame, final TransactionBody.Builder transactionBody) {
        if (!Objects.equals(Wei.ZERO, frame.getValue())) {
            final String INVALID_TRANSFER_MSG = "Transfer of Value to Hedera Precompile";
            frame.setRevertReason(Bytes.of(INVALID_TRANSFER_MSG.getBytes(StandardCharsets.UTF_8)));
            frame.setState(REVERT);
            throw new InvalidTransactionException(INVALID_FEE_SUBMITTED, "", "");
        }
    }

    default Bytes getSuccessResultFor(final RunResult runResult) {
        return SUCCESS_RESULT;
    }

    default Bytes getFailureResultFor(final ResponseCodeEnum status) {
        return EncodingFacade.resultFrom(status);
    }
}
