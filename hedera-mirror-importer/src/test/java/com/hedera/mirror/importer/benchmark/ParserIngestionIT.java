package com.hedera.mirror.importer.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Stopwatch;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javax.annotation.Resource;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.jdbc.Sql;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.parser.record.RecordFileParser;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;

@Log4j2
@Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "classpath:db/scripts/cleanup.sql")
public class ParserIngestionIT extends IntegrationTest {

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

    @Test
    void parseAndIngestSingleFile5000Transactions() throws Exception {
        fileCopier = FileCopier.create(testPath, dataPath)
                .from(streamType.getPath(), "v2", "record0.0.3_1k_tps")
                .filterFiles("2020-02-09T18_30_00.000084Z.rcd")
                .to(streamType.getPath(), streamType.getValid());
        fileCopier.copy();
        parse(40);
    }

    @Test
    void parseAndIngestMultipleFiles10000Transactions() throws Exception {
        fileCopier = FileCopier.create(testPath, dataPath)
                .from(streamType.getPath(), "v2", "record0.0.3_1k_tps")
                .filterFiles("2020-02-09T18_30_0*.rcd")
                .to(streamType.getPath(), streamType.getValid());
        fileCopier.copy();
        parse(70);
    }

    @Test
    void parseAndIngestMultipleFiles60000Transactions() throws Exception {
        fileCopier = FileCopier.create(testPath, dataPath)
                .from(streamType.getPath(), "v2", "record0.0.3_1k_tps")
                .filterFiles("*.rcd")
                .to(streamType.getPath(), streamType.getValid());
        fileCopier.copy();
        parse(400);
    }

    private void parse(long parseTimeThreshold) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        recordFileParser.parse();

        long parseTime = stopwatch.elapsed(TimeUnit.SECONDS);
        log.info("Finished parsing record files in {} s. Threshold was {}", parseTime, parseTimeThreshold);
        assertThat(parseTime).isLessThanOrEqualTo(parseTimeThreshold);
    }
}
