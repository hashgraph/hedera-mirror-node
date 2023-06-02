/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.reader.balance;

import com.hedera.mirror.importer.exception.InvalidDatasetException;
import com.hedera.mirror.importer.parser.balance.BalanceParserProperties;
import com.hedera.mirror.importer.reader.balance.line.AccountBalanceLineParserV2;
import jakarta.inject.Named;
import java.io.BufferedReader;
import java.io.IOException;
import java.time.format.DateTimeParseException;
import org.apache.commons.lang3.StringUtils;

@Named
public class BalanceFileReaderImplV2 extends CsvBalanceFileReader {

    private static final String TIMESTAMP_HEADER_PREFIX = "# TimeStamp:";
    static final String VERSION_HEADER = "# version:2";

    public BalanceFileReaderImplV2(BalanceParserProperties balanceParserProperties, AccountBalanceLineParserV2 parser) {
        super(balanceParserProperties, parser);
    }

    @Override
    protected String getTimestampHeaderPrefix() {
        return TIMESTAMP_HEADER_PREFIX;
    }

    @Override
    protected String getVersionHeaderPrefix() {
        return VERSION_HEADER;
    }

    @Override
    protected long parseConsensusTimestamp(BufferedReader reader) {
        String line = null;
        try {
            line = reader.readLine();
            if (!supports(line)) {
                throw new InvalidDatasetException("Version number not found in account balance file");
            }
            line = reader.readLine();
            if (!StringUtils.startsWith(line, TIMESTAMP_HEADER_PREFIX)) {
                throw new InvalidDatasetException("Timestamp not found in account balance file");
            }
            long consensusTimestamp = convertTimestamp(line.substring(TIMESTAMP_HEADER_PREFIX.length()));
            line = reader.readLine();
            if (!StringUtils.startsWith(line, COLUMN_HEADER_PREFIX)) {
                throw new InvalidDatasetException("Column header not found in account balance file");
            }
            return consensusTimestamp;
        } catch (DateTimeParseException ex) {
            throw new InvalidDatasetException("Invalid timestamp header line: " + line, ex);
        } catch (IOException ex) {
            throw new InvalidDatasetException("Error reading account balance file", ex);
        }
    }
}
