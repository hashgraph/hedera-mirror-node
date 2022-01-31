package com.hedera.mirror.importer.parser.record.pubsub;

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

import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.PubSubIntegrationTest;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.parser.record.RecordFileParser;
import com.hedera.mirror.importer.reader.record.RecordFileReader;

class PubSubRecordParserTest extends PubSubIntegrationTest {

    private static final int NUM_TXNS = 34; // number of transactions in test record files

    @Value("classpath:data/pubsub-messages.txt")
    Path pubSubMessages;

    @Value("classpath:data/recordstreams/v2/record0.0.3/*.rcd")
    Resource[] testFiles;

    @Autowired
    private RecordFileReader recordFileReader;

    @Autowired
    private RecordFileParser recordFileParser;

    @Test
    public void testPubSubExporter() throws Exception {
        for (int index = 0; index < testFiles.length; index++) {
            RecordFile recordFile = recordFileReader.read(StreamFileData.from(testFiles[index].getFile()));
            recordFile.setIndex((long) index);
            recordFile.setNodeAccountId(EntityId.of(0, 0, 3, EntityType.ACCOUNT));
            recordFileParser.parse(recordFile);
        }

        // then
        List<String> expectedMessages = Files.readAllLines(pubSubMessages);
        List<String> actualMessages = getAllMessages(NUM_TXNS).stream()
                .map(PubsubMessage::getData)
                .map(ByteString::toStringUtf8)
                .collect(Collectors.toList());

        // map timestamps to messages and compare individual message JSON strings
        Map<Long, String> expectedMessageMap = mapMessages(expectedMessages);
        Map<Long, String> actualMessageMap = mapMessages(actualMessages);
        assertThat(actualMessageMap.size()).isEqualTo(actualMessages.size());
        assertThat(expectedMessageMap.size()).isEqualTo(expectedMessages.size());
        for (Map.Entry<Long, String> messageEntry : expectedMessageMap.entrySet()) {
            String expectedMessage = messageEntry.getValue();
            String actualMessage = actualMessageMap.get(messageEntry.getKey());
            assertThat(actualMessage).isEqualTo(expectedMessage);
        }
    }

    public Map<Long, String> mapMessages(List<String> inputMessages) {
        // given message records text. Extract timestamp as a key and string as value
        Map<Long, String> messages = new HashMap<>();
        Pattern pattern = Pattern.compile("\"consensusTimestamp\":(\\d{19}),");

        for (String message : inputMessages) {
            Matcher matcher = pattern.matcher(message);
            assertThat(matcher.find()).as("Message is missing consensusTimestamp: " + message).isTrue();
            messages.put(Long.valueOf(matcher.group(1)), message);
        }

        return messages;
    }
}
