package com.hedera.mirror.importer.reader.record;

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

import java.io.DataInputStream;
import java.io.IOException;
import java.util.function.Consumer;
import javax.inject.Named;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Primary;

import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidRecordFileException;
import com.hedera.mirror.importer.exception.RecordFileReaderException;
import com.hedera.mirror.importer.parser.domain.RecordItem;

@Named
@Primary
@Log4j2
@RequiredArgsConstructor
public class CompositeRecordFileReader implements RecordFileReader {

    private final RecordFileReaderImplV1 version1Reader;
    private final RecordFileReaderImplV2 version2Reader;

    @Override
    public RecordFile read(@NonNull StreamFileData streamFileData, Consumer<RecordItem> itemConsumer) {
        try (DataInputStream dis = new DataInputStream(streamFileData.getBufferedInputStream())) {
            RecordFileReader reader;
            String filename = streamFileData.getFilename();

            dis.mark(Integer.BYTES);
            int version = dis.readInt();
            dis.reset();

            switch (version) {
                case 1:
                    reader = version1Reader;
                    break;
                case 2:
                    reader = version2Reader;
                    break;
                default:
                    throw new InvalidRecordFileException(String.format("Unsupported record file version %d in file %s",
                            version, filename));
            }

            log.info("Loading record format version {} from record file: {}", version, filename);
            return reader.read(streamFileData, itemConsumer);
        } catch (IOException e) {
            throw new RecordFileReaderException("Error reading record file " + streamFileData.getFilename(), e);
        }
    }
}
