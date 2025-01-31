/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class BlockItemTest {

    static Stream<ResponseCodeEnum> validStatuses() {
        return Stream.of(
                ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED,
                ResponseCodeEnum.SUCCESS,
                ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION);
    }

    @ParameterizedTest
    @MethodSource("validStatuses")
    void parseSuccess_whenNoParentPresentAndCorrectStatus_returnTrue(ResponseCodeEnum status) {
        TransactionResult transactionResult =
                TransactionResult.newBuilder().setStatus(status).build();

        BlockItem blockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(transactionResult)
                .transactionOutput(List.of())
                .stateChanges(List.of())
                .previous(null)
                .build();

        assertTrue(blockItem.isSuccessful());
    }

    @ParameterizedTest
    @MethodSource("validStatuses")
    void parseSuccess_withSuccessfulParentAndCorrectStatus_returnTrue(ResponseCodeEnum status) {

        BlockItem parentBlockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(
                        TransactionResult.newBuilder().setStatus(status).build())
                .transactionOutput(List.of())
                .stateChanges(List.of())
                .parent(null)
                .build();

        TransactionResult transactionResult =
                TransactionResult.newBuilder().setStatus(status).build();

        BlockItem blockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(transactionResult)
                .transactionOutput(List.of())
                .stateChanges(List.of())
                .parent(parentBlockItem)
                .build();

        assertTrue(blockItem.isSuccessful());
    }

    @Test
    void parseSuccess_parentNotSuccessful_returnFalse() {

        BlockItem parentBlockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(TransactionResult.newBuilder()
                        .setStatus(ResponseCodeEnum.INVALID_TRANSACTION)
                        .build())
                .transactionOutput(List.of())
                .stateChanges(List.of())
                .parent(null)
                .build();

        TransactionResult transactionResult = TransactionResult.newBuilder()
                .setStatus(ResponseCodeEnum.SUCCESS)
                .build();

        BlockItem blockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(transactionResult)
                .transactionOutput(List.of())
                .stateChanges(List.of())
                .parent(parentBlockItem)
                .build();

        assertFalse(blockItem.isSuccessful());
    }

    @ParameterizedTest
    @MethodSource("validStatuses")
    void parseSuccess_parentSuccessfulButStatusNotOneOfTheExpected_returnFalse(ResponseCodeEnum status) {

        BlockItem parentBlockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(
                        TransactionResult.newBuilder().setStatus(status).build())
                .transactionOutput(List.of())
                .stateChanges(List.of())
                .parent(null)
                .build();

        TransactionResult transactionResult =
                TransactionResult.newBuilder().setStatus(ResponseCodeEnum.BUSY).build();

        BlockItem blockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(transactionResult)
                .transactionOutput(List.of())
                .stateChanges(List.of())
                .parent(parentBlockItem) // Parent is successful but status is not one of the expected
                .build();

        // Assert: The block item should not be successful because the status is not one of the expected ones
        assertFalse(blockItem.isSuccessful());
    }

    @Test
    void parseParent_whenNoParent() {
        TransactionResult transactionResult = TransactionResult.newBuilder()
                .setStatus(ResponseCodeEnum.SUCCESS)
                .build();

        BlockItem blockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(transactionResult)
                .transactionOutput(List.of())
                .stateChanges(List.of())
                .previous(null)
                .build();

        // When: The parent is not set
        BlockItem parent = blockItem.parent();

        // Then: The parent should remain null
        assertNull(parent, "Parent should be null when no parent is provided");
    }

    @Test
    void parseParent_whenConsensusTimestampMatch_ParentIsPrevious() {
        TransactionResult transactionResult = TransactionResult.newBuilder()
                .setStatus(ResponseCodeEnum.SUCCESS)
                .setParentConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(12345L).build())
                .build();

        Transaction previousTransaction = Transaction.newBuilder().build();
        TransactionResult previousTransactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(12345L).build())
                .build();

        BlockItem previousBlockItem = BlockItem.builder()
                .transaction(previousTransaction)
                .transactionResult(previousTransactionResult)
                .transactionOutput(List.of())
                .stateChanges(List.of())
                .previous(null)
                .build();

        BlockItem blockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(transactionResult)
                .transactionOutput(List.of())
                .stateChanges(List.of())
                .previous(previousBlockItem)
                .build();

        // When: The parent matches the consensus timestamp of the previous block item
        BlockItem parent = blockItem.parent();

        // Then: The parent should match the previous block item
        assertEquals(
                previousBlockItem, parent, "Parent should match the previous block item based on consensus timestamp");
    }

    @Test
    void parseParent_whenConsensusTimestampDoNotMatch_NoParent() {
        // Given: Create a previous block item with a non-matching parent consensus timestamp
        TransactionResult transactionResult = TransactionResult.newBuilder()
                .setStatus(ResponseCodeEnum.SUCCESS)
                .setParentConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(12345L).build())
                .build();

        Transaction previousTransaction = Transaction.newBuilder().build();
        TransactionResult previousTransactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(67890L).build())
                .build();

        BlockItem previousBlockItem = BlockItem.builder()
                .transaction(previousTransaction)
                .transactionResult(previousTransactionResult)
                .transactionOutput(List.of())
                .stateChanges(List.of())
                .previous(null)
                .build();

        BlockItem blockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(transactionResult)
                .transactionOutput(List.of())
                .stateChanges(List.of())
                .previous(previousBlockItem)
                .build();

        // When: The parent consensus timestamp does not match the previous block item
        BlockItem parent = blockItem.parent();

        // Then: The parent should not match, return the parent as is
        assertNotEquals(
                previousBlockItem,
                parent,
                "Parent should not match the previous block item based on consensus timestamp");
    }

    @Test
    void parseParent_ConsensusTimestampMatchesOlderSibling() {
        TransactionResult parentTransactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(12345L).build()) // Parent timestamp
                .build();

        BlockItem parentBlockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(parentTransactionResult)
                .transactionOutput(List.of())
                .stateChanges(List.of())
                .previous(null)
                .build();

        TransactionResult previousTransactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(12346L).build())
                .setParentConsensusTimestamp(parentTransactionResult.getConsensusTimestamp())
                .build();

        BlockItem previousBlockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(previousTransactionResult)
                .transactionOutput(List.of())
                .stateChanges(List.of())
                .previous(parentBlockItem)
                .build();

        TransactionResult transactionResult = TransactionResult.newBuilder()
                .setParentConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(12345L).build())
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(12347L).build())
                .build();

        BlockItem blockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(transactionResult)
                .transactionOutput(List.of())
                .stateChanges(List.of())
                .previous(previousBlockItem)
                .build();

        BlockItem parent = blockItem.parent();

        assertEquals(
                parentBlockItem,
                parent,
                "Parent should match the previous block item's parent based on consensus timestamp");
    }
}
