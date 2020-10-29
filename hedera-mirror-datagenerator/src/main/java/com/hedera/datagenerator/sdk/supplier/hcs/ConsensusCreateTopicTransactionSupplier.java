package com.hedera.datagenerator.sdk.supplier.hcs;

import java.time.Duration;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicCreateTransaction;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;

@Builder
@Value
public class ConsensusCreateTopicTransactionSupplier implements TransactionSupplier<ConsensusTopicCreateTransaction> {

    //Optional
    private final Ed25519PublicKey adminKey;
    private final AccountId autoRenewAccountId;
    @Builder.Default
    private final Duration autoRenewPeriod = Duration.ofSeconds(8000000);
    @Builder.Default
    private final long maxTransactionFee = 1_000_000_000;

    @Override
    public ConsensusTopicCreateTransaction get() {
        ConsensusTopicCreateTransaction consensusTopicCreateTransaction = new ConsensusTopicCreateTransaction()
                .setMaxTransactionFee(maxTransactionFee)
                .setTopicMemo("Supplier HCS Topic Create_" + Instant.now());

        if (adminKey != null) {
            consensusTopicCreateTransaction
                    .setAdminKey(adminKey)
                    .setSubmitKey(adminKey);
        }
        if (autoRenewAccountId != null) {
            consensusTopicCreateTransaction
                    .setAutoRenewAccountId(autoRenewAccountId)
                    .setAutoRenewPeriod(autoRenewPeriod);
        }
        return consensusTopicCreateTransaction;
    }
}
