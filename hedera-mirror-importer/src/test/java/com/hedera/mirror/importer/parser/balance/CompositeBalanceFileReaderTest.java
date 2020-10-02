package com.hedera.mirror.importer.parser.balance;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.parser.balance.v1.BalanceFileReaderImplV1;
import com.hedera.mirror.importer.parser.balance.v2.BalanceFileReaderImplV2;
//import com.hedera.mirror.importer.parser.balance.v1.BalanceFileReaderImplV1;
//import com.hedera.mirror.importer.parser.balance.v2.BalanceFileReaderImplV2;

@ExtendWith(MockitoExtension.class)
public class CompositeBalanceFileReaderTest {

    private static final String sampleBalanceFileName = "2019-08-30T18_15_00.016002001Z_Balances.csv";

    @TempDir
    Path dataPath;

    @Mock
    BalanceFileReaderImplV1 readerImplV1;

    @Mock
    BalanceFileReaderImplV2 readerImplV2;

    @Mock
    BalanceParserProperties properties;

    private CompositeBalanceFileReader compositeBalanceFileReader;

    private FileCopier fileCopier;
    private File sampleFile;
    private File testFile;

    @BeforeEach
    void setUp() throws IOException {
        var resource = new ClassPathResource("data");
        StreamType streamType = StreamType.BALANCE;
        fileCopier = FileCopier
                .create(resource.getFile().toPath(), dataPath)
                .from(streamType.getPath(), "balance0.0.3")
                .filterFiles(sampleBalanceFileName)
                .to(streamType.getPath(), streamType.getValid());
        sampleFile = fileCopier.getFrom().resolve(sampleBalanceFileName).toFile();
        testFile = fileCopier.getTo().resolve(sampleBalanceFileName).toFile();

        when(properties.getFileBufferSize()).thenReturn(200000);
        compositeBalanceFileReader = new CompositeBalanceFileReader(properties, readerImplV1, readerImplV2);
    }

    @Test
    void testFileV1() {
        fileCopier.copy();
        File balanceFile = fileCopier.getTo().resolve(sampleBalanceFileName).toFile();
        compositeBalanceFileReader.read(balanceFile);
        verify(readerImplV1, times(1)).read(balanceFile);
        verifyNoInteractions(readerImplV2);
    }

    @Test
    void testFileV2() throws IOException {
        List<String> lines = FileUtils.readLines(sampleFile, "utf-8");
        List<String> copy = new LinkedList<>();
        copy.add(CompositeBalanceFileReader.VERSION_2_HEADER_PREFIX);
        copy.addAll(lines);
        FileUtils.writeLines(testFile, copy);
        compositeBalanceFileReader.read(testFile);
        verify(readerImplV2, times(1)).read(testFile);
        verifyNoInteractions(readerImplV1);
    }

    @Test
    void testFileV2NotCommented() throws IOException {
        List<String> lines = FileUtils.readLines(sampleFile, "utf-8");
        List<String> copy = new LinkedList<>();
        copy.add("version:2");
        copy.addAll(lines);
        FileUtils.writeLines(testFile, copy);
        compositeBalanceFileReader.read(testFile);
        verify(readerImplV1, times(1)).read(testFile);
        verifyNoInteractions(readerImplV2);
    }
}
