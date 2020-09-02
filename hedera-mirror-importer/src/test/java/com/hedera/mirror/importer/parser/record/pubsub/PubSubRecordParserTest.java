package com.hedera.mirror.importer.parser.record.pubsub;

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

import com.google.pubsub.v1.PubsubMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.shaded.org.apache.commons.io.FilenameUtils;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.PubSubIntegrationTest;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.parser.record.RecordFilePoller;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.util.Utility;

public class PubSubRecordParserTest extends PubSubIntegrationTest {
    private static final int NUM_TXNS = 34; // number of transactions in test record files

    @TempDir
    Path dataPath;
    @Value("classpath:data")
    Path testResourcesPath;
    @Resource
    private RecordParserProperties parserProperties;
    @Resource
    private RecordFilePoller recordFilePoller;
    @Resource
    private RecordFileRepository recordFileRepository;
    private FileCopier fileCopier;

    @BeforeEach
    void beforeEach() throws IOException {
        parserProperties.getMirrorProperties().setDataPath(dataPath);
        parserProperties.init();

        StreamType streamType = StreamType.RECORD;
        fileCopier = FileCopier.create(testResourcesPath, dataPath)
                .from(streamType.getPath(), "v2", "record0.0.3")
                .filterFiles("*.rcd")
                .to(streamType.getPath(), streamType.getValid());

        EntityId nodeAccountId = EntityId.of(TestUtils.toAccountId("0.0.3"));
        Files.walk(Path.of(testResourcesPath.toString(), streamType.getPath(), "v2", "record0.0.3"))
                .filter(p -> p.toString().endsWith(".rcd"))
                .forEach(p -> {
                    String filename = FilenameUtils.getName(p.toString());
                    RecordFile rf = new RecordFile(Utility.getTimestampFromFilename(filename), 0L, null, filename, 0L, 0L, filename, filename, nodeAccountId, 0L, 2);
                    recordFileRepository.save(rf);
                });
    }

    @Test
    public void testPubSubExporter() throws Exception {
        // given
        fileCopier.copy();

        // when
        recordFilePoller.poll();

        // then
        List<String> expectedMessages =
                Files.readAllLines(testResourcesPath.resolve("pubsub-messages.txt"));
        List<PubsubMessage> actualMessages = getAllMessages(NUM_TXNS);

        assertThat(actualMessages)
                .hasSize(expectedMessages.size())
                .zipSatisfy(expectedMessages, (actual, expected) -> {

                    assertThat(actual.getAttributesMap().get("consensusTimestamp"))
                            .isEqualTo(getConsensusTimestampFromMessage(expected));
                    // Users of PubSub will work with JSON. We don't convert these to Java POJOs since we want to
                    // drectly test json (without deserialization layer)
                    assertThat(actual.getData().toStringUtf8()).isEqualTo(expected);
                });
    }

    private String getConsensusTimestampFromMessage(String message) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readValue(message, Map.class).get("consensusTimestamp").toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
