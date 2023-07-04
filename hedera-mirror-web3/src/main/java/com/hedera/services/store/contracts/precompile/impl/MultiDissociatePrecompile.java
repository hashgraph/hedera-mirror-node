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
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.INT;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_DISSOCIATE_TOKENS;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeTokenIDsFromBytesArray;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.Dissociation;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.txn.token.DissociateLogic;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class MultiDissociatePrecompile extends AbstractDissociatePrecompile {

    private static final Function DISSOCIATE_TOKENS_FUNCTION = new Function("dissociateTokens(address,address[])", INT);
    private static final Bytes DISSOCIATE_TOKENS_SELECTOR = Bytes.wrap(DISSOCIATE_TOKENS_FUNCTION.selector());
    private static final ABIType<Tuple> DISSOCIATE_TOKENS_DECODER = TypeFactory.create("(bytes32,bytes32[])");

    private final SyntheticTxnFactory syntheticTxnFactory;

    public MultiDissociatePrecompile(
            final PrecompilePricingUtils precompilePricingUtils,
            final SyntheticTxnFactory syntheticTxnFactory,
            final DissociateLogic dissociateLogic) {
        super(dissociateLogic, precompilePricingUtils);
        this.syntheticTxnFactory = syntheticTxnFactory;
    }

    @Override
    public Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver, BodyParams bodyParams) {
        final Tuple decodedArguments = decodeFunctionCall(input, DISSOCIATE_TOKENS_SELECTOR, DISSOCIATE_TOKENS_DECODER);

        final var accountID = convertLeftPaddedAddressToAccountId(decodedArguments.get(0), aliasResolver);
        final var tokenIDs = decodeTokenIDsFromBytesArray(decodedArguments.get(1));

        final var dissociateOp = Dissociation.multiDissociation(accountID, tokenIDs);

        return syntheticTxnFactory.createDissociate(dissociateOp);
    }

    @Override
    public long getGasRequirement(long blockTimestamp, Builder transactionBody, Store store) {
        return pricingUtils.computeGasRequirement(blockTimestamp, this, transactionBody, store);
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(ABI_ID_DISSOCIATE_TOKENS);
    }
}
