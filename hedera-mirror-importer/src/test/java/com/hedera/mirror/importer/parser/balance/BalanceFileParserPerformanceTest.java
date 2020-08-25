package com.hedera.mirror.importer.parser.balance;

import java.nio.file.Path;
import javax.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.StreamType;

@Tag("performance")
public class BalanceFileParserPerformanceTest extends IntegrationTest {

    @TempDir
    static Path dataPath;

    @Value("classpath:data")
    Path testPath;

    @Resource
    private BalanceFileParser balanceFileParser;

    @Resource
    private BalanceParserProperties parserProperties;

    private FileCopier fileCopier;

    private StreamType streamType;

    @BeforeEach
    void before() {
        streamType = parserProperties.getStreamType();
        parserProperties.getMirrorProperties().setDataPath(dataPath);
        parserProperties.init();
    }

    @Timeout(15)
    @Test
    void parseAndIngestMultipleBalanceCsvFiles() {
        parse("*.csv");
    }

    private void parse(String filePath) {
        fileCopier = FileCopier.create(testPath, dataPath)
                .from(streamType.getPath(), "performance")
                .filterFiles(filePath)
                .to(streamType.getPath(), streamType.getValid());
        fileCopier.copy();

        balanceFileParser.parse();
    }
}
