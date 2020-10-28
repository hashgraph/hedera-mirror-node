package com.hedera.datagenerator.sdk.supplier;

import java.util.function.Supplier;

import com.hedera.hashgraph.sdk.TransactionBuilder;
import com.hedera.hashgraph.sdk.TransactionId;

public interface TransactionSupplier<T extends TransactionBuilder<TransactionId, ?, T>>
        extends Supplier<TransactionBuilder<TransactionId, ?, T>> {
}
