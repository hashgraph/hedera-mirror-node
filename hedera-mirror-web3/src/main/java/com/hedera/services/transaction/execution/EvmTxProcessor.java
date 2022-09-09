package com.hedera.services.transaction.execution;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static org.hyperledger.besu.evm.MainnetEVMs.registerLondonOperations;

import com.hedera.services.transaction.contracts.operation.HederaSLoadOperation;
import com.hedera.services.transaction.contracts.operation.HederaSStoreOperation;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.contractvalidation.ContractValidationRule;
import org.hyperledger.besu.evm.contractvalidation.MaxCodeSizeRule;
import org.hyperledger.besu.evm.contractvalidation.PrefixCodeRule;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.precompile.MainnetPrecompiledContracts;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.processor.AbstractMessageProcessor;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;

import com.hedera.mirror.web3.evm.SimulatedGasCalculator;
import com.hedera.mirror.web3.evm.SimulatedPricesSource;
import com.hedera.mirror.web3.evm.SimulatedWorldState;
import com.hedera.mirror.web3.evm.properties.BlockMetaSourceProvider;
import com.hedera.mirror.web3.evm.properties.EvmProperties;
import com.hedera.mirror.web3.service.eth.AccountDto;
import com.hedera.services.transaction.HederaMessageCallProcessor;
import com.hedera.services.transaction.TransactionProcessingResult;
import com.hedera.services.transaction.contracts.operation.HederaBalanceOperation;
import com.hedera.services.transaction.contracts.operation.HederaSLoadOperation;
import com.hedera.services.transaction.exception.InvalidTransactionException;
import com.hedera.services.transaction.exception.ValidationUtils;
import com.hedera.services.transaction.models.Account;
import com.hedera.services.transaction.models.Id;
import com.hedera.services.transaction.store.contracts.HederaMutableWorldState;

/**
 * Abstract processor of EVM transactions that prepares the {@link EVM} and all of the peripherals upon
 * instantiation. Provides a base
 * {@link EvmTxProcessor#execute(Account, Address, long, long, long, Bytes, boolean, Instant, boolean, Address, BigInteger, long, Account)}
 * method that handles the end-to-end execution of a EVM transaction.
 */
public abstract class EvmTxProcessor {
    private static final int MAX_STACK_SIZE = 1024;
    private static final int MAX_CODE_SIZE = 0x6000;
    private static final List<ContractValidationRule> VALIDATION_RULES =
            List.of(MaxCodeSizeRule.of(MAX_CODE_SIZE), PrefixCodeRule.of());

    public static final String SBH_CONTEXT_KEY = "sbh";
    public static final String EXPIRY_ORACLE_CONTEXT_KEY = "expiryOracle";
    public static final BigInteger WEIBARS_TO_TINYBARS = BigInteger.valueOf(10_000_000_000L);

    private BlockMetaSourceProvider blockMetaSource;
    private HederaMutableWorldState worldState;

    private final SimulatedGasCalculator gasCalculator;
    private final SimulatedPricesSource simulatedPricesSource;
    private final AbstractMessageProcessor messageCallProcessor;
    private final AbstractMessageProcessor contractCreationProcessor;
    protected final EvmProperties configurationProperties;

    protected EvmTxProcessor(
            final SimulatedPricesSource simulatedPricesSource,
            final EvmProperties configurationProperties,
            final SimulatedGasCalculator gasCalculator,
            final Set<Operation> hederaOperations,
            final Map<String, PrecompiledContract> precompiledContractMap
    ) {
        this(
                null,
                simulatedPricesSource,
                configurationProperties,
                gasCalculator,
                hederaOperations,
                precompiledContractMap,
                null);
    }

    protected void setBlockMetaSource(final BlockMetaSourceProvider blockMetaSource) {
        this.blockMetaSource = blockMetaSource;
    }

    protected void setWorldState(final HederaMutableWorldState worldState) {
        this.worldState = worldState;
    }

    protected EvmTxProcessor(
            final HederaMutableWorldState worldState,
            final SimulatedPricesSource simulatedPricesSource,
            final EvmProperties configurationProperties,
            final SimulatedGasCalculator gasCalculator,
            final Set<Operation> hederaOperations,
            final Map<String, PrecompiledContract> precompiledContractMap,
            final BlockMetaSourceProvider blockMetaSource
    ) {
        this.worldState = worldState;
        this.simulatedPricesSource = simulatedPricesSource;
        this.configurationProperties = configurationProperties;
        this.gasCalculator = gasCalculator;

        var operationRegistry = new OperationRegistry();
        registerLondonOperations(operationRegistry, gasCalculator, BigInteger.valueOf(configurationProperties.getChainId()));
        operationRegistry.put(new HederaSLoadOperation(gasCalculator));
        operationRegistry.put(new HederaSStoreOperation(1, gasCalculator));
        operationRegistry.put(new HederaBalanceOperation(gasCalculator, provideAddressValidator(precompiledContractMap)));
        hederaOperations.forEach(operationRegistry::put);

        final var evm = new EVM(operationRegistry, gasCalculator, EvmConfiguration.DEFAULT);
        final var precompileContractRegistry = new PrecompileContractRegistry();
        MainnetPrecompiledContracts.populateForIstanbul(precompileContractRegistry, this.gasCalculator);

        this.messageCallProcessor = new HederaMessageCallProcessor(
                evm, precompileContractRegistry, precompiledContractMap);
        this.contractCreationProcessor = new ContractCreationProcessor(
                gasCalculator, evm, true, VALIDATION_RULES, 1);
        this.blockMetaSource = blockMetaSource;
    }

    /**
     * Executes the {@link MessageFrame} of the EVM transaction. Returns the result as {@link
     * TransactionProcessingResult}
     *
     * @param sender
     * 		The origin {@link Account} that initiates the transaction
     * @param receiver
     * 		the priority form of the receiving {@link Address} (i.e., EIP-1014 if present); or the newly created address
     * @param gasPrice
     * 		GasPrice to use for gas calculations
     * @param gasLimit
     * 		Externally provided gas limit
     * @param value
     * 		Evm transaction value (HBars)
     * @param payload
     * 		Transaction payload. For Create transactions, the bytecode + constructor arguments
     * @param contractCreation
     * 		Whether or not this is a contract creation transaction
     * @param consensusTime
     * 		Current consensus time
     * @param isStatic
     * 		Whether the execution is static
     * @param mirrorReceiver
     * 		the mirror form of the receiving {@link Address}; or the newly created address
     * @return the result of the EVM execution returned as {@link TransactionProcessingResult}
     */
    public TransactionProcessingResult execute(
            final AccountDto sender,
            final Address receiver,
            final long gasPrice,
            final long gasLimit,
            final long value,
            final Bytes payload,
            final boolean contractCreation,
            final Instant consensusTime,
            final boolean isStatic,
            final Address mirrorReceiver,
            final BigInteger userOfferedGasPrice,
            final long maxGasAllowanceInTinybars,
            final AccountDto relayer
    ) {
        final Wei gasCost = Wei.of(Math.multiplyExact(gasLimit, gasPrice));
        final Wei upfrontCost = gasCost.add(value);
        final long intrinsicGas = gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, contractCreation);

        final SimulatedWorldState.Updater updater = (SimulatedWorldState.Updater) worldState.updater();
        final var senderAccount = updater.getOrCreateSenderAccount(new Id(0, 0, sender.getNum()).asEvmAddress());
        final MutableAccount mutableSender = senderAccount.getMutable();

        MutableAccount mutableRelayer = null;
        if (relayer != null) {
            final var relayerAccount = updater.getOrCreateSenderAccount(new Id(0, 0, relayer.getNum()).asEvmAddress());
            mutableRelayer = relayerAccount.getMutable();
        }
        if (!isStatic) {
            if (intrinsicGas > gasLimit) {
                throw new InvalidTransactionException(INSUFFICIENT_GAS);
            }
            if (relayer == null) {
                final var senderCanAffordGas = mutableSender.getBalance().compareTo(upfrontCost) >= 0;
                ValidationUtils.validateTrue(senderCanAffordGas, INSUFFICIENT_PAYER_BALANCE);
                mutableSender.decrementBalance(gasCost);
            } else {
                final var gasAllowance = Wei.of(maxGasAllowanceInTinybars);
                if (userOfferedGasPrice.equals(BigInteger.ZERO)) {
                    // If sender set gas price to 0, relayer pays all the fees
                    ValidationUtils.validateTrue(gasAllowance.greaterOrEqualThan(gasCost), INSUFFICIENT_TX_FEE);
                    final var relayerCanAffordGas = mutableRelayer.getBalance().compareTo((gasCost)) >= 0;
                    ValidationUtils.validateTrue(relayerCanAffordGas, INSUFFICIENT_PAYER_BALANCE);
                    mutableRelayer.decrementBalance(gasCost);
                } else if (userOfferedGasPrice.divide(WEIBARS_TO_TINYBARS).compareTo(BigInteger.valueOf(gasPrice)) < 0) {
                    // If sender gas price < current gas price, pay the difference from gas allowance
                    var senderFee =
                            Wei.of(userOfferedGasPrice.multiply(BigInteger.valueOf(gasLimit)).divide(WEIBARS_TO_TINYBARS));
                    ValidationUtils.validateTrue(mutableSender.getBalance().compareTo(senderFee) >= 0, INSUFFICIENT_PAYER_BALANCE);
                    final var remainingFee = gasCost.subtract(senderFee);
                    ValidationUtils.validateTrue(gasAllowance.greaterOrEqualThan(remainingFee), INSUFFICIENT_TX_FEE);
                    ValidationUtils.validateTrue(mutableRelayer.getBalance().compareTo(remainingFee) >= 0, INSUFFICIENT_PAYER_BALANCE);
                    mutableSender.decrementBalance(senderFee);
                    mutableRelayer.decrementBalance(remainingFee);
                } else {
                    // If user gas price >= current gas price, sender pays all fees
                    final var senderCanAffordGas = mutableSender.getBalance().compareTo(gasCost) >= 0;
                    ValidationUtils.validateTrue(senderCanAffordGas, INSUFFICIENT_PAYER_BALANCE);
                    mutableSender.decrementBalance(gasCost);
                }
                // In any case, the sender must have sufficient balance to pay for any value sent
                final var senderCanAffordValue = mutableSender.getBalance().compareTo(Wei.of(value)) >= 0;
                ValidationUtils.validateTrue(senderCanAffordValue, INSUFFICIENT_PAYER_BALANCE);
            }
        }

        final var coinbase = Address.ZERO;
        final var blockValues = blockMetaSource.computeBlockValues(gasLimit);
        final var gasAvailable = gasLimit - intrinsicGas;
        final Deque<MessageFrame> messageFrameStack = new ArrayDeque<>();

        final var valueAsWei = Wei.of(value);
        final var stackedUpdater = updater.updater();
        final var senderEvmAddress = sender.canonicalAddress();
        final MessageFrame.Builder commonInitialFrame =
                MessageFrame.builder()
                        .messageFrameStack(messageFrameStack)
                        .maxStackSize(MAX_STACK_SIZE)
                        .worldUpdater(stackedUpdater)
                        .initialGas(gasAvailable)
                        .originator(senderEvmAddress)
                        .gasPrice(Wei.of(gasPrice))
                        .sender(senderEvmAddress)
                        .value(valueAsWei)
                        .apparentValue(valueAsWei)
                        .blockValues(blockValues)
                        .depth(0)
                        .completer(unused -> {
                        })
                        .isStatic(isStatic)
                        .miningBeneficiary(coinbase)
                        .blockHashLookup(blockMetaSource::getBlockHash)
                        .contextVariables(
                                Map.of(
                                "sbh", storageByteHoursTinyBarsGiven(consensusTime),
                                "HederaFunctionality", getFunctionType())
                        );

        final MessageFrame initialFrame = buildInitialFrame(commonInitialFrame, receiver, payload, value);
        messageFrameStack.addFirst(initialFrame);

        while (!messageFrameStack.isEmpty()) {
            process(messageFrameStack.peekFirst(), new HederaTracer());
        }

        var gasUsedByTransaction = calculateGasUsedByTX(gasLimit, initialFrame);
        final long sbhRefund = updater.getSbhRefund();
        final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges = Map.of();

        initialFrame.getSelfDestructs().forEach(updater::deleteAccount);

        // Commit top level updater
        updater.commit();

        // Externalise result
        if (initialFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
            return TransactionProcessingResult.successful(
                    initialFrame.getLogs(),
                    gasUsedByTransaction,
                    sbhRefund,
                    gasPrice,
                    initialFrame.getOutputData(),
                    mirrorReceiver,
                    stateChanges);
        } else {
            return TransactionProcessingResult.failed(
                    gasUsedByTransaction,
                    sbhRefund,
                    gasPrice,
                    initialFrame.getRevertReason(),
                    initialFrame.getExceptionalHaltReason(),
                    stateChanges);
        }
    }

    private long calculateGasUsedByTX(final long txGasLimit, final MessageFrame initialFrame) {
        long gasUsedByTransaction = txGasLimit - initialFrame.getRemainingGas();
        /* Return leftover gas */
        final long selfDestructRefund =
                gasCalculator.getSelfDestructRefundAmount() *
                        Math.min(
                                initialFrame.getSelfDestructs().size(),
                                gasUsedByTransaction / (gasCalculator.getMaxRefundQuotient()));

        gasUsedByTransaction = gasUsedByTransaction - selfDestructRefund - initialFrame.getGasRefund();

        final var maxRefundPercent = configurationProperties.getMaxGasRefundPercentage();
        gasUsedByTransaction = Math.max(gasUsedByTransaction, txGasLimit - txGasLimit * maxRefundPercent / 100);

        return gasUsedByTransaction;
    }

    protected long gasPriceTinyBarsGiven(final Instant consensusTime, boolean isEthTxn) {
        return simulatedPricesSource.currentGasPrice(consensusTime,
                isEthTxn ? HederaFunctionality.EthereumTransaction : getFunctionType());
    }

    protected long storageByteHoursTinyBarsGiven(final Instant consensusTime) {
        return simulatedPricesSource.currentGasPrice(consensusTime, getFunctionType());
    }

    protected abstract HederaFunctionality getFunctionType();

    protected abstract MessageFrame buildInitialFrame(MessageFrame.Builder baseInitialFrame, Address to, Bytes payload,
                                                      final long value);

    protected void process(final MessageFrame frame, final OperationTracer operationTracer) {
        final AbstractMessageProcessor executor = getMessageProcessor(frame.getType());

        executor.process(frame, operationTracer);
    }

    private BiPredicate<Address, MessageFrame> provideAddressValidator(
            final Map<String, PrecompiledContract> precompiledContractMap) {
        final var precompiles =
                precompiledContractMap.keySet().stream()
                        .map(Address::fromHexString)
                        .collect(Collectors.toSet());
        return (address, frame) ->
                precompiles.contains(address) || frame.getWorldUpdater().get(address) != null;
    }

    private AbstractMessageProcessor getMessageProcessor(final MessageFrame.Type type) {
        switch (type) {
            case MESSAGE_CALL:
                return messageCallProcessor;
            case CONTRACT_CREATION:
                return contractCreationProcessor;
            default:
                throw new IllegalStateException("Request for unsupported message processor type " + type);
        }
    }
}
