package com.hedera.mirror.importer.migration;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.importer.reader.record.RecordFileReader.MAX_TRANSACTION_LENGTH;

import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Named;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.transaction.support.TransactionOperations;

import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor.DateRangeFilter;
import com.hedera.mirror.importer.exception.FileOperationException;
import com.hedera.mirror.importer.parser.balance.BalanceStreamFileListener;
import com.hedera.mirror.importer.parser.record.RecordStreamFileListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.parser.record.entity.EntityRecordItemListener;
import com.hedera.mirror.importer.reader.ValidatedDataInputStream;
import com.hedera.mirror.importer.repository.TokenTransferRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;

/**
 * Adds errata information to the database to workaround older, incorrect data on mainnet. See docs/database.md#errata
 * for more detail.
 */
@Named
public class ErrataMigration extends RepeatableMigration implements BalanceStreamFileListener {

    private static final int ACCOUNT_BALANCE_FILE_FIXED_TIME_OFFSET = 53;
    // The consensus timestamps of the first and the last account balance files in mainnet to add the fixed 53ns offset
    private static final long FIRST_ACCOUNT_BALANCE_FILE_TIMESTAMP = 1658420100626004000L;
    private static final long LAST_ACCOUNT_BALANCE_FILE_TIMESTAMP = 1666368000880378770L;

    @Value("classpath:errata/mainnet/balance-offsets.txt")
    private final Resource balanceOffsets;
    private final EntityRecordItemListener entityRecordItemListener;
    private final EntityProperties entityProperties;
    private final NamedParameterJdbcOperations jdbcOperations;
    private final MirrorProperties mirrorProperties;
    private final RecordStreamFileListener recordStreamFileListener;
    private final TokenTransferRepository tokenTransferRepository;
    private final TransactionOperations transactionOperations;
    private final TransactionRepository transactionRepository;
    private final Set<Long> timestamps = new HashSet<>();

    @Lazy
    @SuppressWarnings("java:S107")
    public ErrataMigration(Resource balanceOffsets,
                           EntityRecordItemListener entityRecordItemListener,
                           EntityProperties entityProperties,
                           NamedParameterJdbcOperations jdbcOperations,
                           MirrorProperties mirrorProperties,
                           RecordStreamFileListener recordStreamFileListener,
                           TokenTransferRepository tokenTransferRepository,
                           TransactionOperations transactionOperations,
                           TransactionRepository transactionRepository) {
        super(mirrorProperties.getMigration());
        this.balanceOffsets = balanceOffsets;
        this.entityRecordItemListener = entityRecordItemListener;
        this.entityProperties = entityProperties;
        this.jdbcOperations = jdbcOperations;
        this.mirrorProperties = mirrorProperties;
        this.recordStreamFileListener = recordStreamFileListener;
        this.tokenTransferRepository = tokenTransferRepository;
        this.transactionOperations = transactionOperations;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public String getDescription() {
        return "Add errata information to the database to workaround older, incorrect data";
    }

    @Override
    public void onStart() {
        // Not applicable
    }

    @Override
    public void onEnd(AccountBalanceFile accountBalanceFile) {
        if (isMainnet()) {
            long consensusTimestamp = accountBalanceFile.getConsensusTimestamp();
            if (getTimestamps().contains(consensusTimestamp)) {
                accountBalanceFile.setTimeOffset(-1);
            }

            if (shouldApplyFixedTimeOffset(consensusTimestamp)) {
                accountBalanceFile.setTimeOffset(ACCOUNT_BALANCE_FILE_FIXED_TIME_OFFSET);
            }
        }
    }

    @Override
    public void onError() {
        // Not applicable
    }

    @Override
    protected void doMigrate() throws IOException {
        if (isMainnet()) {
            boolean trackBalance = entityProperties.getPersist().isTrackBalance();
            entityProperties.getPersist().setTrackBalance(false);

            try {
                transactionOperations.executeWithoutResult(t -> {
                    balanceFileAdjustment();
                    spuriousTransfers();
                    missingTransactions();
                });
            } finally {
                entityProperties.getPersist().setTrackBalance(trackBalance);
            }
        }
    }

    private void balanceFileAdjustment() {
        // Adjusts the balance file's consensus timestamp by -1 for use when querying transfers.
        String sql = """
                update account_balance_file set time_offset = -1
                where consensus_timestamp in (:timestamps) and time_offset <> -1
                """;
        int count = jdbcOperations.update(sql, new MapSqlParameterSource("timestamps", getTimestamps()));

        // Set the fixed time offset for account balance files in the applicable range
        sql = """
                update account_balance_file set time_offset = :fixedTimeOffset
                where consensus_timestamp >= :firstTimestamp and consensus_timestamp <= :lastTimestamp
                """;
        var paramSource = new MapSqlParameterSource("fixedTimeOffset", ACCOUNT_BALANCE_FILE_FIXED_TIME_OFFSET)
                .addValue("firstTimestamp", FIRST_ACCOUNT_BALANCE_FILE_TIMESTAMP)
                .addValue("lastTimestamp", LAST_ACCOUNT_BALANCE_FILE_TIMESTAMP);
        count += jdbcOperations.update(sql, paramSource);
        log.info("Updated {} account balance files", count);
    }

    /**
     * Marks the extra transfers erroneously added by services for deletion. Since this issue was fixed around October
     * 3, 2019, we only consider transfers before consensus timestamp 1577836799000000000.
     * <p>
     * This query works by finding the credit, non-fee transfer inside the CTE then use the negative of that to find its
     * corresponding debit and mark both as errata=DELETED. We define fee transfers as coming from 0.0.98 or in the
     * range 0.0.3 to 0.0.27, so we exclude those. Additionally, two timestamps (1570118944399195000,
     * 1570120372315307000) have a corner case where the credit has the payer to be same as the receiver.
     */
    private void spuriousTransfers() {
        String sql = """
                with spurious_transfer as (
                  update crypto_transfer ct
                  set errata = 'DELETE'
                  from transaction t
                  where t.consensus_timestamp = ct.consensus_timestamp and t.payer_account_id = ct.payer_account_id and
                    t.type = 14 and t.result <> 22 and
                    t.consensus_timestamp < 1577836799000000000 and amount > 0 and ct.entity_id <> 98 and
                    (ct.entity_id < 3 or ct.entity_id > 27) and ((ct.entity_id <> ct.payer_account_id) or
                      (ct.consensus_timestamp in (1570118944399195000, 1570120372315307000)
                        and ct.entity_id = ct.payer_account_id))
                  returning ct.*
                )
                update crypto_transfer ct
                set errata = 'DELETE'
                from spurious_transfer st
                where ct.consensus_timestamp = st.consensus_timestamp and ct.amount = st.amount * -1
                """;
        int count = jdbcOperations.getJdbcOperations().update(sql);
        log.info("Updated {} spurious transfers", count * 2);
    }

    /**
     * Adds the transactions and records that are missing due to the insufficient fee funding and FAIL_INVALID NFT
     * transfer issues in services.
     */
    @SneakyThrows
    private void missingTransactions() {
        Set<Long> consensusTimestamps = new HashSet<>();
        var resourceResolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resourceResolver.getResources("classpath*:errata/mainnet/missingtransactions/*.bin");
        Arrays.sort(resources, Comparator.comparing(Resource::getFilename));
        recordStreamFileListener.onStart();
        var dateRangeFilter = new DateRangeFilter(mirrorProperties.getStartDate(), mirrorProperties.getEndDate());

        for (Resource resource : resources) {
            String name = resource.getFilename();

            try (var in = new ValidatedDataInputStream(resource.getInputStream(), name)) {
                byte[] recordBytes = in.readLengthAndBytes(1, MAX_TRANSACTION_LENGTH, false, "record");
                byte[] transactionBytes = in.readLengthAndBytes(1, MAX_TRANSACTION_LENGTH, false, "transaction");
                var transactionRecord = TransactionRecord.parseFrom(recordBytes);
                var transaction = Transaction.parseFrom(transactionBytes);
                var recordItem = RecordItem.builder()
                        .transactionRecord(transactionRecord)
                        .transaction(transaction)
                        .build();
                long timestamp = recordItem.getConsensusTimestamp();
                boolean inRange = dateRangeFilter.filter(timestamp);

                if (transactionRepository.findById(timestamp).isEmpty() && inRange) {
                    entityRecordItemListener.onItem(recordItem);
                    consensusTimestamps.add(timestamp);
                    log.info("Processed errata {} successfully", name);
                } else if (inRange) {
                    missingTokenTransfers(name, recordItem);
                } else {
                    log.info("Skipped previously processed errata {}", name);
                }
            } catch (IOException e) {
                recordStreamFileListener.onError();
                throw new FileOperationException("Error parsing errata file " + name, e);
            }
        }

        if (consensusTimestamps.isEmpty()) {
            log.info("Previously inserted all missing transactions");
            return;
        }

        recordStreamFileListener.onEnd(null);
        var ids = new MapSqlParameterSource("ids", consensusTimestamps);
        jdbcOperations.update("update crypto_transfer set errata = 'INSERT' where consensus_timestamp in (:ids)", ids);
        jdbcOperations.update("update transaction set errata = 'INSERT' where consensus_timestamp in (:ids)", ids);

        Long min = consensusTimestamps.stream().min(Long::compareTo).orElse(null);
        Long max = consensusTimestamps.stream().max(Long::compareTo).orElse(null);
        log.info("Inserted {} missing transactions between {} and {}", consensusTimestamps.size(), min, max);
    }

    // We missed inserting the token transfers from the 2023-02 FAIL_INVALID transactions
    private void missingTokenTransfers(String name, RecordItem recordItem) {
        var count = new AtomicLong(0L);

        recordItem.getTransactionRecord().getTokenTransferListsList().forEach(t -> {
            var tokenId = EntityId.of(t.getToken());

            t.getTransfersList().forEach(aa -> {
                var accountId = EntityId.of(aa.getAccountID());
                var id = new TokenTransfer.Id(recordItem.getConsensusTimestamp(), tokenId, accountId);

                if (tokenTransferRepository.findById(id).isEmpty()) {
                    TokenTransfer tokenTransfer = new TokenTransfer();
                    tokenTransfer.setAmount(aa.getAmount());
                    tokenTransfer.setId(id);
                    tokenTransfer.setIsApproval(false);
                    tokenTransfer.setPayerAccountId(recordItem.getPayerAccountId());
                    tokenTransfer.setTokenDissociate(false);
                    tokenTransferRepository.save(tokenTransfer);
                    count.incrementAndGet();
                }
            });
        });

        if (count.get() > 0) {
            log.info("Processed errata {} successfully with {} missing token transfers", name, count);
        } else {
            log.info("Skipped previously processed errata {}", name);
        }
    }

    private Set<Long> getTimestamps() {
        if (!timestamps.isEmpty()) {
            return timestamps;
        }

        synchronized (timestamps) {
            if (!timestamps.isEmpty()) {
                return timestamps;
            }

            try (var reader = new BufferedReader(new InputStreamReader(balanceOffsets.getInputStream()))) {
                String line = reader.readLine();

                while (line != null) {
                    Long timestamp = Long.parseLong(line);
                    timestamps.add(timestamp);
                    line = reader.readLine();
                }
            } catch (Exception e) {
                log.error("Error processing balance file", e);
            }
        }

        return timestamps;
    }

    private boolean isMainnet() {
        return mirrorProperties.getNetwork() == MirrorProperties.HederaNetwork.MAINNET;
    }

    private boolean shouldApplyFixedTimeOffset(long consensusTimestamp) {
        return consensusTimestamp >= FIRST_ACCOUNT_BALANCE_FILE_TIMESTAMP &&
                consensusTimestamp <= LAST_ACCOUNT_BALANCE_FILE_TIMESTAMP;
    }
}
