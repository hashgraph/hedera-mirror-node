package com.hedera.services.transaction.operation;

import com.google.common.collect.ImmutableList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;
import org.hyperledger.besu.evm.operation.AbstractOperation;
import org.hyperledger.besu.evm.operation.Operation;
import java.util.Optional;
import java.util.OptionalLong;

import static org.apache.tuweni.bytes.Bytes32.leftPad;
import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

public class SimulateLogOperation extends AbstractOperation {
    private static final Logger log = LogManager.getLogger(SimulateLogOperation.class);

    private static final Address UNRESOLVABLE_ADDRESS_STANDIN = Address.fromHexString("00000000000000000000");

    private final int numTopics;

    public SimulateLogOperation(final int numTopics, final GasCalculator gasCalculator) {
        super(0xA0 + numTopics, "LOG" + numTopics, numTopics + 2, 0, 1, gasCalculator);
        this.numTopics = numTopics;
    }

    @Override
    public Operation.OperationResult execute(final MessageFrame frame, final EVM evm) {
        final long dataLocation = clampedToLong(frame.popStackItem());
        final long numBytes = clampedToLong(frame.popStackItem());

        final long cost =
                gasCalculator().logOperationGasCost(frame, dataLocation, numBytes, numTopics);
        final OptionalLong optionalCost = OptionalLong.of(cost);
        if (frame.isStatic()) {
            return new Operation.OperationResult(
                    optionalCost, Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE));
        } else if (frame.getRemainingGas() < cost) {
            return new Operation.OperationResult(
                    optionalCost, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
        }

        final var addressOrAlias = frame.getRecipientAddress();

        //FUTURE WORK finish implementation when we introduce StackedUpdaters

//        final var updater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
//        final var aliases = updater.aliases();
//        var address = aliases.resolveForEvm(addressOrAlias);
//        if (!aliases.isMirror(address)) {
//            address = UNRESOLVABLE_ADDRESS_STANDIN;
//            log.warn("Could not resolve logger address {}", addressOrAlias);
//        }

        final Bytes data = frame.readMemory(dataLocation, numBytes);

        final ImmutableList.Builder<LogTopic> builder =
                ImmutableList.builderWithExpectedSize(numTopics);
        for (int i = 0; i < numTopics; i++) {
            builder.add(LogTopic.create(leftPad(frame.popStackItem())));
        }

//        frame.addLog(new Log(address, data, builder.build()));
        return new Operation.OperationResult(optionalCost, Optional.empty());
    }
}
