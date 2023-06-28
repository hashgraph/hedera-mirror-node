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
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.ADDRESS_PAIR_RAW_TYPE;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.INT;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_DISSOCIATE_TOKEN;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.Dissociation;
import com.hedera.services.store.contracts.precompile.codec.HrcParams;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

public class DissociatePrecompile extends AbstractDissociatePrecompile {

    private static final Function DISSOCIATE_TOKEN_FUNCTION = new Function("dissociateToken(address,address)", INT);
    private static final Bytes DISSOCIATE_TOKEN_SELECTOR = Bytes.wrap(DISSOCIATE_TOKEN_FUNCTION.selector());
    private static final ABIType<Tuple> DISSOCIATE_TOKEN_DECODER = TypeFactory.create(ADDRESS_PAIR_RAW_TYPE);

    public DissociatePrecompile(final PrecompilePricingUtils precompilePricingUtils) {
        super(precompilePricingUtils);
    }

    @Override
    public Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver, BodyParams bodyParams) {
        TokenID tokenId = null;
        Address callerAccountAddress;
        AccountID callerAccountID = null;

        if (bodyParams instanceof HrcParams hrcParams) {
            tokenId = hrcParams.token();
            callerAccountAddress = hrcParams.senderAddress();
            callerAccountID =
                    EntityIdUtils.accountIdFromEvmAddress(Objects.requireNonNull(callerAccountAddress.toArray()));
        }

        final var dissociateOp = tokenId == null
                ? decodeDissociate(input, aliasResolver)
                : Dissociation.singleDissociation(Objects.requireNonNull(callerAccountID), tokenId);

        return createDissociate(dissociateOp);
    }

    @Override
    public long getGasRequirement(long blockTimestamp, Builder transactionBody, Store store) {
        return pricingUtils.computeGasRequirement(blockTimestamp, this, transactionBody, store);
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(ABI_ID_DISSOCIATE_TOKEN);
    }

    public static Dissociation decodeDissociate(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments = decodeFunctionCall(input, DISSOCIATE_TOKEN_SELECTOR, DISSOCIATE_TOKEN_DECODER);

        final var accountID = convertLeftPaddedAddressToAccountId(decodedArguments.get(0), aliasResolver);
        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(1));

        return Dissociation.singleDissociation(accountID, tokenID);
    }
}
