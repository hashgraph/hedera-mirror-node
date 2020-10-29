package com.hedera.datagenerator.sdk.supplier.hcs;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.Builder;
import lombok.Value;

import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicUpdateTransaction;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;

@Builder
@Value
public class ConsensusUpdateTopicTransactionSupplier implements TransactionSupplier<ConsensusTopicUpdateTransaction> {

    //Required
    private final ConsensusTopicId topicId;

    //Optional
    private final Ed25519PublicKey adminKey;
    private final AccountId autoRenewAccountId;
    @Builder.Default
    private final Duration autoRenewPeriod = Duration.ofSeconds(8000000);
    @Builder.Default
    private final Instant expirationTime = Instant.now().plus(120, ChronoUnit.DAYS);

    @Builder.Default
    private final long maxTransactionFee = 1_000_000_000;

    @Override
    public ConsensusTopicUpdateTransaction get() {
        ConsensusTopicUpdateTransaction consensusTopicUpdateTransaction = new ConsensusTopicUpdateTransaction()
                .setTopicId(topicId)
                .setTopicMemo("Supplier HCS Topic Update_" + Instant.now())
                .setExpirationTime(expirationTime);

        if (adminKey != null) {
            consensusTopicUpdateTransaction
                    .setAdminKey(adminKey)
                    .setSubmitKey(adminKey);
        }
        if (autoRenewAccountId != null) {
            consensusTopicUpdateTransaction
                    .setAutoRenewAccountId(autoRenewAccountId)
                    .setAutoRenewPeriod(autoRenewPeriod);
        }
        return consensusTopicUpdateTransaction;
    }
}
