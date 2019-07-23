package com.hedera.recordFileLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.databaseUtilities.DatabaseUtilities;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;

public class RecordFileLogger implements LoggerInterface {
	private static final Logger log = LogManager.getLogger("recordStream-log");
	static final Marker LOGM_EXCEPTION = MarkerManager.getMarker("EXCEPTION");

	private static Connection connect = null;
	private static long fileId = 0;
	private static HashMap<String, Integer> transactionTypes = null;

    enum F_TRANSACTION {
        ZERO // column indices start at 1, this creates the necessary offset
        ,NODE_ACCOUNT_ID
        ,MEMO
        ,SECONDS
        ,NANOS
        ,XFER_COUNT
        ,TRANS_TYPE_ID
        ,TRANS_ACCOUNT_ID
        ,RESULT
        ,CONSENSUS_SECONDS
        ,CONSENSUS_NANOS
        ,CRUD_ENTITY_ID
        ,TRANSACTION_FEE
        ,INITIAL_BALANCE
        ,TRANSACTION_ID
    }
    
    enum F_TRANSFERLIST {
        ZERO // column indices start at 1, this creates the necessary offset
        ,TXID
        ,ACCOUNT_ID
        ,AMOUNT
        ,TYPE_ID
    }
    
    enum F_TRANSFER {
        ZERO // column indices start at 1, this creates the necessary offset
        ,TX_ID
        ,FROM_ACCOUNT_ID
        ,TO_ACCOUNT_ID
        ,AMOUNT
        ,PAYMENT_TYPE_ID
    }
	public static boolean start() {
        connect = DatabaseUtilities.openDatabase(connect);
        if (connect == null) {
        	return false;
        }
        return true;
	}
	public static boolean finish() {
        try {
            connect = DatabaseUtilities.closeDatabase(connect);
        	return false;
        } catch (SQLException e) {
            log.error(LOGM_EXCEPTION, "Exception {}", e);
        }
    	return true;
	}
	public static long createOrGetEntity(long shard, long realm, long num) throws SQLException {
	    long entityId = 0;
	    
	    if (shard + realm + num == 0 ) {
	        return 0;
	    }
	    
	    // inserts or returns an existing entity
        PreparedStatement insertEntity = connect.prepareStatement(
                "insert into t_entities (entity_shard, entity_realm, entity_num) values (?, ?, ?)"
                + " ON CONFLICT (entity_shard, entity_realm, entity_num) "
                + " DO UPDATE"
                + " SET entity_shard = EXCLUDED.entity_shard"
                + " RETURNING id");
        
        insertEntity.setLong(1, shard);
        insertEntity.setLong(2, realm);
        insertEntity.setLong(3, num);
        insertEntity.execute();
        ResultSet newId = insertEntity.getResultSet();
        newId.next();
        entityId = newId.getLong(1);
        newId.close();
        insertEntity.close();
        
        return entityId;
	    
	}
    public static long createOrGetEntity(FileID fileId) throws SQLException {
        return createOrGetEntity(fileId.getShardNum(), fileId.getRealmNum(), fileId.getFileNum());
    }
    public static long createOrGetEntity(ContractID contractId) throws SQLException {
        return createOrGetEntity(contractId.getShardNum(), contractId.getRealmNum(), contractId.getContractNum());
    }
    public static long createOrGetEntity(AccountID accountId) throws SQLException {
        return createOrGetEntity(accountId.getShardNum(), accountId.getRealmNum(), accountId.getAccountNum());
    }
    
	public static boolean initFile(String fileName) {
	    
		try {
	        if (transactionTypes == null) {
	            transactionTypes = new HashMap<String, Integer>();
                ResultSet resultSet = connect.createStatement().executeQuery("select id, name from t_transaction_types order by id");       
                while (resultSet.next()) {      
                    transactionTypes.put(resultSet.getString("name"), resultSet.getInt("id"));      
                }       
                resultSet.close();      
	        }       

		    PreparedStatement insertFile = connect.prepareStatement(
					"insert into t_event_files (name, last_load, status) values (?, ?, 'PROCESSING')"
					+ " ON CONFLICT (name) "
					+ " DO UPDATE set status = 'PROCESSING'"
					+ " RETURNING id");
			insertFile.setString(1, fileName);
			insertFile.setTimestamp(2, java.sql.Timestamp.from(new Date().toInstant()));
			insertFile.execute();
            ResultSet newId = insertFile.getResultSet();
            newId.next();
            fileId = newId.getLong(1);
            newId.close();
			insertFile.close();
			return true;
			
		} catch (SQLException e) {
			e.printStackTrace();
			log.error(LOGM_EXCEPTION, "Exception {}", e);
			return false;
		}
	}
	public static boolean completeFile() {
		// update the file to processed
		try {
			PreparedStatement updateFile = connect.prepareStatement("update t_event_files set status = 'PROCESSED' where id = ?");
			updateFile.setLong(1, fileId);
			updateFile.execute();
			updateFile.close();
		} catch (SQLException e) {
			log.error(LOGM_EXCEPTION, "Exception {}", e);
			return false;
		}
		return true;
	}	
	public static boolean storeRecord(long counter, Instant consensusTimeStamp, Transaction transaction, TransactionRecord txRecord) throws Exception {
		try {
			PreparedStatement sqlInsertTransaction = connect.prepareStatement("insert into t_transactions "
						+ "(node_account_id, memo, seconds, nanos, xfer_count, trans_type_id, trans_account_id, result, consensus_seconds, consensus_nanos, crud_entity_id, transaction_fee, initial_balance, transaction_id) "
						+ " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
						+ " RETURNING id");

			PreparedStatement sqlInsertTransfer = connect.prepareStatement("insert into t_cryptotransfers "
					+ "(tx_id, from_account_id, to_account_id, amount, payment_type_id) "
					+ " values (?, ?, ?, ?, ?)");
	
			PreparedStatement sqlInsertTransferList = connect.prepareStatement("insert into t_cryptotransferlists "
					+ "(tx_id, account_id, amount, payment_type_id) "
					+ " values (?, ?, ?, ?)");
	
			long transactionId = 0;
						
			TransactionBody body = null;
			if (transaction.hasBody()) {
				body = transaction.getBody();
			} else {
				body = TransactionBody.parseFrom(transaction.getBodyBytes());
			}
			
			long nodeAccountId = createOrGetEntity(body.getNodeAccountID());
	
			sqlInsertTransaction.setLong(F_TRANSACTION.NODE_ACCOUNT_ID.ordinal(), nodeAccountId);
			sqlInsertTransaction.setString(F_TRANSACTION.MEMO.ordinal(), body.getMemo());
			sqlInsertTransaction.setLong(F_TRANSACTION.SECONDS.ordinal(), body.getTransactionID().getTransactionValidStart().getSeconds());
			sqlInsertTransaction.setLong(F_TRANSACTION.NANOS.ordinal(), body.getTransactionID().getTransactionValidStart().getNanos());
			int transactionTypeId = getTransactionTypeId(body);
			sqlInsertTransaction.setInt(F_TRANSACTION.TRANS_TYPE_ID.ordinal(), transactionTypeId);

	        long txAccountId = createOrGetEntity(body.getTransactionID().getAccountID());

			sqlInsertTransaction.setLong(F_TRANSACTION.TRANS_ACCOUNT_ID.ordinal(), txAccountId);
			
			String result = ResponseCodeEnum.forNumber(txRecord.getReceipt().getStatus().getNumber()).name();
			sqlInsertTransaction.setString(F_TRANSACTION.RESULT.ordinal(), result); 
			sqlInsertTransaction.setLong(F_TRANSACTION.CONSENSUS_SECONDS.ordinal(), txRecord.getConsensusTimestamp().getSeconds()); 
			sqlInsertTransaction.setLong(F_TRANSACTION.CONSENSUS_NANOS.ordinal(), txRecord.getConsensusTimestamp().getNanos());
			sqlInsertTransaction.setLong(F_TRANSACTION.TRANSACTION_FEE.ordinal(), txRecord.getTransactionFee());
			
			String transactionIdString = "0.0." + txAccountId + "-" + body.getTransactionID().getTransactionValidStart().getSeconds() + "-" + body.getTransactionID().getTransactionValidStart().getNanos();
			
			sqlInsertTransaction.setString(F_TRANSACTION.TRANSACTION_ID.ordinal(), transactionIdString);

            long entityId = 0;
            long initialBalance = 0;
            
            if (txRecord.getReceipt().hasFileID()) {
                entityId = createOrGetEntity(txRecord.getReceipt().getFileID());
            } else if (txRecord.getReceipt().hasContractID()) {
                entityId = createOrGetEntity(txRecord.getReceipt().getContractID());
            } else if (txRecord.getReceipt().hasAccountID()) {
                entityId = createOrGetEntity(txRecord.getReceipt().getAccountID());                
            } 
            
            if (body.hasContractCall()) {
                if (body.getContractCall().hasContractID()) {
                    entityId = createOrGetEntity(body.getContractCall().getContractID());
                }
            } else if (body.hasContractCreateInstance()) {
                initialBalance = body.getContractCreateInstance().getInitialBalance();
            } else if (body.hasContractDeleteInstance()) {
                if (body.getContractDeleteInstance().hasContractID()) {
                    entityId = createOrGetEntity(body.getContractDeleteInstance().getContractID());
                }
            } else if (body.hasContractUpdateInstance()) {
                if (body.getContractUpdateInstance().hasContractID()) {
                    entityId = createOrGetEntity(body.getContractUpdateInstance().getContractID());
                }
            } else if (body.hasCryptoAddClaim()) {
                if (body.getCryptoAddClaim().hasClaim()) {
                    if (body.getCryptoAddClaim().getClaim().hasAccountID()) {
                        entityId = createOrGetEntity(body.getCryptoAddClaim().getClaim().getAccountID());
                    }
                }
            } else if (body.hasCryptoCreateAccount()) {
                initialBalance = body.getCryptoCreateAccount().getInitialBalance();
            } else if (body.hasCryptoDelete()) {
                if (body.getCryptoDelete().hasDeleteAccountID()) {
                    entityId = createOrGetEntity(body.getCryptoDelete().getDeleteAccountID());
                }
            } else if (body.hasCryptoDeleteClaim()) {
                if (body.getCryptoDeleteClaim().hasAccountIDToDeleteFrom()) {
                    entityId = createOrGetEntity(body.getCryptoDeleteClaim().getAccountIDToDeleteFrom());
                }
            } else if (body.hasCryptoDelete()) {
                if (body.getCryptoDelete().hasDeleteAccountID()) {
                    entityId = createOrGetEntity(body.getCryptoDelete().getDeleteAccountID());
                }
            } else if (body.hasCryptoUpdateAccount()) {
                if (body.getCryptoUpdateAccount().hasAccountIDToUpdate()) {
                    entityId = createOrGetEntity(body.getCryptoUpdateAccount().getAccountIDToUpdate());
                }
            } else if (body.hasFileAppend()) {
                if (body.getFileAppend().hasFileID()) {
                    entityId = createOrGetEntity(body.getFileAppend().getFileID());
                }
            } else if (body.hasFileDelete()) {
                if (body.getFileDelete().hasFileID()) {
                    entityId = createOrGetEntity(body.getFileDelete().getFileID());
                }
			}
            if (entityId == 0) {
                // insert null
                sqlInsertTransaction.setObject(F_TRANSACTION.CRUD_ENTITY_ID.ordinal(), null);
            } else {
                sqlInsertTransaction.setLong(F_TRANSACTION.CRUD_ENTITY_ID.ordinal(), entityId);
               
            }
            sqlInsertTransaction.setLong(F_TRANSACTION.INITIAL_BALANCE.ordinal(), initialBalance);
			
			if (!txRecord.hasTransferList()) {
				// xfer_count
				sqlInsertTransaction.setInt(F_TRANSACTION.XFER_COUNT.ordinal(), 0); // xfer_count not applicable here
				
				// allow for duplicates when processing files that contain the same transactions
				try {
				    sqlInsertTransaction.execute();
				} catch (SQLException e) {
				    if (e.getSQLState().contentEquals("23505")) {
                        // duplicate transaction id, that's ok
				    } else {
				        // Other SQL Exception
				        throw e;
				    }				
				}
			} else {
	            boolean doSqlInsertTransfer = false;
	            boolean doSqlInsertTransferList = false;

	            TransferList pTransfer = txRecord.getTransferList();
							
				long[] negAmounts = new long[50];
				long[] posAmounts = new long[50];
				AccountID[] negAccounts = new AccountID[50];
				AccountID[] posAccounts = new AccountID[50];
				int negAmountCount = -1;
				int posAmountCount = -1;
		
				// xfer_count
				sqlInsertTransaction.setInt(F_TRANSACTION.XFER_COUNT.ordinal(), pTransfer.getAccountAmountsCount()); 
                // allow for duplicates when processing files that contain the same transactions
                try {
                    sqlInsertTransaction.execute();

                    ResultSet newId = sqlInsertTransaction.getResultSet();
                    newId.next();
                    transactionId = newId.getLong(1);
                    newId.close();
                    sqlInsertTransaction.close();

                    
                    AccountID account = null;
                    long amount = 0;
                    for (int i = 0; i < pTransfer.getAccountAmountsCount(); i++) {
                        amount = pTransfer.getAccountAmounts(i).getAmount();
                        if (amount < 0) {
                            negAmountCount += 1;
                            negAmounts[negAmountCount] = amount;
                            negAccounts[negAmountCount] = pTransfer.getAccountAmounts(i).getAccountID();
                            account = negAccounts[negAmountCount];
                        } else {
                            posAmountCount += 1;
                            posAmounts[posAmountCount] = amount;
                            posAccounts[posAmountCount] = pTransfer.getAccountAmounts(i).getAccountID();
                            account = posAccounts[posAmountCount];
                        }
                        // insert
                        sqlInsertTransferList.setLong(F_TRANSFERLIST.TXID.ordinal(), transactionId);
                        long xferAccountId = createOrGetEntity(account);
                        sqlInsertTransferList.setLong(F_TRANSFERLIST.ACCOUNT_ID.ordinal(), xferAccountId);
                        sqlInsertTransferList.setLong(F_TRANSFERLIST.AMOUNT.ordinal(), amount);
                        sqlInsertTransferList.setInt(F_TRANSFERLIST.TYPE_ID.ordinal(), getTxType(amount));
        
                        doSqlInsertTransferList = true;
                        sqlInsertTransferList.addBatch();
                    }
        
                    if (negAmountCount == 0) {
                        for (int i = 0; i <= posAmountCount; i++) {
                            sqlInsertTransfer.setLong(F_TRANSFER.TX_ID.ordinal(), transactionId);
                            long xferAccountId = createOrGetEntity(negAccounts[0]);
                            sqlInsertTransfer.setLong(F_TRANSFER.FROM_ACCOUNT_ID.ordinal(), xferAccountId);
                            xferAccountId = createOrGetEntity(posAccounts[i]);
                            sqlInsertTransfer.setLong(F_TRANSFER.TO_ACCOUNT_ID.ordinal(), xferAccountId);
                            sqlInsertTransfer.setLong(F_TRANSFER.AMOUNT.ordinal(), posAmounts[i]);
                            sqlInsertTransfer.setInt(F_TRANSFER.PAYMENT_TYPE_ID.ordinal(), getTxType(posAmounts[i]));
                            doSqlInsertTransfer = true;
                            sqlInsertTransfer.addBatch();
                        }
                    }
                    if (doSqlInsertTransfer) {
                        sqlInsertTransfer.executeBatch();
                    }
                    if (doSqlInsertTransferList) {
                        sqlInsertTransferList.executeBatch();
                    }
                } catch (SQLException e) {
                    if (e.getSQLState().contentEquals("23505")) {
                        // duplicate transaction id, that's ok
                    } else {
                        // Other SQL Exception
                        log.error(LOGM_EXCEPTION, "Exception {}", e);
                        Exception e2 = e.getNextException();
                        throw e2;
                    }               
                }
			}
	
		} catch (SQLException e) {
			e.printStackTrace();
			log.error(LOGM_EXCEPTION, "Exception {}", e);
			return false;
		} catch (InvalidProtocolBufferException e) {
			e.printStackTrace();
			log.error(LOGM_EXCEPTION, "Exception {}", e);
			return false;
		}
		return true;
	}
	
	public static boolean storeSignature(String signature) {
//		try {
//			fw.write("SIGNATURE\n");
//			fw.write(signature);
//			fw.write("\n");
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
		return true;
	}

	private static int getTransactionTypeId (TransactionBody body) {
		if (body.hasContractCall()) {
			return transactionTypes.get("contractcall");
		} else if (body.hasContractCreateInstance()) {
			return transactionTypes.get("contractcreate");
		} else if (body.hasContractDeleteInstance()) {
			return transactionTypes.get("contractdelete");
		} else if (body.hasContractUpdateInstance()) {
			return transactionTypes.get("contractupdate");
		} else if (body.hasCryptoCreateAccount()) {
			return transactionTypes.get("cryptocreate");
		} else if (body.hasCryptoDelete()) {
			return transactionTypes.get("cryptodelete");
        } else if (body.hasCryptoTransfer()) {
            return transactionTypes.get("cryptotransfer");
        } else if (body.hasCryptoUpdateAccount()) {
            return transactionTypes.get("cryptoupdate");
        } else if (body.hasCryptoAddClaim()) {
            return transactionTypes.get("cryptoaddclaim");
		} else if (body.hasCryptoDeleteClaim()) {
			return transactionTypes.get("cryptodeleteclaim");
		} else if (body.hasFileAppend()) {
			return transactionTypes.get("fileappend");
		} else if (body.hasFileCreate()) {
			return transactionTypes.get("filecreate");
		} else if (body.hasFileDelete()) {
			return transactionTypes.get("filedelete");
		} else if (body.hasFileUpdate()) {
			return transactionTypes.get("fileupdate");
		} else {
			return transactionTypes.get("unknown");
		}
	}
	private static int getTxType(long amount) {
		if (amount < 0) {
			amount = -amount;
		}
		if (amount == 500000000) {
			// REWARD
			return 2;
		} else if (amount == 4666667) {
			// DTS
			return 3;
		} else if (amount == 100000) {
			// FEE
			return 4;
		} else {
			// OTHER
			return 1;
		}
	}
}
