package com.hedera.mirror.importer.reader.record;

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

import com.google.common.base.Stopwatch;
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
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hedera.mirror.importer.exception.StreamFileReaderException;
import com.hedera.mirror.importer.parser.domain.RecordItem;

@Log4j2
@Named
@Primary
@RequiredArgsConstructor
public class CompositeRecordFileReader implements RecordFileReader {

    private final RecordFileReaderImplV1 version1Reader;
    private final RecordFileReaderImplV2 version2Reader;
    private final RecordFileReaderImplV5 version5Reader;

    @Override
    public RecordFile read(@NonNull StreamFileData streamFileData, Consumer<RecordItem> itemConsumer) {
        long count = 0;
        Stopwatch stopwatch = Stopwatch.createStarted();
        String filename = streamFileData.getFilename();
        int version = 0;

        try (DataInputStream dis = new DataInputStream(streamFileData.getInputStream())) {
            RecordFileReader reader;
            version = dis.readInt();

            switch (version) {
                case 1:
                    reader = version1Reader;
                    break;
                case 2:
                    reader = version2Reader;
                    break;
                case 5:
                    reader = version5Reader;
                    break;
                default:
                    throw new InvalidStreamFileException(String.format("Unsupported record file version %d in file %s",
                            version, filename));
            }

            RecordFile recordFile = reader.read(streamFileData, itemConsumer);
            count = recordFile.getCount();
            return recordFile;
        } catch (IOException e) {
            throw new StreamFileReaderException("Error reading record file " + filename, e);
        } finally {
            log.info("Read {} items {}successfully from v{} record file {} in {}",
                    count, count != 0 ? "" : "un", version, filename, stopwatch);
        }
    }
}
