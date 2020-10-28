package com.hedera.datagenerator.sdk.supplier.hcs;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicDeleteTransaction;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;

@Builder
@Value
public class ConsensusDeleteTopicTransactionSupplier implements TransactionSupplier<ConsensusTopicDeleteTransaction> {

    //Required
    private final ConsensusTopicId topicId;

    //Optional
    @Builder.Default
    private final long maxTransactionFee = 1_000_000_000;

    @Override
    public ConsensusTopicDeleteTransaction get() {
        return new ConsensusTopicDeleteTransaction()
                .setTopicId(topicId)
                .setMaxTransactionFee(maxTransactionFee)
                .setTransactionMemo("Supplier HCS Topic Create_" + Instant.now());
    }
}
