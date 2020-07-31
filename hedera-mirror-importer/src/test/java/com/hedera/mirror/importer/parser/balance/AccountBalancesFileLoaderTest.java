package com.hedera.mirror.importer.parser.balance;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import com.hedera.mirror.importer.parser.domain.AccountBalanceItem;
import com.hedera.mirror.importer.util.Utility;

@ExtendWith(MockitoExtension.class)
public class AccountBalancesFileLoaderTest {
    private static final String sampleFileName = "2019-08-30T18_15_00.016002001Z_Balances.csv";

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement ps;

    @Mock
    private BalanceFileReader balanceFileReader;

    private AccountBalancesFileLoader loader;

    private File testFile;

    private long fileTimestamp;

    private long  systemShardNum;

    private final static int insertBatchSize = 200;

    @BeforeEach
    void setup() throws SQLException {
        BalanceParserProperties balanceParserProperties = new BalanceParserProperties(new MirrorProperties());
        balanceParserProperties.setBatchSize(insertBatchSize);
        loader = new AccountBalancesFileLoader(balanceParserProperties, dataSource, balanceFileReader);

        lenient().doReturn(connection).when(dataSource).getConnection();
        lenient().doReturn(ps).when(connection).prepareStatement(any(String.class));

        testFile = new File(sampleFileName);
        fileTimestamp = Utility.getTimestampFromFilename(sampleFileName);
        systemShardNum = balanceParserProperties.getMirrorProperties().getShard();
    }

    @ParameterizedTest(name = "{0} account balance lines")
    @MethodSource("createAccountBalancesLineCounts")
    void loadAccountBalancesWithValidFile(int accountBalanceLineCount) throws SQLException {
        AtomicInteger itemCountInBatch = new AtomicInteger();
        AtomicInteger totalItemCount = new AtomicInteger();
        var itemStream = createAccountBalanceItemStream(accountBalanceLineCount, systemShardNum, fileTimestamp);
        doReturn(itemStream).when(balanceFileReader).read(testFile);
        doAnswer((Answer<Void>) invocation -> {
            itemCountInBatch.getAndIncrement();
            totalItemCount.getAndIncrement();
            return null;
        }).when(ps).addBatch();
        doAnswer((Answer<int[]>) invocation -> {
            int[] result = new int[itemCountInBatch.get()];
            itemCountInBatch.set(0);
            Arrays.fill(result, 1);
            return result;
        }).when(ps).executeBatch();

        verifyCompleteOrPartialInsertFailure(accountBalanceLineCount, 0);
    }

    @Test
    void loadAccountBalancesWhenSomeBatchInsertFail() throws SQLException {
        final int accountBalanceLineCount = 310;
        AtomicInteger itemCountInBatch = new AtomicInteger();
        AtomicInteger totalItemCount = new AtomicInteger();
        var itemStream = createAccountBalanceItemStream(accountBalanceLineCount, systemShardNum, fileTimestamp);
        doReturn(itemStream).when(balanceFileReader).read(testFile);
        doAnswer((Answer<Void>) invocation -> {
            itemCountInBatch.getAndIncrement();
            totalItemCount.getAndIncrement();
            return null;
        }).when(ps).addBatch();
        doAnswer((Answer<int[]>) invocation -> {
            int[] result = new int[itemCountInBatch.get()];
            itemCountInBatch.set(0);
            Arrays.fill(result, 1);
            result[result.length - 1] = Statement.EXECUTE_FAILED; // make the last one fail
            return result;
        }).when(ps).executeBatch();

        verifyCompleteOrPartialInsertFailure(accountBalanceLineCount, (accountBalanceLineCount + insertBatchSize - 1) / insertBatchSize);
    }

    @Test
    void loadAccountBalancesWhenShardNumMismatch() throws SQLException {
        var itemStream = createAccountBalanceItemStream(300, systemShardNum+1, fileTimestamp);
        doReturn(itemStream).when(balanceFileReader).read(testFile);

        assertThat(loader.loadAccountBalances(testFile)).isTrue();
        verify(dataSource).getConnection();
        verify(connection, atLeast(1)).prepareStatement(any(String.class));
        verify(ps, atLeast(1)).execute();
        verify(ps, never()).executeBatch();
        verify(ps, never()).addBatch();
    }

    @Test
    void loadAccountBalancesWhenReaderThrowsException() throws SQLException {
        doThrow(InvalidDatasetException.class).when(balanceFileReader).read(testFile);

        assertThat(loader.loadAccountBalances(testFile)).isFalse();
        verify(dataSource).getConnection();
        verify(connection, atLeast(1)).prepareStatement(any(String.class));
        verify(ps, never()).execute();
        verify(ps, never()).executeBatch();
    }

    @Test
    void loadAccountBalancesWhenGetConnectionThrowsException() throws SQLException {
        doThrow(SQLException.class).when(dataSource).getConnection();

        assertThat(loader.loadAccountBalances(testFile)).isFalse();
        verify(dataSource).getConnection();
        verify(connection, never()).prepareStatement(any(String.class));
        verify(ps, never()).execute();
        verify(ps, never()).executeBatch();
    }

    @Test
    void loadAccountBalancesWhenPrepareStatementThrowsException() throws SQLException {
        doThrow(SQLException.class).when(connection).prepareStatement(any(String.class));

        assertThat(loader.loadAccountBalances(testFile)).isFalse();
        verify(dataSource).getConnection();
        verify(connection).prepareStatement(any(String.class));
        verify(ps, never()).execute();
        verify(ps, never()).executeBatch();
    }

    private Stream<AccountBalanceItem> createAccountBalanceItemStream(int count, long shardNum, long timestamp) {
        return LongStream.rangeClosed(1, count)
                .boxed()
                .map(accountNum -> AccountBalanceItem.of(String.format("%d,0,%d,100", shardNum, accountNum), timestamp));
    }

    private void verifyCompleteOrPartialInsertFailure(int accountBalanceLineCount,
            int failedAccountBalanceLineCount) throws SQLException {
        assertThat(loader.loadAccountBalances(testFile)).isEqualTo(failedAccountBalanceLineCount == 0);
        verify(dataSource).getConnection();
        verify(connection, atLeast(1)).prepareStatement(any(String.class));
        verify(ps, atLeast(1)).execute();
        int expectedBatchCount = (accountBalanceLineCount + insertBatchSize - 1) / insertBatchSize;
        verify(ps, times(expectedBatchCount)).executeBatch();
        verify(ps, times(accountBalanceLineCount)).addBatch();
    }

    private static Stream<Arguments> createAccountBalancesLineCounts() {
        return Stream.of(
                Arguments.of(insertBatchSize - 1),
                Arguments.of(2 * insertBatchSize - 1),
                Arguments.of(2 * insertBatchSize),
                Arguments.of(2 * insertBatchSize + 1)
        );
    }
}
