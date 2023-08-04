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

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils.addressFromBytes;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.ADDRESS_TRIO_RAW_TYPE;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.INT_BOOL_PAIR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.IsApproveForAllWrapper;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.IsApprovedForAllResult;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * This class is a modified copy of IsApprovedForAllPrecompile from hedera-services repo.
 *
 * Differences with the original:
 *  1. Implements a modified {@link Precompile} interface
 *  2. Removed class fields and adapted constructors in order to achieve stateless behaviour
 *  3. Body method is modified to accept {@link BodyParams} argument in order to achieve stateless behaviour
 *  4. Run method accepts Store argument in order to achieve stateless behaviour and returns {@link RunResult}
 *  5. Run method only reads from Store and returns {@link RunResult} which is needed in getSuccessResultFor
 */
public class IsApprovedForAllPrecompile extends AbstractReadOnlyPrecompile {
    private static final Function HAPI_IS_APPROVED_FOR_ALL =
            new Function("isApprovedForAll(address,address,address)", INT_BOOL_PAIR);
    private static final Bytes HAPI_IS_APPROVED_FOR_ALL_SELECTOR = Bytes.wrap(HAPI_IS_APPROVED_FOR_ALL.selector());
    private static final ABIType<Tuple> HAPI_IS_APPROVED_FOR_ALL_DECODER = TypeFactory.create(ADDRESS_TRIO_RAW_TYPE);

    public IsApprovedForAllPrecompile(
            SyntheticTxnFactory syntheticTxnFactory, EncodingFacade encoder, PrecompilePricingUtils pricingUtils) {
        super(syntheticTxnFactory, encoder, pricingUtils);
    }

    @Override
    public RunResult run(MessageFrame frame, TransactionBody transactionBody) {
        final var updater = ((HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater());
        final var inputData = frame.getInputData();
        final var wrapper = decodeIsApprovedForAll(inputData);
        final var allowances = updater.tokenAccessor()
                .staticIsOperator(
                        addressFromBytes(wrapper.owner()),
                        addressFromBytes(wrapper.operator()),
                        addressFromBytes(wrapper.token()));
        return new IsApprovedForAllResult(allowances);
    }

    @Override
    public Bytes getSuccessResultFor(RunResult runResult) {
        final var allowanceResult = (IsApprovedForAllResult) runResult;
        return encoder.encodeIsApprovedForAll(SUCCESS.getNumber(), allowanceResult.isApprovedForAll());
    }

    public static IsApproveForAllWrapper<byte[], byte[], byte[]> decodeIsApprovedForAll(final Bytes input) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, HAPI_IS_APPROVED_FOR_ALL_SELECTOR, HAPI_IS_APPROVED_FOR_ALL_DECODER);
        return new IsApproveForAllWrapper<>(decodedArguments.get(0), decodedArguments.get(1), decodedArguments.get(2));
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(AbiConstants.ABI_ID_IS_APPROVED_FOR_ALL);
    }
}
