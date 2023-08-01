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

import static com.hedera.mirror.common.converter.ObjectToStringSerializer.OBJECT_MAPPER;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.NftTransfer;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

class TransactionTest {

    private static final String EXPECTED_JSON_TEMPLATE =
            """
                    {
                      "consensus_timestamp": 1684791152000000000,
                      "charged_tx_fee": 1,
                      "entity_id": 2,
                      "errata": "INSERT",
                      "index":4,
                      "initial_balance": 5,
                      "itemized_transfer": %s,
                      "memo": "BgcI",
                      "max_fee": 9,
                      "nft_transfer": %s,
                      "node_account_id": 3,
                      "nonce": 19,
                      "parent_consensus_timestamp": 20,
                      "payer_account_id": 21,
                      "result": 22,
                      "scheduled": false,
                      "transaction_bytes": "FxgZ",
                      "transaction_hash": "Ghsc",
                      "type": 29,
                      "valid_duration_seconds": 30,
                      "valid_start_ns": 31
                    }
                    """;
    private static final String EXPECTED_ITEMIZED_TRANSFER_VALUE =
            """
                    "[{\\"amount\\":-200,\\"entity_id\\":50,\\"is_approval\\":true},{\\"amount\\":200,\\"entity_id\\":51,\\"is_approval\\":false}]"
                    """;
    private static final String EXPECTED_NFT_TRANSFER_VALUE =
            """
                    "[{\\"is_approval\\":false,\\"receiver_account_id\\":10,\\"sender_account_id\\":11,\\"serial_number\\":12,\\"token_id\\":13},{\\"is_approval\\":true,\\"receiver_account_id\\":14,\\"sender_account_id\\":15,\\"serial_number\\":16,\\"token_id\\":17}]"
                    """;

    @Test
    void addItemizedTransfer() {
        var transaction = Transaction.builder().build();
        assertThat(transaction.getItemizedTransfer()).isNull();

        var domainBuilder = new DomainBuilder();
        var itemizedTransfer1 = ItemizedTransfer.builder()
                .amount(domainBuilder.id())
                .entityId(domainBuilder.entityId(EntityType.ACCOUNT))
                .isApproval(false)
                .build();
        transaction.addItemizedTransfer(itemizedTransfer1);
        assertThat(transaction.getItemizedTransfer()).containsExactly(itemizedTransfer1);

        var itemizedTransfer2 = ItemizedTransfer.builder()
                .amount(domainBuilder.id())
                .entityId(domainBuilder.entityId(EntityType.ACCOUNT))
                .isApproval(true)
                .build();
        transaction.addItemizedTransfer(itemizedTransfer2);
        assertThat(transaction.getItemizedTransfer()).containsExactly(itemizedTransfer1, itemizedTransfer2);
    }

    @Test
    void addNftTransfer() {
        var transaction = Transaction.builder().build();
        assertThat(transaction.getNftTransfer()).isNull();

        var domainBuilder = new DomainBuilder();
        var nftTransfer1 = domainBuilder.nftTransfer().get();
        transaction.addNftTransfer(nftTransfer1);
        assertThat(transaction.getNftTransfer()).containsExactly(nftTransfer1);

        var nftTransfer2 = domainBuilder.nftTransfer().get();
        transaction.addNftTransfer(nftTransfer2);
        assertThat(transaction.getNftTransfer()).containsExactly(nftTransfer1, nftTransfer2);
    }

    @Test
    void toJson() throws Exception {
        // given
        var transaction = getTransaction();
        var itemizedTransfer1 = ItemizedTransfer.builder()
                .amount(-200L)
                .entityId(EntityId.of(50, EntityType.ACCOUNT))
                .isApproval(true)
                .build();
        transaction.addItemizedTransfer(itemizedTransfer1);

        var itemizedTransfer2 = ItemizedTransfer.builder()
                .amount(200L)
                .entityId(EntityId.of(51, EntityType.ACCOUNT))
                .isApproval(false)
                .build();
        transaction.addItemizedTransfer(itemizedTransfer2);

        var nftTransfer1 = new NftTransfer();
        nftTransfer1.setIsApproval(false);
        nftTransfer1.setReceiverAccountId(EntityId.of(10L, EntityType.ACCOUNT));
        nftTransfer1.setSenderAccountId(EntityId.of(11L, EntityType.ACCOUNT));
        nftTransfer1.setSerialNumber(12L);
        nftTransfer1.setTokenId(EntityId.of(13L, EntityType.TOKEN));
        transaction.addNftTransfer(nftTransfer1);

        NftTransfer nftTransfer2 = new NftTransfer();
        nftTransfer2.setIsApproval(true);
        nftTransfer2.setReceiverAccountId(EntityId.of(14L, EntityType.ACCOUNT));
        nftTransfer2.setSenderAccountId(EntityId.of(15L, EntityType.ACCOUNT));
        nftTransfer2.setSerialNumber(16L);
        nftTransfer2.setTokenId(EntityId.of(17L, EntityType.TOKEN));
        transaction.addNftTransfer(nftTransfer2);

        // when
        String actual = OBJECT_MAPPER.writeValueAsString(transaction);

        // then
        String expected =
                String.format(EXPECTED_JSON_TEMPLATE, EXPECTED_ITEMIZED_TRANSFER_VALUE, EXPECTED_NFT_TRANSFER_VALUE);
        JSONAssert.assertEquals(expected, actual, JSONCompareMode.STRICT);
    }

    @Test
    void toJsonNullItemizedTransferAndNullNftTransfer() throws Exception {
        // given
        var transaction = getTransaction();

        // when
        String actual = OBJECT_MAPPER.writeValueAsString(transaction);

        // then
        String expected = String.format(EXPECTED_JSON_TEMPLATE, "null", "null");
        JSONAssert.assertEquals(expected, actual, JSONCompareMode.STRICT);
    }

    private Transaction getTransaction() {
        var transaction = new Transaction();
        transaction.setConsensusTimestamp(1684791152000000000L);
        transaction.setChargedTxFee(1L);
        transaction.setEntityId(EntityId.of(2L, EntityType.ACCOUNT));
        transaction.setErrata(ErrataType.INSERT);
        transaction.setIndex(4);
        transaction.setInitialBalance(5L);
        transaction.setMemo(new byte[] {6, 7, 8});
        transaction.setMaxFee(9L);
        transaction.setNodeAccountId(EntityId.of(3L, EntityType.ACCOUNT));
        transaction.setNonce(19);
        transaction.setParentConsensusTimestamp(20L);
        transaction.setPayerAccountId(EntityId.of(21L, EntityType.ACCOUNT));
        transaction.setResult(22);
        transaction.setScheduled(false);
        transaction.setTransactionBytes(new byte[] {23, 24, 25});
        transaction.setTransactionHash(new byte[] {26, 27, 28});
        transaction.setType(29);
        transaction.setValidDurationSeconds(30L);
        transaction.setValidStartNs(31L);
        return transaction;
    }
}
