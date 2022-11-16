package com.hedera.mirror.web3.evm;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.time.Instant;
import java.util.Map;
import javax.inject.Provider;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;

import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.services.evm.accounts.HederaEvmContractAliases;
import com.hedera.services.evm.contracts.execution.BlockMetaSource;
import com.hedera.services.evm.contracts.execution.EvmProperties;
import com.hedera.services.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.services.evm.contracts.execution.HederaEvmTxProcessor;
import com.hedera.services.evm.contracts.execution.PricesAndFeesProvider;
import com.hedera.services.evm.store.contracts.AbstractCodeCache;
import com.hedera.services.evm.store.contracts.HederaEvmEntityAccess;
import com.hedera.services.evm.store.contracts.HederaEvmMutableWorldState;
import com.hedera.services.evm.store.models.HederaEvmAccount;

public class MirrorEvmTxProcessor extends HederaEvmTxProcessor {

    private final AbstractCodeCache codeCache;
    private final HederaEvmContractAliases aliasManager;

    public MirrorEvmTxProcessor(
            final HederaEvmMutableWorldState worldState,
            final PricesAndFeesProvider pricesAndFeesProvider,
            final EvmProperties dynamicProperties,
            final GasCalculator gasCalculator,
            final Map<String, Provider<MessageCallProcessor>> mcps,
            final Map<String, Provider<ContractCreationProcessor>> ccps,
            final BlockMetaSource blockMetaSource,
            final HederaEvmContractAliases aliasManager,
            final HederaEvmEntityAccess evmEntityAccess
    ) {
        super(worldState, pricesAndFeesProvider, dynamicProperties, gasCalculator, mcps, ccps,
                blockMetaSource);
        this.aliasManager = aliasManager;
        this.codeCache = new AbstractCodeCache(10, evmEntityAccess);
    }

    public HederaEvmTransactionProcessingResult execute(
            final HederaEvmAccount sender,
            final Address receiver,
            final long providedGasLimit,
            final long value,
            final Bytes callData,
            final Instant consensusTime,
            final boolean isStatic) {
        final long gasPrice = gasPriceTinyBarsGiven(consensusTime, false);

        return super.execute(
                sender,
                receiver,
                gasPrice,
                providedGasLimit,
                value,
                callData,
                false,
                isStatic,
                aliasManager.resolveForEvm(receiver)
        );
    }

    @Override
    protected HederaFunctionality getFunctionType() {
        return HederaFunctionality.ContractCall;
    }

    @Override
    protected MessageFrame buildInitialFrame(final MessageFrame.Builder baseInitialFrame, final Address to,
            final Bytes payload, long value) {
        final var code = codeCache.getIfPresent(aliasManager.resolveForEvm(to));
        /* The ContractCallTransitionLogic would have rejected a missing or deleted
         * contract, so at this point we should have non-null bytecode available.
         * If there is no bytecode, it means we have a non-token and non-contract account,
         * hence the code should be null and there must be a value transfer.
         */
        validateTrue(code != null || value > 0, ResponseCodeEnum.INVALID_ETHEREUM_TRANSACTION);

        return baseInitialFrame
                .type(MessageFrame.Type.MESSAGE_CALL)
                .address(to)
                .contract(to)
                .inputData(payload)
                .code(code == null ? Code.EMPTY : code)
                .build();
    }

    public static void validateTrue(final boolean flag, final ResponseCodeEnum code) {
        if (!flag) {
            throw new InvalidTransactionException(code);
        }
    }
}
