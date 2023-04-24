package com.hedera.services.utils.accessors;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.Transaction;
import javax.inject.Inject;

public class AccessorFactory {

    @Inject
    public AccessorFactory() {
    }

    public TxnAccessor nonTriggeredTxn(final byte[] transactionBytes) throws InvalidProtocolBufferException {
        return internalSpecializedConstruction(transactionBytes, Transaction.parseFrom(transactionBytes));
    }

    public TxnAccessor triggeredTxn(
            final Transaction transaction,
            final AccountID payer,
            final ScheduleID parent,
            final boolean markThrottleExempt,
            final boolean markCongestionExempt)
            throws InvalidProtocolBufferException {
        final var subtype = constructSpecializedAccessor(transaction);
        subtype.setScheduleRef(parent);
        subtype.setPayer(payer);
        if (markThrottleExempt) {
            subtype.markThrottleExempt();
        }
        if (markCongestionExempt) {
            subtype.markCongestionExempt();
        }
        return subtype;
    }

    /**
     * Given a gRPC {@link Transaction}, returns a {@link SignedTxnAccessor} specialized to handle the transaction's
     * logical operation.
     *
     * @param transaction the gRPC transaction
     * @return a specialized accessor
     */
    public SignedTxnAccessor constructSpecializedAccessor(final Transaction transaction)
            throws InvalidProtocolBufferException {
        return internalSpecializedConstruction(transaction.toByteArray(), transaction);
    }

    private SignedTxnAccessor internalSpecializedConstruction(
            final byte[] transactionBytes, final Transaction transaction) throws InvalidProtocolBufferException {
        return SignedTxnAccessor.from(transactionBytes, transaction);
    }

    public TxnAccessor uncheckedSpecializedAccessor(final Transaction transaction) {
        try {
            return constructSpecializedAccessor(transaction);
        } catch (final InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Not a valid signed transaction");
        }
    }
}
