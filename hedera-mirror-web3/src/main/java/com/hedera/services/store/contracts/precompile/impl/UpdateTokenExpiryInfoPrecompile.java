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
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_UPDATE_TOKEN_EXPIRY_INFO;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeTokenExpiry;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.TokenUpdateLogic;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.EmptyRunResult;
import com.hedera.services.store.contracts.precompile.codec.FunctionParam;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.codec.TokenUpdateExpiryInfoWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class UpdateTokenExpiryInfoPrecompile extends AbstractTokenUpdatePrecompile {

    private TokenUpdateExpiryInfoWrapper updateExpiryInfoOp;
    private HederaTokenStore hederaTokenStore;
    private final ContextOptionValidator contextOptionValidator;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;
    private final TokenUpdateLogic tokenUpdateLogic;

    public UpdateTokenExpiryInfoPrecompile(
            TokenUpdateLogic tokenUpdateLogic,
            MirrorNodeEvmProperties mirrorNodeEvmProperties,
            ContextOptionValidator contextOptionValidator,
            SyntheticTxnFactory syntheticTxnFactory,
            PrecompilePricingUtils precompilePricingUtils) {
        super(precompilePricingUtils, syntheticTxnFactory);
        this.tokenUpdateLogic = tokenUpdateLogic;
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
        this.contextOptionValidator = contextOptionValidator;
    }

    @Override
    public Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver, BodyParams bodyParams) {
        final var functionId = ((FunctionParam) bodyParams).functionId();
        final var updateExpiryInfoAbi =
                switch (functionId) {
                    case AbiConstants.ABI_ID_UPDATE_TOKEN_EXPIRY_INFO -> SystemContractAbis.UPDATE_TOKEN_EXPIRY_INFO_V1;
                    case AbiConstants.ABI_ID_UPDATE_TOKEN_EXPIRY_INFO_V2 -> SystemContractAbis
                            .UPDATE_TOKEN_EXPIRY_INFO_V2;
                    default -> throw new IllegalArgumentException("invalid selector to updateExpiryInfo precompile");
                };
        updateExpiryInfoOp = getTokenUpdateExpiryInfoWrapper(input, aliasResolver, updateExpiryInfoAbi);
        return syntheticTxnFactory.createTokenUpdateExpiryInfo(updateExpiryInfoOp);
    }

    @Override
    public RunResult run(MessageFrame frame, Store store, TransactionBody transactionBody) {
        Objects.requireNonNull(updateExpiryInfoOp);
        validateTrue(updateExpiryInfoOp.tokenID() != null, INVALID_TOKEN_ID);

        initializeHederaTokenStore(store);

        final var validity = tokenUpdateLogic.validate(transactionBody);
        validateTrue(validity == OK, validity);

        tokenUpdateLogic.updateTokenExpiryInfo(transactionBody.getTokenUpdate(), store, hederaTokenStore);

        return new EmptyRunResult();
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(ABI_ID_UPDATE_TOKEN_EXPIRY_INFO);
    }

    private void initializeHederaTokenStore(Store store) {
        hederaTokenStore = new HederaTokenStore(contextOptionValidator, mirrorNodeEvmProperties, store);
    }

    public static TokenUpdateExpiryInfoWrapper getTokenUpdateExpiryInfoWrapper(
            Bytes input, UnaryOperator<byte[]> aliasResolver, @NonNull final SystemContractAbis abi) {
        final Tuple decodedArguments = decodeFunctionCall(input, abi.selector, abi.decoder);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final Tuple tokenExpiryStruct = decodedArguments.get(1);
        final var tokenExpiry = decodeTokenExpiry(tokenExpiryStruct, aliasResolver);
        return new TokenUpdateExpiryInfoWrapper(tokenID, tokenExpiry);
    }
}
