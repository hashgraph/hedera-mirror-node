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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenAllowanceWrapper;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.AllowanceResult;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class AllowancePrecompile extends AbstractReadOnlyPrecompile {

    private static final Function HAPI_ALLOWANCE_FUNCTION =
            new Function("allowance(address,address,address)", "(int,int)");
    private static final Bytes HAPI_ALLOWANCE_SELECTOR = Bytes.wrap(HAPI_ALLOWANCE_FUNCTION.selector());
    private static final ABIType<Tuple> HAPI_ALLOWANCE_DECODER = TypeFactory.create(ADDRESS_TRIO_RAW_TYPE);

    public AllowancePrecompile(
            SyntheticTxnFactory syntheticTxnFactory, EncodingFacade encoder, PrecompilePricingUtils pricingUtils) {
        super(syntheticTxnFactory, encoder, pricingUtils);
    }

    @Override
    public RunResult run(MessageFrame frame, TransactionBody transactionBody) {
        final var updater = ((HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater());
        final var inputData = frame.getInputData();
        final var wrapper = decodeTokenAllowance(inputData);
        final var allowances = updater.tokenAccessor()
                .staticAllowanceOf(
                        addressFromBytes(wrapper.owner()),
                        addressFromBytes(wrapper.spender()),
                        addressFromBytes(wrapper.token()));
        return new AllowanceResult(allowances);
    }

    @Override
    public Bytes getSuccessResultFor(RunResult runResult) {
        final var allowanceResult = (AllowanceResult) runResult;
        return encoder.encodeAllowance(SUCCESS.getNumber(), allowanceResult.allowances());
    }

    public static TokenAllowanceWrapper<byte[], byte[], byte[]> decodeTokenAllowance(final Bytes input) {
        final Tuple decodedArguments = decodeFunctionCall(input, HAPI_ALLOWANCE_SELECTOR, HAPI_ALLOWANCE_DECODER);
        return new TokenAllowanceWrapper<>(decodedArguments.get(0), decodedArguments.get(1), decodedArguments.get(2));
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(AbiConstants.ABI_ID_ALLOWANCE);
    }
}
