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
        nftTransfer1.setReceiverAccountId(10L);
        nftTransfer1.setSenderAccountId(11L);
        nftTransfer1.setSerialNumber(12L);
        nftTransfer1.setTokenId(13L);

        NftTransfer nftTransfer2 = new NftTransfer();
        nftTransfer2.setIsApproval(true);
        nftTransfer2.setReceiverAccountId(14L);
        nftTransfer2.setSenderAccountId(15L);
        nftTransfer2.setSerialNumber(16L);
        nftTransfer2.setTokenId(17L);

        transaction.setNftTransfer(Arrays.asList(nftTransfer1, nftTransfer2));
        transaction.setNodeAccountId(EntityId.of(0, 1, 18, EntityType.ACCOUNT));
        transaction.setNonce(19);
        transaction.setParentConsensusTimestamp(20L);
        transaction.setPayerAccountId(EntityId.of("0.0.21", EntityType.ACCOUNT));
        transaction.setResult(22);
        transaction.setScheduled(false);
        transaction.setTransactionBytes(new byte[] {23, 24, 25});
        transaction.setTransactionHash(new byte[] {26, 27, 28});
        transaction.setType(29);
        transaction.setValidDurationSeconds(30L);
        transaction.setValidStartNs(31L);

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
                        + "\"nft_transfer\":\"["
                        + "{\\\"is_approval\\\":false,"
                        + "\\\"receiver_account_id\\\":10,"
                        + "\\\"sender_account_id\\\":11,"
                        + "\\\"serial_number\\\":12,"
                        + "\\\"token_id\\\":13},"
                        + "{\\\"is_approval\\\":true,"
                        + "\\\"receiver_account_id\\\":14,"
                        + "\\\"sender_account_id\\\":15,"
                        + "\\\"serial_number\\\":16,"
                        + "\\\"token_id\\\":17}]\","
                        + "\"node_account_id\":4294967314,"
                        + "\"nonce\":19,"
                        + "\"parent_consensus_timestamp\":20,"
                        + "\"payer_account_id\":21,"
                        + "\"result\":22,"
                        + "\"scheduled\":false,"
                        + "\"transaction_bytes\":\"FxgZ\","
                        + "\"transaction_hash\":\"Ghsc\","
                        + "\"type\":29,"
                        + "\"valid_duration_seconds\":30,"
                        + "\"valid_start_ns\":31}");
    }
}
