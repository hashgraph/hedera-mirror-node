package com.hedera.mirror.importer.reader.balance;

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
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import com.hedera.mirror.importer.parser.balance.BalanceParserProperties;
import com.hedera.mirror.importer.reader.balance.line.AccountBalanceLineParserV1;
import com.hedera.mirror.importer.util.Utility;

@AllArgsConstructor
@Log4j2
@Named
public class BalanceFileReaderImplV1 implements BalanceFileReader {
    private static final int MAX_HEADER_ROWS = 10;
    private static final String TIMESTAMP_HEADER_PREFIX = "timestamp:";
    private static final String COLUMN_HEADER_PREFIX = "shard";

    private final BalanceParserProperties balanceParserProperties;
    private final AccountBalanceLineParserV1 parser;

    @Override
    public Stream<AccountBalance> read(File file) {
        if (file == null) {
            throw new InvalidDatasetException("Null file provided to balance file reader");
        }
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)),
                    balanceParserProperties.getFileBufferSize());
            long consensusTimestamp = parseHeaderForConsensusTimestamp(reader);
            long shard = balanceParserProperties.getMirrorProperties().getShard();

            return reader.lines()
                    .map(line -> {
                        try {
                            return parser.parse(line, consensusTimestamp, shard);
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

    private long parseHeaderForConsensusTimestamp(BufferedReader reader) {
        // The file should contain:
        //  - single header row Timestamp:YYYY-MM-DDTHH:MM:SS.NNNNNNNNZ
        //  - shardNum,realmNum,accountNum,balance
        // followed by rows of data.
        // The logic here is a slight bit more lenient. Look at up to MAX_HEADER_ROWS rows ending at any row containing
        // "shard" and requiring that one of the rows had "Timestamp: some value"
        String line = null;
        try {
            long consensusTimestamp = -1;
            for (int i = 0; i < MAX_HEADER_ROWS; i++) {
                line = reader.readLine();
                if (StringUtils.startsWithIgnoreCase(line, TIMESTAMP_HEADER_PREFIX)) {
                    Instant instant = Instant.parse(line.substring(TIMESTAMP_HEADER_PREFIX.length()));
                    consensusTimestamp = Utility.convertToNanosMax(instant.getEpochSecond(), instant.getNano());
                } else if (StringUtils.startsWithIgnoreCase(line, COLUMN_HEADER_PREFIX)) {
                    if (consensusTimestamp == -1) {
                        break;
                    }
                    return consensusTimestamp;
                }
            }
        } catch (DateTimeParseException ex) {
            throw new InvalidDatasetException("Invalid timestamp header line: " + line, ex);
        } catch (IOException ex) {
            throw new InvalidDatasetException("Error reading account balance file", ex);
        }

        throw new InvalidDatasetException("Timestamp / column header not found in account balance file");
    }
}
