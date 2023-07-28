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

import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GetTokenExpiryInfoWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenExpiryInfo;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmGetTokenExpiryInfoPrecompile;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.codec.TokenInfoResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class GetTokenExpiryInfoPrecompile extends AbstractReadOnlyPrecompile
        implements EvmGetTokenExpiryInfoPrecompile {

    public GetTokenExpiryInfoPrecompile(
            final SyntheticTxnFactory syntheticTxnFactory,
            final EncodingFacade encoder,
            final EvmEncodingFacade evmEncoder,
            final PrecompilePricingUtils pricingUtils) {
        super(syntheticTxnFactory, encoder, evmEncoder, pricingUtils);
    }

    @Override
    public RunResult run(final MessageFrame frame, final TransactionBody transactionBody) {
        Objects.requireNonNull(transactionBody, "`body` method should be called before `run`");
        final var updater = ((HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater());
        final var getTokenExpiryInfoWrapper = decodeGetTokenExpiryInfo(frame.getInputData());
        final var tokenId = getTokenExpiryInfoWrapper.token();
        final var tokenInfo = updater.tokenAccessor().evmInfoForToken(EntityIdUtils.asTypedEvmAddress(tokenId));
        return new TokenInfoResult(tokenInfo.orElse(null));
    }

    @Override
    public Bytes getSuccessResultFor(final RunResult runResult) {
        final var tokenInfoResult = (TokenInfoResult) runResult;
        final var tokenInfo = tokenInfoResult.tokenInfo();

        validateTrue(tokenInfo != null, ResponseCodeEnum.INVALID_TOKEN_ID);
        Objects.requireNonNull(tokenInfo);

        final var expiryInfo = new TokenExpiryInfo(
                tokenInfo.getExpiry(), tokenInfo.getAutoRenewAccount(), tokenInfo.getAutoRenewPeriod());

        return evmEncoder.encodeGetTokenExpiryInfo(expiryInfo);
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(AbiConstants.ABI_ID_GET_TOKEN_EXPIRY_INFO);
    }

    public static GetTokenExpiryInfoWrapper<TokenID> decodeGetTokenExpiryInfo(final Bytes input) {
        final var rawGetTokenExpityInfoWrapper = EvmGetTokenExpiryInfoPrecompile.decodeGetTokenExpiryInfo(input);
        return new GetTokenExpiryInfoWrapper<>(convertAddressBytesToTokenID(rawGetTokenExpityInfoWrapper.token()));
    }
}
