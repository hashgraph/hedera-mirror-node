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
    //TODO Do we care about having this much customization?
    private final Ed25519PublicKey adminKey;
    private final Ed25519PublicKey submitKey;
    private final AccountId autoRenewAccountId;
    private final Duration autoRenewPeriod;

    @Builder.Default
    private final long maxTransactionFee = 1_000_000_000;

    @Override
    public ConsensusTopicCreateTransaction get() {
        ConsensusTopicCreateTransaction consensusTopicCreateTransaction = new ConsensusTopicCreateTransaction()
                .setMaxTransactionFee(maxTransactionFee)
                .setTopicMemo("Supplier HCS Topic Create_" + Instant.now());

        if (submitKey != null) {
            consensusTopicCreateTransaction.setSubmitKey(submitKey);
        }
        if (adminKey != null) {
            consensusTopicCreateTransaction.setAdminKey(adminKey);
        }
        if (autoRenewAccountId != null) {
            consensusTopicCreateTransaction.setAutoRenewAccountId(autoRenewAccountId);
        }
        if (autoRenewPeriod != null) {
            consensusTopicCreateTransaction.setAutoRenewPeriod(autoRenewPeriod);
        }
        return consensusTopicCreateTransaction;
    }
}
