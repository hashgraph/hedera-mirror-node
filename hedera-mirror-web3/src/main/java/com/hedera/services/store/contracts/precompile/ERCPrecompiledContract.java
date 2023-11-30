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

import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.google.common.annotations.VisibleForTesting;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
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
import com.hedera.services.store.contracts.precompile.impl.ApprovePrecompile;
import com.hedera.services.store.contracts.precompile.impl.AssociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.DissociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.ERCTransferPrecompile;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
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

public class ERCPrecompiledContract extends EvmHTSPrecompiledContract {

    public static final TupleType redirectType = TupleType.parse("(int32,bytes)");

    public static final PrecompileContractResult INVALID_DELEGATE = new PrecompileContractResult(
            null, true, MessageFrame.State.COMPLETED_FAILED, Optional.of(ExceptionalHaltReason.PRECOMPILE_ERROR));
    private static final Logger log = LogManager.getLogger(HTSPrecompiledContract.class);
    private static final Bytes STATIC_CALL_REVERT_REASON = Bytes.of("HTS precompiles are not static".getBytes());

    private final MirrorNodeEvmProperties evmProperties;
    private final EvmInfrastructureFactory infrastructureFactory;
    private final PrecompileMapperErc precompileMapperErc;
    private final Store store;
    private final TokenAccessor tokenAccessor;
    private final PrecompilePricingUtils precompilePricingUtils;

    @SuppressWarnings("java:S107")
    public ERCPrecompiledContract(
            final EvmInfrastructureFactory infrastructureFactory,
            final MirrorNodeEvmProperties evmProperties,
            final PrecompileMapperErc precompileMapperErc,
            final Store store,
            final TokenAccessor tokenAccessor,
            final PrecompilePricingUtils precompilePricingUtils) {
        super(infrastructureFactory);
        this.infrastructureFactory = infrastructureFactory;
        this.evmProperties = evmProperties;
        this.precompileMapperErc = precompileMapperErc;
        this.store = store;
        this.tokenAccessor = tokenAccessor;
        this.precompilePricingUtils = precompilePricingUtils;
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

    public Pair<Long, Bytes> computeCosted(
            final Bytes input,
            final MessageFrame frame,
            final ViewGasCalculator viewGasCalculator,
            final TokenAccessor tokenAccessor) {
        if (frame.isStatic()) {
            if (!isTokenProxyRedirect(input) && !isViewFunction(input)) {
                frame.setRevertReason(STATIC_CALL_REVERT_REASON);
                return Pair.of(defaultGas(), null);
            }

            return super.computeCosted(input, frame, precompilePricingUtils::computeViewFunctionGas, tokenAccessor);
        }

        /* Workaround allowing execution of read only precompile methods in a dynamic context (non pure/view).
        This is done by calling ViewExecutor/RedirectViewExecutor logic instead of Precompile classes.*/

        if ((isTokenProxyRedirect(input) || isViewFunction(input)) && !isNestedFunctionSelectorForWrite(input)) {
            return handleReadsFromDynamicContext(input, frame);
        }

        final var result = computePrecompile(input, frame);
        return Pair.of(ContractCallContext.get().getGasRequirement(), result.getOutput());
    }

    @NonNull
    public PrecompileContractResult computePrecompile(final Bytes input, @NonNull final MessageFrame frame) {
        if (unqualifiedDelegateDetected(frame)) {
            frame.setExceptionalHaltReason(Optional.of(ExceptionalHaltReason.PRECOMPILE_ERROR));
            return INVALID_DELEGATE;
        }
        prepareFields(frame);
        try {
            prepareComputation(input, ((HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater())::unaliased);
        } catch (final NoSuchElementException e) {
            final var haltReason = HederaExceptionalHaltReason.ERROR_DECODING_PRECOMPILE_INPUT;
            frame.setExceptionalHaltReason(Optional.of(haltReason));
            return PrecompileContractResult.halt(null, Optional.of(haltReason));
        }

        final var now = frame.getBlockValues().getTimestamp();

        final var contractCallContext = ContractCallContext.get();
        contractCallContext.setGasRequirement(
                contractCallContext.getPrecompile().getGasRequirement(now, contractCallContext.getTransactionBody()));
        final Bytes result = computeInternal(frame);

        return result == null
                ? PrecompileContractResult.halt(null, Optional.of(ExceptionalHaltReason.NONE))
                : PrecompileContractResult.success(result);
    }

    public boolean unqualifiedDelegateDetected(final MessageFrame frame) {
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
        ContractCallContext contractCallContext = ContractCallContext.get();
        Bytes result;
        final var precompile = contractCallContext.getPrecompile();
        try {
            precompile.handleSentHbars(frame, contractCallContext.getTransactionBody());
            validateTrue(frame.getRemainingGas() >= contractCallContext.getGasRequirement(), INSUFFICIENT_GAS);

            // if we have top level token call with estimate gas and missing sender - return empty result
            // N.B. this should be done for precompiles that depend on the sender address
            if (Address.ZERO.equals(contractCallContext.getSenderAddress())
                    && contractCallContext.isEstimate()
                    && (precompile instanceof ERCTransferPrecompile
                            || precompile instanceof ApprovePrecompile
                            || precompile instanceof AssociatePrecompile
                            || precompile instanceof DissociatePrecompile)) {
                return Bytes.EMPTY;
            }
            final var precompileResultWrapper = precompile.run(
                    frame, contractCallContext.getTransactionBody().build());

            result = precompile.getSuccessResultFor(precompileResultWrapper);

            final var inputData = frame.getInputData();
            if (inputData != null) {
                final var redirect = getRedirectTarget(inputData);
                final var isExplicitRedirect = isTokenProxyRedirect(inputData) && redirect.massagedInput() != null;
                if (isExplicitRedirect) {
                    final var signatureTuple = Tuple.of(ResponseCodeEnum.SUCCESS_VALUE, result.toArray());
                    result = Bytes.wrap(redirectType.encode(signatureTuple).array());
                }
            }
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
                // ERC AND HTS are both handled by precompileMapper
                // we need to add a check in precompile mapper lookup to be based on block number
                precompile = precompileMapperErc.lookup(functionId).orElseThrow();
                contractCallContext.setTransactionBody(
                        precompile.body(input, aliasResolver, new FunctionParam(functionId)));
            }
        }
        contractCallContext.setPrecompile(precompile);
        contractCallContext.setGasRequirement(defaultGas());
    }

    void prepareFields(final MessageFrame frame) {
        final var unaliasedSenderAddress = ((HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater())
                .permissivelyUnaliased(frame.getSenderAddress().toArray());
        ContractCallContext.get().setSenderAddress(Address.wrap(Bytes.of(unaliasedSenderAddress)));
    }

    private Pair<Long, Bytes> handleReadsFromDynamicContext(Bytes input, @NonNull final MessageFrame frame) {
        Pair<Long, Bytes> resultFromExecutor = Pair.of(-1L, Bytes.EMPTY);

        if (isTokenProxyRedirect(input)) {
            final var target = getRedirectTarget(input);
            final var isExplicitRedirectCall = target.massagedInput() != null;
            if (isExplicitRedirectCall) {
                input = target.massagedInput();
            }

            resultFromExecutor = computeUsingRedirectExecutor(input, frame);

            if (isExplicitRedirectCall) {
                final var signatureTuple = Tuple.of(
                        ResponseCodeEnum.SUCCESS_VALUE,
                        resultFromExecutor.getRight().toArray());
                final var encodedBytes =
                        Bytes.wrap(redirectType.encode(signatureTuple).array());

                resultFromExecutor = Pair.of(resultFromExecutor.getLeft(), encodedBytes);
            }
        } else if (isViewFunction(input)) {
            final var executor = infrastructureFactory.newViewExecutor(
                    input, frame, precompilePricingUtils::computeViewFunctionGas, tokenAccessor);
            resultFromExecutor = executor.computeCosted();
        }

        return resultFromExecutor;
    }

    private Pair<Long, Bytes> computeUsingRedirectExecutor(final Bytes input, final MessageFrame frame) {
        final var executor = infrastructureFactory.newRedirectExecutor(
                input, frame, precompilePricingUtils::computeViewFunctionGas, tokenAccessor);
        final var result = executor.computeCosted();

        if (result.getRight() == null) {
            throw new UnsupportedOperationException(UNSUPPORTED_ERROR);
        }

        return result;
    }

    private boolean isNestedFunctionSelectorForWrite(final Bytes input) {
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

    private static RedirectTarget getRedirectTarget(final Bytes input) {
        try {
            return DescriptorUtils.getRedirectTarget(input);
        } catch (final Exception e) {
            throw new InvalidTransactionException(ResponseCodeEnum.ERROR_DECODING_BYTESTRING);
        }
    }

    private long defaultGas() {
        return evmProperties.getHtsDefaultGasCost();
    }

    @VisibleForTesting
    Precompile getPrecompile() {
        return ContractCallContext.get().getPrecompile();
    }
}
