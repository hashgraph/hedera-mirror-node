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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import javax.annotation.Resource;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import com.hedera.mirror.importer.reader.balance.line.AccountBalanceLineParserV2;
import com.hedera.mirror.importer.util.Utility;

class BalanceFileReaderImplV2Test extends IntegrationTest {

    private static final String VERSION_2_HEADER_PREFIX = "# version:2";
    private static final String VERSION_1_TIMESTAMP_HEADER_PREFIX = "timestamp:";

    @Resource
    private MirrorProperties mirrorProperties;
    @Resource
    private BalanceFileReaderImplV2 balanceFileReader;
    @Resource
    private AccountBalanceLineParserV2 parser;

    private long sampleConsensusTimestamp;

    @Value("classpath:data/accountBalances/v2/2020-09-22T04_25_00.083212003Z_Balances.csv")
    private File balanceFile;
    private File testFile;

    @BeforeEach
    void setup() throws IOException {
        sampleConsensusTimestamp = Utility.getTimestampFromFilename(balanceFile.getName());
        testFile = Files.createTempFile(null, null).toFile();
    }

    @Test
    void readValid() throws IOException {
        List<AccountBalance> accountBalances = new ArrayList<>();
        balanceFileReader.read(StreamFileData.from(balanceFile), accountBalances::add);
        verifySuccess(balanceFile, accountBalances, sampleConsensusTimestamp, 2);
    }

    @Test
    void readInvalidFileWithLeadingEmptyLine() throws IOException {
        List<String> lines = FileUtils.readLines(balanceFile, "utf-8");
        List<String> copy = new LinkedList<>();
        copy.add("");
        copy.addAll(lines);
        FileUtils.writeLines(testFile, copy);
        assertThrows(InvalidDatasetException.class, () -> {
            balanceFileReader.read(StreamFileData.from(testFile));
        });
    }

    @Test
    void readInvalidWhenFileHasNoVersionHeader() throws IOException {
        List<String> lines = FileUtils.readLines(balanceFile, "utf-8");
        lines.remove(0);
        FileUtils.writeLines(testFile, lines);

        assertThrows(InvalidDatasetException.class, () -> {
            balanceFileReader.read(StreamFileData.from(testFile));
        });
    }

    @Test
    void readInvalidWhenFileHasNoTimestampHeader() throws IOException {
        List<String> lines = FileUtils.readLines(balanceFile, "utf-8");
        lines.remove(1);
        FileUtils.writeLines(testFile, lines);

        assertThrows(InvalidDatasetException.class, () -> {
            balanceFileReader.read(StreamFileData.from(testFile));
        });
    }

    @Test
    void readInvalidWhenFileHasV1TimestampHeader() throws IOException {
        List<String> lines = FileUtils.readLines(balanceFile, "utf-8");
        lines.remove(1);
        List<String> copy = new LinkedList<>();
        copy.add(lines.get(0));
        lines.remove(0);
        copy.add("TimeStamp:2020-09-22T04:25:00.083212003Z");
        copy.addAll(lines);
        FileUtils.writeLines(testFile, copy);

        assertThrows(InvalidDatasetException.class, () -> {
            balanceFileReader.read(StreamFileData.from(testFile));
        });
    }

    @Test
    void readInvalidWhenFileHasNoHeader() throws IOException {
        List<String> lines = FileUtils.readLines(balanceFile, "utf-8");
        lines.remove(0);
        lines.remove(0);
        lines.remove(0);
        FileUtils.writeLines(testFile, lines);

        assertThrows(InvalidDatasetException.class, () -> {
            balanceFileReader.read(StreamFileData.from(testFile));
        });
    }

    @Test
    void readInvalidWhenFileHasNoColumnHeader() throws IOException {
        List<String> lines = FileUtils.readLines(balanceFile, "utf-8");
        lines.remove(2);
        FileUtils.writeLines(testFile, lines);

        assertThrows(InvalidDatasetException.class, () -> {
            balanceFileReader.read(StreamFileData.from(testFile));
        });
    }

    @Test
    void readInvalidWhenFileIsEmpty() throws IOException {
        FileUtils.write(testFile, "", "utf-8");
        assertThrows(InvalidDatasetException.class, () -> {
            balanceFileReader.read(StreamFileData.from(testFile));
        });
    }

    @Test
    void readInvalidWhenFileDoesNotExist() {
        assertThrows(InvalidDatasetException.class, () -> {
            balanceFileReader.read(StreamFileData.from(testFile));
        });
    }

    @Test
    void readInvalidWhenFileHasMalformedTimestamp() throws IOException {
        List<String> lines = FileUtils.readLines(balanceFile, "utf-8");
        lines.remove(1);
        List<String> copy = new LinkedList<>();
        copy.add(lines.get(0));
        lines.remove(0);
        copy.add("# Timestamp:AAAA-08-30T18:15:00.016002001Z");
        copy.addAll(lines);
        FileUtils.writeLines(testFile, copy);

        assertThrows(InvalidDatasetException.class, () -> {
            balanceFileReader.read(StreamFileData.from(testFile));
        });
    }

    @Test
    void readValidWhenFileHasTrailingEmptyLines() throws IOException {
        List<String> lines = FileUtils.readLines(balanceFile, "utf-8");
        FileUtils.writeLines(testFile, lines);
        FileUtils.writeStringToFile(testFile, "\n\n\n", "utf-8", true);

        List<AccountBalance> accountBalances = new ArrayList<>();
        balanceFileReader.read(StreamFileData.from(testFile), accountBalances::add);
        verifySuccess(testFile, accountBalances, sampleConsensusTimestamp, 2);
    }

    @Test
    void readValidWhenFileHasBadTrailingLines() throws IOException {
        List<String> lines = FileUtils.readLines(balanceFile, "utf-8");
        FileUtils.writeLines(testFile, lines);
        FileUtils.writeStringToFile(testFile, "\n0.0.3.20340\nfoobar\n", "utf-8", true);

        List<AccountBalance> accountBalances = new ArrayList<>();
        balanceFileReader.read(StreamFileData.from(testFile), accountBalances::add);
        verifySuccess(testFile, accountBalances, sampleConsensusTimestamp, 2);
    }

    @Test
    void readValidWhenFileHasLinesWithDifferentShardNum() throws IOException {
        List<String> lines = FileUtils.readLines(balanceFile, "utf-8");
        FileUtils.writeLines(testFile, lines);
        long otherShard = mirrorProperties.getShard() + 1;
        FileUtils.writeStringToFile(testFile,
                String.format("\n%d,0,3,340\n%d,0,4,340\n", otherShard, otherShard), "utf-8", true);

        List<AccountBalance> accountBalances = new ArrayList<>();
        balanceFileReader.read(StreamFileData.from(testFile), accountBalances::add);
        verifySuccess(testFile, accountBalances, sampleConsensusTimestamp, 2);
    }

    @Test
    void fileVersion2HeaderConfirmed() {
        StreamFileData streamFileData = StreamFileData.from("foo.csv", VERSION_2_HEADER_PREFIX);
        assertThat(balanceFileReader.supports(streamFileData)).isTrue();
    }

    @Test
    void fileVersion1HeaderIsRejected() {
        StreamFileData streamFileData = StreamFileData.from("foo.csv", VERSION_1_TIMESTAMP_HEADER_PREFIX);
        assertThat(balanceFileReader.supports(streamFileData)).isFalse();
    }

    @Test
    void nullFirstLineIsRejected() {
        StreamFileData streamFileData = StreamFileData.from("foo.csv", "");
        assertThat(balanceFileReader.supports(streamFileData)).isFalse();
    }

    @Test
    void randomFirstLineIsRejected() {
        StreamFileData streamFileData = StreamFileData.from("foo.csv", "junk");
        assertThat(balanceFileReader.supports(streamFileData)).isFalse();
    }

    private void verifySuccess(File file, List<AccountBalance> accountBalances, long expectedConsensusTimestamp,
                               int skipLines) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            while (skipLines > 0) {
                reader.readLine();
                skipLines--;
            }

            var lineIter = reader.lines().iterator();
            var accountBalanceIter = accountBalances.iterator();

            while (lineIter.hasNext()) {
                String line = lineIter.next();
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                try {
                    AccountBalance expectedItem = parser
                            .parse(line, expectedConsensusTimestamp, mirrorProperties.getShard());
                    AccountBalance actualItem = accountBalanceIter.next();
                    assertThat(actualItem).isEqualTo(expectedItem);
                } catch (InvalidDatasetException ex) {
                }
            }

            assertThat(accountBalanceIter.hasNext()).isFalse();
        }
    }
}
