package com.hedera.mirror.importer.domain;

import com.hedera.mirror.common.domain.transaction.TransactionHash;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionHashTest {


    @Test
    void testShardCalculation() {
        byte [] bytes = new byte[3];
        bytes[0] = (byte)0x81;
        bytes[1] = 0x22;
        bytes[2] = 0x25;

        TransactionHash.TransactionHashBuilder builder = TransactionHash.builder()
                .consensusTimestamp(1)
                .payerAccountId(5)
                .hash(bytes);

        assertThat(builder.build().calculateV1Shard()).isEqualTo(1);

        bytes[0] = 0x25;
        assertThat(builder.build().calculateV1Shard()).isEqualTo(5);
    }
}
