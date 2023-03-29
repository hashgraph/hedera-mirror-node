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

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenInfoWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmGetTokenTypePrecompile;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;

public class GetTokenTypePrecompile extends AbstractTokenInfoPrecompile implements EvmGetTokenTypePrecompile {

    public GetTokenTypePrecompile(
            final TokenID tokenId,
            final WorldLedgers ledgers,
            final EncodingFacade encoder,
            final EvmEncodingFacade evmEncoder,
            final PrecompilePricingUtils pricingUtils) {
        super(tokenId, ledgers, encoder, evmEncoder, pricingUtils);
    }

    @Override
    public void body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final var tokenInfoWrapper = decodeGetTokenType(input);
        tokenId = tokenInfoWrapper.token();
    }

    @Override
    public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
        //final var token = ledgers.getImmutableRef(tokenId);
        //validateTrue(token != null, ResponseCodeEnum.INVALID_TOKEN_ID);
        final var tokenType = ledgers.tokenType().ordinal();
        return evmEncoder.encodeGetTokenType(tokenType);
    }

    public static TokenInfoWrapper<TokenID> decodeGetTokenType(final Bytes input) {
        final var rawTokenInfoWrapper = EvmGetTokenTypePrecompile.decodeGetTokenType(input);
        return TokenInfoWrapper.forToken(convertAddressBytesToTokenID(rawTokenInfoWrapper.token()));
    }
}
