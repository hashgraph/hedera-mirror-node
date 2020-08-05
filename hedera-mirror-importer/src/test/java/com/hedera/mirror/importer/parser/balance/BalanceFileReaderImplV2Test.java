package com.hedera.mirror.importer.parser.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import com.hedera.mirror.importer.util.Utility;

public class BalanceFileReaderImplV2Test {
    private static final String sampleBalanceFileName = "2019-08-30T18_15_00.016002001Z_Balances.csv";

    @TempDir
    Path dataPath;

    private MirrorProperties mirrorProperties;
    private BalanceFileReaderImplV2 balanceFileReader;
    private AccountBalanceLineParser parser;

    private long sampleConsensusTimestamp;
    private FileCopier fileCopier;
    private File sampleFile;
    private File testFile;

    @BeforeEach
    void setup() throws IOException {
        mirrorProperties = new MirrorProperties();
        parser = new AccountBalanceLineParser();
        balanceFileReader = new BalanceFileReaderImplV2(new BalanceParserProperties(mirrorProperties), parser);
        var resource = new ClassPathResource("data");
        StreamType streamType = StreamType.BALANCE;
        fileCopier = FileCopier
                .create(resource.getFile().toPath(), dataPath)
                .from(streamType.getPath(), "balance0.0.3")
                .filterFiles(sampleBalanceFileName)
                .to(streamType.getPath(), streamType.getValid());
        sampleFile = fileCopier.getFrom().resolve(sampleBalanceFileName).toFile();
        testFile = fileCopier.getTo().resolve(sampleBalanceFileName).toFile();
        sampleConsensusTimestamp = Utility.getTimestampFromFilename(sampleBalanceFileName);
    }

    @Test
    void readValid() throws IOException {
        fileCopier.copy();
        File balanceFile = fileCopier.getTo().resolve(sampleBalanceFileName).toFile();
        Stream<AccountBalance> accountBalanceStream = balanceFileReader.read(balanceFile);
        verifySuccess(balanceFile, accountBalanceStream, sampleConsensusTimestamp, 2);
    }

    @Test
    void readValidFileWithLeadingEmptyLine() throws IOException {
        List<String> lines = FileUtils.readLines(sampleFile, "utf-8");
        List<String> copy = new LinkedList<>();
        copy.add("");
        copy.addAll(lines);
        FileUtils.writeLines(testFile, lines);
        Stream<AccountBalance> accountBalanceStream = balanceFileReader.read(testFile);
        verifySuccess(testFile, accountBalanceStream, sampleConsensusTimestamp, 2);
    }

    @Test
    void readInvalidWhenFileHasNoTimestampHeader() throws IOException {
        List<String> lines = FileUtils.readLines(sampleFile, "utf-8");
        lines.remove(0);
        FileUtils.writeLines(testFile, lines);

        assertThrows(InvalidDatasetException.class, () -> {
            balanceFileReader.read(testFile);
        });
    }

    @Test
    void readInvalidWhenFileHasNoHeader() throws IOException {
        List<String> lines = FileUtils.readLines(sampleFile, "utf-8");
        lines.remove(0);
        lines.remove(0);
        FileUtils.writeLines(testFile, lines);

        assertThrows(InvalidDatasetException.class, () -> {
            balanceFileReader.read(testFile);
        });
    }

    @Test
    void readInvalidWhenFileHasNoColumnHeader() throws IOException {
        List<String> lines = FileUtils.readLines(sampleFile, "utf-8");
        lines.remove(1);
        FileUtils.writeLines(testFile, lines);

        assertThrows(InvalidDatasetException.class, () -> {
            balanceFileReader.read(testFile);
        });
    }

    @Test
    void readInvalidWhenFileIsEmpty() throws IOException {
        FileUtils.write(testFile, "", "utf-8");
        assertThrows(InvalidDatasetException.class, () -> {
            balanceFileReader.read(testFile);
        });
    }

    @Test
    void readInvalidWhenFileDoesNotExist() {
        assertThrows(InvalidDatasetException.class, () -> {
            balanceFileReader.read(testFile);
        });
    }

    @Test
    void readInvalidWhenFileHasMalformedTimestamp() throws IOException {
        List<String> lines = FileUtils.readLines(sampleFile, "utf-8");
        lines.remove(0);
        List<String> copy = new LinkedList<>();
        copy.add("Timestamp:AAAA-08-30T18:15:00.016002001Z");
        copy.addAll(lines);
        FileUtils.writeLines(testFile, lines);

        assertThrows(InvalidDatasetException.class, () -> {
            balanceFileReader.read(testFile);
        });
    }

    @Test
    void readValidWhenFileHasTrailingEmptyLines() throws IOException {
        fileCopier.copy();
        FileUtils.writeStringToFile(testFile, "\n\n\n", "utf-8",true);

        Stream<AccountBalance> accountBalanceStream = balanceFileReader.read(testFile);
        verifySuccess(testFile, accountBalanceStream, sampleConsensusTimestamp, 2);
    }

    @Test
    void readValidWhenFileHasBadTrailingLines() throws IOException {
        fileCopier.copy();
        FileUtils.writeStringToFile(testFile, "\n0.0.3.20340\nfoobar\n", "utf-8",true);

        Stream<AccountBalance> accountBalanceStream = balanceFileReader.read(testFile);
        verifySuccess(testFile, accountBalanceStream, sampleConsensusTimestamp, 2);
    }

    @Test
    void readValidWhenFileHasLinesWithDifferentShardNum() throws IOException {
        fileCopier.copy();
        long otherShard = mirrorProperties.getShard() + 1;
        FileUtils.writeStringToFile(testFile,
                String.format("\n%d,0,3,340\n%d,0,4,340\n", otherShard, otherShard), "utf-8",true);

        Stream<AccountBalance> accountBalanceStream = balanceFileReader.read(testFile);
        verifySuccess(testFile, accountBalanceStream, sampleConsensusTimestamp, 2);
    }

    private void verifySuccess(File file, Stream<AccountBalance> stream, long expectedConsensusTimestamp, int skipLines) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            while (skipLines > 0) {
                reader.readLine();
                skipLines--;
            }

            var lineIter = reader.lines().iterator();
            var accountBalanceIter = stream.iterator();

            while (lineIter.hasNext()) {
                String line = lineIter.next();
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                try {
                    AccountBalance expectedItem = parser.parse(line, expectedConsensusTimestamp, mirrorProperties.getShard());
                    AccountBalance actualItem = accountBalanceIter.next();
                    assertThat(actualItem).isEqualTo(expectedItem);
                } catch(InvalidDatasetException ex) {
                }
            }

            assertThat(accountBalanceIter.hasNext()).isFalse();
        }
    }
}
