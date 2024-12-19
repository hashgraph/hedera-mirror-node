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

import com.google.protobuf.GeneratedMessageV3;
import com.hedera.hapi.block.stream.output.protoc.CallContractOutput;
import com.hedera.hapi.block.stream.output.protoc.CryptoTransferOutput;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.AssessedCustomFee;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

/**
 * Generates typical protobuf request and response objects with all fields populated.
 */
@Named
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class BlockItemBuilder {

    private final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();

    public BlockItemBuilder.Builder<CryptoTransferTransactionBody.Builder> cryptoTransfer() {
        var recordItem = recordItemBuilder.cryptoTransfer().build();
        var transaction = recordItem.getTransactionBody();
        var transactionBody = transaction.toBuilder().getCryptoTransferBuilder();
        var transactionResult = TransactionResult.newBuilder()
                .setTransferList(transaction.getCryptoTransfer().getTransfers())
                .build();

        var contractCallTransactionOutput = TransactionOutput.newBuilder()
                .setContractCall(CallContractOutput.newBuilder()
                        .setContractCallResult(recordItemBuilder.contractFunctionResult())
                        .build())
                .build();
        var cryptoTransferTransactionOutput = TransactionOutput.newBuilder()
                .setCryptoTransfer(CryptoTransferOutput.newBuilder()
                        .addAssessedCustomFees(assessedCustomFees())
                        .build())
                .build();

        return new BlockItemBuilder.Builder<>(
                TransactionType.CRYPTOTRANSFER,
                transactionBody,
                transactionResult,
                List.of(contractCallTransactionOutput, cryptoTransferTransactionOutput),
                Optional.empty());
    }

    private AssessedCustomFee.Builder assessedCustomFees() {
        return AssessedCustomFee.newBuilder()
                .setAmount(1L)
                .addEffectivePayerAccountId(recordItemBuilder.accountId())
                .setFeeCollectorAccountId(recordItemBuilder.accountId())
                .setTokenId(recordItemBuilder.tokenId());
    }

    public class Builder<T extends GeneratedMessageV3.Builder<T>> {
        private final TransactionType type;
        private final T transactionBody;
        private final TransactionBody.Builder transactionBodyWrapper;
        private final List<TransactionOutput> transactionOutputs;
        private final TransactionResult transactionResult;
        private final Optional<StateChanges> stateChanges;
        private final BlockItem.BlockItemBuilder blockItemBuilder;

        private Builder(
                TransactionType type,
                T transactionBody,
                TransactionResult transactionResult,
                List<TransactionOutput> transactionOutputs,
                Optional<StateChanges> stateChanges) {
            this.blockItemBuilder = BlockItem.builder();
            this.stateChanges = stateChanges;
            this.type = type;
            this.transactionBody = transactionBody;
            this.transactionBodyWrapper = defaultTransactionBody();
            this.transactionOutputs = transactionOutputs;
            this.transactionResult = transactionResult;
        }

        private TransactionBody.Builder defaultTransactionBody() {
            return TransactionBody.newBuilder().setMemo(type.name());
        }

        private Transaction.Builder transaction() {
            return Transaction.newBuilder()
                    .setSignedTransactionBytes(SignedTransaction.newBuilder()
                            .setBodyBytes(transactionBodyWrapper.build().toByteString())
                            .build()
                            .toByteString());
        }

        public BlockItem build() {
            var field = transactionBodyWrapper.getDescriptorForType().findFieldByNumber(type.getProtoId());
            if (field != null) { // Not UNKNOWN transaction type
                transactionBodyWrapper.setField(field, transactionBody.build());
            }

            var transaction = transaction().build();
            // Clear these so that the builder can be reused and get new incremented values.
            transactionBodyWrapper.clearTransactionID();

            return blockItemBuilder
                    .transaction(transaction)
                    .transactionResult(transactionResult)
                    .transactionOutput(transactionOutputs)
                    .stateChanges(stateChanges)
                    .build();
        }
    }
}
