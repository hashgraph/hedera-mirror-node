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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Flux;

import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.domain.AccountBalanceFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import com.hedera.mirror.importer.parser.balance.BalanceParserProperties;
import com.hedera.mirror.importer.reader.balance.line.AccountBalanceLineParser;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@RequiredArgsConstructor
public abstract class CsvBalanceFileReader implements BalanceFileReader {

    static final int BUFFER_SIZE = 16;
    static final Charset CHARSET = StandardCharsets.UTF_8;
    static final String COLUMN_HEADER_PREFIX = "shard";
    private static final String FILE_EXTENSION = "csv";

    private final BalanceParserProperties balanceParserProperties;
    private final AccountBalanceLineParser parser;

    @Override
    public boolean supports(StreamFileData streamFileData) {
        if (!FILE_EXTENSION.equals(streamFileData.getStreamFilename().getExtension().getName())) {
            return false;
        }

        InputStream inputStream = streamFileData.getInputStream();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, CHARSET), BUFFER_SIZE)) {
            String firstLine = reader.readLine();
            return firstLine != null && supports(firstLine);
        } catch (Exception e) {
            throw new InvalidDatasetException("Error reading account balance file", e);
        }
    }

    protected boolean supports(String firstLine) {
        return StringUtils.startsWithIgnoreCase(firstLine, getVersionHeaderPrefix());
    }

    protected abstract String getTimestampHeaderPrefix();

    protected abstract String getVersionHeaderPrefix();

    @Override
    public AccountBalanceFile read(StreamFileData streamFileData) {
        MessageDigest messageDigest = DigestUtils.getSha384Digest();
        int bufferSize = balanceParserProperties.getFileBufferSize();

        try (InputStream inputStream = new DigestInputStream(streamFileData.getInputStream(), messageDigest);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, CHARSET), bufferSize)) {
            long consensusTimestamp = parseConsensusTimestamp(reader);
            AtomicLong count = new AtomicLong(0L);
            List<AccountBalance> items = new ArrayList<>();

            AccountBalanceFile accountBalanceFile = new AccountBalanceFile();
            accountBalanceFile.setBytes(streamFileData.getBytes());
            accountBalanceFile.setConsensusTimestamp(consensusTimestamp);
            accountBalanceFile.setLoadStart(Instant.now().getEpochSecond());
            accountBalanceFile.setName(streamFileData.getFilename());

            reader.lines()
                    .map(line -> {
                        try {
                            AccountBalance accountBalance = parser.parse(line, consensusTimestamp);
                            count.incrementAndGet();
                            return accountBalance;
                        } catch (InvalidDatasetException ex) {
                            log.error(ex);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .forEachOrdered(items::add);

            accountBalanceFile.setCount(count.get());
            accountBalanceFile.setFileHash(Utility.bytesToHex(messageDigest.digest()));
            accountBalanceFile.setItems(Flux.fromIterable(items));
            return accountBalanceFile;
        } catch (IOException ex) {
            throw new InvalidDatasetException("Error reading account balance file", ex);
        }
    }

    protected abstract long parseConsensusTimestamp(BufferedReader reader);

    protected long convertTimestamp(String timestamp) {
        Instant instant = Instant.parse(timestamp);
        return Utility.convertToNanosMax(instant);
    }
}
