package com.hedera.mirror.importer.reader.balance;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
import java.io.IOException;
import java.time.format.DateTimeParseException;
import javax.inject.Named;
import org.apache.commons.lang3.StringUtils;

import com.hedera.mirror.importer.exception.InvalidDatasetException;
import com.hedera.mirror.importer.parser.balance.BalanceParserProperties;
import com.hedera.mirror.importer.reader.balance.line.AccountBalanceLineParserV1;

@Named
public class BalanceFileReaderImplV1 extends CsvBalanceFileReader {

    private static final int MAX_HEADER_ROWS = 10;
    static final String TIMESTAMP_HEADER_PREFIX = "timestamp:";

    public BalanceFileReaderImplV1(BalanceParserProperties balanceParserProperties, AccountBalanceLineParserV1 parser) {
        super(balanceParserProperties, parser);
    }

    @Override
    protected String getTimestampHeaderPrefix() {
        return TIMESTAMP_HEADER_PREFIX;
    }

    @Override
    protected String getVersionHeaderPrefix() {
        return TIMESTAMP_HEADER_PREFIX;
    }

    /**
     * The file should contain 2 header rows:
     * <p>
     * - single header row Timestamp:YYYY-MM-DDTHH:MM:SS.NNNNNNNNZ
     * <p>
     * - shardNum,realmNum,accountNum,balance
     * <p>
     * followed by rows of data. The logic here is a slight bit more lenient. Look at up to MAX_HEADER_ROWS rows ending
     * at any row containing "shard" and requiring that one of the rows had "Timestamp: some value"
     *
     * @param reader
     * @return consensusTimestamp
     */
    @Override
    protected long parseConsensusTimestamp(BufferedReader reader) {
        String line = null;
        try {
            long consensusTimestamp = -1;
            for (int i = 0; i < MAX_HEADER_ROWS; i++) {
                line = reader.readLine();
                if (supports(line)) {
                    consensusTimestamp = convertTimestamp(line.substring(TIMESTAMP_HEADER_PREFIX.length()));
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
