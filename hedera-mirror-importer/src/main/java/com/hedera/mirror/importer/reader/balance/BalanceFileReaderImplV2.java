package com.hedera.mirror.importer.reader.balance;

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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.stream.Stream;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import com.hedera.mirror.importer.parser.balance.BalanceParserProperties;
import com.hedera.mirror.importer.parser.balance.v2.AccountBalanceLineParserV2;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
@RequiredArgsConstructor
public class BalanceFileReaderImplV2 implements BalanceFileReader {
    private static final String COLUMN_HEADER_PREFIX = "shard";
    private static final String TIMESTAMP_HEADER_PREFIX = "# TimeStamp:";
    private static final String VERSION_2_HEADER_PREFIX = "# version:2";

    private final BalanceParserProperties balanceParserProperties;
    private final AccountBalanceLineParserV2 parser;

    @Override
    public Stream<AccountBalance> read(File file) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)),
                    balanceParserProperties.getFileBufferSize());
            long consensusTimestamp = parseHeaderForConsensusTimestamp(reader);

            return reader.lines()
                    .map(line -> {
                        try {
                            return parser.parse(line, consensusTimestamp, balanceParserProperties.getMirrorProperties()
                                    .getShard());
                        } catch (InvalidDatasetException ex) {
                            log.error(ex);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .onClose(() -> {
                        try {
                            reader.close();
                        } catch (Exception ex) {
                        }
                    });
        } catch (IOException ex) {
            throw new InvalidDatasetException("Error reading account balance file", ex);
        }
    }

    public boolean isFirstLineFromFileVersion(String firstLine) {
        return firstLine.startsWith(VERSION_2_HEADER_PREFIX);
    }

    private long parseHeaderForConsensusTimestamp(BufferedReader reader) {
        String line = null;
        try {
            //Discard the first line/version number
            reader.readLine();
            line = reader.readLine();
            if (line.startsWith(TIMESTAMP_HEADER_PREFIX)) {
                long consensusTimestamp = convertTimestampLine(line);
                line = reader.readLine();
                if (line.startsWith(COLUMN_HEADER_PREFIX)) {
                    return consensusTimestamp;
                } else {
                    throw new InvalidDatasetException("Column header not found in account balance file");
                }
            } else {
                throw new InvalidDatasetException("Timestamp not found in account balance file");
            }
        } catch (NullPointerException ex) {
            throw new InvalidDatasetException("Timestamp / column header not found in account balance file");
        } catch (DateTimeParseException ex) {
            throw new InvalidDatasetException("Invalid timestamp header line: " + line, ex);
        } catch (IOException ex) {
            throw new InvalidDatasetException("Error reading account balance file", ex);
        }
    }

    private long convertTimestampLine(String timestampLine) {
        Instant instant = Instant.parse(timestampLine.substring(TIMESTAMP_HEADER_PREFIX.length()));
        return Utility.convertToNanosMax(instant.getEpochSecond(), instant.getNano());
    }
}
