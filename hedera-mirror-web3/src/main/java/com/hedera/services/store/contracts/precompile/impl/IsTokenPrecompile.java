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

import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;

import com.hederahashgraph.api.proto.java.TokenID;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenInfoWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmIsTokenPrecompile;
import com.hedera.services.store.contracts.MirrorState;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;

public class IsTokenPrecompile extends AbstractTokenInfoPrecompile implements EvmIsTokenPrecompile {

    public IsTokenPrecompile(
            final TokenID tokenId,
            final MirrorState ledgers,
            final EncodingFacade encoder,
            final EvmEncodingFacade evmEncoder,
            final PrecompilePricingUtils pricingUtils) {
        super(tokenId, ledgers, encoder, evmEncoder, pricingUtils);
    }

    @Override
    public void body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final var tokenInfoWrapper = decodeIsToken(input);
        tokenId = tokenInfoWrapper.token();
    }

    @Override
    public Bytes getSuccessResultFor() {
        final var isToken = ledgers.contains(tokenId);
        return evmEncoder.encodeIsToken(isToken);
    }

    public static TokenInfoWrapper<TokenID> decodeIsToken(final Bytes input) {
        final var rawTokenInfoWrapper = EvmIsTokenPrecompile.decodeIsToken(input);
        return TokenInfoWrapper.forToken(convertAddressBytesToTokenID(rawTokenInfoWrapper.token()));
    }
}
