package com.hedera.services.transaction.context;

import com.hedera.services.transaction.utils.accessors.TxnAccessor;

/**
 * Defines a type that manages transaction-specific context for a node. (That is,
 * context built while processing a consensus transaction.) Most of this context
 * is ultimately captured by a {@link ExpirableTxnRecord}, so the core
 * responsibility of this type is to construct an appropriate record in method
 * {@code recordSoFar}.
 */
public interface TransactionContext {

    /**
     * Gets an accessor to the defined type {@link SignedTxnAccessor}
     * currently being processed.
     *
     * @return accessor for the current txn.
     */
    TxnAccessor accessor();
}
