/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.common.domain.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.NftTransfer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class TransactionTest {

    // Test serialization to JSON to verify contract with PostgreSQL listen/notify
    @Test
    void toJson() throws Exception {
        Transaction transaction = new Transaction();
        transaction.setConsensusTimestamp(1684791152000000000L);
        transaction.setChargedTxFee(1L);
        transaction.setEntityId(EntityId.of("0.0.2", EntityType.ACCOUNT));
        transaction.setErrata(ErrataType.INSERT);
        transaction.setIndex(4);
        transaction.setInitialBalance(5L);
        transaction.setMemo(new byte[] {6, 7, 8});
        transaction.setMaxFee(9L);

        NftTransfer nftTransfer1 = new NftTransfer();
        nftTransfer1.setIsApproval(false);
        nftTransfer1.setPayerAccountId(EntityId.of("0.0.11", EntityType.ACCOUNT));
        nftTransfer1.setReceiverAccountId(EntityId.of("0.0.12", EntityType.ACCOUNT));
        nftTransfer1.setSenderAccountId(EntityId.of("0.0.13", EntityType.ACCOUNT));

        NftTransfer nftTransfer2 = new NftTransfer();
        nftTransfer2.setIsApproval(true);
        nftTransfer2.setPayerAccountId(EntityId.of("0.0.15", EntityType.ACCOUNT));
        nftTransfer2.setReceiverAccountId(EntityId.of("0.0.16", EntityType.ACCOUNT));
        nftTransfer2.setSenderAccountId(EntityId.of("0.0.17", EntityType.ACCOUNT));

        transaction.setNftTransfer(Arrays.asList(nftTransfer1, nftTransfer2));
        transaction.setNodeAccountId(EntityId.of(0, 1, 19, EntityType.ACCOUNT));
        transaction.setNonce(20);
        transaction.setParentConsensusTimestamp(21L);
        transaction.setPayerAccountId(EntityId.of("0.0.22", EntityType.ACCOUNT));
        transaction.setResult(23);
        transaction.setScheduled(false);
        transaction.setTransactionBytes(new byte[] {25, 26, 27});
        transaction.setTransactionHash(new byte[] {28, 29, 30});
        transaction.setType(31);
        transaction.setValidDurationSeconds(32L);
        transaction.setValidStartNs(33L);

        ObjectMapper objectMapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        String json = objectMapper.writeValueAsString(transaction);
        assertThat(json)
                .isEqualTo("{" + "\"consensus_timestamp\":1684791152000000000,"
                        + "\"charged_tx_fee\":1,"
                        + "\"entity_id\":2,"
                        + "\"errata\":\"INSERT\","
                        + "\"index\":4,"
                        + "\"initial_balance\":5,"
                        + "\"memo\":\"BgcI\","
                        + "\"max_fee\":9,"
                        + "\"nft_transfer\":["
                        + "{\"is_approval\":false,"
                        + "\"payer_account_id\":{\"shard_num\":0,\"realm_num\":0,\"entity_num\":11,\"type\":1},"
                        + "\"receiver_account_id\":12,"
                        + "\"sender_account_id\":13},"
                        + "{\"is_approval\":true,"
                        + "\"payer_account_id\":{\"shard_num\":0,\"realm_num\":0,\"entity_num\":15,\"type\":1},"
                        + "\"receiver_account_id\":16,"
                        + "\"sender_account_id\":17}],"
                        + "\"node_account_id\":4294967315,"
                        + "\"nonce\":20,"
                        + "\"parent_consensus_timestamp\":21,"
                        + "\"payer_account_id\":22,"
                        + "\"result\":23,"
                        + "\"scheduled\":false,"
                        + "\"transaction_bytes\":\"GRob\","
                        + "\"transaction_hash\":\"HB0e\","
                        + "\"type\":31,"
                        + "\"valid_duration_seconds\":32,"
                        + "\"valid_start_ns\":33}");
    }
}
