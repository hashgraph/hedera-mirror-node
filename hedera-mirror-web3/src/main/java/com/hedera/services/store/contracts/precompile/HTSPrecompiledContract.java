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

import static com.hedera.node.app.service.evm.store.contracts.HederaEvmWorldStateTokenAccount.TOKEN_PROXY_ACCOUNT_NONCE;
import static com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils.isTokenProxyRedirect;
import static com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils.isViewFunction;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.store.contracts.precompile.PrecompileMapper.UNSUPPORTED_ERROR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.mirror.web3.evm.store.contract.precompile.HTSPrecompiledContractAdapter;
import com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases;
import com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.RedirectTarget;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.ViewGasCalculator;
import com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.services.store.contracts.precompile.codec.ApproveForAllParams;
import com.hedera.services.store.contracts.precompile.codec.ApproveParams;
import com.hedera.services.store.contracts.precompile.codec.ERCTransferParams;
import com.hedera.services.store.contracts.precompile.codec.FunctionParam;
import com.hedera.services.store.contracts.precompile.codec.HrcParams;
import com.hedera.services.store.contracts.precompile.codec.TransferParams;
import com.hedera.services.store.contracts.precompile.impl.ApprovePrecompile;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.PrecompiledContract.PrecompileContractResult;

/**
 * This class is a modified copy of HTSPrecompiledContract from hedera-services repo. Additionally, it implements an
 * adapter interface which is used by
 * {@link com.hedera.mirror.web3.evm.store.contract.precompile.MirrorHTSPrecompiledContract}. In this way once we start
 * consuming libraries like smart-contract-service it would be easier to delete the code base inside com.hedera.services package.
 *
 * Differences with the original class:
 * 1. Use abstraction for the state by introducing {@link Store} interface.
 * 2. Use workaround to execute read only precompiles via calling ViewExecutor and RedirectViewExecutors, thus removing the need of having separate precompile classes
 */
public class HTSPrecompiledContract implements HTSPrecompiledContractAdapter {

    public static final PrecompileContractResult INVALID_DELEGATE = new PrecompileContractResult(
            null, true, MessageFrame.State.COMPLETED_FAILED, Optional.of(ExceptionalHaltReason.PRECOMPILE_ERROR));
    private static final Logger log = LogManager.getLogger(HTSPrecompiledContract.class);
    private static final Bytes STATIC_CALL_REVERT_REASON = Bytes.of("HTS precompiles are not static".getBytes());

    private final MirrorNodeEvmProperties evmProperties;
    private final EvmInfrastructureFactory infrastructureFactory;
    private final PrecompileMapper precompileMapper;
    private final EvmHTSPrecompiledContract evmHTSPrecompiledContract;
    private Store store;
    private HederaEvmContractAliases mirrorNodeEvmProperties;
    private Precompile precompile;
    private long gasRequirement = 0L;
    private TransactionBody.Builder transactionBody;
    private HederaEvmStackedWorldStateUpdater updater;
    private ViewGasCalculator viewGasCalculator;
    private TokenAccessor tokenAccessor;
    private Address senderAddress;

    public HTSPrecompiledContract(
            final EvmInfrastructureFactory infrastructureFactory,
            final MirrorNodeEvmProperties evmProperties,
            final PrecompileMapper precompileMapper,
            final EvmHTSPrecompiledContract evmHTSPrecompiledContract) {
        this.infrastructureFactory = infrastructureFactory;
        this.evmProperties = evmProperties;
        this.precompileMapper = precompileMapper;
        this.evmHTSPrecompiledContract = evmHTSPrecompiledContract;
    }

    private static boolean isDelegateCall(final MessageFrame frame) {
        final var contract = frame.getContractAddress();
        final var recipient = frame.getRecipientAddress();
        return !contract.equals(recipient);
    }

    static boolean isToken(final MessageFrame frame, final Address address) {
        final var account = frame.getWorldUpdater().get(address);
        if (account != null) {
            return account.getNonce() == TOKEN_PROXY_ACCOUNT_NONCE;
        }
        return false;
    }

    @Override
    public Pair<Long, Bytes> computeCosted(
            Bytes input, MessageFrame frame, ViewGasCalculator viewGasCalculator, TokenAccessor tokenAccessor) {
        this.viewGasCalculator = viewGasCalculator;
        this.tokenAccessor = tokenAccessor;

        if (frame.isStatic()) {
            if (!isTokenProxyRedirect(input) && !isViewFunction(input)) {
                frame.setRevertReason(STATIC_CALL_REVERT_REASON);
                return Pair.of(defaultGas(), null);
            }

            return evmHTSPrecompiledContract.computeCosted(input, frame, viewGasCalculator, tokenAccessor);
        }

        /* Workaround allowing execution of read only precompile methods in a dynamic context (non pure/view).
        This is done by calling ViewExecutor/RedirectViewExecutor logic instead of Precompile classes.*/

        if ((isTokenProxyRedirect(input) || isViewFunction(input)) && !isNestedFunctionSelectorForWrite(input)) {
            return handleReadsFromDynamicContext(input, frame);
        }

        final var result = computePrecompile(input, frame);
        return Pair.of(gasRequirement, result.getOutput());
    }

    @NonNull
    public PrecompileContractResult computePrecompile(final Bytes input, @NonNull final MessageFrame frame) {
        if (unqualifiedDelegateDetected(frame)) {
            frame.setExceptionalHaltReason(Optional.of(ExceptionalHaltReason.PRECOMPILE_ERROR));
            return INVALID_DELEGATE;
        }
        prepareFields(frame);
        try {
            prepareComputation(input, updater::unaliased);
        } catch (final NoSuchElementException e) {
            final var haltReason = HederaExceptionalHaltReason.ERROR_DECODING_PRECOMPILE_INPUT;
            frame.setExceptionalHaltReason(Optional.of(haltReason));
            return PrecompileContractResult.halt(null, Optional.of(haltReason));
        }

        final var now = frame.getBlockValues().getTimestamp();
        gasRequirement = precompile.getGasRequirement(now, transactionBody, store, mirrorNodeEvmProperties);
        final Bytes result = computeInternal(frame);

        return result == null
                ? PrecompileContractResult.halt(null, Optional.of(ExceptionalHaltReason.NONE))
                : PrecompileContractResult.success(result);
    }

    public boolean unqualifiedDelegateDetected(MessageFrame frame) {
        // if the first message frame is not a delegate, it's not a delegate
        if (!isDelegateCall(frame)) {
            return false;
        }

        final var recipient = frame.getRecipientAddress();
        // but we accept delegates iff the token redirect contract calls us,
        // so if they are not a token, or on the permitted callers list, then
        // we are a delegate and we are done.
        if (isToken(frame, recipient)) {
            // make sure we have a parent calling context
            final var stack = frame.getMessageFrameStack();
            final var frames = stack.iterator();
            frames.next();
            if (!frames.hasNext()) {
                // Impossible to get here w/o a catastrophic EVM bug
                log.error("Possibly CATASTROPHIC failure - delegatecall frame had no parent");
                return false;
            }
            // If the token redirect contract was called via delegate, then it's a delegate
            return isDelegateCall(frames.next());
        }
        return true;
    }

    protected Bytes computeInternal(final MessageFrame frame) {
        Bytes result;
        try {
            validateTrue(frame.getRemainingGas() >= gasRequirement, INSUFFICIENT_GAS);

            precompile.handleSentHbars(frame);
            final var precompileResultWrapper = precompile.run(frame, transactionBody.build());

            result = precompile.getSuccessResultFor(precompileResultWrapper);
        } catch (final InvalidTransactionException e) {
            final var status = e.getResponseCode();
            result = precompile.getFailureResultFor(status);
            if (e.isReverting()) {
                frame.setState(MessageFrame.State.REVERT);
                frame.setRevertReason(e.getRevertReason());
            }
        } catch (final Exception e) {
            log.warn("Internal precompile failure", e);
            result = precompile.getFailureResultFor(FAIL_INVALID);
        }

        return result;
    }

    @SuppressWarnings({"java:S1301", "java:S3776"})
    void prepareComputation(Bytes input, final UnaryOperator<byte[]> aliasResolver) {

        final int functionId = input.getInt(0);
        switch (functionId) {
            case AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN -> {
                RedirectTarget target;
                try {
                    target = DescriptorUtils.getRedirectTarget(input);
                } catch (final Exception e) {
                    throw new InvalidTransactionException(ResponseCodeEnum.ERROR_DECODING_BYTESTRING);
                }
                var isExplicitRedirectCall = target.massagedInput() != null;
                if (isExplicitRedirectCall) {
                    input = target.massagedInput();
                }
                var tokenId = EntityIdUtils.tokenIdFromEvmAddress(target.token());
                var nestedFunctionSelector = target.descriptor();
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
                        this.precompile =
                                precompileMapper.lookup(nestedFunctionSelector).orElseThrow();
                        this.transactionBody = precompile.body(
                                input,
                                aliasResolver,
                                new ApproveParams(target.token(), senderAddress, ownerId, isFungibleToken));
                    }
                    case AbiConstants.ABI_ID_ERC_SET_APPROVAL_FOR_ALL -> {
                        this.precompile =
                                precompileMapper.lookup(nestedFunctionSelector).orElseThrow();
                        this.transactionBody =
                                precompile.body(input, aliasResolver, new ApproveForAllParams(tokenId, senderAddress));
                    }
                    case AbiConstants.ABI_ID_ERC_TRANSFER, AbiConstants.ABI_ID_ERC_TRANSFER_FROM -> {
                        this.precompile =
                                precompileMapper.lookup(nestedFunctionSelector).orElseThrow();
                        this.transactionBody = precompile.body(
                                input.slice(24),
                                aliasResolver,
                                new ERCTransferParams(nestedFunctionSelector, senderAddress, tokenAccessor, tokenId));
                    }
                    default -> {
                        this.precompile =
                                precompileMapper.lookup(nestedFunctionSelector).orElseThrow();
                        if (AbiConstants.ABI_ID_HRC_ASSOCIATE == nestedFunctionSelector
                                || AbiConstants.ABI_ID_HRC_DISSOCIATE == nestedFunctionSelector) {
                            this.transactionBody =
                                    precompile.body(input, aliasResolver, new HrcParams(tokenId, senderAddress));
                        }
                    }
                }
            }
            case AbiConstants.ABI_ID_APPROVE -> {
                this.precompile = precompileMapper.lookup(functionId).orElseThrow();
                this.transactionBody = precompile.body(
                        input, aliasResolver, new ApproveParams(Address.ZERO, senderAddress, null, true));
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
                this.precompile = precompileMapper.lookup(functionId).orElseThrow();
                this.transactionBody = precompile.body(
                        input, aliasResolver, new ApproveParams(Address.ZERO, senderAddress, ownerId, false));
            }
            case AbiConstants.ABI_ID_SET_APPROVAL_FOR_ALL -> {
                this.precompile = precompileMapper.lookup(functionId).orElseThrow();
                this.transactionBody =
                        precompile.body(input, aliasResolver, new ApproveForAllParams(null, senderAddress));
            }
            case AbiConstants.ABI_ID_TRANSFER_TOKENS,
                    AbiConstants.ABI_ID_TRANSFER_TOKEN,
                    AbiConstants.ABI_ID_TRANSFER_NFTS,
                    AbiConstants.ABI_ID_TRANSFER_NFT,
                    AbiConstants.ABI_ID_CRYPTO_TRANSFER,
                    AbiConstants.ABI_ID_CRYPTO_TRANSFER_V2 -> {
                this.precompile = precompileMapper.lookup(functionId).orElseThrow();
                this.transactionBody =
                        precompile.body(input, aliasResolver, new TransferParams(functionId, senderAddress));
            }
            case AbiConstants.ABI_ID_TRANSFER_FROM, AbiConstants.ABI_ID_TRANSFER_FROM_NFT -> {
                this.precompile = precompileMapper.lookup(functionId).orElseThrow();
                this.transactionBody = precompile.body(
                        input, aliasResolver, new ERCTransferParams(functionId, senderAddress, tokenAccessor, null));
            }
            default -> {
                this.precompile = precompileMapper.lookup(functionId).orElseThrow();
                this.transactionBody = precompile.body(input, aliasResolver, new FunctionParam(functionId));
            }
        }
        gasRequirement = defaultGas();
    }

    void prepareFields(final MessageFrame frame) {
        this.updater = (HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater();
        final var unaliasedSenderAddress =
                updater.permissivelyUnaliased(frame.getSenderAddress().toArray());
        this.senderAddress = Address.wrap(Bytes.of(unaliasedSenderAddress));
        this.store = updater.getStore();
        this.mirrorNodeEvmProperties = updater.aliases();
    }

    private Pair<Long, Bytes> handleReadsFromDynamicContext(final Bytes input, @NonNull final MessageFrame frame) {
        Pair<Long, Bytes> resultFromExecutor = Pair.of(-1L, Bytes.EMPTY);
        if (isTokenProxyRedirect(input)) {
            final var executor =
                    infrastructureFactory.newRedirectExecutor(input, frame, viewGasCalculator, tokenAccessor);
            resultFromExecutor = executor.computeCosted();

            if (resultFromExecutor.getRight() == null) {
                throw new UnsupportedOperationException(UNSUPPORTED_ERROR);
            }
        } else if (isViewFunction(input)) {
            final var executor = infrastructureFactory.newViewExecutor(input, frame, viewGasCalculator, tokenAccessor);
            resultFromExecutor = executor.computeCosted();
        }
        return resultFromExecutor;
    }

    private boolean isNestedFunctionSelectorForWrite(Bytes input) {
        RedirectTarget target;
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

    private long defaultGas() {
        return evmProperties.getHtsDefaultGasCost();
    }

    @VisibleForTesting
    Precompile getPrecompile() {
        return precompile;
    }
}
