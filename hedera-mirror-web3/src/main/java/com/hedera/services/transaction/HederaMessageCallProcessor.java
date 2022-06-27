package com.hedera.services.transaction;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;

/**
 * Overrides Besu precompiler handling, so we can break model layers in Precompile execution
 */
public class HederaMessageCallProcessor extends MessageCallProcessor {
    private static final String INVALID_TRANSFER_MSG = "Transfer of Value to Hedera Precompile";
    public static final Bytes INVALID_TRANSFER = Bytes.of(INVALID_TRANSFER_MSG.getBytes(StandardCharsets.UTF_8));

    private final Map<Address, PrecompiledContract> hederaPrecompiles;

    public HederaMessageCallProcessor(
            final EVM evm,
            final PrecompileContractRegistry precompiles,
            final Map<String, PrecompiledContract> hederaPrecompileList
    ) {
        super(evm, precompiles);
        hederaPrecompiles = new HashMap<>();
        hederaPrecompileList.forEach((k, v) -> hederaPrecompiles.put(Address.fromHexString(k), v));
    }

    @Override
    public void start(final MessageFrame frame, final OperationTracer operationTracer) {
        final var hederaPrecompile = hederaPrecompiles.get(frame.getContractAddress());
        if (hederaPrecompile != null) {
            executeHederaPrecompile(hederaPrecompile, frame, operationTracer);
        } else {
            super.start(frame, operationTracer);
        }
    }

    void executeHederaPrecompile(
            final PrecompiledContract contract,
            final MessageFrame frame,
            final OperationTracer operationTracer
    ) {
        final long gasRequirement;
        final Bytes output;
        //TODO: connect precompile logic
//        if (contract instanceof HTSPrecompiledContract htsPrecompile) {
//            final var costedResult = htsPrecompile.computeCosted(frame.getInputData(), frame);
//            if (frame.getState() == REVERT) {
//                return;
//            }
//            output = costedResult.getValue();
//            gasRequirement = costedResult.getKey();
//        } else {
//            output = contract.computePrecompile(frame.getInputData(), frame).getOutput();
//            gasRequirement = contract.gasRequirement(frame.getInputData());
//        }
//        operationTracer.tracePrecompileCall(frame, gasRequirement, output);
//        if (frame.getRemainingGas() < gasRequirement) {
//            frame.decrementRemainingGas(frame.getRemainingGas());
//            frame.setExceptionalHaltReason(Optional.of(INSUFFICIENT_GAS));
//            frame.setState(EXCEPTIONAL_HALT);
//        } else if (output != null) {
//            frame.decrementRemainingGas(gasRequirement);
//            frame.setOutputData(output);
//            frame.setState(COMPLETED_SUCCESS);
//        } else {
//            frame.setState(EXCEPTIONAL_HALT);
//        }
    }
}
