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

package com.hedera.services.store.contracts.precompile;

import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.RedirectTarget;
import com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.services.store.contracts.precompile.codec.ApproveForAllParams;
import com.hedera.services.store.contracts.precompile.codec.ApproveParams;
import com.hedera.services.store.contracts.precompile.codec.ERCTransferParams;
import com.hedera.services.store.contracts.precompile.codec.FunctionParam;
import com.hedera.services.store.contracts.precompile.impl.ApprovePrecompile;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityIdUtils;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class ERCPrecompiledContract extends PrecompiledContractBase {

    private final PrecompileMapperErc precompileMapperErc;

    @SuppressWarnings("java:S107")
    public ERCPrecompiledContract(
            final EvmInfrastructureFactory infrastructureFactory,
            final MirrorNodeEvmProperties evmProperties,
            final PrecompileMapperErc precompileMapperErc,
            final Store store,
            final TokenAccessor tokenAccessor,
            final PrecompilePricingUtils precompilePricingUtils) {
        super(infrastructureFactory, evmProperties, store, tokenAccessor, precompilePricingUtils);
        this.precompileMapperErc = precompileMapperErc;
    }

    void prepareComputation(Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final int functionId = input.getInt(0);

        var contractCallContext = ContractCallContext.get();
        var senderAddress = contractCallContext.getSenderAddress();
        Precompile precompile;
        switch (functionId) {
            case AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN -> {
                final var target = getRedirectTarget(input);
                final var isExplicitRedirectCall = target.massagedInput() != null;
                if (isExplicitRedirectCall) {
                    input = target.massagedInput();
                }
                final var tokenId = EntityIdUtils.tokenIdFromEvmAddress(target.token());
                final var nestedFunctionSelector = target.descriptor();
                switch (nestedFunctionSelector) {
                        // cases will be added with the addition of precompiles using redirect operations
                    case AbiConstants.ABI_ID_ERC_APPROVE -> {
                        final var isFungibleToken =
                                /* For implicit redirect call scenarios, at this point in the logic it has already been
                                 * verified that the token exists, so comfortably call ledgers.typeOf() without worrying about INVALID_TOKEN_ID.
                                 *
                                 * Explicit redirect calls, however, verify the existence of the token in RedirectPrecompile.run(), so only
                                 * call ledgers.typeOf() if the token exists.
                                 *  */
                                (!isExplicitRedirectCall
                                                || !store.getToken(target.token(), OnMissing.DONT_THROW)
                                                        .isEmptyToken())
                                        && store.getToken(target.token(), OnMissing.THROW)
                                                .isFungibleCommon();
                        Id ownerId = null;
                        if (!isFungibleToken) {
                            final var approveDecodedNftInfo =
                                    ApprovePrecompile.decodeTokenIdAndSerialNum(input.slice(24), tokenId);
                            final var serialNumber = approveDecodedNftInfo.serialNumber();
                            ownerId = store.getUniqueToken(
                                            new NftId(
                                                    tokenId.getShardNum(),
                                                    tokenId.getRealmNum(),
                                                    tokenId.getTokenNum(),
                                                    serialNumber.longValue()),
                                            OnMissing.THROW)
                                    .getOwner();
                        }
                        precompile = precompileMapperErc
                                .lookup(nestedFunctionSelector)
                                .orElseThrow();
                        contractCallContext.setTransactionBody(precompile.body(
                                input,
                                aliasResolver,
                                new ApproveParams(target.token(), senderAddress, ownerId, isFungibleToken)));
                    }
                    case AbiConstants.ABI_ID_ERC_SET_APPROVAL_FOR_ALL -> {
                        precompile = precompileMapperErc
                                .lookup(nestedFunctionSelector)
                                .orElseThrow();
                        contractCallContext.setTransactionBody(
                                precompile.body(input, aliasResolver, new ApproveForAllParams(tokenId, senderAddress)));
                    }
                    case AbiConstants.ABI_ID_ERC_TRANSFER, AbiConstants.ABI_ID_ERC_TRANSFER_FROM -> {
                        precompile = precompileMapperErc
                                .lookup(nestedFunctionSelector)
                                .orElseThrow();
                        contractCallContext.setTransactionBody(precompile.body(
                                input.slice(24),
                                aliasResolver,
                                new ERCTransferParams(nestedFunctionSelector, senderAddress, tokenAccessor, tokenId)));
                    }
                    default -> {
                        precompile = precompileMapperErc
                                .lookup(nestedFunctionSelector)
                                .orElseThrow();
                    }
                }
            }
            default -> {
                precompile = precompileMapperErc.lookup(functionId).orElseThrow();
                contractCallContext.setTransactionBody(
                        precompile.body(input, aliasResolver, new FunctionParam(functionId)));
            }
        }
        contractCallContext.setPrecompile(precompile);
        contractCallContext.setGasRequirement(defaultGas());
    }

    boolean isNestedFunctionSelectorForWrite(final Bytes input) {
        final RedirectTarget target;
        try {
            target = DescriptorUtils.getRedirectTarget(input);
        } catch (final Exception e) {
            return false;
        }
        final var nestedFunctionSelector = target.descriptor();
        return switch (nestedFunctionSelector) {
            case AbiConstants.ABI_ID_ERC_APPROVE,
                    AbiConstants.ABI_ID_ERC_TRANSFER,
                    AbiConstants.ABI_ID_ERC_TRANSFER_FROM,
                    AbiConstants.ABI_ID_ERC_SET_APPROVAL_FOR_ALL -> true;
            default -> false;
        };
    }
}
