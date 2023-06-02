/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.parser.batch;

import com.hedera.mirror.common.domain.transaction.TransactionHash;
import jakarta.inject.Named;
import java.sql.Connection;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import lombok.CustomLog;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@RequiredArgsConstructor
@CustomLog
@Named
public class TransactionHashTxManager implements TransactionSynchronization {
    private final Map<String, ThreadState> threadConnections = new ConcurrentHashMap<>();
    private final DataSource dataSource;
    private long itemCount;
    private long recordTimestamp;
    private String shardedTableName;

    @Override
    public void afterCompletion(int status) {
        var failedShards = new TreeSet<Integer>();
        var successfulShards = new TreeSet<Integer>();

        for (ThreadState threadState : threadConnections.values()) {
            try (Connection connection = threadState.getConnection()) {
                if (status == STATUS_COMMITTED) {
                    connection.commit();
                    successfulShards.addAll(threadState.getProcessedShards());
                    threadState.setStatus(STATUS_COMMITTED);
                } else if (status == STATUS_ROLLED_BACK) {
                    connection.rollback();
                    successfulShards.addAll(threadState.getProcessedShards());
                    threadState.setStatus(STATUS_ROLLED_BACK);
                } else {
                    connection.rollback();
                    failedShards.addAll(threadState.getProcessedShards());
                    threadState.setStatus(STATUS_UNKNOWN);
                }
            } catch (Exception e) {
                log.error(
                        "Received exception processing connections for shards {} in file containing " + "timestamp {}",
                        threadState.getProcessedShards(),
                        recordTimestamp,
                        e);
                threadState.setStatus(Integer.MAX_VALUE);
                failedShards.addAll(threadState.getProcessedShards());
            }
        }

        String statusString =
                switch (status) {
                    case STATUS_COMMITTED -> "committed";
                    case STATUS_ROLLED_BACK -> "rolled back";
                    default -> "unknown";
                };

        if (failedShards.isEmpty()) {
            log.debug(
                    "Successfully {} {} items in {} shards {} in file containing timestamp {}",
                    statusString,
                    itemCount,
                    shardedTableName,
                    successfulShards,
                    recordTimestamp);
        } else {
            log.error(
                    "Errors occurred processing sharded table {}. parent status {} successful shards {} "
                            + "failed shards {} in file containing timestamp {}",
                    shardedTableName,
                    statusString,
                    successfulShards,
                    failedShards,
                    recordTimestamp);
        }
        threadConnections.clear();
    }

    public void initialize(Collection<?> items, String shardedTableName) {
        // This will be non-empty when there are multiple calls to persist
        // in the same parent transaction which is the case if batch limit is reached
        if (!threadConnections.isEmpty()) {
            this.itemCount += items.size();
            return;
        }

        this.shardedTableName = shardedTableName;
        this.itemCount = items.size();
        this.recordTimestamp = ((TransactionHash) items.iterator().next()).getConsensusTimestamp();
        TransactionSynchronizationManager.registerSynchronization(this);
    }

    /**
     * Start new transaction or update state of existing transaction
     *
     * @return state of the thread
     */
    public ThreadState updateAndGetThreadState(int shard) {
        return threadConnections.compute(Thread.currentThread().getName(), (key, value) -> {
            if (value == null) {
                ThreadState returnVal = setupThreadTransaction();
                returnVal.getProcessedShards().add(shard);

                return returnVal;
            }
            value.getProcessedShards().add(shard);
            return value;
        });
    }

    @SneakyThrows
    private ThreadState setupThreadTransaction() {
        // Clean thread from previous run
        TransactionSynchronizationManager.clear();
        TransactionSynchronizationManager.unbindResourceIfPossible(dataSource);

        // initialize transaction for thread
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        // Subsequent calls to get connection on this thread will use the same connection
        Connection connection = DataSourceUtils.getConnection(dataSource);
        connection.setAutoCommit(false);
        return new ThreadState(connection);
    }

    Map<String, ThreadState> getThreadConnections() {
        return threadConnections;
    }

    long getItemCount() {
        return itemCount;
    }

    @Data
    @ToString(exclude = "connection")
    static class ThreadState {
        private final Connection connection;
        private final Set<Integer> processedShards = new HashSet<>();
        private int status = -1;

        public ThreadState(Connection connection) {
            this.connection = connection;
        }
    }
}
