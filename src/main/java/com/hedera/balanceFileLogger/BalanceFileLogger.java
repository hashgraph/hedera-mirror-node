package com.hedera.balanceFileLogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Stopwatch;
import com.hedera.configLoader.ConfigLoader;
import com.hedera.configLoader.ConfigLoader.OPERATION_TYPE;
import com.hedera.databaseutilities.DatabaseUtilities;
import com.hedera.fileWatcher.FileWatcher;
import com.hedera.utilities.Utility;

import java.time.Instant;

public class BalanceFileLogger extends FileWatcher {

	enum BalanceSelect {
		ZERO
		,SHARD
		,REALM
		,NUM
	}

	enum BalanceUpdate {
		ZERO
		,BALANCE
		,ID
	}

	enum BalanceInsert {
		ZERO
		,SHARD
		,REALM
		,NUM
		,BALANCE
	}

    enum BalanceHistoryInsert {
        ZERO
        ,SNAPSHOT_TIME
        ,SECONDS
        ,NANOS
        ,SNAPSHOT_TIME_NS
        ,FK_BAL_ID
        ,BALANCE
    }

    enum BalanceHistoryInsertBalance {
        ZERO
        ,SHARD
        ,REALM
        ,NUM
    }

	private static Instant fileTimestamp;
	private static long fileSeconds = 0;
	private static long fileNanos = 0;
	private static File balanceFilePath = new File(ConfigLoader.getDefaultParseDir(OPERATION_TYPE.BALANCE));
	
	public BalanceFileLogger(File pathToWatch) {
		super(pathToWatch);
	}

	public static void main(String[] args) {
		FileWatcher fileWatcher = new BalanceFileLogger(balanceFilePath);
		fileWatcher.watch();
	}

	@Override
	public void onCreate() {
		processLastBalanceFile();
		processAllFilesForHistory();
	}

	private boolean parseFileName(File fileName) {

		String shortFileName = fileName.getName().replace(".csv", "");
		if (shortFileName.contains("_Balances")) {
			shortFileName = shortFileName.replace("_Balances", "");
			shortFileName = shortFileName.replace("_",":");
			fileTimestamp = Instant.parse(shortFileName);

			fileSeconds = fileTimestamp.getEpochSecond();
			fileNanos = fileTimestamp.getNano();
		} else {
			// old format -- 2019-06-28-22-05.csv
			String[] fileParts = shortFileName.split("-");
			if (fileParts.length != 5) {
				log.error("File {} is not named as expected, should be like 2019-06-28-22-05.csv", fileName);
				return false;
			} else {
				Calendar c = Calendar.getInstance();
				c.clear();

				c.set(Integer.parseInt(fileParts[0])
						, Integer.parseInt(fileParts[1]) -1
						, Integer.parseInt(fileParts[2])
						, Integer.parseInt(fileParts[3])
						, Integer.parseInt(fileParts[4])
						,0
				);
				fileTimestamp = c.toInstant();
				fileSeconds = fileTimestamp.getEpochSecond();
				fileNanos = fileTimestamp.getNano();
			}
		}
		return true;
	}

	private File getLatestBalancefile() throws IOException {

		File lastFile = null;
        // find all files in path
        // return the greatest file name

        List<String> balancefiles = new ArrayList<String>();
	    for (final File balanceFile : balanceFilePath.listFiles()) {
	        if (balanceFile.getName().toString().endsWith(".csv") ) {
	            balancefiles.add(balanceFile.getName());
	        }
	    	
	    }
        if (balancefiles.size() != 0) {
            Collections.sort(balancefiles);

            lastFile = new File(balanceFilePath + File.separator + balancefiles.get(balancefiles.size()-1));
        }

        return lastFile;
	}

	private void processAllFilesForHistory() {
		Stopwatch stopwatch = Stopwatch.createStarted();

        try {
        	File balanceFilesPath = balanceFilePath;
			File[] balanceFiles = balanceFilesPath.listFiles();

	        for (final File balanceFile : balanceFiles) {
				if (Utility.checkStopFile()) {
					throw new RuntimeException("Stop file found, exiting");
				}
				if (processFileForHistory(balanceFile)) {
					// move it
					Utility.moveFileToParsedDir(balanceFile.getCanonicalPath(), "/parsedBalanceFiles/"); 
				}
	        }

			log.info("Completed processing {} balance files in {}", balanceFiles.length, stopwatch);
		} catch (Exception e) {
            log.error("Error processing balances files after {}", stopwatch, e);
		}
	}

	public boolean processFileForHistory(File balanceFile) {
        boolean processLine = false;
        Stopwatch stopwatch = Stopwatch.createStarted();

        if ( ! balanceFile.toString().endsWith(".csv") ) {
        	return false;
        }
        try (Connection connect = DatabaseUtilities.getConnection()) {
        	log.debug("Processing balance file: {}", balanceFile);

			try {
				connect.setAutoCommit(false);
			} catch (SQLException e) {
				log.error("Unable to unset database auto commit", e);
				return false;
			}

			if ( ! parseFileName(balanceFile)) {
				return false;
			}

			PreparedStatement selectBalance = connect.prepareStatement(
					"SELECT id"
					+ " FROM t_account_balances"
					+ " WHERE shard = ?"
					+ " AND realm = ?"
					+ " AND num = ?");

			PreparedStatement insertBalance = connect.prepareStatement(
					"INSERT INTO t_account_balances (shard, realm, num, balance) "
					+ " VALUES (?, ?, ?, 0) "
					+ " RETURNING id");

			PreparedStatement insertBalanceHistory = connect.prepareStatement(
					"insert into t_account_balance_history (snapshot_time, seconds, nanos, snapshot_time_ns, fk_balance_id, balance) "
					+ " values ("
					+ " ?" // snapshot
					+ ", ?" // seconds
					+ ", ?" // nanos
					+ ", ?" // snapshot_time_ns
					+ ", ?" // balance_id
					+ ", ?" // balance
					+ ")"
					+ " ON CONFLICT (snapshot_time, seconds, fk_balance_id)"
					+ " DO UPDATE set balance = EXCLUDED.balance");

			try (BufferedReader br = new BufferedReader(new FileReader(balanceFile))) {
				String line;
				while ((line = br.readLine()) != null) {
					if (processLine) {
						String[] balanceLine = line.split(",");
						if (balanceLine.length != 4) {
							throw new IllegalStateException("Balance file appears truncated");
						} else {

							// get the account id from t_Account_balances
							long accountId = 0;

							selectBalance.setLong(BalanceSelect.SHARD.ordinal(), Long.valueOf(balanceLine[0]));
							selectBalance.setLong(BalanceSelect.REALM.ordinal(), Long.valueOf(balanceLine[1]));
							selectBalance.setLong(BalanceSelect.NUM.ordinal(), Long.valueOf(balanceLine[2]));

							selectBalance.execute();
							ResultSet balanceRow = selectBalance.getResultSet();

							if (balanceRow.next()) {
								accountId = balanceRow.getLong(1);
							} else {
								insertBalance.setLong(BalanceHistoryInsertBalance.SHARD.ordinal(), Long.valueOf(balanceLine[0]));
								insertBalance.setLong(BalanceHistoryInsertBalance.REALM.ordinal(), Long.valueOf(balanceLine[1]));
								insertBalance.setLong(BalanceHistoryInsertBalance.NUM.ordinal(), Long.valueOf(balanceLine[2]));

								insertBalance.execute();

								ResultSet newId = insertBalance.getResultSet();
								if (newId.next()) {
									accountId = newId.getLong(1);
									newId.close();
								} else {
									// failed to create or fetch the account from t_account_balances
									newId.close();
									balanceRow.close();
									throw new IllegalStateException("Unable to create or find, shard " + balanceLine[0] + ", realm " + balanceLine[1] + ", num " + balanceLine[2]);
								}
							}
							balanceRow.close();
							Timestamp timestamp = Timestamp.from(fileTimestamp);
							insertBalanceHistory.setTimestamp(BalanceHistoryInsert.SNAPSHOT_TIME.ordinal(), timestamp);
							insertBalanceHistory.setLong(BalanceHistoryInsert.SECONDS.ordinal(), fileSeconds);
							insertBalanceHistory.setLong(BalanceHistoryInsert.NANOS.ordinal(), fileNanos);
							insertBalanceHistory.setLong(BalanceHistoryInsert.SNAPSHOT_TIME_NS.ordinal(), Utility.convertInstantToNanos(fileTimestamp));
							insertBalanceHistory.setLong(BalanceHistoryInsert.FK_BAL_ID.ordinal(), accountId);
							insertBalanceHistory.setLong(BalanceHistoryInsert.BALANCE.ordinal(), Long.valueOf(balanceLine[3]));

							insertBalanceHistory.execute();
						}
					} else if (line.contains("shard")) {
						processLine = true;
					}
				}
				connect.commit();
				log.info("Finished processing balance file {} successfully in {}", balanceFile.getName(), stopwatch);
				return true;
			} catch (Exception e) {
				log.error("Error processing balance file {}", balanceFile.getName(), e);
				connect.rollback();
			} finally {
				insertBalanceHistory.close();
				selectBalance.close();
				insertBalance.close();
			}
        } catch (Exception e) {
            log.error("Error connecting to database", e);
        }
        return false;
	}

	private void processLastBalanceFile() {
        boolean processLine = false;
        Stopwatch stopwatch = Stopwatch.createStarted();

        try (Connection connect = DatabaseUtilities.getConnection()) {
			File balanceFile = getLatestBalancefile();
			log.debug("Processing last balance file {}", balanceFile);

			if (balanceFile == null) {
				return;
			} else if (!parseFileName(balanceFile)) {
				log.info("Invalid balance file");
				return;
			}

			PreparedStatement updateLastBalanceTime = connect.prepareStatement(
					"UPDATE t_account_balance_refresh_time"
							+ " SET seconds = ?"
							+ ",nanos = ?");

			PreparedStatement selectBalance = connect.prepareStatement(
					"SELECT id"
							+ " FROM t_account_balances"
							+ " WHERE shard = ?"
							+ " AND realm = ?"
							+ " AND num = ?");

			PreparedStatement updateBalance = connect.prepareStatement(
					"UPDATE t_account_balances"
							+ " SET balance = ?"
							+ " WHERE id = ?");


			PreparedStatement insertBalance = connect.prepareStatement(
					"INSERT INTO t_account_balances (shard, realm, num, balance) "
							+ " VALUES (?, ?, ?, ?)");

			try (BufferedReader br = new BufferedReader(new FileReader(balanceFile))) {
				connect.setAutoCommit(false);

				// update last file update time
				updateLastBalanceTime.setLong(1, fileSeconds);
				updateLastBalanceTime.setLong(2, fileNanos);
				updateLastBalanceTime.execute();

				String line;
				while ((line = br.readLine()) != null) {
					if (processLine) {
						String[] balanceLine = line.split(",");
						if (balanceLine.length != 4) {
							throw new IllegalStateException("Balance file appears truncated");
						} else {
							selectBalance.setLong(BalanceSelect.SHARD.ordinal(), Long.valueOf(balanceLine[0]));
							selectBalance.setLong(BalanceSelect.REALM.ordinal(), Long.valueOf(balanceLine[1]));
							selectBalance.setLong(BalanceSelect.NUM.ordinal(), Long.valueOf(balanceLine[2]));

							selectBalance.execute();
							ResultSet balanceRow = selectBalance.getResultSet();
							if (balanceRow.next()) {
								// update the balance
								updateBalance.setLong(BalanceUpdate.BALANCE.ordinal(), Long.valueOf(balanceLine[3]));
								updateBalance.setLong(BalanceUpdate.ID.ordinal(), balanceRow.getLong(1));
								updateBalance.execute();
							} else {
								// insert new row
								insertBalance.setLong(BalanceInsert.SHARD.ordinal(), Long.valueOf(balanceLine[0]));
								insertBalance.setLong(BalanceInsert.REALM.ordinal(), Long.valueOf(balanceLine[1]));
								insertBalance.setLong(BalanceInsert.NUM.ordinal(), Long.valueOf(balanceLine[2]));
								insertBalance.setLong(BalanceInsert.BALANCE.ordinal(), Long.valueOf(balanceLine[3]));
								insertBalance.execute();
							}
							balanceRow.close();
						}
					} else if (line.contentEquals("shard,realm,number,balance")) {
						// skip all lines until shard,realm,number,balance
						processLine = true;
					} else if (line.contentEquals("shardNum,realmNum,accountNum,balance")) {
						// skip all lines until shard,realm,number,balance
						processLine = true;
					}
				}
				connect.commit();
				log.info("Finished processing latest balance file {} successfully in {}", balanceFile, stopwatch);
			} catch (Exception e) {
				connect.rollback();
				log.error("Error processing latest balance file {} after {}", balanceFile, stopwatch, e);
			} finally {
				insertBalance.close();
				updateBalance.close();
				selectBalance.close();
				updateLastBalanceTime.close();
			}
		} catch (Exception e) {
			log.error("Error closing connection", e);
		}
    }
}
