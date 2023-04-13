package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.codec.EncodingFacade.SUCCESS_RESULT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FEE_SUBMITTED;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.REVERT;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;

import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;

import com.hedera.services.utils.accessors.TxnAccessor;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;

public interface Precompile {
    // Construct the synthetic transaction
    TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver);

    // Customize fee charging
    long getMinimumFeeInTinybars(Timestamp consensusTime);

    default void addImplicitCostsIn(final TxnAccessor accessor) {
        // Most transaction types can compute their full Hedera fee from just an initial transaction
        // body; but
        // for a token transfer, we may need to recompute to charge for the extra work implied by
        // custom fees
    }

    // Change the world state through the given frame
    void run(MessageFrame frame);

    long getGasRequirement(long blockTimestamp);

    default void handleSentHbars(final MessageFrame frame) {
        if (!Objects.equals(Wei.ZERO, frame.getValue())) {
            frame.setRevertReason(INVALID_TRANSFER);
            frame.setState(REVERT);

            throw new InvalidTransactionException(INVALID_FEE_SUBMITTED);
        }
    }

    default List<FcAssessedCustomFee> getCustomFees() {
        return Collections.emptyList();
    }

    default Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
        return SUCCESS_RESULT;
    }

    default Bytes getFailureResultFor(final ResponseCodeEnum status) {
        return EncodingFacade.resultFrom(status);
    }

    default boolean shouldAddTraceabilityFieldsToRecord() {
        return true;
    }
}
