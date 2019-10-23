package com.hedera.mirror.parser.balance;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import com.google.common.base.Stopwatch;
import com.hedera.databaseUtilities.DatabaseUtilities;
import com.hedera.mirror.exception.InvalidDatasetException;
import com.hedera.mirror.util.TimestampConverter;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.io.*;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.stream.Stream;

/**
 * Parse an account balances file and load it into the database.
 */
@Log4j2
public final class AccountBalancesFileLoader implements AutoCloseable {
	private final Path filePath;
	private final Instant filenameTimestamp;
	private final AccountBalancesDataset dataset;
	private final TimestampConverter timestampConverter = new TimestampConverter();
	private final int insertBatchSize;
	@Getter
	private int validRowCount;
	private boolean loaded;
	private final long systemShardNum;

	/**
	 * Read an account balances dataset from a file and begin preprocessing the dataset (ie - reading the header).
     * @throws IllegalArgumentException if the filename doesn't match known/required format (to extract consensus
	 *  timestamp)
     * @throws InvalidDatasetException invalid file header
     * @throws FileNotFoundException
	 */
	public AccountBalancesFileLoader(BalanceParserProperties balanceProperties, final Path filePath) throws IllegalArgumentException, InvalidDatasetException,
            FileNotFoundException {
		this.filePath = filePath;
		this.systemShardNum = balanceProperties.getMirrorProperties().getShard();
		final var info = new AccountBalancesFileInfo(filePath);
		filenameTimestamp = info.getFilenameTimestamp();
		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath.toFile())), balanceProperties.getFileBufferSize());
		dataset = new AccountBalancesDatasetV2(filePath.getFileName().toString(), reader);
		insertBatchSize = balanceProperties.getBatchSize();
	}

	/**
	 * Process a line (CSV account balance line) and insert it into the DB (or skip it if empty).
	 */
	private void processLine(final PreparedStatement ps, final long consensusTimestamp, final NumberedLine line)
			throws InvalidDatasetException, SQLException {
		final String[] cols = line.getValue().split(",");
		if (4 != cols.length) {
			throw new InvalidDatasetException(String.format(
					"Invalid line in account balances file %s:line(%d):%s",
					filePath, line.getLineNumber(), line.getValue()));
		}

		final var shardNum = Long.valueOf(cols[0]);
		if (shardNum != systemShardNum) {
			throw new InvalidDatasetException(String.format(
					"Invalid shardNum %d in account balances file %s:line(%d):%s",
					shardNum, filePath, line.getLineNumber(), line.getValue()));
		}

		try {
			ps.setLong(1, consensusTimestamp);
			ps.setShort(2, Short.valueOf(cols[1])); // realm_num
			ps.setInt(3, Integer.valueOf(cols[2])); // num
			ps.setLong(4, Long.valueOf(cols[3])); // balance (hbar_tinybars);
			ps.addBatch();
		} catch (NumberFormatException e) {
			throw new InvalidDatasetException(String.format("Invalid line in account balances file %s:line(%d):%s",
					filePath, line.getLineNumber(), line.getValue()));
		}
	}

	/**
	 * @return true if all lines in the stream were successfully inserted; false, if any errors were seen.
	 */
	private boolean processRecordStream(final PreparedStatement ps, final long consensusTimestamp,
									 final Stream<NumberedLine> stream) {
		var state = new Object() {
			int recordsInCurrentBatch = 0;
			boolean insertSuccess = true;
		};
		stream.forEachOrdered((line) -> {
			try {
				if (line.getValue().isEmpty()) return;
				processLine(ps, consensusTimestamp, line);
				++validRowCount;
				++state.recordsInCurrentBatch;
				if (state.recordsInCurrentBatch >= insertBatchSize) {
					state.recordsInCurrentBatch = 0;
					ps.executeBatch();
				}
			} catch (InvalidDatasetException | SQLException e) {
				log.error(e);
				state.insertSuccess = false;
			}
		});
		// Process any remaining insert batches.
		if (state.recordsInCurrentBatch > 0) {
			try {
				ps.executeBatch();
			} catch (SQLException e) {
				log.error(e);
				state.insertSuccess = false;
			}
		}
		return state.insertSuccess;
	}

	/**
	 * Process the file and load all the data into the database.
	 * @return true on success (if the file was completely and full processed).
	 */
	public boolean loadAccountBalances() {
		if (loaded) {
			throw new RuntimeException(toString() + " loadBalances() called more than once");
		}
		loaded = true;

	    final var consensusTimestamp = dataset.getConsensusTimestamp();
	    if (!filenameTimestamp.equals(consensusTimestamp)) {
			// The assumption is that the dataset has been validated via signatures and running hashes, so it is
			// the "next" dataset, and the consensus timestamp in it is correct.
			// The fact that the filename timestamp and timestamp in the file differ should still be investigated.
	        log.error("Account balance dataset timestamp mismatch! Processing can continue, but this must be investigated! Dataset {} internal timestamp {} filename timestamp {}.",
                    filePath.getFileName(), filenameTimestamp, consensusTimestamp);
        }

	    // consensusTimestamp (from file) has been signed, filenameTimestamp has not.
	    final var longConsensusTimestamp = timestampConverter.toNanosecondLong(consensusTimestamp);

		//
		// 1) insert row into account_balance_sets.
		// 2) stream insert all the account_balances records.
		// 3) update/close the account_balance_set.
		//
		log.info("Starting processing account balances file {}", filePath);
		var stopwatch = Stopwatch.createStarted();
		try (Connection conn = DatabaseUtilities.getConnection()) {
			final Stream<NumberedLine> stream = dataset.getRecordStream();

			final var insertSet = conn.prepareStatement(
					"insert into account_balance_sets (consensus_timestamp) values (?) on conflict do nothing returning is_complete, processing_start_timestamp;");
			final var insertBalance = conn.prepareStatement(
					"insert into account_balances (consensus_timestamp, account_realm_num, account_num, balance) values (?, ?, ?, ?) on conflict do nothing;");
			final var updateSet = conn.prepareStatement(
					"update account_balance_sets set is_complete = true, processing_end_timestamp = now() at time zone 'utc' where consensus_timestamp = ? and is_complete = false;");

			insertSet.setLong(1, longConsensusTimestamp);
			insertSet.execute();

			if (processRecordStream(insertBalance, longConsensusTimestamp, stream)) {
				updateSet.setLong(1, longConsensusTimestamp);
				updateSet.execute();
				log.info("Successfully processed account balances file {} with {} records in {}", filePath,
						validRowCount, stopwatch);
				return true;
			} else {
				log.error("ERRORS processing account balances file {} with {} records in {}", filePath,
						validRowCount, stopwatch);
			}
		} catch (SQLException | InvalidDatasetException e) {
			log.error("Exception processing account balances file {}", filePath, e);
		}
		return false;
	}

    @Override
    public void close() throws Exception {
        if (null != dataset) {
            dataset.close();
        }
    }
}
