package com.hedera.mirror.importer.parser.record;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;

import com.hedera.mirror.importer.repository.TopicMessageRepository;

import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;

import java.util.Optional;
import javax.annotation.Resource;

import org.junit.jupiter.api.Assertions;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.domain.TransactionResult;
import com.hedera.mirror.importer.repository.ContractResultRepository;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.EntityTypeRepository;
import com.hedera.mirror.importer.repository.FileDataRepository;
import com.hedera.mirror.importer.repository.LiveHashRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;
import com.hedera.mirror.importer.repository.TransactionResultRepository;
import com.hedera.mirror.importer.util.Utility;

public class AbstractRecordFileLoggerTest extends IntegrationTest {

    @Resource
    protected TransactionRepository transactionRepository;
    @Resource
    protected EntityRepository entityRepository;
    @Resource
    protected ContractResultRepository contractResultRepository;
    @Resource
    protected RecordFileRepository recordFileRepository;
    @Resource
    protected CryptoTransferRepository cryptoTransferRepository;
    @Resource
    protected LiveHashRepository liveHashRepository;
    @Resource
    protected FileDataRepository fileDataRepository;
    @Resource
    protected TransactionResultRepository transactionResultRepository;
    @Resource
    protected EntityTypeRepository entityTypeRepository;
    @Resource
    protected TopicMessageRepository topicMessageRepository;

    @Resource
    protected RecordParserProperties parserProperties;

    protected final void assertAccount(AccountID accountId, com.hedera.mirror.importer.domain.Entities dbEntity) {
        assertThat(accountId)
                .isNotEqualTo(AccountID.getDefaultInstance())
                .extracting(AccountID::getShardNum, AccountID::getRealmNum, AccountID::getAccountNum)
                .containsExactly(dbEntity.getEntityShard(), dbEntity.getEntityRealm(), dbEntity.getEntityNum());
        assertThat(dbEntity.getEntityTypeId())
                .isEqualTo(entityTypeRepository.findByName("account").get().getId());
    }

    protected final void assertFile(FileID fileId, com.hedera.mirror.importer.domain.Entities dbEntity) {
        assertThat(fileId)
                .isNotEqualTo(FileID.getDefaultInstance())
                .extracting(FileID::getShardNum, FileID::getRealmNum, FileID::getFileNum)
                .containsExactly(dbEntity.getEntityShard(), dbEntity.getEntityRealm(), dbEntity.getEntityNum());
        assertThat(dbEntity.getEntityTypeId())
                .isEqualTo(entityTypeRepository.findByName("file").get().getId());
    }

    protected final void assertContract(ContractID contractId, com.hedera.mirror.importer.domain.Entities dbEntity) {
        assertThat(contractId)
                .isNotEqualTo(ContractID.getDefaultInstance())
                .extracting(ContractID::getShardNum, ContractID::getRealmNum, ContractID::getContractNum)
                .containsExactly(dbEntity.getEntityShard(), dbEntity.getEntityRealm(), dbEntity.getEntityNum());
        assertThat(dbEntity.getEntityTypeId())
                .isEqualTo(entityTypeRepository.findByName("contract").get().getId());
    }

    protected final void assertRecordTransfers(TransactionRecord record) {
        TransferList transferList = record.getTransferList();
        for (AccountAmount accountAmount : transferList.getAccountAmountsList()) {
            AccountID xferAccountId = accountAmount.getAccountID();
            Optional<com.hedera.mirror.importer.domain.Entities> accountId = entityRepository
                    .findByPrimaryKey(xferAccountId.getShardNum(), xferAccountId.getRealmNum(), xferAccountId
                            .getAccountNum());

            var accountNum = accountId.get().getEntityNum();
            var cryptoTransfer = cryptoTransferRepository.findByConsensusTimestampAndEntityNum(
                    Utility.timeStampInNanos(record.getConsensusTimestamp()),
                    accountNum).get();
            Assertions.assertEquals(accountAmount.getAmount(), cryptoTransfer.getAmount());
            Assertions.assertEquals(accountAmount.getAccountID().getRealmNum(), cryptoTransfer.getRealmNum());
        }
    }

    protected final void assertRecord(TransactionRecord record, Transaction dbTransaction) {
        com.hedera.mirror.importer.domain.Entities dbPayerEntity = entityRepository
                .findById(dbTransaction.getPayerAccountId()).get();
        TransactionResult dbResult = transactionResultRepository.findById(dbTransaction.getResult()).get();
        // record inputs
        assertEquals(Utility.timeStampInNanos(record.getConsensusTimestamp()), dbTransaction.getConsensusNs());
        assertEquals(record.getTransactionFee(), dbTransaction.getChargedTxFee());
        // payer
        assertAccount(record.getTransactionID().getAccountID(), dbPayerEntity);
        // transaction id
        assertEquals(Utility.timeStampInNanos(record.getTransactionID().getTransactionValidStart()), dbTransaction
                .getValidStartNs());
        // receipt
        assertEquals(record.getReceipt().getStatusValue(), dbResult.getProtoId());
        assertEquals(record.getReceipt().getStatus().getValueDescriptor().getName(), dbResult.getResult());
        assertArrayEquals(record.getTransactionHash().toByteArray(), dbTransaction.getTransactionHash());
    }

    protected final void assertTransaction(TransactionBody transactionBody,
                                           Transaction dbTransaction) {

        com.hedera.mirror.importer.domain.Entities dbNodeEntity = entityRepository
                .findById(dbTransaction.getNodeAccountId()).get();
        Entities dbPayerEntity = entityRepository.findById(dbTransaction.getPayerAccountId()).get();

        assertAll(
                () -> assertArrayEquals(transactionBody.getMemoBytes().toByteArray(), dbTransaction.getMemo())
                , () -> assertAccount(transactionBody.getNodeAccountID(), dbNodeEntity)
                , () -> assertAccount(transactionBody.getTransactionID().getAccountID(), dbPayerEntity)
                , () -> assertEquals(Utility
                        .timeStampInNanos(transactionBody.getTransactionID().getTransactionValidStart()), dbTransaction
                        .getValidStartNs())
                , () -> assertEquals(transactionBody.getTransactionValidDuration().getSeconds(), dbTransaction
                        .getValidDurationSeconds())
                , () -> assertEquals(transactionBody.getTransactionFee(), dbTransaction.getMaxFee())
                // By default the raw bytes are not stored
                , () -> assertEquals(null, dbTransaction.getTransactionBytes())
        );
    }

    protected final SignatureMap getSigMap() {
        String key1 = "11111111111111111111c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e91";
        String signature1 = "Signature 1 here";
        String key2 = "22222222222222222222c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e91";
        String signature2 = "Signature 2 here";

        SignatureMap.Builder sigMap = SignatureMap.newBuilder();
        SignaturePair.Builder sigPair = SignaturePair.newBuilder();
        sigPair.setEd25519(ByteString.copyFromUtf8(signature1));
        sigPair.setPubKeyPrefix(ByteString.copyFromUtf8(key1));

        sigMap.addSigPair(sigPair);

        sigPair = SignaturePair.newBuilder();
        sigPair.setEd25519(ByteString.copyFromUtf8(signature2));
        sigPair.setPubKeyPrefix(ByteString.copyFromUtf8(key2));

        sigMap.addSigPair(sigPair);

        return sigMap.build();
    }

    protected final Key keyFromString(String key) {
        return Key.newBuilder().setEd25519(ByteString.copyFromUtf8(key)).build();
    }

    protected final Builder defaultTransactionBodyBuilder(String memo) {

        long validDuration = 120;
        AccountID payerAccountId = AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(2).build();
        long txFee = 53968962L;
        AccountID nodeAccount = AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(3).build();

        TransactionBody.Builder body = TransactionBody.newBuilder();
        body.setTransactionFee(txFee);
        body.setMemo(memo);
        body.setNodeAccountID(nodeAccount);
        body.setTransactionID(Utility.getTransactionId(payerAccountId));
        body.setTransactionValidDuration(Duration.newBuilder().setSeconds(validDuration).build());
        return body;
    }
}
