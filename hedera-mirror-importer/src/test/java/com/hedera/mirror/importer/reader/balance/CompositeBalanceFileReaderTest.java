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

import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.domain.StreamFileData;

@ExtendWith(MockitoExtension.class)
public class CompositeBalanceFileReaderTest {

    private static final String BALANCE_FILENAME_PREFIX = "2021-03-15T14_30_00Z_Balances";

    @Mock
    private BalanceFileReaderImplV1 readerImplV1;

    @Mock
    private BalanceFileReaderImplV2 readerImplV2;

    @Mock
    private ProtoBalanceFileReader protoBalanceFileReader;

    @InjectMocks
    private CompositeBalanceFileReader compositeBalanceFileReader;

    private final Consumer<AccountBalance> consumer = accountBalance -> {
    };

    @Test
    void defaultsToVersion1Reader() {
        StreamFileData streamFileData = StreamFileData.from(BALANCE_FILENAME_PREFIX + ".csv", "timestamp:1");
        when(protoBalanceFileReader.supports(streamFileData)).thenReturn(false);
        when(readerImplV2.supports(streamFileData)).thenReturn(false);
        compositeBalanceFileReader.read(streamFileData, consumer);
        verify(readerImplV1, times(1)).read(streamFileData, consumer);
        verify(readerImplV2, never()).read(streamFileData, consumer);
        verify(protoBalanceFileReader, never()).read(streamFileData, consumer);
    }

    @Test
    void usesVersion2Reader() {
        StreamFileData streamFileData = StreamFileData.from(BALANCE_FILENAME_PREFIX + ".csv", "# version:2");
        when(protoBalanceFileReader.supports(streamFileData)).thenReturn(false);
        when(readerImplV2.supports(streamFileData)).thenReturn(true);
        compositeBalanceFileReader.read(streamFileData, consumer);
        verify(readerImplV2, times(1)).read(streamFileData, consumer);
        verify(readerImplV1, never()).read(streamFileData, consumer);
        verify(protoBalanceFileReader, never()).read(streamFileData, consumer);
    }

    @Test
    void usesProtoBalanceFileReader() {
        StreamFileData streamFileData = StreamFileData.from(BALANCE_FILENAME_PREFIX + ".pb.gz", "proto-based balance file");
        when(protoBalanceFileReader.supports(streamFileData)).thenReturn(true);
        compositeBalanceFileReader.read(streamFileData, consumer);
        verify(protoBalanceFileReader, times(1)).read(streamFileData, consumer);
        verify(readerImplV1, never()).read(streamFileData, consumer);
        verify(readerImplV2, never()).read(streamFileData, consumer);
    }
}
