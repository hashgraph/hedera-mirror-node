package com.hedera.recordFileLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLType;
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

	public enum INIT_RESULT {
		OK
		,FAIL
		,SKIP
	}
    enum F_TRANSACTION {
        ZERO // column indices start at 1, this creates the necessary offset
        ,FK_NODE_ACCOUNT_ID
        ,MEMO
        ,VS_SECONDS
        ,VS_NANOS
        ,FK_TRANS_TYPE_ID
        ,FK_PAYER_ACCOUNT_ID
        ,FK_RESULT
        ,CONSENSUS_SECONDS
        ,CONSENSUS_NANOS
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

	public static boolean start() {
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
        return true;
	}
	public static boolean finish() {
        try {
            connect = DatabaseUtilities.closeDatabase(connect);
        	return false;
        } catch (SQLException e) {
            log.error(LOGM_EXCEPTION, "Exception {}", e.getMessage());
        }
    	return true;
	}

	public static INIT_RESULT initFile(String fileName) {

		try {
			fileId = 0;

			// Has the file been processed already ?
            PreparedStatement selectFile = connect.prepareStatement("SELECT id FROM t_record_files WHERE name = ?");
			selectFile.setString(1,  fileName);
			ResultSet resultSet = selectFile.executeQuery();

            while (resultSet.next()) {
            	fileId = resultSet.getLong(1);
        		log.info("File {} already processed successfully", fileName);
        		return INIT_RESULT.SKIP;
            }
            resultSet.close();

            if (fileId == 0) {
			    PreparedStatement insertFile = connect.prepareStatement(
						"INSERT INTO t_record_files (name, load_start) VALUES (?, ?)"
						+ " RETURNING id");
				insertFile.setString(1, fileName);
				insertFile.setLong(2, Instant.now().getEpochSecond());
				insertFile.execute();
	            ResultSet newId = insertFile.getResultSet();
	            newId.next();
	            fileId = newId.getLong(1);
	            newId.close();
				insertFile.close();

				return INIT_RESULT.OK;
            }

		} catch (SQLException e) {
			log.error(LOGM_EXCEPTION, "Exception {}", e);
		}
		return INIT_RESULT.FAIL;
	}
	public static boolean completeFile(String fileHash, String previousHash) {
		// update the file to processed
		try {
			PreparedStatement updateFile = connect.prepareStatement("UPDATE t_record_files SET load_end = ?, file_hash = ?, prev_hash = ? WHERE id = ?");

			updateFile.setLong(1, Instant.now().getEpochSecond());
			if (Utility.hashIsEmpty(fileHash)) {
				updateFile.setObject(2, null);
			} else {
				updateFile.setString(2, fileHash);
			}

			if (Utility.hashIsEmpty(previousHash)) {
				updateFile.setObject(3, null);
			} else {
				updateFile.setString(3, previousHash);
			}
			updateFile.setLong(4, fileId);
			updateFile.execute();
			updateFile.close();
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
		try {
			connect.rollback();
		} catch (SQLException e) {
			log.error(LOGM_EXCEPTION, "Exception while rolling transaction back. Exception {}", e);
		}
	}
	public static boolean storeRecord(long counter, Instant consensusTimeStamp, Transaction transaction, TransactionRecord txRecord, ConfigLoader configLoader) throws Exception {
		try {
			PreparedStatement sqlInsertTransaction = connect.prepareStatement("INSERT INTO t_transactions"
						+ " (fk_node_acc_id, memo, vs_seconds, vs_nanos, fk_trans_type_id, fk_payer_acc_id"
						+ ", fk_result_id, consensus_seconds, consensus_nanos, fk_cud_entity_id, charged_tx_fee"
						+ ", initial_balance, fk_rec_file_id)"
						+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
						+ " RETURNING id");

			PreparedStatement sqlInsertTransferList = connect.prepareStatement("INSERT INTO t_cryptotransferlists"
					+ " (fk_trans_id, account_id, amount)"
					+ " VALUES (?, ?, ?)");

			PreparedStatement sqlInsertFileData = connect.prepareStatement("INSERT INTO t_file_data"
					+ " (fk_trans_id, file_data)"
					+ " VALUES (?, ?)");

			PreparedStatement sqlInsertContractCall = connect.prepareStatement("INSERT INTO t_contract_result"
					+ " (fk_trans_id, function_params, gas_supplied, call_result, gas_used)"
					+ " VALUES (?, ?, ?, ?, ?)");

			long fkTransactionId = 0;

			TransactionBody body = null;
			if (transaction.hasBody()) {
				body = transaction.getBody();
			} else {
				body = TransactionBody.parseFrom(transaction.getBodyBytes());
			}

			long fkNodeAccountId = entities.createOrGetEntity(body.getNodeAccountID());

			sqlInsertTransaction.setLong(F_TRANSACTION.FK_NODE_ACCOUNT_ID.ordinal(), fkNodeAccountId);
			sqlInsertTransaction.setBytes(F_TRANSACTION.MEMO.ordinal(), body.getMemo().getBytes());
			sqlInsertTransaction.setLong(F_TRANSACTION.VS_SECONDS.ordinal(), body.getTransactionID().getTransactionValidStart().getSeconds());
			sqlInsertTransaction.setLong(F_TRANSACTION.VS_NANOS.ordinal(), body.getTransactionID().getTransactionValidStart().getNanos());
			sqlInsertTransaction.setInt(F_TRANSACTION.FK_TRANS_TYPE_ID.ordinal(), getTransactionTypeId(body));
			sqlInsertTransaction.setLong(F_TRANSACTION.FK_REC_FILE_ID.ordinal(), fileId);

	        long fkPayerAccountId = entities.createOrGetEntity(body.getTransactionID().getAccountID());

			sqlInsertTransaction.setLong(F_TRANSACTION.FK_PAYER_ACCOUNT_ID.ordinal(), fkPayerAccountId);

			long fk_result_id = -1;
			String responseCode = ResponseCodeEnum.forNumber(txRecord.getReceipt().getStatus().getNumber()).getValueDescriptor().getName();

			if (transactionResults.containsKey(responseCode)) {
				fk_result_id = transactionResults.get(responseCode);
			}

			sqlInsertTransaction.setLong(F_TRANSACTION.FK_RESULT.ordinal(), fk_result_id);
			sqlInsertTransaction.setLong(F_TRANSACTION.CONSENSUS_SECONDS.ordinal(), txRecord.getConsensusTimestamp().getSeconds());
			sqlInsertTransaction.setLong(F_TRANSACTION.CONSENSUS_NANOS.ordinal(), txRecord.getConsensusTimestamp().getNanos());
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
                fkTransactionId = insertTransaction(sqlInsertTransaction);
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
                    if (configLoader.getPersistCryptoTransferAmounts()) {
	                    for (int i = 0; i < pTransfer.getAccountAmountsCount(); i++) {
	                        // insert
	                        sqlInsertTransferList.setLong(F_TRANSFERLIST.TXID.ordinal(), fkTransactionId);
	                        long xferAccountId = entities.createOrGetEntity(pTransfer.getAccountAmounts(i).getAccountID());
	                        sqlInsertTransferList.setLong(F_TRANSFERLIST.ACCOUNT_ID.ordinal(), xferAccountId);
	                        sqlInsertTransferList.setLong(F_TRANSFERLIST.AMOUNT.ordinal(), pTransfer.getAccountAmounts(i).getAmount());

	                        sqlInsertTransferList.addBatch();
	                    }

                        sqlInsertTransferList.executeBatch();
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
            	if (configLoader.getPersistContracts()) {
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
            	if (configLoader.getPersistContracts()) {
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
            	if (configLoader.getPersistClaims()) {
	            	byte[] claim = body.getCryptoAddClaim().getClaim().getHash().toByteArray();
	    			PreparedStatement sqlInsertClaimData = connect.prepareStatement("INSERT INTO t_livehash_data"
	    					+ " (fk_trans_id, livehash)"
	    					+ " VALUES (?, ?)");

	    			sqlInsertClaimData.setLong(F_LIVEHASH_DATA.FK_TRANS_ID.ordinal(), fkTransactionId);
	            	sqlInsertClaimData.setBytes(F_LIVEHASH_DATA.LIVEHASH.ordinal(), claim);
	            	sqlInsertClaimData.execute();

	            	sqlInsertClaimData.close();
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
            	if (configLoader.getPersistFiles().contentEquals("ALL") || (configLoader.getPersistFiles().contentEquals("SYSTEM") && body.getFileAppend().getFileID().getFileNum() < 1000)) {
	            	byte[] contents = body.getFileAppend().getContents().toByteArray();
	            	sqlInsertFileData.setLong(F_FILE_DATA.FK_TRANS_ID.ordinal(), fkTransactionId);
	            	sqlInsertFileData.setBytes(F_FILE_DATA.FILE_DATA.ordinal(), contents);
	            	sqlInsertFileData.execute();
            	}
            } else if (body.hasFileCreate()) {
            	if (configLoader.getPersistFiles().contentEquals("ALL") || (configLoader.getPersistFiles().contentEquals("SYSTEM") && txRecord.getReceipt().getFileID().getFileNum() < 1000)) {
	            	byte[] contents = body.getFileCreate().getContents().toByteArray();
	            	sqlInsertFileData.setLong(F_FILE_DATA.FK_TRANS_ID.ordinal(), fkTransactionId);
	            	sqlInsertFileData.setBytes(F_FILE_DATA.FILE_DATA.ordinal(), contents);
	            	sqlInsertFileData.execute();
            	}
            	//TODO:Address book + proxy amounts for nodes
            } else if (body.hasFileDelete()) {
            	// Do nothing
            } else if (body.hasFileUpdate()) {
            	if (configLoader.getPersistFiles().contentEquals("ALL") || (configLoader.getPersistFiles().contentEquals("SYSTEM") && body.getFileUpdate().getFileID().getFileNum() < 1000)) {
	            	byte[] contents = body.getFileUpdate().getContents().toByteArray();
	            	sqlInsertFileData.setLong(F_FILE_DATA.FK_TRANS_ID.ordinal(), fkTransactionId);
	            	sqlInsertFileData.setBytes(F_FILE_DATA.FILE_DATA.ordinal(), contents);
	            	sqlInsertFileData.execute();
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

            sqlInsertFileData.close();
            sqlInsertTransferList.close();
            sqlInsertTransaction.close();
            sqlInsertContractCall.close();

		} catch (SQLException e) {
			log.error(LOGM_EXCEPTION, "Exception {}", e);
			rollback();
			return false;
		} catch (InvalidProtocolBufferException e) {
			log.error(LOGM_EXCEPTION, "Exception {}", e);
			rollback();
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
		//TODO: Use oneOfDescriptor ?
		String transactionName = body.getDataCase().name();
		if (transactionTypes.containsKey(transactionName)) {
			return transactionTypes.get(transactionName);
		} else {
			return transactionTypes.get("unknown");
		}
	}

	private static long insertTransaction(PreparedStatement insertTransaction) throws SQLException {
		insertTransaction.execute();

        ResultSet newId = insertTransaction.getResultSet();
        newId.next();
        Long txId = newId.getLong(1);
        newId.close();

        return txId;
	}

	public static void insertContractResults(PreparedStatement insert, long fkTxId, byte[] functionParams, long gasSupplied, byte[] callResult, long gasUsed) throws SQLException {

		insert.setLong(F_CONTRACT_CALL.FK_TRANS_ID.ordinal(), fkTxId);
		insert.setBytes(F_CONTRACT_CALL.FUNCTION_PARAMS.ordinal(), functionParams);
		insert.setLong(F_CONTRACT_CALL.GAS_SUPPLIED.ordinal(), gasSupplied);
		insert.setBytes(F_CONTRACT_CALL.CALL_RESULT.ordinal(), callResult);
		insert.setLong(F_CONTRACT_CALL.GAS_USED.ordinal(), gasUsed);

		insert.execute();

	}
}
