package com.hedera.mirror.importer.parser.record.transactionhandler;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.EthereumTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import lombok.SneakyThrows;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.parser.record.ethereum.EthereumTransactionParser;

class EthereumTransactionHandlerTest extends AbstractTransactionHandlerTest {

    static final String LONDON_RAW_TX =
            "02f87082012a022f2f83018000947e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181880de0b6b3a764000083123456c001a0df48f2efd10421811de2bfb125ab75b2d3c44139c4642837fb1fccce911fd479a01aaf7ae92bee896651dfc9d99ae422a296bf5d9f1ca49b2d96d82b79eb112d66";

    @Mock(lenient = true)
    protected EthereumTransactionParser ethereumTransactionParser;

    private EntityProperties entityProperties;

    @Override
    protected TransactionHandler getTransactionHandler() {
        doReturn(domainBuilder.ethereumTransaction(true).get()).when(ethereumTransactionParser).decode(any());
        entityProperties = new EntityProperties();
        return new EthereumTransactionHandler(entityProperties, entityListener,
                ethereumTransactionParser);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder().setEthereumTransaction(EthereumTransactionBody.newBuilder().build());
    }

    @Override
    protected TransactionRecord.Builder getDefaultTransactionRecord() {
        return TransactionRecord.newBuilder()
                .setConsensusTimestamp(MODIFIED_TIMESTAMP)
                .setReceipt(getTransactionReceipt(ResponseCodeEnum.SUCCESS))
                .setContractCallResult(recordItemBuilder.contractFunctionResult(contractId));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.CONTRACT;
    }

    @SneakyThrows
    @Test
    void testGetEntityIdOnCreate() {
        // given LONDON_RAW_TX matching components
        doReturn(domainBuilder.ethereumTransaction(true)
                .customize(x -> x
                        .callData(Hex.decode("123456"))
                        .chainId(Hex.decode("012a"))
                        .gasLimit(98304L)
                        .maxFeePerGas(Hex.decode("2f"))
                        .maxPriorityFeePerGas(Hex.decode("2f"))
                        .nonce(2L)
                        .recoveryId(1)
                        .signatureR(Hex.decode("df48f2efd10421811de2bfb125ab75b2d3c44139c4642837fb1fccce911fd479"))
                        .signatureS(Hex.decode("1aaf7ae92bee896651dfc9d99ae422a296bf5d9f1ca49b2d96d82b79eb112d66"))
                        .toAddress(Hex.decode("7e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181"))
                        .value(Hex.decode("0de0b6b3a7640000")))
                .get()).when(ethereumTransactionParser).decode(any());

        var recordItem = recordItemBuilder.ethereumTransaction(true)
                .record(x -> x.setEthereumHash(ByteString.copyFrom(Hex.decode(LONDON_RAW_TX))))
                .build();
        ContractID contractId = recordItem.getRecord().getContractCreateResult().getContractID();
        EntityId expectedEntityId = EntityId.of(contractId);

        when(entityIdService.lookup(contractId)).thenReturn(expectedEntityId);
        EntityId entityId = transactionHandler.getEntity(recordItem);
        assertThat(entityId).isEqualTo(expectedEntityId);

        verify(entityListener, times(1)).onEthereumTransaction(any());
    }

    @Test
    void testGetEntityIdOnCall() {
        var recordItem = recordItemBuilder.ethereumTransaction(false)
                .record(x -> x.setEthereumHash(ByteString.copyFrom(Hex.decode(LONDON_RAW_TX))))
                .build();
        ContractID contractId = recordItem.getRecord().getContractCallResult().getContractID();
        EntityId expectedEntityId = EntityId.of(contractId);

        when(entityIdService.lookup(contractId)).thenReturn(expectedEntityId);
        EntityId entityId = transactionHandler.getEntity(recordItem);
        assertThat(entityId).isEqualTo(expectedEntityId);

        verify(entityListener, atLeastOnce()).onEthereumTransaction(any());
    }

    @Test
    void testGetEntityIdNullEthereumTransaction() {
        var recordItem = recordItemBuilder.ethereumTransaction(true)
                .build();
        ContractID contractId = recordItem.getRecord().getContractCreateResult().getContractID();
        EntityId expectedEntityId = EntityId.of(contractId);

        when(entityIdService.lookup(contractId)).thenReturn(expectedEntityId);

        doThrow(InvalidDatasetException.class).when(ethereumTransactionParser).decode(any());

        assertThatThrownBy(() -> transactionHandler.getEntity(recordItem)).isInstanceOf(InvalidDatasetException.class);

        // verify entityListener.onEthereumTransaction never called
        verify(entityListener, never()).onEthereumTransaction(any());
    }

    @Test
    void testDisabledEthereumTransaction() {
        entityProperties.getPersist().setEthereumTransactions(false);
        var recordItem = recordItemBuilder.ethereumTransaction(true)
                .build();

        transactionHandler.getEntity(recordItem);

        // verify parse and listener are never called
        verify(entityListener, never()).onEthereumTransaction(any());
        verify(ethereumTransactionParser, never()).decode(any());
    }
}
