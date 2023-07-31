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
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.ARRAY_BRACKETS;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.BYTES32;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.TOKEN_KEY;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeTokenKeys;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.TokenUpdateLogic;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EmptyRunResult;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.codec.TokenUpdateKeysWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class TokenUpdateKeysPrecompile extends AbstractTokenUpdatePrecompile {
    private static final Function TOKEN_UPDATE_KEYS_FUNCTION =
            new Function("updateTokenKeys(address," + TOKEN_KEY + ARRAY_BRACKETS + ")");
    private static final Bytes TOKEN_UPDATE_KEYS_SELECTOR = Bytes.wrap(TOKEN_UPDATE_KEYS_FUNCTION.selector());
    private static final ABIType<Tuple> TOKEN_UPDATE_KEYS_DECODER = TypeFactory.create("("
            + DecodingFacade.removeBrackets(BYTES32)
            + ","
            + DecodingFacade.TOKEN_KEY_DECODER
            + ARRAY_BRACKETS
            + ")");
    private final TokenUpdateLogic tokenUpdateLogic;
    private final OptionValidator optionValidator;
    private final MirrorNodeEvmProperties evmProperties;

    public TokenUpdateKeysPrecompile(
            final SyntheticTxnFactory syntheticTxnFactory,
            final PrecompilePricingUtils pricingUtils,
            final TokenUpdateLogic tokenUpdateLogic,
            OptionValidator optionValidator,
            MirrorNodeEvmProperties evmProperties) {
        super(pricingUtils, syntheticTxnFactory);
        this.tokenUpdateLogic = tokenUpdateLogic;
        this.optionValidator = optionValidator;
        this.evmProperties = evmProperties;
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver, BodyParams bodyParams) {
        final var updateOp = decodeUpdateTokenKeys(input, aliasResolver);
        return syntheticTxnFactory.createTokenUpdateKeys(updateOp);
    }

    @Override
    public RunResult run(final MessageFrame frame, TransactionBody transactionBody) {
        Objects.requireNonNull(transactionBody, "`body` method should be called before `run`");
        final var updateOp = transactionBody.getTokenUpdate();
        final var store = ((HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater()).getStore();
        final var hederaTokenStore = new HederaTokenStore(optionValidator, evmProperties, store); // ?

        final var validity = tokenUpdateLogic.validate(transactionBody);
        validateTrue(validity == OK, validity);
        /* --- Execute the transaction and capture its results --- */
        tokenUpdateLogic.updateTokenKeys(updateOp, frame.getBlockValues().getTimestamp(), hederaTokenStore);

        return new EmptyRunResult();
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(AbiConstants.ABI_ID_UPDATE_TOKEN_KEYS);
    }

    public static TokenUpdateKeysWrapper decodeUpdateTokenKeys(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments = decodeFunctionCall(input, TOKEN_UPDATE_KEYS_SELECTOR, TOKEN_UPDATE_KEYS_DECODER);
        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var tokenKeys = decodeTokenKeys(decodedArguments.get(1), aliasResolver);
        return new TokenUpdateKeysWrapper(tokenID, tokenKeys);
    }
}
