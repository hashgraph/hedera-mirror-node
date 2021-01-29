package com.hedera.mirror.monitor.expression;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.hedera.datagenerator.sdk.supplier.TransactionType;
import com.hedera.hashgraph.proto.AccountID;
import com.hedera.hashgraph.proto.TokenID;
import com.hedera.hashgraph.proto.TopicID;
import com.hedera.hashgraph.proto.TransactionReceipt;
import com.hedera.hashgraph.sdk.TransactionRecord;
import com.hedera.mirror.monitor.MonitorProperties;
import com.hedera.mirror.monitor.publish.PublishRequest;
import com.hedera.mirror.monitor.publish.PublishResponse;
import com.hedera.mirror.monitor.publish.TransactionPublisher;

@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ExpressionConverterImplTest {

    @Mock
    private TransactionPublisher transactionPublisher;

    @Spy
    private final MonitorProperties monitorProperties = new MonitorProperties();

    @InjectMocks
    private ExpressionConverterImpl expressionConverter;

    @Captor
    private ArgumentCaptor<PublishRequest> request;

    @BeforeEach
    void setup() {
        monitorProperties.getOperator().setAccountId("0.0.2");
    }

    @Test
    void invalidExpression() {
        assertThatThrownBy(() -> expressionConverter.convert("${foo.bar}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not a valid property expression");
    }

    @Test
    void incompleteExpression() {
        assertThat(expressionConverter.convert("${topic.bar")).isEqualTo("${topic.bar");
    }

    @Test
    void withoutId() {
        assertThatThrownBy(() -> expressionConverter.convert("${topic}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not a valid property expression");
    }

    @Test
    void nullValue() {
        assertThat(expressionConverter.convert((String) null)).isNull();
    }

    @Test
    void empty() {
        assertThat(expressionConverter.convert("")).isEmpty();
    }

    @Test
    void regularString() {
        assertThat(expressionConverter.convert("0.0.100")).isEqualTo("0.0.100");
    }

    @Test
    void errorPublishing() {
        when(transactionPublisher.publish(any())).thenThrow(new RuntimeException());
        assertThatThrownBy(() -> expressionConverter.convert("${topic.foo}"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void account() {
        TransactionType type = TransactionType.ACCOUNT_CREATE;
        when(transactionPublisher.publish(any())).thenReturn(response(type, 100));
        assertThat(expressionConverter.convert("${account.foo}")).isEqualTo("0.0.100");

        verify(transactionPublisher).publish(request.capture());
        assertThat(request.getValue().getType()).isEqualTo(type);
    }

    @Test
    void token() {
        TransactionType type = TransactionType.TOKEN_CREATE;
        when(transactionPublisher.publish(any())).thenReturn(response(type, 101));
        assertThat(expressionConverter.convert("${token.foo}")).isEqualTo("0.0.101");

        verify(transactionPublisher).publish(request.capture());
        assertThat(request.getValue().getType()).isEqualTo(type);
    }

    @Test
    void topic() {
        TransactionType type = TransactionType.CONSENSUS_CREATE_TOPIC;
        when(transactionPublisher.publish(any())).thenReturn(response(type, 100));
        assertThat(expressionConverter.convert("${topic.foo}")).isEqualTo("0.0.100");

        verify(transactionPublisher).publish(request.capture());
        assertThat(request.getValue().getType()).isEqualTo(type);
    }

    @Test
    void cached() {
        TransactionType type = TransactionType.CONSENSUS_CREATE_TOPIC;
        when(transactionPublisher.publish(any()))
                .thenReturn(response(type, 100));

        assertThat(expressionConverter.convert("${topic.foo}")).isEqualTo("0.0.100");
        assertThat(expressionConverter.convert("${topic.foo}")).isEqualTo("0.0.100");

        verify(transactionPublisher).publish(request.capture());
        assertThat(request.getValue().getType()).isEqualTo(type);
    }

    @Test
    void map() {
        Map<String, String> properties = Map.of("accountId", "0.0.100", "topicId", "${topic.fooBar_123}");
        TransactionType type = TransactionType.CONSENSUS_CREATE_TOPIC;
        when(transactionPublisher.publish(any())).thenReturn(response(type, 101));

        assertThat(expressionConverter.convert(properties))
                .hasSize(2)
                .containsEntry("accountId", "0.0.100")
                .containsEntry("topicId", "0.0.101");

        verify(transactionPublisher).publish(request.capture());
        assertThat(request.getValue().getType()).isEqualTo(type);
    }

    private PublishResponse response(TransactionType type, long id) {
        TransactionReceipt.Builder receipt = TransactionReceipt.newBuilder();

        switch (type) {
            case ACCOUNT_CREATE:
                receipt.setAccountID(AccountID.newBuilder().setAccountNum(id).build());
                break;
            case CONSENSUS_CREATE_TOPIC:
                receipt.setTopicID(TopicID.newBuilder().setTopicNum(id).build());
                break;
            case TOKEN_CREATE:
                receipt.setTokenID(TokenID.newBuilder().setTokenNum(id).build());
                break;
        }

        TransactionRecord record = new TransactionRecord(com.hedera.hashgraph.proto.TransactionRecord.newBuilder()
                .setReceipt(receipt)
                .build());
        return PublishResponse.builder()
                .record(record)
                .receipt(record.receipt)
                .build();
    }
}
