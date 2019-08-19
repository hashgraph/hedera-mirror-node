package com.hedera.recordFileLogger;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.HashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.addressBook.NetworkAddressBook;
import com.hedera.configLoader.ConfigLoader;
import com.hedera.databaseUtilities.DatabaseUtilities;
import com.hedera.hashgraph.sdk.proto.ResponseCodeEnum;
import com.hedera.utilities.Utility;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;

public class RecordFileLogger {
	private static final Logger log = LogManager.getLogger("recordfilelogger");
	static final Marker LOGM_EXCEPTION = MarkerManager.getMarker("EXCEPTION");

	public static Connection connect = null;
	private static Entities entities = null;

	private static HashMap<String, Integer> transactionResults = null;
	private static HashMap<String, Integer> transactionTypes = null;

	private static long fileId = 0;
	private static long BATCH_SIZE = 100;
	private static long batch_count = 0;

	private static PreparedStatement sqlInsertTransaction;
	private static PreparedStatement sqlInsertTransferList;
	private static PreparedStatement sqlInsertFileData;
	private static PreparedStatement sqlInsertContractCall;
	private static PreparedStatement sqlInsertClaimData;
	
	public enum INIT_RESULT {
		OK
		,FAIL
		,SKIP
	}
    enum F_TRANSACTION {
        ZERO // column indices start at 1, this creates the necessary offset
        ,ID
        ,FK_NODE_ACCOUNT_ID
        ,MEMO
        ,VS_SECONDS
        ,VS_NANOS
        ,VALID_START_NS
        ,FK_TRANS_TYPE_ID
        ,FK_PAYER_ACCOUNT_ID
        ,FK_RESULT
        ,CONSENSUS_SECONDS
        ,CONSENSUS_NANOS
        ,CONSENSUS_NS
        ,CUD_ENTITY_ID
        ,CHARGED_TX_FEE
        ,INITIAL_BALANCE
        ,FK_REC_FILE_ID
    }

    enum F_TRANSFERLIST {
        ZERO // column indices start at 1, this creates the necessary offset
        ,TXID
        ,ACCOUNT_ID
        ,AMOUNT
        ,TYPE_ID
    }

    enum F_FILE_DATA {
    	ZERO
    	,FK_TRANS_ID
    	,FILE_DATA
    }

    enum F_CONTRACT_CALL {
    	ZERO
    	,FK_TRANS_ID
    	,FUNCTION_PARAMS
    	,GAS_SUPPLIED
    	,CALL_RESULT
    	,GAS_USED
    }

    enum F_LIVEHASH_DATA {
    	ZERO
    	,FK_TRANS_ID
    	,LIVEHASH
    }
    
    private static boolean bSkip = false;

	public static boolean start() {
		batch_count = 0;
		
        connect = DatabaseUtilities.openDatabase(connect);

        if (connect == null) {
            log.error(LOGM_EXCEPTION, "Unable to connect to database");
        	return false;
        }
        // do not auto-commit
        try {
			connect.setAutoCommit(false);
		} catch (SQLException e) {
            log.error(LOGM_EXCEPTION, "Unable to connect to set connection to not auto commit, Exception: {}", e.getMessage());
        	return false;
		}
        try {
			entities = new Entities(connect);
		} catch (SQLException e) {
            log.error(LOGM_EXCEPTION, "Unable to fetch entity types, Exception: {}", e.getMessage());
        	return false;
		}

        if (transactionTypes == null) {
            transactionTypes = new HashMap<String, Integer>();
            ResultSet resultSet;
			try {
				resultSet = connect.createStatement().executeQuery("SELECT id, name FROM t_transaction_types ORDER BY id");
	            while (resultSet.next()) {
	                transactionTypes.put(resultSet.getString("name"), resultSet.getInt("id"));
	            }
	            resultSet.close();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
	            log.error(LOGM_EXCEPTION, "Unable to fetch transaction types - Exception {}", e.getMessage());
				return false;
			}
        }
        if (transactionResults == null) {
        	transactionResults = new HashMap<String, Integer>();
            ResultSet resultSet;
			try {
				resultSet = RecordFileLogger.connect.createStatement().executeQuery("SELECT id, result FROM t_transaction_results ORDER BY id");
	            while (resultSet.next()) {
	            	transactionResults.put(resultSet.getString("result"), resultSet.getInt("id"));
	            }
	            resultSet.close();
			} catch (SQLException e) {
	            log.error(LOGM_EXCEPTION, "Unable to fetch entity types - Exception {}", e.getMessage());
				return false;
			}
        }
		try {
			sqlInsertTransaction = connect.prepareStatement("INSERT INTO t_transactions"
					+ " (id, fk_node_acc_id, memo, vs_seconds, vs_nanos, valid_start_ns, fk_trans_type_id, fk_payer_acc_id"
					+ ", fk_result_id, consensus_seconds, consensus_nanos, consensus_ns, fk_cud_entity_id, charged_tx_fee"
					+ ", initial_balance, fk_rec_file_id)"
					+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
					+ " RETURNING id");
			sqlInsertTransferList = connect.prepareStatement("INSERT INTO t_cryptotransferlists"
					+ " (fk_trans_id, account_id, amount)"
					+ " VALUES (?, ?, ?)");
		
			sqlInsertFileData = connect.prepareStatement("INSERT INTO t_file_data"
					+ " (fk_trans_id, file_data)"
					+ " VALUES (?, ?)");
		
			sqlInsertContractCall = connect.prepareStatement("INSERT INTO t_contract_result"
					+ " (fk_trans_id, function_params, gas_supplied, call_result, gas_used)"
					+ " VALUES (?, ?, ?, ?, ?)");

			sqlInsertClaimData = connect.prepareStatement("INSERT INTO t_livehash_data"
					+ " (fk_trans_id, livehash)"
					+ " VALUES (?, ?)");
			
		} catch (SQLException e) {
            log.error(LOGM_EXCEPTION, "Unable to prepare SQL statements - Exception {}", e.getMessage());
			return false;
		}

        return true;
	}
	public static boolean finish() {
        try {
            sqlInsertFileData.close();
            sqlInsertTransferList.close();
            sqlInsertTransaction.close();
            sqlInsertContractCall.close();
            sqlInsertClaimData.close();
        	
            connect = DatabaseUtilities.closeDatabase(connect);
        	return false;
        } catch (SQLException e) {
            log.error(LOGM_EXCEPTION, "Exception {}", e.getMessage());
        }
    	return true;
	}

	public static INIT_RESULT initFile(String fileName) {
		if (bSkip) { fileId = 0; return INIT_RESULT.OK;}

		try {
			fileId = 0;

			CallableStatement fileCreate = connect.prepareCall("{? = call f_file_create( ? ) }");
			fileCreate.registerOutParameter(1, Types.BIGINT);
			fileCreate.setString(2, fileName);
			fileCreate.execute();
			fileId = fileCreate.getLong(1);
			fileCreate.close();
			
			if (fileId == 0) {
				return INIT_RESULT.SKIP;
			} else {
				return INIT_RESULT.OK;
			}

		} catch (SQLException e) {
			log.error(LOGM_EXCEPTION, "Exception {}", e);
		}
		return INIT_RESULT.FAIL;
	}
		
	public static boolean completeFile(String fileHash, String previousHash) {
		if (bSkip) { return true;}

		try {
			// execute any remaining batches
	    	executeBatches();

			// update the file to processed
	    	CallableStatement fileClose = connect.prepareCall("{call f_file_complete( ?, ?, ? ) }");
	    	
			fileClose.setLong(1, fileId);

			if (Utility.hashIsEmpty(fileHash)) {
				fileClose.setObject(2, null);
			} else {
				fileClose.setString(2, fileHash);
			}

			if (Utility.hashIsEmpty(previousHash)) {
				fileClose.setObject(3, null);
			} else {
				fileClose.setString(3, previousHash);
			}
			
			fileClose.execute();
			fileClose.close();
			// commit the changes to the database
			connect.commit();
		} catch (SQLException e) {
			log.error(LOGM_EXCEPTION, "Exception {}", e);
			rollback();
			return false;
		}
		return true;
	}
	public static void rollback() {
		if (bSkip) { return;}
		try {
			connect.rollback();
		} catch (SQLException e) {
			log.error(LOGM_EXCEPTION, "Exception while rolling transaction back. Exception {}", e);
		}
	}
	public static boolean storeRecord(long counter, Instant consensusTimeStamp, Transaction transaction, TransactionRecord txRecord) throws Exception {

		try {

			long fkTransactionId = 0;
			
            ResultSet resultSet;
			try {
				resultSet = connect.createStatement().executeQuery("SELECT nextval('s_transactions_seq')");
				resultSet.next();
            	fkTransactionId = resultSet.getLong(1);
	            resultSet.close();
			} catch (SQLException e) {
	            log.error(LOGM_EXCEPTION, "Unable to fetch new transaction id - Exception {}", e.getMessage());
				return false;
			}
			
			TransactionBody body = null;
			if (transaction.hasBody()) {
				body = transaction.getBody();
			} else {
				body = TransactionBody.parseFrom(transaction.getBodyBytes());
			}
			long fkNodeAccountId = entities.createOrGetEntity(body.getNodeAccountID());
			TransactionID transactionID = body.getTransactionID();
			long seconds = transactionID.getTransactionValidStart().getSeconds();
			long nanos = transactionID.getTransactionValidStart().getNanos();
			long validStartNs = Utility.convertInstantToNanos(Instant.ofEpochSecond(seconds, nanos));

			sqlInsertTransaction.setLong(F_TRANSACTION.ID.ordinal(), fkTransactionId);
			sqlInsertTransaction.setLong(F_TRANSACTION.FK_NODE_ACCOUNT_ID.ordinal(), fkNodeAccountId);
			sqlInsertTransaction.setBytes(F_TRANSACTION.MEMO.ordinal(), body.getMemo().getBytes());
			sqlInsertTransaction.setLong(F_TRANSACTION.VS_SECONDS.ordinal(), seconds);
			sqlInsertTransaction.setLong(F_TRANSACTION.VS_NANOS.ordinal(), nanos);
			sqlInsertTransaction.setLong(F_TRANSACTION.VALID_START_NS.ordinal(), validStartNs);
			sqlInsertTransaction.setInt(F_TRANSACTION.FK_TRANS_TYPE_ID.ordinal(), getTransactionTypeId(body));
			sqlInsertTransaction.setLong(F_TRANSACTION.FK_REC_FILE_ID.ordinal(), fileId);

	        long fkPayerAccountId = entities.createOrGetEntity(transactionID.getAccountID());

			sqlInsertTransaction.setLong(F_TRANSACTION.FK_PAYER_ACCOUNT_ID.ordinal(), fkPayerAccountId);

			long fk_result_id = -1;
			String responseCode = ResponseCodeEnum.forNumber(txRecord.getReceipt().getStatus().getNumber()).getValueDescriptor().getName();

			fk_result_id = transactionResults.get(responseCode);

			seconds = txRecord.getConsensusTimestamp().getSeconds();
			nanos = txRecord.getConsensusTimestamp().getNanos();
			long consensusNs = Utility.convertInstantToNanos(Instant.ofEpochSecond(seconds, nanos));
			
			sqlInsertTransaction.setLong(F_TRANSACTION.FK_RESULT.ordinal(), fk_result_id);
			sqlInsertTransaction.setLong(F_TRANSACTION.CONSENSUS_SECONDS.ordinal(), seconds);
			sqlInsertTransaction.setLong(F_TRANSACTION.CONSENSUS_NANOS.ordinal(), nanos);
			sqlInsertTransaction.setLong(F_TRANSACTION.CONSENSUS_NS.ordinal(), consensusNs);
			sqlInsertTransaction.setLong(F_TRANSACTION.CHARGED_TX_FEE.ordinal(), txRecord.getTransactionFee());
			
            long entityId = 0;
            long initialBalance = 0;

            if (body.hasContractCall()) {
                if (body.getContractCall().hasContractID()) {
                    entityId = entities.createOrGetEntity(body.getContractCall().getContractID());
                }
            } else if (body.hasContractCreateInstance()) {
            	if (txRecord.getReceipt().hasContractID()) {
            		ContractCreateTransactionBody txMessage = body.getContractCreateInstance();
	            	long expiration_time_sec = 0;
	            	long expiration_time_nanos = 0;
	            	long auto_renew_period = 0;
	            	if (txMessage.hasAutoRenewPeriod()) {
	            		auto_renew_period = txMessage.getAutoRenewPeriod().getSeconds();
	            	}
	            	byte[] admin_key = null;
	            	if (txMessage.hasAdminKey()) {
	            		admin_key = txMessage.getAdminKey().toByteArray();
	            	}
	            	byte[] key = null;
	            	long proxy_account_id = entities.createOrGetEntity(txMessage.getProxyAccountID());
	            	entityId = entities.createEntity(txRecord.getReceipt().getContractID(), expiration_time_sec, expiration_time_nanos, auto_renew_period, admin_key, key, proxy_account_id);
            	}

                initialBalance = body.getContractCreateInstance().getInitialBalance();
            } else if (body.hasContractDeleteInstance()) {
                if (body.getContractDeleteInstance().hasContractID()) {
                    entityId = entities.createOrGetEntity(body.getContractDeleteInstance().getContractID());
                    entityId = entities.deleteEntity(body.getContractDeleteInstance().getContractID());
                }
            } else if (body.hasContractUpdateInstance()) {
        		ContractUpdateTransactionBody txMessage = body.getContractUpdateInstance();
            	long expiration_time_sec = 0;
            	long expiration_time_nanos = 0;
            	if (txMessage.hasExpirationTime()) {
            		expiration_time_sec = txMessage.getExpirationTime().getSeconds();
            		expiration_time_nanos = txMessage.getExpirationTime().getNanos();
            	}
            	long auto_renew_period = 0;
            	if (txMessage.hasAutoRenewPeriod()) {
            		auto_renew_period = txMessage.getAutoRenewPeriod().getSeconds();
            	}

            	byte[] admin_key = null;
            	if (txMessage.hasAdminKey()) {
            		admin_key = txMessage.getAdminKey().toByteArray();
            	}

            	byte[] key = null;
            	long proxy_account_id = entities.createOrGetEntity(txMessage.getProxyAccountID());

            	entityId = entities.updateEntity(txMessage.getContractID(), expiration_time_sec, expiration_time_nanos, auto_renew_period, admin_key, key, proxy_account_id);
            } else if (body.hasCryptoAddClaim()) {
                if (body.getCryptoAddClaim().hasClaim()) {
                    if (body.getCryptoAddClaim().getClaim().hasAccountID()) {
                        entityId = entities.createOrGetEntity(body.getCryptoAddClaim().getClaim().getAccountID());
                    }
                }
            } else if (body.hasCryptoCreateAccount()) {
            	if (txRecord.getReceipt().hasAccountID()) {
            		CryptoCreateTransactionBody txMessage = body.getCryptoCreateAccount();
	            	long expiration_time_sec = 0;
	            	long expiration_time_nanos = 0;
	            	long auto_renew_period = 0;
	            	if (txMessage.hasAutoRenewPeriod()) {
	            		auto_renew_period = txMessage.getAutoRenewPeriod().getSeconds();
	            	}
	            	byte[] admin_key = null;
	            	byte[] key = null;
	            	if (txMessage.hasKey()) {
	            		key = txMessage.getKey().toByteArray();
	            	}
	            	long proxy_account_id = entities.createOrGetEntity(txMessage.getProxyAccountID());
	            	entityId = entities.createEntity(txRecord.getReceipt().getAccountID(), expiration_time_sec, expiration_time_nanos, auto_renew_period, admin_key, key, proxy_account_id);
            	}

            	initialBalance = body.getCryptoCreateAccount().getInitialBalance();
            } else if (body.hasCryptoDelete()) {
                if (body.getCryptoDelete().hasDeleteAccountID()) {
                    entityId = entities.createOrGetEntity(body.getCryptoDelete().getDeleteAccountID());
                    entityId = entities.deleteEntity(body.getCryptoDelete().getDeleteAccountID());
                }
            } else if (body.hasCryptoDeleteClaim()) {
                if (body.getCryptoDeleteClaim().hasAccountIDToDeleteFrom()) {
                    entityId = entities.createOrGetEntity(body.getCryptoDeleteClaim().getAccountIDToDeleteFrom());
                }
            } else if (body.hasCryptoTransfer()) {
            	// do nothing
            } else if (body.hasCryptoUpdateAccount()) {
        		CryptoUpdateTransactionBody txMessage = body.getCryptoUpdateAccount();
            	long expiration_time_sec = 0;
            	long expiration_time_nanos = 0;
            	if (txMessage.hasExpirationTime()) {
            		expiration_time_sec = txMessage.getExpirationTime().getSeconds();
            		expiration_time_nanos = txMessage.getExpirationTime().getNanos();
            	}
            	long auto_renew_period = 0;
            	if (txMessage.hasAutoRenewPeriod()) {
            		auto_renew_period = txMessage.getAutoRenewPeriod().getSeconds();
            	}

            	byte[] admin_key = null;

            	byte[] key = null;
            	if (txMessage.hasKey()) {
            		key = txMessage.getKey().toByteArray();
            	}

            	long proxy_account_id = entities.createOrGetEntity(txMessage.getProxyAccountID());

            	entityId = entities.updateEntity(body.getCryptoUpdateAccount().getAccountIDToUpdate(), expiration_time_sec, expiration_time_nanos, auto_renew_period, admin_key, key, proxy_account_id);
            } else if (body.hasFileCreate()) {
            	if (txRecord.getReceipt().hasFileID()) {
            		FileCreateTransactionBody txMessage = body.getFileCreate();
	            	long expiration_time_sec = 0;
	            	long expiration_time_nanos = 0;
	            	if (txMessage.hasExpirationTime()) {
	            		expiration_time_sec = txMessage.getExpirationTime().getSeconds();
	            		expiration_time_nanos = txMessage.getExpirationTime().getNanos();
	            	}
	            	long auto_renew_period = 0;
	            	byte[] admin_key = null;
	            	byte[] key = null;
	            	if (txMessage.hasKeys()) {
	            		key = txMessage.getKeys().toByteArray();
	            	}
	            	long proxy_account_id = 0;
	            	entityId = entities.createEntity(txRecord.getReceipt().getFileID(), expiration_time_sec, expiration_time_nanos, auto_renew_period, admin_key, key, proxy_account_id);
            	}
            } else if (body.hasFileAppend()) {
                if (body.getFileAppend().hasFileID()) {
                    entityId = entities.createOrGetEntity(body.getFileAppend().getFileID());
                }
            } else if (body.hasFileDelete()) {
                if (body.getFileDelete().hasFileID()) {
                    entityId = entities.createOrGetEntity(body.getFileDelete().getFileID());
                    entityId = entities.deleteEntity(body.getFileDelete().getFileID());
                }
            } else if (body.hasFileUpdate()) {
        		FileUpdateTransactionBody txMessage = body.getFileUpdate();
            	long expiration_time_sec = 0;
            	long expiration_time_nanos = 0;
            	if (txMessage.hasExpirationTime()) {
            		expiration_time_sec = txMessage.getExpirationTime().getSeconds();
            		expiration_time_nanos = txMessage.getExpirationTime().getNanos();
            	}
            	long auto_renew_period = 0;

            	byte[] admin_key = null;

            	byte[] key = null;
            	if (txMessage.hasKeys()) {
            		key = txMessage.getKeys().toByteArray();
            	}

            	long proxy_account_id = 0;

            	entityId = entities.updateEntity(txMessage.getFileID(), expiration_time_sec, expiration_time_nanos, auto_renew_period, admin_key, key, proxy_account_id);

			} else if (body.hasFreeze()) {
				//TODO:
			} else if (body.hasSystemDelete()) {
				if (body.getSystemDelete().hasContractID()) {
					entityId = entities.createOrGetEntity(body.getSystemDelete().getContractID());
                    entityId = entities.deleteEntity(body.getSystemDelete().getContractID());
				} else if (body.getSystemDelete().hasFileID()) {
					entityId = entities.createOrGetEntity(body.getSystemDelete().getFileID());
                    entityId = entities.deleteEntity(body.getSystemDelete().getFileID());
				}
			} else if (body.hasSystemUndelete()) {
				if (body.getSystemUndelete().hasContractID()) {
					entityId = entities.createOrGetEntity(body.getSystemUndelete().getContractID());
                    entityId = entities.unDeleteEntity(body.getSystemDelete().getContractID());
				} else if (body.getSystemDelete().hasFileID()) {
					entityId = entities.createOrGetEntity(body.getSystemUndelete().getFileID());
                    entityId = entities.unDeleteEntity(body.getSystemDelete().getFileID());
				}
			}

            if (entityId == 0) {
                // insert null
                sqlInsertTransaction.setObject(F_TRANSACTION.CUD_ENTITY_ID.ordinal(), null);
            } else {
                sqlInsertTransaction.setLong(F_TRANSACTION.CUD_ENTITY_ID.ordinal(), entityId);

            }
            sqlInsertTransaction.setLong(F_TRANSACTION.INITIAL_BALANCE.ordinal(), initialBalance);

			try {
//                fkTransactionId = insertTransaction(sqlInsertTransaction);
				sqlInsertTransaction.addBatch();
			} catch (SQLException e) {
			    if (e.getSQLState().contentEquals("23505")) {
                    // duplicate transaction id, rollback and exit
                	rollback();
                	return false;
			    } else {
			        // Other SQL Exception
			        throw e;
			    }
			}

			if (txRecord.hasTransferList()) {
	            TransferList pTransfer = txRecord.getTransferList();

                try {
                    if (ConfigLoader.getPersistCryptoTransferAmounts()) {
	                    for (int i = 0; i < pTransfer.getAccountAmountsCount(); i++) {
	                        // insert
	                        sqlInsertTransferList.setLong(F_TRANSFERLIST.TXID.ordinal(), fkTransactionId);
	                        long xferAccountId = entities.createOrGetEntity(pTransfer.getAccountAmounts(i).getAccountID());
	                        sqlInsertTransferList.setLong(F_TRANSFERLIST.ACCOUNT_ID.ordinal(), xferAccountId);
	                        sqlInsertTransferList.setLong(F_TRANSFERLIST.AMOUNT.ordinal(), pTransfer.getAccountAmounts(i).getAmount());

	                        sqlInsertTransferList.addBatch();
	                    }
//	                    if ( ! bSkip) {
//	                    	sqlInsertTransferList.executeBatch();
//	                    }
                    }
                } catch (SQLException e) {
                    if (e.getSQLState().contentEquals("23505")) {
                        // duplicate transaction id, rollback and exit
                    	rollback();
                    	return false;
                    } else {
                        // Other SQL Exception
                    	rollback();
                        log.error(LOGM_EXCEPTION, "Exception {}", e);
                        Exception e2 = e.getNextException();
                        throw e2;
                    }
                }
			}

			// now deal with transaction specifics
            if (body.hasContractCall()) {
            	if (ConfigLoader.getPersistContracts()) {
	            	byte[] functionParams = body.getContractCall().getFunctionParameters().toByteArray();
	            	long gasSupplied = body.getContractCall().getGas();
	            	byte[] callResult = new byte[0];
	            	long gasUsed = 0;
	            	if (txRecord.hasContractCallResult()) {
	            		callResult = txRecord.getContractCallResult().toByteArray();
	            		gasUsed = txRecord.getContractCallResult().getGasUsed();
	            	}

	            	insertContractResults(sqlInsertContractCall, fkTransactionId, functionParams, gasSupplied, callResult, gasUsed);
            	}
            } else if (body.hasContractCreateInstance()) {
            	if (ConfigLoader.getPersistContracts()) {
	            	byte[] functionParams = body.getContractCreateInstance().getConstructorParameters().toByteArray();
	            	long gasSupplied = body.getContractCreateInstance().getGas();
	            	byte[] callResult = new byte[0];
	            	long gasUsed = 0;
	            	if (txRecord.hasContractCreateResult()) {
	            		callResult = txRecord.getContractCreateResult().toByteArray();
	            		gasUsed = txRecord.getContractCreateResult().getGasUsed();
	            	}

	            	insertContractResults(sqlInsertContractCall, fkTransactionId, functionParams, gasSupplied, callResult, gasUsed);
            	}
            } else if (body.hasContractDeleteInstance()) {
            	// Do nothing
            } else if (body.hasContractUpdateInstance()) {
            	// Do nothing
            } else if (body.hasCryptoAddClaim()) {
            	if (ConfigLoader.getPersistClaims()) {
	            	byte[] claim = body.getCryptoAddClaim().getClaim().getHash().toByteArray();

	    			sqlInsertClaimData.setLong(F_LIVEHASH_DATA.FK_TRANS_ID.ordinal(), fkTransactionId);
	            	sqlInsertClaimData.setBytes(F_LIVEHASH_DATA.LIVEHASH.ordinal(), claim);
                    if ( ! bSkip) {
                    	sqlInsertClaimData.addBatch();
                    }
            	}
            } else if (body.hasCryptoDeleteClaim()) {
            	// Do nothing
            } else if (body.hasCryptoCreateAccount()) {
            	// Do nothing
            } else if (body.hasCryptoDelete()) {
            	// Do nothing
            } else if (body.hasCryptoTransfer()) {
            	// Do nothing
            } else if (body.hasCryptoUpdateAccount()) {
            	// Do nothing
            } else if (body.hasFileAppend()) {
            	if (ConfigLoader.getPersistFiles().contentEquals("ALL") || (ConfigLoader.getPersistFiles().contentEquals("SYSTEM") && body.getFileAppend().getFileID().getFileNum() < 1000)) {
	            	byte[] contents = body.getFileAppend().getContents().toByteArray();
	            	sqlInsertFileData.setLong(F_FILE_DATA.FK_TRANS_ID.ordinal(), fkTransactionId);
	            	sqlInsertFileData.setBytes(F_FILE_DATA.FILE_DATA.ordinal(), contents);
                    if ( ! bSkip) {
                    	sqlInsertFileData.addBatch();
                    }
            	}
            } else if (body.hasFileCreate()) {
            	if (ConfigLoader.getPersistFiles().contentEquals("ALL") || (ConfigLoader.getPersistFiles().contentEquals("SYSTEM") && txRecord.getReceipt().getFileID().getFileNum() < 1000)) {
	            	byte[] contents = body.getFileCreate().getContents().toByteArray();
	            	sqlInsertFileData.setLong(F_FILE_DATA.FK_TRANS_ID.ordinal(), fkTransactionId);
	            	sqlInsertFileData.setBytes(F_FILE_DATA.FILE_DATA.ordinal(), contents);
                    if ( ! bSkip) {
                    	sqlInsertFileData.addBatch();
                    }
            	}
            	//TODO:Address book + proxy amounts for nodes
            } else if (body.hasFileDelete()) {
            	// Do nothing
            } else if (body.hasFileUpdate()) {
            	if (ConfigLoader.getPersistFiles().contentEquals("ALL") || (ConfigLoader.getPersistFiles().contentEquals("SYSTEM") && body.getFileUpdate().getFileID().getFileNum() < 1000)) {
	            	byte[] contents = body.getFileUpdate().getContents().toByteArray();
	            	sqlInsertFileData.setLong(F_FILE_DATA.FK_TRANS_ID.ordinal(), fkTransactionId);
	            	sqlInsertFileData.setBytes(F_FILE_DATA.FILE_DATA.ordinal(), contents);
                    if ( ! bSkip) {
                    	sqlInsertFileData.addBatch();
                    }
            	}

            	// update the local address book
            	FileID updatedFile = body.getFileUpdate().getFileID();

            	if ((updatedFile.getFileNum() == 102) && (updatedFile.getShardNum() == 0) && (updatedFile.getRealmNum() == 0)) {
            		// we have an address book update, refresh the local file
            		NetworkAddressBook.writeFile(body.getFileUpdate().getContents().toByteArray());
            	}

            } else if (body.hasFreeze()) {
            	// Do nothing
            } else if (body.hasSystemDelete()) {
            	// Do nothing
            } else if (body.hasSystemUndelete()) {
            	// Do nothing
            }

		} catch (SQLException e) {
			log.error(LOGM_EXCEPTION, "Exception {}", e);
			rollback();
			return false;
		} catch (InvalidProtocolBufferException e) {
			log.error(LOGM_EXCEPTION, "Exception {}", e);
			rollback();
			return false;
		}
		
		if (batch_count == BATCH_SIZE - 1) {
        	// execute any remaining batches
        	executeBatches();
			batch_count = 0;
		} else {
			batch_count += 1 ;
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
		//TODO: Use oneOfDescriptor ?
		String transactionName = body.getDataCase().name();
		if (transactionTypes.containsKey(transactionName)) {
			return transactionTypes.get(transactionName);
		} else {
			return transactionTypes.get("unknown");
		}
	}

//	private static long insertTransaction(PreparedStatement insertTransaction) throws SQLException {
//        if ( bSkip) { return 0;}
//        
//		insertTransaction.execute();
//
//        ResultSet newId = insertTransaction.getResultSet();
//        newId.next();
//        Long txId = newId.getLong(1);
//        newId.close();
//
//        return txId;
//	}

	public static void insertContractResults(PreparedStatement insert, long fkTxId, byte[] functionParams, long gasSupplied, byte[] callResult, long gasUsed) throws SQLException {
        if ( bSkip) { return;}

		insert.setLong(F_CONTRACT_CALL.FK_TRANS_ID.ordinal(), fkTxId);
		insert.setBytes(F_CONTRACT_CALL.FUNCTION_PARAMS.ordinal(), functionParams);
		insert.setLong(F_CONTRACT_CALL.GAS_SUPPLIED.ordinal(), gasSupplied);
		insert.setBytes(F_CONTRACT_CALL.CALL_RESULT.ordinal(), callResult);
		insert.setLong(F_CONTRACT_CALL.GAS_USED.ordinal(), gasUsed);

		insert.addBatch();

	}
	
	private static void executeBatches() throws SQLException {
		sqlInsertTransaction.executeBatch();
		sqlInsertTransferList.executeBatch();
		sqlInsertFileData.executeBatch();
		sqlInsertContractCall.executeBatch();
		sqlInsertClaimData.executeBatch();
	}
}
