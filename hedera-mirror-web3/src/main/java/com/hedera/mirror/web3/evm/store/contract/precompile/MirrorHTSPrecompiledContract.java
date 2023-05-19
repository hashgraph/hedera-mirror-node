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

package com.hedera.mirror.web3.evm.store.contract.precompile;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.mirror.web3.exception.ResourceLimitException;
import com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.ViewGasCalculator;
import com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import lombok.CustomLog;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

import java.util.Optional;
import java.util.function.UnaryOperator;

import static com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason.ERROR_DECODING_PRECOMPILE_INPUT;
import static com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils.isTokenProxyRedirect;
import static com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils.isViewFunction;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;

@CustomLog
public class MirrorHTSPrecompiledContract extends EvmHTSPrecompiledContract {

    public static final PrecompileContractResult INVALID_DELEGATE = new PrecompileContractResult(
            null, true, MessageFrame.State.COMPLETED_FAILED, Optional.of(ExceptionalHaltReason.PRECOMPILE_ERROR));
    private static final Bytes STATIC_CALL_REVERT_REASON = Bytes.of("HTS precompiles are not static".getBytes());
    private static final String UNSUPPORTED_ERROR_MESSAGE = "Precompile not supported for non-static frames";
    private final MirrorNodeEvmProperties evmProperties;
    private Precompile precompile;
    private TransactionBody.Builder transactionBody;
    private long gasRequirement = 0;
    private Address senderAddress;
    private HederaEvmStackedWorldStateUpdater updater;
    private StackedStateFrames<Object> stackedStateFrames;
    private ViewGasCalculator viewGasCalculator;
    private TokenAccessor tokenAccessor;


    public MirrorHTSPrecompiledContract(final EvmInfrastructureFactory infrastructureFactory,
                                       final MirrorNodeEvmProperties evmProperties,
                                        final StackedStateFrames<Object> stackedStateFrames) {
        super(infrastructureFactory);
        this.evmProperties = evmProperties;
        this.stackedStateFrames = stackedStateFrames;
    }

    @Override
    public Pair<Long, Bytes> computeCosted(final Bytes input, final MessageFrame frame,
                                           final ViewGasCalculator viewGasCalculator,
                                           final TokenAccessor tokenAccessor) {
        this.viewGasCalculator = viewGasCalculator;
        this.tokenAccessor = tokenAccessor;

        if (frame.isStatic()) {
            if (!isTokenProxyRedirect(input) && !isViewFunction(input)) {
                frame.setRevertReason(STATIC_CALL_REVERT_REASON);
                return Pair.of(defaultGas(), null);
            }

            return super.computeCosted(
                        input,
                        frame,
                        viewGasCalculator,
                        tokenAccessor);
        }
        final var result = computePrecompile(input, frame);
        return Pair.of(gasRequirement, result.getOutput());
    }

    @NonNull
    @Override
    public PrecompileContractResult computePrecompile(final Bytes input, @NonNull final MessageFrame frame) {
        //Temporary workaround allowing eth_call to execute precompile methods in a dynamic context (non pure/view).
        //This is done by calling ViewExecutor/RedirectViewExecutor logic instead of Precompile classes.
        //After the Precompile classes are implemented, this workaround won't be needed.
        if(isViewFunction(input)) {
            final var resultFromExecutor = super.computeCosted(
                    input,
                    frame,
                    viewGasCalculator,
                    tokenAccessor);
            return resultFromExecutor == null
                    ? PrecompiledContract.PrecompileContractResult.halt(null, Optional.of(ExceptionalHaltReason.NONE))
                    : PrecompiledContract.PrecompileContractResult.success(resultFromExecutor.getRight());
        }

        if (unqualifiedDelegateDetected(frame)) {
            frame.setExceptionalHaltReason(Optional.of(ExceptionalHaltReason.PRECOMPILE_ERROR));
            return INVALID_DELEGATE;
        }
        prepareFields(frame);
        try {
            prepareComputation(input, updater::unaliased);
        } catch (InvalidTransactionException e) {
            final var haltReason = NOT_SUPPORTED.equals(e.getResponseCode())
                    ? HederaExceptionalHaltReason.NOT_SUPPORTED
                    : HederaExceptionalHaltReason.ERROR_DECODING_PRECOMPILE_INPUT;
            frame.setExceptionalHaltReason(Optional.of(haltReason));
            return PrecompileContractResult.halt(null, Optional.of(haltReason));
        }

        gasRequirement = defaultGas();
        if (this.precompile == null || this.transactionBody == null) {
            final var haltReason = Optional.of(ERROR_DECODING_PRECOMPILE_INPUT);
            frame.setExceptionalHaltReason(haltReason);
            return PrecompileContractResult.halt(null, haltReason);
        }

        final var now = frame.getBlockValues().getTimestamp();
        gasRequirement = precompile.getGasRequirement(now);
        final Bytes result = computeInternal(frame);

        return result == null
                ? PrecompiledContract.PrecompileContractResult.halt(null, Optional.of(ExceptionalHaltReason.NONE))
                : PrecompiledContract.PrecompileContractResult.success(result);
    }

    public boolean unqualifiedDelegateDetected(MessageFrame frame) {
        // if the first message frame is not a delegate, it's not a delegate
        if (!isDelegateCall(frame)) {
            return false;
        }

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

    protected Bytes computeInternal(final MessageFrame frame) {
        Bytes result;
        try {
            validateTrue(frame.getRemainingGas() >= gasRequirement, INSUFFICIENT_GAS);

            precompile.handleSentHbars(frame);
            precompile.run(frame);

            result = precompile.getSuccessResultFor();

            stackedStateFrames.top().commit();
        } catch (final ResourceLimitException e) {
            // we want to propagate ResourceLimitException, so it is handled
            // in {@code HederaEvmTxProcessor.execute()} as expected
            throw e;
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

        // This should always have a parent stacked updater
        final var parentUpdater = updater.parentUpdater();
        if (!parentUpdater.isPresent()) {
            throw new InvalidTransactionException("HTS precompile frame had no parent updater", FAIL_INVALID);
        }

        return result;
    }

    void prepareComputation(Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        this.precompile = null;
        this.transactionBody = null;

        final int functionId = input.getInt(0);
        this.gasRequirement = 0L;

        this.precompile = switch (functionId) {
            case AbiConstants.ABI_ID_CRYPTO_TRANSFER,
                    AbiConstants.ABI_ID_TRANSFER_TOKENS,
                    AbiConstants.ABI_ID_TRANSFER_TOKEN,
                    AbiConstants.ABI_ID_TRANSFER_NFTS,
                    AbiConstants.ABI_ID_TRANSFER_NFT -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_CRYPTO_TRANSFER_V2 -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_MINT_TOKEN, AbiConstants.ABI_ID_MINT_TOKEN_V2 -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_BURN_TOKEN, AbiConstants.ABI_ID_BURN_TOKEN_V2 -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_ASSOCIATE_TOKENS -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_ASSOCIATE_TOKEN -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_DISSOCIATE_TOKENS -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_DISSOCIATE_TOKEN -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_PAUSE_TOKEN -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_UNPAUSE_TOKEN -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_ALLOWANCE -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_APPROVE -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_APPROVE_NFT -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_SET_APPROVAL_FOR_ALL -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_GET_APPROVED -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_IS_APPROVED_FOR_ALL -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_IS_KYC -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_GRANT_TOKEN_KYC -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_REVOKE_TOKEN_KYC -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_WIPE_TOKEN_ACCOUNT_FUNGIBLE,
                    AbiConstants.ABI_WIPE_TOKEN_ACCOUNT_FUNGIBLE_V2 -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_WIPE_TOKEN_ACCOUNT_NFT -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_IS_FROZEN -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_FREEZE -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_UNFREEZE -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_DELETE_TOKEN -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_UPDATE_TOKEN_INFO,
                    AbiConstants.ABI_ID_UPDATE_TOKEN_INFO_V2,
                    AbiConstants.ABI_ID_UPDATE_TOKEN_INFO_V3 -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_UPDATE_TOKEN_KEYS -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_GET_TOKEN_KEY -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN -> {
                final var target = DescriptorUtils.getRedirectTarget(input);

                final var nestedFunctionSelector = target.descriptor();
                final Precompile tokenPrecompile =
                        switch (nestedFunctionSelector) {
                            case AbiConstants.ABI_ID_ERC_NAME -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
                            case AbiConstants.ABI_ID_ERC_SYMBOL -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
                            case AbiConstants.ABI_ID_ERC_DECIMALS -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
                            case AbiConstants.ABI_ID_ERC_TOTAL_SUPPLY_TOKEN -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
                            case AbiConstants.ABI_ID_ERC_BALANCE_OF_TOKEN -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
                            case AbiConstants.ABI_ID_ERC_OWNER_OF_NFT -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
                            case AbiConstants.ABI_ID_ERC_TOKEN_URI_NFT -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
                            case AbiConstants.ABI_ID_ERC_TRANSFER -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
                            case AbiConstants.ABI_ID_ERC_TRANSFER_FROM -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
                            case AbiConstants.ABI_ID_ERC_ALLOWANCE -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
                            case AbiConstants.ABI_ID_ERC_APPROVE -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
                            case AbiConstants.ABI_ID_ERC_SET_APPROVAL_FOR_ALL -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
                            case AbiConstants.ABI_ID_ERC_GET_APPROVED -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
                            case AbiConstants.ABI_ID_ERC_IS_APPROVED_FOR_ALL -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
                            default -> null;
                        };

                yield tokenPrecompile;
            }
            case AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN,
                    AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES,
                    AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN,
                    AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES,
                    AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_V2,
                    AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES_V2,
                    AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_V2,
                    AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_V2,
                    AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_V3,
                    AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES_V3,
                    AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_V3,
                    AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_V3 -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_GET_TOKEN_INFO -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_GET_FUNGIBLE_TOKEN_INFO -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_IS_TOKEN -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_GET_TOKEN_TYPE -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_GET_TOKEN_CUSTOM_FEES -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_GET_TOKEN_EXPIRY_INFO -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_UPDATE_TOKEN_EXPIRY_INFO,
                    AbiConstants.ABI_ID_UPDATE_TOKEN_EXPIRY_INFO_V2 -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_TRANSFER_FROM -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            case AbiConstants.ABI_ID_TRANSFER_FROM_NFT -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
            default -> null;};
        if (precompile != null) {
            decodeInput(input, aliasResolver);
        }
    }

    void decodeInput(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        this.transactionBody = TransactionBody.newBuilder();
        try {
            this.transactionBody = this.precompile.body(input, aliasResolver);
        } catch (final Exception e) {
            transactionBody = null;
        }
    }

    void prepareFields(final MessageFrame frame) {
        this.updater = (HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater();
        stackedStateFrames.push();

        final var unaliasedSenderAddress =
                updater.permissivelyUnaliased(frame.getSenderAddress().toArray());
        this.senderAddress = Address.wrap(Bytes.of(unaliasedSenderAddress));
    }

    private static boolean isDelegateCall(final MessageFrame frame) {
        final var contract = frame.getContractAddress();
        final var recipient = frame.getRecipientAddress();
        return !contract.equals(recipient);
    }

    private long defaultGas() {
        return evmProperties.getHtsDefaultGasCost();
    }
}
