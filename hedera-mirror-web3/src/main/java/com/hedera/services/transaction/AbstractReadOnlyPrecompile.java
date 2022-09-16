package com.hedera.services.transaction;

import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import java.util.function.UnaryOperator;

public abstract class AbstractReadOnlyPrecompile implements Precompile {
    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        return null;
    }

    @Override
    public void run(final MessageFrame frame) {
        // No changes to state to apply
    }
}
