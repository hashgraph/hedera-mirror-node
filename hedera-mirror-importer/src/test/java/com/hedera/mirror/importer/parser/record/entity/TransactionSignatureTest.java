package com.hedera.mirror.importer.parser.record.entity;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionSignature;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.domain.ContractResultService;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.parser.CommonParserProperties;
import com.hedera.mirror.importer.parser.record.NonFeeTransferExtractionStrategy;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandler;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandlerFactory;
import com.hedera.mirror.importer.repository.FileDataRepository;
import com.hedera.mirror.importer.util.Utility;

@ExtendWith(MockitoExtension.class)
class TransactionSignatureTest {

    private static final long CONSENSUS_TIMESTAMP = 10L;
    private static final EntityId ENTITY_ID = EntityId.of("0.0.123", EntityType.UNKNOWN);

    @Mock
    private AddressBookService addressBookService;

    @Mock
    private ContractResultService contractResultService;

    @Mock
    private EntityListener entityListener;

    @Mock
    private EntityIdService entityIdService;

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

    private Set<TransactionType> transactionSignatures;

    private static Stream<Arguments> provideDefaultTransactionSignatures() {
        return new EntityProperties().getPersist().getTransactionSignatures()
                .stream()
                .map(Arguments::of);
    }

    @BeforeEach
    void setup() {
        CommonParserProperties commonParserProperties = new CommonParserProperties();
        EntityProperties entityProperties = new EntityProperties();
        RecordParserProperties parserProperties = new RecordParserProperties();
        entityRecordItemListener = new EntityRecordItemListener(addressBookService, commonParserProperties,
                contractResultService, entityIdService, entityListener, entityProperties, fileDataRepository,
                nonFeeTransferExtractionStrategy, parserProperties, transactionHandlerFactory);
        defaultSignatureMap = getDefaultSignatureMap();
        defaultTransactionSignatures = defaultSignatureMap.getSigPairList()
                .stream()
                .map(pair -> {
                    TransactionSignature transactionSignature = new TransactionSignature();
                    transactionSignature.setConsensusTimestamp(CONSENSUS_TIMESTAMP);
                    transactionSignature.setEntityId(ENTITY_ID);
                    transactionSignature.setPublicKeyPrefix(pair.getPubKeyPrefix().toByteArray());
                    transactionSignature.setSignature(pair.getEd25519().toByteArray());
                    transactionSignature.setType(SignaturePair.SignatureCase.ED25519.getNumber());
                    return transactionSignature;
                })
                .collect(Collectors.toList());
        transactionSignatures = entityProperties.getPersist().getTransactionSignatures();

        doReturn(ENTITY_ID).when(transactionHandler).getEntity(any(RecordItem.class));
        doReturn(transactionHandler).when(transactionHandlerFactory).get(any(TransactionType.class));
    }

    @ParameterizedTest
    @EnumSource(value = TransactionType.class, names = "UNKNOWN", mode = EXCLUDE)
    void transactionSignatureEnabled(TransactionType transactionType) {
        transactionSignatures.clear();
        transactionSignatures.add(transactionType);

        RecordItem recordItem = getRecordItem(transactionType, SUCCESS);
        entityRecordItemListener.onItem(recordItem);

        assertTransactionSignatures(defaultTransactionSignatures);
    }

    @ParameterizedTest
    @EnumSource(value = TransactionType.class, names = "UNKNOWN", mode = EXCLUDE)
    void transactionSignatureDisabled(TransactionType transactionType) {
        transactionSignatures.clear();
        transactionSignatures.addAll(EnumSet.complementOf(EnumSet.of(transactionType)));

        RecordItem recordItem = getRecordItem(transactionType, SUCCESS);
        entityRecordItemListener.onItem(recordItem);

        assertTransactionSignatures(Collections.emptyList());
    }

    @ParameterizedTest
    @EnumSource(value = TransactionType.class, names = "UNKNOWN", mode = EXCLUDE)
    void transactionSignatureFailedTransaction(TransactionType transactionType) {
        transactionSignatures.addAll(EnumSet.allOf(TransactionType.class));

        RecordItem recordItem = getRecordItem(transactionType, FAIL_FEE);
        entityRecordItemListener.onItem(recordItem);

        assertTransactionSignatures(Collections.emptyList());
    }

    @ParameterizedTest
    @MethodSource("provideDefaultTransactionSignatures")
    void transactionSignatureDefault(TransactionType transactionType) {
        RecordItem recordItem = getRecordItem(transactionType, SUCCESS);
        entityRecordItemListener.onItem(recordItem);

        assertTransactionSignatures(defaultTransactionSignatures);
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

    private RecordItem getRecordItem(TransactionType transactionType, ResponseCodeEnum responseCode) {
        return getRecordItem(transactionType, responseCode, defaultSignatureMap.build());
    }

    private RecordItem getRecordItem(TransactionType transactionType, ResponseCodeEnum responseCode,
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
        return RecordItem.builder().transactionRecord(transactionRecord).transaction(transaction).build();
    }

    private TransactionBody getTransactionBody(TransactionType transactionType) {
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
