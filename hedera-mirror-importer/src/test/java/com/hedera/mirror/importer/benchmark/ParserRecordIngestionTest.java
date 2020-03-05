package com.hedera.mirror.importer.benchmark;

import java.nio.file.Path;
import javax.annotation.Resource;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.jdbc.Sql;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.parser.record.RecordFileParser;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;

@Log4j2
@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:db/scripts/cleanup.sql")
@Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "classpath:db/scripts/cleanup.sql")
@Tag("performance")
public class ParserRecordIngestionTest extends IntegrationTest {

    @TempDir
    Path dataPath;

    @Value("classpath:data")
    Path testPath;

    @Resource
    private RecordFileParser recordFileParser;

    @Resource
    private RecordParserProperties parserProperties;

    private FileCopier fileCopier;

    private StreamType streamType;

    @BeforeEach
    void before() {
        streamType = parserProperties.getStreamType();
        parserProperties.getMirrorProperties().setDataPath(dataPath);
        parserProperties.init();
    }

    @Timeout(400)
    @Test
    void parseAndIngestMultipleFiles60000Transactions() throws Exception {
        fileCopier = FileCopier.create(testPath, dataPath)
                .from(streamType.getPath(), "performance")
                .filterFiles("*.rcd")
                .to(streamType.getPath(), streamType.getValid());
        fileCopier.copy();

        recordFileParser.parse();
    }
}
