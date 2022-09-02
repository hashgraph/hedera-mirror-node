package com.hedera.services.transaction;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FEE_SUBMITTED;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.REVERT;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;

public interface Precompile {
    // Construct the synthetic transaction
    TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver);

    // Customize fee charging
    long getMinimumFeeInTinybars(Timestamp consensusTime);

    default boolean shouldAddTraceabilityFieldsToRecord() {
        return true;
    }

    default Bytes getSuccessResultFor() {
        return UInt256.valueOf(22);
    }
}
