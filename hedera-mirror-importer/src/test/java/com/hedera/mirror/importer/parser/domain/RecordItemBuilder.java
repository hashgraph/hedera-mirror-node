package com.hedera.mirror.importer.parser.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static com.hedera.mirror.importer.domain.DomainBuilder.KEY_LENGTH_ECDSA;
import static com.hedera.mirror.importer.domain.DomainBuilder.KEY_LENGTH_ED25519;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;

import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessageV3;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.RealmID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ShardID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.inject.Named;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.util.Utility;

/**
 * Generates typical protobuf request and response objects with all fields populated.
 */
@Named
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RecordItemBuilder {

    private static final AccountID NODE = AccountID.newBuilder().setAccountNum(3).build();
    private static final RealmID REALM_ID = RealmID.getDefaultInstance();
    private static final ShardID SHARD_ID = ShardID.getDefaultInstance();
    private static final AccountID TREASURY = AccountID.newBuilder().setAccountNum(98).build();

    private final AtomicLong id = new AtomicLong(1000L);
    private final Instant now = Instant.now();
    private final SecureRandom random = new SecureRandom();

    public Builder<ContractCallTransactionBody.Builder> contractCall() {
        ContractID contractId = contractId();
        ContractCallTransactionBody.Builder transactionBody = ContractCallTransactionBody.newBuilder()
                .setAmount(5_000L)
                .setContractID(contractId)
                .setFunctionParameters(bytes(64))
                .setGas(10_000L);

        return new Builder<>(TransactionType.CONTRACTCALL, transactionBody)
                .record(r -> r.setContractCallResult(contractFunctionResult(contractId)));
    }

    public Builder<ContractCreateTransactionBody.Builder> contractCreate() {
        ContractID contractId = contractId();
        ContractCreateTransactionBody.Builder transactionBody = ContractCreateTransactionBody.newBuilder()
                .setAdminKey(key())
                .setAutoRenewPeriod(duration(30))
                .setConstructorParameters(bytes(64))
                .setFileID(fileId())
                .setGas(10_000L)
                .setInitialBalance(20_000L)
                .setMemo(text(16))
                .setNewRealmAdminKey(key())
                .setProxyAccountID(accountId())
                .setRealmID(REALM_ID)
                .setShardID(SHARD_ID);

        return new Builder<>(TransactionType.CONTRACTCREATEINSTANCE, transactionBody)
                .receipt(r -> r.setContractID(contractId))
                .record(r -> r.setContractCreateResult(contractFunctionResult(contractId)));
    }

    private ContractFunctionResult.Builder contractFunctionResult(ContractID contractId) {
        return ContractFunctionResult.newBuilder()
                .setBloom(bytes(256))
                .setContractCallResult(bytes(16))
                .setContractID(contractId)
                .addCreatedContractIDs(contractId)
                .addCreatedContractIDs(contractId())
                .setErrorMessage(text(10))
                .setGasUsed(1000L)
                .addLogInfo(ContractLoginfo.newBuilder()
                        .setBloom(bytes(256))
                        .setContractID(contractId)
                        .setData(bytes(128))
                        .addTopic(bytes(32))
                        .addTopic(bytes(32))
                        .addTopic(bytes(32))
                        .addTopic(bytes(32))
                        .build())
                .addLogInfo(ContractLoginfo.newBuilder()
                        .setBloom(bytes(256))
                        .setContractID(contractId())
                        .setData(bytes(128))
                        .addTopic(bytes(32))
                        .addTopic(bytes(32))
                        .addTopic(bytes(32))
                        .addTopic(bytes(32))
                        .build());
    }

    public Builder<TokenMintTransactionBody.Builder> tokenMint(TokenType tokenType) {
        TokenMintTransactionBody.Builder transactionBody = TokenMintTransactionBody.newBuilder().setToken(tokenId());

        if (tokenType == FUNGIBLE_COMMON) {
            transactionBody.setAmount(1000L);
        } else {
            transactionBody.addMetadata(bytes(16)).addMetadata(bytes(16));
        }

        return new Builder<>(TransactionType.TOKENMINT, transactionBody);
    }

    // Helper methods
    private AccountAmount accountAmount(AccountID accountID, long amount) {
        return AccountAmount.newBuilder().setAccountID(accountID).setAmount(amount).build();
    }

    private AccountID accountId() {
        return AccountID.newBuilder().setAccountNum(id()).build();
    }

    private ByteString bytes(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return ByteString.copyFrom(bytes);
    }

    private ContractID contractId() {
        return ContractID.newBuilder().setContractNum(id()).build();
    }

    private Duration duration(int seconds) {
        return Duration.newBuilder().setSeconds(seconds).build();
    }

    private FileID fileId() {
        return FileID.newBuilder().setFileNum(id()).build();
    }

    private long id() {
        return id.incrementAndGet();
    }

    private Key key() {
        if (id.get() % 2 == 0) {
            return Key.newBuilder().setECDSASecp256K1(bytes(KEY_LENGTH_ECDSA)).build();
        } else {
            return Key.newBuilder().setEd25519(bytes(KEY_LENGTH_ED25519)).build();
        }
    }

    public String text(int characters) {
        return RandomStringUtils.randomAlphanumeric(characters);
    }

    private Timestamp timestamp() {
        return Utility.instantToTimestamp(now.plusSeconds(id()));
    }

    private TokenID tokenId() {
        return TokenID.newBuilder().setTokenNum(id()).build();
    }

    public class Builder<T extends GeneratedMessageV3.Builder> {
        private final TransactionType type;
        private final T transactionBody;
        private final TransactionBody.Builder transactionBodyWrapper;
        private final TransactionRecord.Builder transactionRecord;
        private final AccountID payerAccountId;

        private Builder(TransactionType type, T transactionBody) {
            this.payerAccountId = accountId();
            this.type = type;
            this.transactionBody = transactionBody;
            this.transactionBodyWrapper = defaultTransactionBody();
            this.transactionRecord = defaultTransactionRecord();
        }

        public RecordItem build() {
            Transaction transaction = transaction().build();
            return new RecordItem(transaction, transactionRecord.build());
        }

        public Builder<T> receipt(Consumer<TransactionReceipt.Builder> consumer) {
            consumer.accept(transactionRecord.getReceiptBuilder());
            return this;
        }

        public Builder<T> record(Consumer<TransactionRecord.Builder> consumer) {
            consumer.accept(transactionRecord);
            return this;
        }

        public Builder<T> status(ResponseCodeEnum status) {
            transactionRecord.getReceiptBuilder().setStatus(status);
            return this;
        }

        public Builder<T> transactionBody(Consumer<T> consumer) {
            consumer.accept(transactionBody);
            return this;
        }

        public Builder<T> transactionBodyWrapper(Consumer<TransactionBody.Builder> consumer) {
            consumer.accept(transactionBodyWrapper);
            return this;
        }

        private SignatureMap.Builder defaultSignatureMap() {
            return SignatureMap.newBuilder()
                    .addSigPair(SignaturePair.newBuilder()
                            .setEd25519(bytes(32))
                            .setPubKeyPrefix(bytes(16)));
        }

        private TransactionBody.Builder defaultTransactionBody() {
            TransactionBody.Builder transactionBody = TransactionBody.newBuilder()
                    .setMemo(type.name())
                    .setNodeAccountID(NODE)
                    .setTransactionFee(100L)
                    .setTransactionID(Utility.getTransactionId(payerAccountId))
                    .setTransactionValidDuration(duration(120));

            var field = transactionBody.getDescriptorForType().findFieldByNumber(type.getProtoId());
            transactionBody.setField(field, this.transactionBody.build());

            return transactionBody;
        }

        private TransactionRecord.Builder defaultTransactionRecord() {
            TransactionRecord.Builder transactionRecord = TransactionRecord.newBuilder()
                    .setConsensusTimestamp(timestamp())
                    .setMemoBytes(ByteString.copyFromUtf8(transactionBodyWrapper.getMemo()))
                    .setTransactionFee(transactionBodyWrapper.getTransactionFee())
                    .setTransactionHash(bytes(48))
                    .setTransactionID(transactionBodyWrapper.getTransactionID())
                    .setTransferList(TransferList.newBuilder()
                            .addAccountAmounts(accountAmount(payerAccountId, -3000L))
                            .addAccountAmounts(accountAmount(NODE, 1000L))
                            .addAccountAmounts(accountAmount(TREASURY, 2000L))
                            .build());
            transactionRecord.getReceiptBuilder().setStatus(ResponseCodeEnum.SUCCESS);
            return transactionRecord;
        }

        private Transaction.Builder transaction() {
            return Transaction.newBuilder()
                    .setSignedTransactionBytes(SignedTransaction.newBuilder()
                            .setBodyBytes(transactionBodyWrapper.build().toByteString())
                            .setSigMap(defaultSignatureMap())
                            .build()
                            .toByteString()
                    );
        }
    }
}
