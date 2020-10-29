package com.hedera.datagenerator.sdk.supplier.hcs;

import com.google.common.primitives.Longs;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.RandomStringUtils;

import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.consensus.ConsensusMessageSubmitTransaction;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;

@Builder
@Value
public class ConsensusSubmitMessageTransactionSupplier implements TransactionSupplier<ConsensusMessageSubmitTransaction> {

    //Required
    private final ConsensusTopicId topicId;

    //Optional
    private final String message;
    @Builder.Default
    private final int messageSize = 256;
    @Builder.Default
    private final long maxTransactionFee = 1_000_000;

    @Override
    public ConsensusMessageSubmitTransaction get() {
        return new ConsensusMessageSubmitTransaction()
                .setTopicId(topicId)
                .setMessage(message != null ? message : getMessage())
                .setTransactionMemo("Supplier HCS Topic Message_" + Instant.now())
                .setMaxTransactionFee(maxTransactionFee);
    }

    private String getMessage() {
        byte[] timeRefBytes = Longs.toByteArray(Instant.now().toEpochMilli());
        int additionalBytes = messageSize <= timeRefBytes.length ? 0 : messageSize - timeRefBytes.length;
        String randomAlphanumeric = RandomStringUtils.randomAlphanumeric(additionalBytes);
        return Base64.encodeBase64String(timeRefBytes) + randomAlphanumeric;
    }
}
