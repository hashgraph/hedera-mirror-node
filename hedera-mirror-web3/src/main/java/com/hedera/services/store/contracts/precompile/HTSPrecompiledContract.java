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
import com.hedera.services.store.contracts.precompile.codec.CreateParams;
import com.hedera.services.store.contracts.precompile.codec.ERCTransferParams;
import com.hedera.services.store.contracts.precompile.codec.FunctionParam;
import com.hedera.services.store.contracts.precompile.codec.HrcParams;
import com.hedera.services.store.contracts.precompile.codec.TransferParams;
import com.hedera.services.store.contracts.precompile.impl.ApprovePrecompile;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

/**
 * This class is a modified thread-safe copy of HTSPrecompiledContract from hedera-services repo.
 * <p>
 * Differences with the original class: 1. Use abstraction for the state by introducing {@link Store} interface. 2. Use
 * workaround to execute read only precompiles via calling ViewExecutor and RedirectViewExecutors, thus removing the
 * need of having separate precompile classes. 3. All stateful fields are extracted into {@link ContractCallContext} and the class is converted to a singleton bean
 */
public class HTSPrecompiledContract extends PrecompiledContractBase {

    private final PrecompileMapper precompileMapper;

    @SuppressWarnings("java:S107")
    public HTSPrecompiledContract(
            final EvmInfrastructureFactory infrastructureFactory,
            final MirrorNodeEvmProperties evmProperties,
            final PrecompileMapper precompileMapper,
            final Store store,
            final TokenAccessor tokenAccessor,
            final PrecompilePricingUtils precompilePricingUtils) {
        super(infrastructureFactory, evmProperties, store, tokenAccessor, precompilePricingUtils);
        this.precompileMapper = precompileMapper;
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
                        precompile =
                                precompileMapper.lookup(nestedFunctionSelector).orElseThrow();
                        contractCallContext.setTransactionBody(precompile.body(
                                input,
                                aliasResolver,
                                new ApproveParams(target.token(), senderAddress, ownerId, isFungibleToken)));
                    }
                    case AbiConstants.ABI_ID_ERC_SET_APPROVAL_FOR_ALL -> {
                        precompile =
                                precompileMapper.lookup(nestedFunctionSelector).orElseThrow();
                        contractCallContext.setTransactionBody(
                                precompile.body(input, aliasResolver, new ApproveForAllParams(tokenId, senderAddress)));
                    }
                    case AbiConstants.ABI_ID_ERC_TRANSFER, AbiConstants.ABI_ID_ERC_TRANSFER_FROM -> {
                        precompile =
                                precompileMapper.lookup(nestedFunctionSelector).orElseThrow();
                        contractCallContext.setTransactionBody(precompile.body(
                                input.slice(24),
                                aliasResolver,
                                new ERCTransferParams(nestedFunctionSelector, senderAddress, tokenAccessor, tokenId)));
                    }
                    default -> {
                        precompile =
                                precompileMapper.lookup(nestedFunctionSelector).orElseThrow();
                        if (AbiConstants.ABI_ID_HRC_ASSOCIATE == nestedFunctionSelector
                                || AbiConstants.ABI_ID_HRC_DISSOCIATE == nestedFunctionSelector) {
                            contractCallContext.setTransactionBody(
                                    precompile.body(input, aliasResolver, new HrcParams(tokenId, senderAddress)));
                        }
                    }
                }
            }
            case AbiConstants.ABI_ID_APPROVE -> {
                precompile = precompileMapper.lookup(functionId).orElseThrow();
                contractCallContext.setTransactionBody(precompile.body(
                        input, aliasResolver, new ApproveParams(Address.ZERO, senderAddress, null, true)));
            }
            case AbiConstants.ABI_ID_APPROVE_NFT -> {
                final var approveDecodedNftInfo =
                        ApprovePrecompile.decodeTokenIdAndSerialNum(input, TokenID.getDefaultInstance());
                final var tokenID = approveDecodedNftInfo.tokenId();
                final var serialNumber = approveDecodedNftInfo.serialNumber();
                final var ownerId = store.getUniqueToken(
                                new NftId(
                                        tokenID.getShardNum(),
                                        tokenID.getRealmNum(),
                                        tokenID.getTokenNum(),
                                        serialNumber.longValue()),
                                OnMissing.THROW)
                        .getOwner();
                precompile = precompileMapper.lookup(functionId).orElseThrow();
                contractCallContext.setTransactionBody(precompile.body(
                        input, aliasResolver, new ApproveParams(Address.ZERO, senderAddress, ownerId, false)));
            }
            case AbiConstants.ABI_ID_SET_APPROVAL_FOR_ALL -> {
                precompile = precompileMapper.lookup(functionId).orElseThrow();
                contractCallContext.setTransactionBody(
                        precompile.body(input, aliasResolver, new ApproveForAllParams(null, senderAddress)));
            }
            case AbiConstants.ABI_ID_TRANSFER_TOKENS,
                    AbiConstants.ABI_ID_TRANSFER_TOKEN,
                    AbiConstants.ABI_ID_TRANSFER_NFTS,
                    AbiConstants.ABI_ID_TRANSFER_NFT,
                    AbiConstants.ABI_ID_CRYPTO_TRANSFER,
                    AbiConstants.ABI_ID_CRYPTO_TRANSFER_V2 -> {
                precompile = precompileMapper.lookup(functionId).orElseThrow();
                contractCallContext.setTransactionBody(
                        precompile.body(input, aliasResolver, new TransferParams(functionId, store::exists)));
            }
            case AbiConstants.ABI_ID_TRANSFER_FROM, AbiConstants.ABI_ID_TRANSFER_FROM_NFT -> {
                precompile = precompileMapper.lookup(functionId).orElseThrow();
                contractCallContext.setTransactionBody(precompile.body(
                        input, aliasResolver, new ERCTransferParams(functionId, senderAddress, tokenAccessor, null)));
            }
            case AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN,
                    AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_V2,
                    AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_V3,
                    AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES,
                    AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES_V2,
                    AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES_V3,
                    AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN,
                    AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_V2,
                    AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_V3,
                    AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES,
                    AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_V2,
                    AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_V3 -> {
                precompile = precompileMapper.lookup(functionId).orElseThrow();
                contractCallContext.setTransactionBody(precompile.body(
                        input,
                        aliasResolver,
                        new CreateParams(functionId, store.getAccount(senderAddress, OnMissing.DONT_THROW))));
            }
            default -> {
                precompile = precompileMapper.lookup(functionId).orElseThrow();
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
                    AbiConstants.ABI_ID_HRC_ASSOCIATE,
                    AbiConstants.ABI_ID_HRC_DISSOCIATE,
                    AbiConstants.ABI_ID_ERC_TRANSFER_FROM,
                    AbiConstants.ABI_ID_ERC_SET_APPROVAL_FOR_ALL -> true;
            default -> false;
        };
    }
}
