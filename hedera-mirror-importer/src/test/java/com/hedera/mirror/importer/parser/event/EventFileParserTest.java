package com.hedera.mirror.importer.parser.event;

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

import static com.hedera.mirror.importer.domain.StreamFilename.FileType.DATA;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import reactor.core.publisher.Flux;

import com.hedera.mirror.importer.domain.DigestAlgorithm;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.EventFile;
import com.hedera.mirror.importer.domain.StreamFile;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.AbstractStreamFileParserTest;
import com.hedera.mirror.importer.parser.domain.EventItem;

class EventFileParserTest extends AbstractStreamFileParserTest<EventFileParser> {

    private long count = 0;

    @Override
    protected EventFileParser getParser() {
        EventParserProperties parserProperties = new EventParserProperties();
        return new EventFileParser(dataSource, meterRegistry, parserProperties, streamFileRepository);
    }

    @Override
    protected void assertParsed(StreamFile streamFile, boolean parsed, boolean dbError) {
        EventFile eventFile = (EventFile) streamFile;

        if (parsed) {
            verify(streamFileRepository).save(eventFile);
        } else {
            if (!dbError) {
                verify(streamFileRepository, never()).save(any());
            }
        }
    }

    @Override
    protected StreamFile getStreamFile() {
        long id = ++count;
        Instant instant = Instant.ofEpochSecond(0L, id);
        String filename = StreamFilename.getFilename(parserProperties.getStreamType(), DATA, instant);
        EventFile eventFile = new EventFile();
        eventFile.setBytes(new byte[] {0, 1, 2});
        eventFile.setConsensusEnd(id);
        eventFile.setConsensusStart(id);
        eventFile.setConsensusEnd(id);
        eventFile.setCount(id);
        eventFile.setDigestAlgorithm(DigestAlgorithm.SHA384);
        eventFile.setFileHash("fileHash" + id);
        eventFile.setHash("hash" + id);
        eventFile.setLoadEnd(id);
        eventFile.setLoadStart(id);
        eventFile.setName(filename);
        eventFile.setNodeAccountId(EntityId.of("0.0.3", EntityTypeEnum.ACCOUNT));
        eventFile.setPreviousHash("previousHash" + (id - 1));
        eventFile.setVersion(1);
        eventFile.setItems(Flux.just(new EventItem()));
        return eventFile;
    }

    @Override
    protected void mockDbFailure() {
        doThrow(ParserException.class).when(streamFileRepository).save(any());
    }
}
