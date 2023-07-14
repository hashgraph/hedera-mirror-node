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

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.INT;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.WIPE_NFT;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.WipeWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.txn.token.WipeLogic;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class WipeNonFungiblePrecompile extends AbstractWipePrecompile {
    private static final Function WIPE_TOKEN_ACCOUNT_NFT_FUNCTION =
            new Function("wipeTokenAccountNFT(address,address,int64[])", INT);
    private static final Bytes WIPE_TOKEN_ACCOUNT_NFT_SELECTOR = Bytes.wrap(WIPE_TOKEN_ACCOUNT_NFT_FUNCTION.selector());
    private static final ABIType<Tuple> WIPE_TOKEN_ACCOUNT_NFT_DECODER =
            TypeFactory.create("(bytes32,bytes32,int64[])");

    protected final SyntheticTxnFactory syntheticTxnFactory;

    public WipeNonFungiblePrecompile(
            PrecompilePricingUtils pricingUtils, SyntheticTxnFactory syntheticTxnFactory, WipeLogic wipeLogic) {
        super(pricingUtils, wipeLogic);
        this.syntheticTxnFactory = syntheticTxnFactory;
    }

    public static WipeWrapper decodeWipeNFT(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, WIPE_TOKEN_ACCOUNT_NFT_SELECTOR, WIPE_TOKEN_ACCOUNT_NFT_DECODER);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var accountID = convertLeftPaddedAddressToAccountId(decodedArguments.get(1), aliasResolver);
        final var serialNumbers = ((long[]) decodedArguments.get(2));

        return WipeWrapper.forNonFungible(
                tokenID, accountID, Arrays.stream(serialNumbers).boxed().toList());
    }

    @Override
    public TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver, BodyParams bodyParams) {
        final var wipeOp = decodeWipeNFT(input, aliasResolver);
        return syntheticTxnFactory.createWipe(wipeOp);
    }

    @Override
    public long getMinimumFeeInTinybars(Timestamp consensusTime, final TransactionBody transactionBody) {
        Objects.requireNonNull(transactionBody, "`body` method should be called before `getMinimumFeeInTinybars`");
        return pricingUtils.getMinimumPriceInTinybars(WIPE_NFT, consensusTime);
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(AbiConstants.ABI_WIPE_TOKEN_ACCOUNT_NFT);
    }
}
