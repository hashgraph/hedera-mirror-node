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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.importer.domain.AccountBalanceFile;
import com.hedera.mirror.importer.domain.StreamFileData;

@ExtendWith(MockitoExtension.class)
public class CompositeBalanceFileReaderTest {

    private static final String BALANCE_FILENAME_PREFIX = "2021-03-15T14_30_00Z_Balances";

    @Mock(lenient = true)
    private BalanceFileReaderImplV1 readerImplV1;

    @Mock
    private BalanceFileReaderImplV2 readerImplV2;

    @Mock
    private ProtoBalanceFileReader protoBalanceFileReader;

    @InjectMocks
    private CompositeBalanceFileReader compositeBalanceFileReader;

    private final AccountBalanceFile accountBalanceFile = AccountBalanceFile.builder().count(1L).build();

    @Test
    void defaultsToVersion1Reader() {
        StreamFileData streamFileData = StreamFileData.from(BALANCE_FILENAME_PREFIX + ".csv", "timestamp:1");
        configMockReader(protoBalanceFileReader, streamFileData, false);
        configMockReader(readerImplV1, streamFileData, true);

        compositeBalanceFileReader.read(streamFileData);

        verify(readerImplV1, times(1)).read(streamFileData);
        verify(readerImplV2, never()).read(streamFileData);
        verify(protoBalanceFileReader, never()).read(streamFileData);
    }

    @Test
    void usesVersion2Reader() {
        StreamFileData streamFileData = StreamFileData.from(BALANCE_FILENAME_PREFIX + ".csv", "# version:2");
        configMockReader(protoBalanceFileReader, streamFileData, false);
        configMockReader(readerImplV2, streamFileData, true);

        compositeBalanceFileReader.read(streamFileData);

        verify(readerImplV2, times(1)).read(streamFileData);
        verify(readerImplV1, never()).read(streamFileData);
        verify(protoBalanceFileReader, never()).read(streamFileData);
    }

    @Test
    void usesProtoBalanceFileReader() {
        StreamFileData streamFileData = StreamFileData
                .from(BALANCE_FILENAME_PREFIX + ".pb.gz", "proto-based balance file");
        configMockReader(protoBalanceFileReader, streamFileData, true);

        compositeBalanceFileReader.read(streamFileData);

        verify(protoBalanceFileReader, times(1)).read(streamFileData);
        verify(readerImplV1, never()).read(streamFileData);
        verify(readerImplV2, never()).read(streamFileData);
    }

    private void configMockReader(BalanceFileReader reader, StreamFileData streamFileData, boolean supports) {
        if (supports) {
            when(reader.supports(streamFileData)).thenReturn(true);
            when(reader.read(streamFileData)).thenReturn(accountBalanceFile);
        } else {
            when(reader.supports(streamFileData)).thenReturn(false);
        }
    }
}
