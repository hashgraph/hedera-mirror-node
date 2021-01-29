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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.importer.exception.InvalidDatasetException;

@ExtendWith(MockitoExtension.class)
public class CompositeBalanceFileReaderTest {

    @Mock
    BalanceFileReaderImplV1 readerImplV1;

    @Mock
    BalanceFileReaderImplV2 readerImplV2;

    private CompositeBalanceFileReader compositeBalanceFileReader;

    private File balanceFile;

    @BeforeEach
    void setUp() throws IOException {
        balanceFile = Files.createTempFile(null, null).toFile();
        compositeBalanceFileReader = new CompositeBalanceFileReader(readerImplV1, readerImplV2);
    }

    @Test
    void defaultsToVersion1Reader() throws IOException {
        String firstLine = "null";
        FileUtils.writeStringToFile(balanceFile, firstLine, "utf-8");
        when(readerImplV2.isFirstLineFromFileVersion(firstLine)).thenReturn(false);
        compositeBalanceFileReader.read(balanceFile);
        verify(readerImplV1, times(1)).read(balanceFile);
        verify(readerImplV2, times(0)).read(balanceFile);
    }

    @Test
    void usesVersion2Reader() throws IOException {
        String firstLine = "# version:2";
        FileUtils.writeStringToFile(balanceFile, firstLine, "utf-8");
        when(readerImplV2.isFirstLineFromFileVersion(firstLine)).thenReturn(true);
        compositeBalanceFileReader.read(balanceFile);
        verify(readerImplV2, times(1)).read(balanceFile);
        verify(readerImplV1, times(0)).read(balanceFile);
    }

    @Test
    void verifyLongFirstLineStopsAtBuffer() throws IOException {
        String firstLine = "A".repeat(32);
        String bufferedFirstLine = "A".repeat(CompositeBalanceFileReader.BUFFER_SIZE);
        FileUtils.writeStringToFile(balanceFile, firstLine, "utf-8");
        when(readerImplV2.isFirstLineFromFileVersion(bufferedFirstLine)).thenReturn(false);
        compositeBalanceFileReader.read(balanceFile);
        verify(readerImplV1, times(1)).read(balanceFile);
        verify(readerImplV2, times(0)).read(balanceFile);
    }

    @Test
    void missingFile() throws IOException {
        assertThrows(InvalidDatasetException.class, () -> {
            compositeBalanceFileReader.read(new File(""));
        });
    }

    @Test
    void nullFile() throws IOException {
        assertThrows(InvalidDatasetException.class, () -> {
            compositeBalanceFileReader.read(null);
        });
    }

    @Test
    void emptyFile() throws IOException {
        assertThrows(InvalidDatasetException.class, () -> {
            compositeBalanceFileReader.read(balanceFile);
        });
    }
}
