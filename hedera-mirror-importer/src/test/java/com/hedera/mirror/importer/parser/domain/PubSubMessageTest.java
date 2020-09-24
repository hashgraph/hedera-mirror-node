package com.hedera.mirror.importer.parser.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.util.Utility;

class PubSubMessageTest {
    private static final Long DEFAULT_TIMESTAMP_LONG = 123456789L;
    private static final Timestamp TIMESTAMP =
            Utility.instantToTimestamp(Instant.ofEpochSecond(0L, DEFAULT_TIMESTAMP_LONG));
    private static final AccountID ACCOUNT_ID = AccountID.newBuilder().setAccountNum(10L).build();
    private static final TopicID TOPIC_ID = TopicID.newBuilder().setTopicNum(20L).build();
    private static final ByteString BYTE_STRING = ByteString.copyFromUtf8("abcdef");
    private static final Long INT64_VALUE = 100_000_000L;

    @Test
    void testSerializationAllFieldsSet() throws Exception {
        Iterable<AccountAmount> nonFeeTransfers = Lists.newArrayList(
                AccountAmount.newBuilder().setAccountID(ACCOUNT_ID).setAmount(INT64_VALUE).build(),
                AccountAmount.newBuilder().setAccountID(ACCOUNT_ID).setAmount(INT64_VALUE).build());
        PubSubMessage pubSubMessage = new PubSubMessage(
                DEFAULT_TIMESTAMP_LONG,
                EntityId.of(TOPIC_ID),
                10,
                new PubSubMessage.Transaction(getTransactionBody(), getSignatureMap()),
                getTransactionRecord(),
                nonFeeTransfers);
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode actual = objectMapper.readTree(objectMapper.writeValueAsString(pubSubMessage));
        JsonNode expected = objectMapper.readTree("{" +
                "  \"consensusTimestamp\" : 123456789," +
                "  \"entity\" : {" +
                "    \"shardNum\" : 0," +
                "    \"realmNum\" : 0," +
                "    \"entityNum\" : 20," +
                "    \"type\" : 4" +
                "  }," +
                "  \"transactionType\" : 10," +
                getExpectedTransactionJson() + "," +
                getExpectedTransactionRecord() + "," +
                "  \"nonFeeTransfers\" : [ {" +
                "    \"accountID\": {" +
                "      \"shardNum\": \"0\"," +
                "      \"realmNum\": \"0\"," +
                "      \"accountNum\": \"10\"" +
                "      }," +
                "    \"amount\": \"100000000\"" +
                "  }, {" +
                "    \"accountID\": {" +
                "      \"shardNum\": \"0\"," +
                "      \"realmNum\": \"0\"," +
                "      \"accountNum\": \"10\"" +
                "      }," +
                "    \"amount\": \"100000000\"" +
                "  } ]" +
                "}");
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void testSerializationWithNullFields() throws Exception {
        PubSubMessage pubSubMessage = new PubSubMessage(DEFAULT_TIMESTAMP_LONG, null, 10, new PubSubMessage.Transaction(getTransactionBody(), getSignatureMap()),
                getTransactionRecord(), null);
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode actual = objectMapper.readTree(objectMapper.writeValueAsString(pubSubMessage));
        JsonNode expected = objectMapper.readTree("{" +
                "  \"consensusTimestamp\" : 123456789," +
                "  \"transactionType\" : 10," +
                getExpectedTransactionJson() + "," +
                getExpectedTransactionRecord() +
                "}");
        assertThat(actual).isEqualTo(expected);
    }

    private static TransactionBody getTransactionBody() {
        return TransactionBody.newBuilder()
                .setTransactionID(TransactionID.newBuilder()
                        .setTransactionValidStart(TIMESTAMP)
                        .setAccountID(ACCOUNT_ID)
                        .build())
                .setNodeAccountID(ACCOUNT_ID)
                .setTransactionFee(INT64_VALUE)
                .setTransactionValidDuration(Duration.newBuilder()
                        .setSeconds(INT64_VALUE).build())
                .setMemoBytes(BYTE_STRING)
                .setConsensusSubmitMessage(ConsensusSubmitMessageTransactionBody
                        .newBuilder()
                        .setTopicID(TOPIC_ID)
                        .setMessage(BYTE_STRING)
                        .build())
                .build();
    }

    private static SignatureMap getSignatureMap() {
        return SignatureMap.newBuilder()
                .addSigPair(SignaturePair.newBuilder()
                        .setEd25519(BYTE_STRING)
                        .setPubKeyPrefix(BYTE_STRING)
                        .build())
                .build();
    }

    private static String getExpectedTransactionJson() {
        return "\"transaction\" : {" +
                "  \"body\": {" +
                "    \"transactionID\": {" +
                "      \"transactionValidStart\": {" +
                "        \"seconds\": \"0\"," +
                "        \"nanos\": 123456789" +
                "      }," +
                "      \"accountID\": {" +
                "        \"shardNum\": \"0\"," +
                "        \"realmNum\": \"0\"," +
                "        \"accountNum\": \"10\"" +
                "      }" +
                "    }," +
                "    \"nodeAccountID\": {" +
                "      \"shardNum\": \"0\"," +
                "      \"realmNum\": \"0\"," +
                "      \"accountNum\": \"10\"" +
                "    }," +
                "    \"transactionFee\": \"100000000\"," +
                "    \"transactionValidDuration\": {" +
                "      \"seconds\": \"100000000\"" +
                "    }," +
                "    \"generateRecord\": false," +
                "    \"memo\": \"abcdef\"," +
                "    \"consensusSubmitMessage\": {" +
                "      \"topicID\": {" +
                "        \"shardNum\": \"0\"," +
                "        \"realmNum\": \"0\"," +
                "        \"topicNum\": \"20\"" +
                "      }," +
                "      \"message\": \"YWJjZGVm\"" +
                "    }" +
                "  }," +
                "  \"sigMap\": {" +
                "    \"sigPair\": [{" +
                "      \"pubKeyPrefix\": \"YWJjZGVm\"," +
                "      \"ed25519\": \"YWJjZGVm\"" +
                "    }]" +
                "  }" +
                "}";
    }

    private static TransactionRecord getTransactionRecord() {
        return TransactionRecord.newBuilder()
                .setReceipt(TransactionReceipt.newBuilder()
                        .setStatus(ResponseCodeEnum.SUCCESS)
                        .setTopicRunningHash(BYTE_STRING)
                        .setTopicSequenceNumber(INT64_VALUE)
                        .build())
                .setTransactionHash(BYTE_STRING)
                .setConsensusTimestamp(TIMESTAMP)
                .setTransferList(TransferList.newBuilder()
                        .addAccountAmounts(
                                AccountAmount.newBuilder().setAccountID(ACCOUNT_ID).setAmount(INT64_VALUE).build())
                        .addAccountAmounts(
                                AccountAmount.newBuilder().setAccountID(ACCOUNT_ID).setAmount(INT64_VALUE).build())
                        .build())
                .build();
    }

    private static String getExpectedTransactionRecord() {
        return "\"transactionRecord\" : {" +
                "  \"receipt\": {" +
                "    \"status\": \"SUCCESS\"," +
                "    \"topicSequenceNumber\": \"100000000\"," +
                "    \"topicRunningHash\": \"YWJjZGVm\"," +
                "    \"topicRunningHashVersion\": \"0\"" +
                "  }," +
                "  \"transactionHash\": \"YWJjZGVm\"," +
                "  \"consensusTimestamp\": {" +
                "    \"seconds\": \"0\"," +
                "    \"nanos\": 123456789" +
                "  }," +
                "  \"memo\": \"\"," +
                "  \"transactionFee\": \"0\"," +
                "  \"transferList\": {" +
                "    \"accountAmounts\": [{" +
                "      \"accountID\": {" +
                "        \"shardNum\": \"0\"," +
                "        \"realmNum\": \"0\"," +
                "        \"accountNum\": \"10\"" +
                "      }," +
                "      \"amount\": \"100000000\"" +
                "    }, {" +
                "      \"accountID\": {" +
                "        \"shardNum\": \"0\"," +
                "        \"realmNum\": \"0\"," +
                "        \"accountNum\": \"10\"" +
                "      }," +
                "      \"amount\": \"100000000\"" +
                "    }]" +
                "  }," +
                "  \"tokenTransferLists\":[]" +
                "}";
    }
}
