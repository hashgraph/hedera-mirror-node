/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.domain;

import com.hedera.hapi.block.stream.protoc.Block;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.nio.file.Files;
import java.nio.file.Paths;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

public class TempBlockItemBuilderTest {

    private final BlockItemBuilder blockItemBuilder = new BlockItemBuilder();

    @SneakyThrows
    @Test
    void cryptoTransfer() {
        // Example of block
        var path = Paths.get("/Users/edwingreene/testBlocks/000000000000000000000000000000006422.blk")
                .toAbsolutePath()
                .normalize();
        var block = Block.parseFrom(Files.readAllBytes(path));
        var cryptoTransferEventTransaction = block.getItems(10);
        var transactionBytes = cryptoTransferEventTransaction
                .getEventTransaction()
                .getApplicationTransaction()
                .toByteArray();
        var transaction = Transaction.parseFrom(transactionBytes);
        var signedTransaction = SignedTransaction.parseFrom(transaction.getSignedTransactionBytes());
        var transactionBody = TransactionBody.parseFrom(signedTransaction.getBodyBytes());
        var cryptoTransferOutput = block.getItems(13);

        var cryptotransferTransactionBlockItem =
                blockItemBuilder.cryptoTransfer().build();
        var parsedTransaction = Transaction.parseFrom(cryptotransferTransactionBlockItem
                .transaction()
                .getSignedTransactionBytes()
                .toByteArray());
    }
}
