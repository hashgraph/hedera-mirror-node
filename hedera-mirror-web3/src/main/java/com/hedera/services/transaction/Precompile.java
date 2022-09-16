package com.hedera.services.transaction;

import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.evm.frame.MessageFrame;

public interface Precompile {
    // Construct the synthetic transaction
    TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver);

    // Change the world state through the given frame
    void run(MessageFrame frame);

    default Bytes getSuccessResultFor() {
        return UInt256.valueOf(22);
    }
}
