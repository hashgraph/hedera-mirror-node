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

package com.hedera.node.app.service.evm.contracts.execution;

import static com.hedera.mirror.web3.common.PrecompileContext.PRECOMPILE_CONTEXT;

import com.hedera.mirror.web3.common.PrecompileContext;
import com.hedera.node.app.service.evm.contracts.execution.traceability.HederaEvmOperationTracer;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmMutableWorldState;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.time.Instant;
import java.util.Map;
import javax.inject.Provider;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.processor.AbstractMessageProcessor;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;

/**
 * Stateless invariant copy of its hedera-services counterpart. It is used to process EVM transactions in
 * an asynchronous manner.
 *
 * All class fields are final and immutable and some of them moved in the execute method.
 *
 * */
public class HederaEvmTxProcessor {
    private static final int MAX_STACK_SIZE = 1024;

    protected final BlockMetaSource blockMetaSource;
    protected final HederaEvmMutableWorldState worldState;

    protected final GasCalculator gasCalculator;
    // FEATURE WORK to be covered by #3949
    protected final PricesAndFeesProvider livePricesSource;
    protected final Map<String, Provider<MessageCallProcessor>> mcps;
    protected final Map<String, Provider<ContractCreationProcessor>> ccps;
    protected final HederaEvmOperationTracer tracer;
    protected final EvmProperties dynamicProperties;

    @SuppressWarnings("java:S107")
    protected HederaEvmTxProcessor(
            final HederaEvmMutableWorldState worldState,
            final PricesAndFeesProvider livePricesSource,
            final EvmProperties dynamicProperties,
            final GasCalculator gasCalculator,
            final Map<String, Provider<MessageCallProcessor>> mcps,
            final Map<String, Provider<ContractCreationProcessor>> ccps,
            final BlockMetaSource blockMetaSource,
            final HederaEvmOperationTracer tracer) {
        this.worldState = worldState;
        this.livePricesSource = livePricesSource;
        this.dynamicProperties = dynamicProperties;
        this.gasCalculator = gasCalculator;

        this.mcps = mcps;
        this.ccps = ccps;
        this.blockMetaSource = blockMetaSource;
        this.tracer = tracer;
    }

    /**
     * Executes the {@link MessageFrame} of the EVM transaction and fills execution results into a
     * field.
     *
     * @param sender The origin {@link MutableAccount} that initiates the transaction
     * @param receiver the priority form of the receiving {@link Address} (i.e., EIP-1014 if
     *     present); or the newly created address
     * @param gasPrice GasPrice to use for gas calculations
     * @param gasLimit Externally provided gas limit
     * @param value transaction value
     * @param payload transaction payload. For Create transactions, the bytecode + constructor
     *     arguments
     * @param isStatic Whether the execution is static
     * @param mirrorReceiver the mirror form of the receiving {@link Address}; or the newly created
     *     address
     */
    @SuppressWarnings("java:S107")
    public HederaEvmTransactionProcessingResult execute(
            final HederaEvmAccount sender,
            final Address receiver,
            final long gasPrice,
            final boolean isEstimate,
            final long gasLimit,
            final long value,
            final Bytes payload,
            final boolean isStatic,
            final Address mirrorReceiver,
            final boolean contractCreation) {
        final var blockValues = blockMetaSource.computeBlockValues(gasLimit);
        final var intrinsicGas = gasCalculator.transactionIntrinsicGasCost(payload, contractCreation);
        final var gasAvailable = gasLimit - intrinsicGas;

        final var valueAsWei = Wei.of(value);
        final var updater = worldState.updater();
        final var stackedUpdater = updater.updater();
        final var senderEvmAddress = sender.canonicalAddress();
        final var precompileContext = new PrecompileContext();
        precompileContext.setEstimate(isEstimate);

        final MessageFrame.Builder commonInitialFrame = MessageFrame.builder()
                .maxStackSize(MAX_STACK_SIZE)
                .worldUpdater(stackedUpdater)
                .initialGas(gasAvailable)
                .originator(senderEvmAddress)
                .gasPrice(Wei.of(gasPrice))
                .sender(senderEvmAddress)
                .value(valueAsWei)
                .apparentValue(valueAsWei)
                .blockValues(blockValues)
                .completer(unused -> {})
                .isStatic(isStatic)
                .miningBeneficiary(dynamicProperties.fundingAccountAddress())
                .blockHashLookup(blockMetaSource::getBlockHash)
                .contextVariables(Map.of(
                        "HederaFunctionality",
                        getFunctionType(contractCreation),
                        PRECOMPILE_CONTEXT,
                        precompileContext));

        final var initialFrame = buildInitialFrame(commonInitialFrame, receiver, payload, value);
        final var messageFrameStack = initialFrame.getMessageFrameStack();

        tracer.init(initialFrame);

        final String evmVersion = dynamicProperties.evmVersion();

        while (!messageFrameStack.isEmpty()) {
            process(messageFrameStack.peekFirst(), tracer, evmVersion);
        }

        final var gasUsed = calculateGasUsedByTX(gasLimit, initialFrame);
        final var sbhRefund = updater.getSbhRefund();

        tracer.finalizeOperation(initialFrame);

        // Externalise result
        if (initialFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
            return HederaEvmTransactionProcessingResult.successful(
                    initialFrame.getLogs(), gasUsed, sbhRefund, gasPrice, initialFrame.getOutputData(), mirrorReceiver);
        } else {
            return HederaEvmTransactionProcessingResult.failed(
                    gasUsed,
                    sbhRefund,
                    gasPrice,
                    initialFrame.getRevertReason(),
                    initialFrame.getExceptionalHaltReason());
        }
    }

    protected long calculateGasUsedByTX(final long txGasLimit, final MessageFrame initialFrame) {
        long gasUsedByTransaction = txGasLimit - initialFrame.getRemainingGas();
        /* Return leftover gas */
        final long selfDestructRefund = gasCalculator.getSelfDestructRefundAmount()
                * Math.min(
                        initialFrame.getSelfDestructs().size(),
                        gasUsedByTransaction / (gasCalculator.getMaxRefundQuotient()));

        gasUsedByTransaction = gasUsedByTransaction - selfDestructRefund - initialFrame.getGasRefund();

        final var maxRefundPercent = dynamicProperties.maxGasRefundPercentage();
        gasUsedByTransaction = Math.max(gasUsedByTransaction, txGasLimit - txGasLimit * maxRefundPercent / 100);

        return gasUsedByTransaction;
    }

    protected long gasPriceTinyBarsGiven(final Instant consensusTime) {
        return livePricesSource.currentGasPrice(consensusTime, HederaFunctionality.EthereumTransaction);
    }

    protected HederaFunctionality getFunctionType(final boolean contractCreation) {
        return contractCreation ? HederaFunctionality.ContractCreate : HederaFunctionality.ContractCall;
    }

    @SuppressWarnings("java:S1172")
    protected MessageFrame buildInitialFrame(
            MessageFrame.Builder baseInitialFrame, Address to, Bytes payload, final long value) {
        return MessageFrame.builder().build();
    }

    protected void process(final MessageFrame frame, final OperationTracer operationTracer, final String evmVersion) {
        final AbstractMessageProcessor executor = getMessageProcessor(frame.getType(), evmVersion);

        executor.process(frame, operationTracer);
    }

    private AbstractMessageProcessor getMessageProcessor(final MessageFrame.Type type, final String evmVersion) {
        return switch (type) {
            case MESSAGE_CALL -> mcps.get(evmVersion).get();
            case CONTRACT_CREATION -> ccps.get(evmVersion).get();
        };
    }
}
