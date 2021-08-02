package com.hedera.mirror.importer.parser.record.entity;

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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.TransactionSignature;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.parser.CommonParserProperties;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.parser.record.NonFeeTransferExtractionStrategy;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandler;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandlerFactory;
import com.hedera.mirror.importer.repository.FileDataRepository;
import com.hedera.mirror.importer.util.Utility;

@ExtendWith(MockitoExtension.class)
class TransactionSignatureTest {

    private static final long CONSENSUS_TIMESTAMP = 10L;
    private static final EntityId ENTITY_ID = EntityId.of("0.0.123", EntityTypeEnum.UNKNOWN);

    @Mock
    private AddressBookService addressBookService;

    @Mock
    private EntityListener entityListener;

    @Mock
    private NonFeeTransferExtractionStrategy nonFeeTransferExtractionStrategy;

    @Mock
    private TransactionHandler transactionHandler;

    @Mock
    private TransactionHandlerFactory transactionHandlerFactory;

    @Mock
    private FileDataRepository fileDataRepository;

    private SignatureMap.Builder defaultSignatureMap;

    private List<TransactionSignature> defaultTransactionSignatures;

    private EntityRecordItemListener entityRecordItemListener;

    private Set<TransactionTypeEnum> transactionSignatures;

    @BeforeEach
    void setup() {
        CommonParserProperties commonParserProperties = new CommonParserProperties();
        EntityProperties entityProperties = new EntityProperties();
        entityRecordItemListener = new EntityRecordItemListener(commonParserProperties, entityProperties,
                addressBookService, nonFeeTransferExtractionStrategy, entityListener, transactionHandlerFactory,
                fileDataRepository);
        defaultSignatureMap = getDefaultSignatureMap();
        defaultTransactionSignatures = defaultSignatureMap.getSigPairList()
                .stream()
                .map(pair -> {
                    TransactionSignature transactionSignature = new TransactionSignature();
                    transactionSignature.setId(new TransactionSignature.Id(
                            CONSENSUS_TIMESTAMP,
                            pair.getPubKeyPrefix().toByteArray())
                    );
                    transactionSignature.setEntityId(ENTITY_ID);
                    transactionSignature.setSignature(pair.getEd25519().toByteArray());
                    return transactionSignature;
                })
                .collect(Collectors.toList());
        transactionSignatures = entityProperties.getPersist().getTransactionSignatures();

        doReturn(ENTITY_ID).when(transactionHandler).getEntity(any(RecordItem.class));
        doReturn(transactionHandler).when(transactionHandlerFactory).create(any(TransactionBody.class));
    }

    @Test
    void nonEd25519Signature() {
        transactionSignatures.add(TransactionTypeEnum.CONSENSUSSUBMITMESSAGE);

        SignatureMap signatureMap = defaultSignatureMap
                .addSigPair(SignaturePair.newBuilder()
                        .setPubKeyPrefix(ByteString.copyFromUtf8("RSA3072PubKey"))
                        .setRSA3072(ByteString.copyFromUtf8("RSA3072Signature"))
                        .build())
                .build();
        RecordItem recordItem = getRecordItem(TransactionTypeEnum.CONSENSUSSUBMITMESSAGE, SUCCESS, signatureMap);

        assertThrows(ImporterException.class, () -> entityRecordItemListener.onItem(recordItem));
    }

    @ParameterizedTest
    @EnumSource(value = TransactionTypeEnum.class, names = "UNKNOWN", mode = EXCLUDE)
    void transactionSignatureEnabled(TransactionTypeEnum transactionType) {
        transactionSignatures.clear();
        transactionSignatures.add(transactionType);

        RecordItem recordItem = getRecordItem(transactionType, SUCCESS);
        entityRecordItemListener.onItem(recordItem);

        assertTransactionSignatures(defaultTransactionSignatures);
    }

    @ParameterizedTest
    @EnumSource(value = TransactionTypeEnum.class, names = "UNKNOWN", mode = EXCLUDE)
    void transactionSignatureDisabled(TransactionTypeEnum transactionType) {
        transactionSignatures.clear();
        transactionSignatures.addAll(EnumSet.complementOf(EnumSet.of(transactionType)));

        RecordItem recordItem = getRecordItem(transactionType, SUCCESS);
        entityRecordItemListener.onItem(recordItem);

        assertTransactionSignatures(Collections.emptyList());
    }

    @ParameterizedTest
    @EnumSource(value = TransactionTypeEnum.class, names = "UNKNOWN", mode = EXCLUDE)
    void transactionSignatureFailedTransaction(TransactionTypeEnum transactionType) {
        transactionSignatures.addAll(EnumSet.allOf(TransactionTypeEnum.class));

        RecordItem recordItem = getRecordItem(transactionType, FAIL_FEE);
        entityRecordItemListener.onItem(recordItem);

        assertTransactionSignatures(Collections.emptyList());
    }

    @ParameterizedTest
    @MethodSource("provideDefaultTransactionSignatures")
    void transactionSignatureDefault(TransactionTypeEnum transactionType) {
        RecordItem recordItem = getRecordItem(transactionType, SUCCESS);
        entityRecordItemListener.onItem(recordItem);

        assertTransactionSignatures(defaultTransactionSignatures);
    }

    private static Stream<Arguments> provideDefaultTransactionSignatures() {
        return new EntityProperties().getPersist().getTransactionSignatures()
                .stream()
                .map(Arguments::of);
    }

    private SignatureMap.Builder getDefaultSignatureMap() {
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
        return sigMap;
    }

    private RecordItem getRecordItem(TransactionTypeEnum transactionType, ResponseCodeEnum responseCode) {
        return getRecordItem(transactionType, responseCode, defaultSignatureMap.build());
    }

    private RecordItem getRecordItem(TransactionTypeEnum transactionType, ResponseCodeEnum responseCode,
                                     SignatureMap signatureMap) {
        TransactionBody transactionBody = getTransactionBody(transactionType);
        Transaction transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(
                        SignedTransaction.newBuilder()
                                .setBodyBytes(transactionBody.toByteString())
                                .setSigMap(signatureMap)
                                .build()
                                .toByteString()
                )
                .build();
        TransactionRecord transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(Utility.instantToTimestamp(Instant.ofEpochSecond(0, CONSENSUS_TIMESTAMP)))
                .setReceipt(TransactionReceipt.newBuilder().setStatus(responseCode).build())
                .build();
        return new RecordItem(transaction, transactionRecord);
    }

    private TransactionBody getTransactionBody(TransactionTypeEnum transactionType) {
        TransactionBody.Builder builder = TransactionBody.newBuilder();
        Descriptors.Descriptor descriptor = TransactionBody.getDescriptor();
        Descriptors.FieldDescriptor fieldDescriptor = descriptor.findFieldByNumber(transactionType.getProtoId());
        Message defaultInstance = builder.getFieldBuilder(fieldDescriptor).getDefaultInstanceForType();
        return TransactionBody.newBuilder().setField(fieldDescriptor, defaultInstance).build();
    }

    private void assertTransactionSignatures(List<TransactionSignature> expected) {
        ArgumentCaptor<TransactionSignature> captor = ArgumentCaptor.forClass(TransactionSignature.class);
        verify(entityListener, times(expected.size())).onTransactionSignature(captor.capture());
        assertThat(captor.getAllValues()).hasSameElementsAs(expected);
    }
}
