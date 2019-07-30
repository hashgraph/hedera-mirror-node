package com.hedera.balanceFileLogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.hedera.configLoader.ConfigLoader;
import com.hedera.databaseUtilities.DatabaseUtilities;
import com.hedera.utilities.Utility;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

public class BalanceFileLogger {

	private static final Logger log = LogManager.getLogger("recordStream-log");
	private static final Marker MARKER = MarkerManager.getMarker("BALANCE");
	static final Marker LOGM_EXCEPTION = MarkerManager.getMarker("EXCEPTION");

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
        ,FK_BAL_ID
        ,BALANCE 
    }

    enum BalanceHistoryInsertBalance {
        ZERO
        ,SHARD
        ,REALM
        ,NUM 
    }

    private static Connection connect = null;
	
    private static ConfigLoader configLoader = new ConfigLoader("./config/config.json");
	private static String balanceFolder = configLoader.getDownloadToDir();
	private static File balanceFilesPath ;
	
    static void moveFileToParsedDir(String fileName) {
		File sourceFile = new File(fileName);
		File parsedDir = new File(sourceFile.getParentFile().getParentFile().getPath() + "/parsedRecordFiles/");
		parsedDir.mkdirs();
		File destFile = new File(parsedDir.getPath() + "/" + sourceFile.getName());
		try {
			Files.move(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			log.info(MARKER, sourceFile.toPath() + " has been moved to " + destFile.getPath());
		} catch (IOException ex) {
			log.error(MARKER, "Fail to move {} to {} : {}",
					fileName, parsedDir.getName(),
					ex.getStackTrace());
		}
	}
	
	private static File getLatestBalancefile(File balanceFilesPath) throws IOException {
	    
	    File lastFile = null;
        // find all files in path
        // return the greatest file name
	    
	    for (final File nodeFolders : balanceFilesPath.listFiles()) {
	        if (nodeFolders.isDirectory()) {
	            
	            List<String> balancefiles = new ArrayList<String>();

	            for (final File balanceFile : nodeFolders.listFiles()) {
	                balancefiles.add(balanceFile.getName());
	            }
	            if (balancefiles.size() != 0) {
    	            Collections.sort(balancefiles);
    	            File lastFound = new File(nodeFolders.getCanonicalPath() + File.separator + balancefiles.get(balancefiles.size()-1));
    	            System.out.println (nodeFolders.getCanonicalPath());
    	            System.out.println (balancefiles.get(balancefiles.size()-1));
    	            
    	            if (lastFile == null) {
    	                lastFile = lastFound;
    	            } else if (lastFile.getName().compareTo(lastFound.getName()) < 0) {
    	                lastFile = lastFound;
    	            }
	            }
	        }
	    }	    
	    return lastFile;
	}

	public static void main(String[] args) {
	    while (true) {
			if (Utility.checkStopFile()) {
				log.info(MARKER, "Stop file found, exiting.");
				System.exit(0);
			}
			if (!balanceFolder.endsWith("/")) {
				balanceFolder += "/";
			}
		    balanceFilesPath = new File(balanceFolder + "accountBalances/balance");

		    processLastBalanceFile();
		    processAllFilesForHistory();
	    }
	}
	
	private static void processAllFilesForHistory() {
	    File balanceFilesPath = new File(balanceFolder + "accountBalances/balance");
        String balanceFilePath = "";
        String donePath = "";
        try {
        
            for (final File nodeFolders : balanceFilesPath.listFiles()) {
    			if (Utility.checkStopFile()) {
    				log.info(MARKER, "Stop file found, stopping.");
    				break;
    			}            	
                donePath = nodeFolders.getCanonicalPath().toString().replace("/balance", "/processed");
                File targetPath = new File (donePath);
                targetPath.mkdirs();
                if (nodeFolders.isDirectory()) {
                    for (final File balanceFile : nodeFolders.listFiles()) {
            			if (Utility.checkStopFile()) {
            				log.info(MARKER, "Stop file found, stopping.");
            				break;
            			}            	
                        if (processFileForHistory(balanceFile)) {
                            // move it
                            File destFile = new File(targetPath.getCanonicalPath() + File.separator + balanceFile.getName());

                            Files.move(balanceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            log.info(MARKER, balanceFile.toPath() + " has been moved to " + destFile.getPath());
                            balanceFilePath = balanceFile.getCanonicalFile().toString();
                        }
                    }
                }
            } 
        } catch (IOException ex) {
            log.error(MARKER, "Fail to move {} to {} : {}",balanceFilePath, donePath, ex.getStackTrace());
        }
        log.info(MARKER, "Balance History processing done");
	}
	
	public static boolean processFileForHistory(File balanceFile) {
        boolean processLine = false;
        boolean getDate = false;
        
        try {
            // process the file
            connect = DatabaseUtilities.openDatabase(connect);
            
            if (connect != null) {

                try {
        			connect.setAutoCommit(false);
        		} catch (SQLException e) {
                    log.error(LOGM_EXCEPTION, "Unable to unset database auto commit, Exception: {}", e.getMessage());
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

            	PreparedStatement insertBalanceHistory;
	            insertBalanceHistory = connect.prepareStatement(
	                    "insert into t_account_balance_history (snapshot_time, seconds, fk_balance_id, balance) "
	                    + " values ("
	                    + " to_timestamp(?, 'YYYY,MONTH,DD,hh24,mi,ss')" // snapshot
	                    + ", EXTRACT(EPOCH FROM to_timestamp(?, 'YYYY,MONTH,DD,hh24,mi,ss'))" //seconds
	                    + ", ?" // balance_id
	                    + ", ?" // balance
	                    + ")"
	                    + " ON CONFLICT (snapshot_time, seconds, fk_balance_id)"
	                    + " DO UPDATE set balance = EXCLUDED.balance");
	        
	            BufferedReader br = new BufferedReader(new FileReader(balanceFile));
	
	            String line;
	            String dateLine = "";
	            while ((line = br.readLine()) != null) {
	                if (processLine) {
	                    try {
	                        String[] balanceLine = line.split(",");
	                        if (balanceLine.length != 4) {
		                        log.error(LOGM_EXCEPTION, "Balance file {} appears truncated", balanceFile);
		                        connect.rollback();
		                        insertBalanceHistory.close();
		                        selectBalance.close();
		                        insertBalance.close();
		                        br.close();
		                        return false;
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
	                        	
		                        insertBalanceHistory.setString(BalanceHistoryInsert.SNAPSHOT_TIME.ordinal(), dateLine);
		                        insertBalanceHistory.setString(BalanceHistoryInsert.SECONDS.ordinal(), dateLine);
	                        	insertBalanceHistory.setLong(BalanceHistoryInsert.FK_BAL_ID.ordinal(), accountId);
		                        insertBalanceHistory.setLong(BalanceHistoryInsert.BALANCE.ordinal(), Long.valueOf(balanceLine[3]));
		
		                        insertBalanceHistory.execute();
	                        }	                        
	                    } catch (SQLException e) {
	                        log.error(LOGM_EXCEPTION, "Exception {}", e);
	                        connect.rollback();
	                        insertBalanceHistory.close();
	                        selectBalance.close();
	                        insertBalance.close();
	                        br.close();
	                        return false;
	                    }
	                } else if (getDate) {
	                    getDate = false;
	                    dateLine = line;
	                } else if (line.contentEquals("year,month,day,hour,minute,second")) {
	                    getDate = true;
	                } else if (line.contentEquals("shard,realm,number,balance")) {
	                    // skip all lines until shard,realm,number,balance
	                    processLine = true;
	                }
	            }
	            connect.commit();
	            insertBalanceHistory.close();
                selectBalance.close();
            	insertBalance.close();
	            br.close();
	            return true;
            }
        } catch (FileNotFoundException e) {
            log.error(LOGM_EXCEPTION, "Exception {}", e);
        } catch (IOException e) {
            log.error(LOGM_EXCEPTION, "Exception {}", e);
        } catch (SQLException e) {
            log.error(LOGM_EXCEPTION, "Exception {}", e);
        }
        return false;
	}
	
	private static void processLastBalanceFile() {

	    boolean processLine = false;
	    
        try {
            File balanceFile = getLatestBalancefile(balanceFilesPath);
            if (balanceFile != null) {
                // process the file
                connect = DatabaseUtilities.openDatabase(connect);
                
                if (connect != null) {
                    connect.setAutoCommit(false);
                    
                    PreparedStatement selectBalance = connect.prepareStatement(
                    		"SELECT id"
                    		+ " FROM t_account_balances"
                    		+ " WHERE shard = ?"
                    		+ " AND realm = ?"
                    		+ " AND num = ?");

	                PreparedStatement updateBalance =  connect.prepareStatement(
	                        "UPDATE t_account_balances"
	                        + " SET balance = ?"
	                        + " WHERE id = ?");
    	                
    	            
                    PreparedStatement insertBalance =  connect.prepareStatement(
	                        "INSERT INTO t_account_balances (shard, realm, num, balance) "
	                        + " VALUES (?, ?, ?, ?)");
		        
	                BufferedReader br = new BufferedReader(new FileReader(balanceFile));
	
	                String line;
		            while ((line = br.readLine()) != null) {
		                if (processLine) {
		                    try {
		                        String[] balanceLine = line.split(",");
		                        if (balanceLine.length != 4) {
			                        log.error(LOGM_EXCEPTION, "Balance file {} appears truncated", balanceFile);
			                        connect.rollback();
			                        break;
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
		                        
		                    } catch (SQLException e) {
		                        connect.rollback();
		                        log.error(LOGM_EXCEPTION, "Exception {}", e);
		                        break;
		                    }
		                } else if (line.contentEquals("shard,realm,number,balance")) {
		                    // skip all lines until shard,realm,number,balance
	                        processLine = true;
	                    }
		            }
	                connect.commit();
	                insertBalance.close();
	                updateBalance.close();
	                selectBalance.close();
	                br.close();
	            }
	        } else {
	            log.info("No balance file to parse found");
	        }
	    } catch (IOException e) {
            log.error(LOGM_EXCEPTION, "Exception {}", e);
        } catch (SQLException e) {
            log.error(LOGM_EXCEPTION, "Exception {}", e);
            try {
				connect.rollback();
			} catch (SQLException e1) {
	            log.error(LOGM_EXCEPTION, "Exception {}", e1);
			}
        }
        log.info(MARKER, "Last Balance processing done");
    }
}
