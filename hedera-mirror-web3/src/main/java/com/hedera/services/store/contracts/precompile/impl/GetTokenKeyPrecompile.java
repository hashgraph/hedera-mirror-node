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

import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmKey;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GetTokenKeyWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmGetTokenKeyPrecompile;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.store.contracts.MirrorState;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.utils.EntityIdUtils;

public class GetTokenKeyPrecompile extends AbstractReadOnlyPrecompile implements EvmGetTokenKeyPrecompile {
    private TokenProperty keyType;

    public GetTokenKeyPrecompile(
            final TokenID tokenId,
            final MirrorState ledgers,
            final EncodingFacade encoder,
            final EvmEncodingFacade evmEncoder,
            final PrecompilePricingUtils pricingUtils) {
        super(tokenId, ledgers, encoder, evmEncoder, pricingUtils);
    }

    @Override
    public void body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final var getTokenKeyWrapper = decodeGetTokenKey(input);
        tokenId = getTokenKeyWrapper.token();
        keyType = TokenProperty.valueOf(getTokenKeyWrapper.tokenKeyType().name());
    }

    @Override
    public Bytes getSuccessResultFor() {
        validateTrue(ledgers.exists(tokenId), ResponseCodeEnum.INVALID_TOKEN_ID);
        Objects.requireNonNull(keyType);
        final var key = ledgers.get(tokenId, keyType);
        validateTrue(key != null, ResponseCodeEnum.KEY_NOT_PROVIDED);
        final var evmKey = convertToEvmKey(key);
        return evmEncoder.encodeGetTokenKey(evmKey);
    }

    public static GetTokenKeyWrapper<TokenID> decodeGetTokenKey(final Bytes input) {
        final var rawGetTokenKeyWrapper = EvmGetTokenKeyPrecompile.decodeGetTokenKey(input);

        final var tokenID = convertAddressBytesToTokenID(rawGetTokenKeyWrapper.token());
        final var tokenType = rawGetTokenKeyWrapper.keyType();
        return new GetTokenKeyWrapper<>(tokenID, tokenType);
    }

    public static EvmKey convertToEvmKey(Key key) {
        final var contractId = key.getContractID().getContractNum() > 0
                ? EntityIdUtils.asTypedEvmAddress(key.getContractID())
                : EntityIdUtils.asTypedEvmAddress(ContractID.newBuilder()
                .setShardNum(0L)
                .setRealmNum(0L)
                .setContractNum(0L)
                .build());
        final var ed25519 = key.getEd25519().toByteArray();
        final var ecdsaSecp256K1 = key.getECDSASecp256K1().toByteArray();
        final var delegatableContractId = key.getDelegatableContractId().getContractNum() > 0
                ? EntityIdUtils.asTypedEvmAddress(key.getDelegatableContractId())
                : EntityIdUtils.asTypedEvmAddress(ContractID.newBuilder()
                .setShardNum(0L)
                .setRealmNum(0L)
                .setContractNum(0L)
                .build());
        return new EvmKey(contractId, ed25519, ecdsaSecp256K1, delegatableContractId);
    }
}
