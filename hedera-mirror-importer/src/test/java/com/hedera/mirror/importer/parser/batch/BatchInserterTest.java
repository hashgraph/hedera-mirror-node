/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.common.domain.transaction.AssessedCustomFee;
import com.hedera.mirror.common.domain.transaction.CryptoTransfer;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.AssessedCustomFeeWrapper;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.CommonParserProperties;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.TokenTransferRepository;
import com.hedera.mirror.importer.repository.TopicMessageRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.Reader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class BatchInserterTest extends IntegrationTest {

    private final BatchPersister batchInserter;
    private final CryptoTransferRepository cryptoTransferRepository;
    private final TopicMessageRepository topicMessageRepository;
    private final TokenTransferRepository tokenTransferRepository;
    private final TransactionRepository transactionRepository;

    @Test
    void persist() {
        var cryptoTransfers = new ArrayList<CryptoTransfer>();
        cryptoTransfers.add(domainBuilder.cryptoTransfer().get());
        cryptoTransfers.add(domainBuilder.cryptoTransfer().get());
        cryptoTransfers.add(domainBuilder.cryptoTransfer().get());

        var tokenTransfers = new ArrayList<TokenTransfer>();
        tokenTransfers.add(domainBuilder.tokenTransfer().get());
        tokenTransfers.add(domainBuilder.tokenTransfer().get());
        tokenTransfers.add(domainBuilder.tokenTransfer().get());

        batchInserter.persist(cryptoTransfers);
        batchInserter.persist(tokenTransfers);

        assertThat(cryptoTransferRepository.findAll()).containsExactlyInAnyOrderElementsOf(cryptoTransfers);
        assertThat(tokenTransferRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokenTransfers);
    }

    @Test
    void throwsParserException() throws SQLException, IOException {
        // given
        CopyManager copyManager = mock(CopyManager.class);
        doThrow(SQLException.class).when(copyManager).copyIn(any(), (Reader) any(), anyInt());
        PGConnection pgConnection = mock(PGConnection.class);
        doReturn(copyManager).when(pgConnection).getCopyAPI();
        DataSource dataSource = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        doReturn(conn).when(dataSource).getConnection();
        doReturn(pgConnection).when(conn).unwrap(any());
        var cryptoTransferBatchInserter2 = new BatchInserter(
                CryptoTransfer.class, dataSource, new SimpleMeterRegistry(), new CommonParserProperties());
        var cryptoTransfers = new HashSet<CryptoTransfer>();
        cryptoTransfers.add(domainBuilder.cryptoTransfer().get());

        // when
        assertThatThrownBy(() -> cryptoTransferBatchInserter2.persist(cryptoTransfers))
                .isInstanceOf(ParserException.class);
    }

    @Test
    void persistNull() {
        batchInserter.persist(null);
        assertThat(cryptoTransferRepository.count()).isZero();
    }

    @Test
    void largeConsensusSubmitMessage() {
        var topicMessages = new HashSet<TopicMessage>();
        topicMessages.add(topicMessage(6000)); // max 6KiB
        topicMessages.add(topicMessage(6000));
        topicMessages.add(topicMessage(6000));
        topicMessages.add(topicMessage(6000));

        batchInserter.persist(topicMessages);

        assertThat(topicMessageRepository.findAll()).hasSize(4).containsExactlyInAnyOrderElementsOf(topicMessages);
    }

    @Test
    void assessedCustomFees() {
        long consensusTimestamp = 10L;
        EntityId collectorId1 = EntityId.of("0.0.2000", EntityType.ACCOUNT);
        EntityId collectorId2 = EntityId.of("0.0.2001", EntityType.ACCOUNT);
        EntityId payerId1 = EntityId.of("0.0.3000", EntityType.ACCOUNT);
        EntityId payerId2 = EntityId.of("0.0.3001", EntityType.ACCOUNT);
        EntityId tokenId1 = EntityId.of("0.0.5000", EntityType.TOKEN);
        EntityId tokenId2 = EntityId.of("0.0.5001", EntityType.TOKEN);

        // fee paid in HBAR with empty effective payer list
        AssessedCustomFee assessedCustomFee1 = new AssessedCustomFee();
        assessedCustomFee1.setAmount(10L);
        assessedCustomFee1.setId(new AssessedCustomFee.Id(collectorId1, consensusTimestamp));
        assessedCustomFee1.setPayerAccountId(payerId1);

        AssessedCustomFee assessedCustomFee2 = new AssessedCustomFee();
        assessedCustomFee2.setAmount(20L);
        assessedCustomFee2.setEffectivePayerAccountIds(List.of(payerId1.getId()));
        assessedCustomFee2.setTokenId(tokenId1);
        assessedCustomFee2.setId(new AssessedCustomFee.Id(collectorId2, consensusTimestamp));
        assessedCustomFee2.setPayerAccountId(payerId1);

        AssessedCustomFee assessedCustomFee3 = new AssessedCustomFee();
        assessedCustomFee3.setAmount(30L);
        assessedCustomFee3.setEffectivePayerAccountIds(List.of(payerId1.getId(), payerId2.getId()));
        assessedCustomFee3.setTokenId(tokenId2);
        assessedCustomFee3.setId(new AssessedCustomFee.Id(collectorId2, consensusTimestamp));
        assessedCustomFee3.setPayerAccountId(payerId2);

        List<AssessedCustomFee> assessedCustomFees =
                List.of(assessedCustomFee1, assessedCustomFee2, assessedCustomFee3);

        // when
        batchInserter.persist(assessedCustomFees);

        // then
        var actual = jdbcOperations.query(AssessedCustomFeeWrapper.SELECT_QUERY, AssessedCustomFeeWrapper.ROW_MAPPER);
        assertThat(actual)
                .map(AssessedCustomFeeWrapper::getAssessedCustomFee)
                .containsExactlyInAnyOrderElementsOf(assessedCustomFees);
    }

    private TopicMessage topicMessage(int messageSize) {
        return domainBuilder
                .topicMessage()
                .customize(t -> t.message(RandomUtils.nextBytes(messageSize)))
                .get();
    }
}
