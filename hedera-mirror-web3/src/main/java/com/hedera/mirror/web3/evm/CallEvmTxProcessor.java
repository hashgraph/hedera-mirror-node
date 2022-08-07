package com.hedera.mirror.web3.evm;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.frame.MessageFrame.Builder;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

import com.hedera.mirror.web3.evm.properties.BlockMetaSourceProvider;
import com.hedera.mirror.web3.evm.properties.EvmProperties;
import com.hedera.mirror.web3.service.eth.AccountDto;
import com.hedera.services.transaction.TransactionProcessingResult;
import com.hedera.services.transaction.execution.EvmTxProcessor;
import com.hedera.services.transaction.store.contracts.HederaMutableWorldState;

public class CallEvmTxProcessor extends EvmTxProcessor {

    public CallEvmTxProcessor(
            SimulatedPricesSource simulatedPricesSource,
            EvmProperties configurationProperties,
            SimulatedGasCalculator gasCalculator,
            Set<Operation> hederaOperations,
            Map<String, PrecompiledContract> precompiledContractMap) {
        super(simulatedPricesSource, configurationProperties, gasCalculator, hederaOperations,
                precompiledContractMap);
    }

    public TransactionProcessingResult executeEth(
            final AccountDto sender,
            final Address receiver,
            final long providedGasLimit,
            final long value,
            final Bytes callData,
            final Instant consensusTime,
            final BigInteger userOfferedGasPrice,
            final AccountDto relayer,
            final long maxGasAllowanceInTinybars,
            final boolean isStatic) {
        final long gasPrice = gasPriceTinyBarsGiven(consensusTime, true);

        return super.execute(
                sender,
                receiver,
                gasPrice,
                providedGasLimit,
                value,
                callData,
                false,
                consensusTime,
                isStatic,
                null,
                receiver,
                userOfferedGasPrice,
                maxGasAllowanceInTinybars,
                relayer);
    }

    public void setBlockMetaSource(final BlockMetaSourceProvider blockMetaSource) {
        super.setBlockMetaSource(blockMetaSource);
    }

    public void setWorldState(final HederaMutableWorldState worldState) {
        super.setWorldState(worldState);
    }


    @Override
    protected HederaFunctionality getFunctionType() {
        return HederaFunctionality.ContractCall;
    }

    @Override
    protected MessageFrame buildInitialFrame(
            Builder baseInitialFrame,
            Address to, Bytes payload,
            long value) {
//        final var code = codeCache.getIfPresent(aliasManager.resolveForEvm(to));
        /* The ContractCallTransitionLogic would have rejected a missing or deleted
         * contract, so at this point we should have non-null bytecode available.
         * If there is no bytecode, it means we have a non-token and non-contract account,
         * hence the code should be null and there must be a value transfer.
         */
//        validateTrue(code != null || value > 0, ResponseCodeEnum.INVALID_ETHEREUM_TRANSACTION);

        return baseInitialFrame
                .type(MessageFrame.Type.MESSAGE_CALL)
                .address(to)
                .contract(to)
                .inputData(payload)
                .code(Code.EMPTY)
                .build();
    }
}
