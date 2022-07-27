package com.hedera.services.transaction.operation;

import com.hedera.services.transaction.operation.context.EvmSigsVerifier;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.CallOperation;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import javax.inject.Inject;
import java.util.Map;
import java.util.function.BiPredicate;

public class SimulateCallOperation extends CallOperation {
    private final EvmSigsVerifier sigsVerifier;
    private final BiPredicate<Address, MessageFrame> addressValidator;
    private final Map<String, PrecompiledContract> precompiledContractMap;

    @Inject
    public SimulateCallOperation(
            final EvmSigsVerifier sigsVerifier,
            final GasCalculator gasCalculator,
            final BiPredicate<Address, MessageFrame> addressValidator,
            final Map<String, PrecompiledContract> precompiledContractMap) {
        super(gasCalculator);
        this.sigsVerifier = sigsVerifier;
        this.addressValidator = addressValidator;
        this.precompiledContractMap = precompiledContractMap;
    }

    @Override
    public Operation.OperationResult execute(final MessageFrame frame, final EVM evm) {
        return SimulateOperationUtil.addressSignatureCheckExecution(
                sigsVerifier,
                frame,
                to(frame),
                () -> cost(frame),
                () -> super.execute(frame, evm),
                addressValidator,
                precompiledContractMap);
    }
}
