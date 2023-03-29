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
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenFreezeUnfreezeWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmIsFrozenPrecompile;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;

public class IsFrozenPrecompile extends AbstractReadOnlyPrecompile implements EvmIsFrozenPrecompile {

    private AccountID accountId;

    public IsFrozenPrecompile(
            final TokenID tokenId,
            final WorldLedgers ledgers,
            final EncodingFacade encoder,
            final EvmEncodingFacade evmEncoder,
            final PrecompilePricingUtils pricingUtils) {
        super(tokenId, ledgers, encoder, evmEncoder, pricingUtils);
    }

    @Override
    public void body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final var tokenIsFrozenWrapper = decodeIsFrozen(input, aliasResolver);
        tokenId = tokenIsFrozenWrapper.token();
        accountId = tokenIsFrozenWrapper.account();
    }

    @Override
    public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
        final boolean isFrozen = ledgers.isFrozen(accountId, tokenId);
        return evmEncoder.encodeIsFrozen(isFrozen);
    }

    public static TokenFreezeUnfreezeWrapper<TokenID, AccountID> decodeIsFrozen(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final var rawTokenFreezeUnfreezeWrapper = EvmIsFrozenPrecompile.decodeIsFrozen(input);
        return TokenFreezeUnfreezeWrapper.forIsFrozen(
                convertAddressBytesToTokenID(rawTokenFreezeUnfreezeWrapper.token()),
                convertLeftPaddedAddressToAccountId(rawTokenFreezeUnfreezeWrapper.account(), aliasResolver));
    }
}
