package com.hedera.recordFileLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.configLoader.ConfigLoader;
import com.hedera.databaseUtilities.DatabaseUtilities;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
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
    
    enum F_FILE_DATA {
    	ZERO
    	,TX_ID
    	,FILE_DATA
    }
    
    enum F_CONTRACT_CALL {
    	ZERO
    	,TX_ID
    	,FUNCTION_PARAMS
    	,GAS_SUPPLIED
    	,CALL_RESULT
    	,GAS_USED
    }
    
    enum F_CLAIM_DATA {
    	ZERO
    	,TX_ID
    	,CLAIM_DATA
    }
    
    enum F_ENTITIES {
    	ZERO
    	,SHARD
    	,REALM
    	,NUM
    	,EXP_TIME_SECONDS
    	,EXP_TIME_NANOS
    	,AUTO_RENEW
    	,ADMIN_KEY
    	,KEY
    	,PROXY_ACCOUNT_ID
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
	public static long updateEntity(long shard, long realm, long num, long exp_time_seconds, long exp_time_nanos, long auto_renew_period, byte[] admin_key, byte[] key, long proxy_account_id) throws SQLException {
	    
		long entityId = 0;
		
	    if (shard + realm + num == 0 ) {
	        return 0;
	    }
	    // build the SQL to prepare a statement
	    String sqlUpdate = "UPDATE t_entities SET ";
	    int fieldCount = 0;
	    boolean bDoComma = false;
	    
	    if ((exp_time_seconds != 0) || (exp_time_nanos != 0)) {
	    	sqlUpdate += " exp_time_seconds = ?";
	    	sqlUpdate += ",exp_time_nanos = ?";
	    	bDoComma = true;
	    	
	    }
	    if (auto_renew_period != 0) {
	    	if (bDoComma) sqlUpdate += ",";
	    	sqlUpdate += "auto_renew_period = ?";
	    	bDoComma = true;
	    }
	    
	    if (admin_key != null) {
	    	if (bDoComma) sqlUpdate += ",";
	    	sqlUpdate += "admin_key = ?";
	    	bDoComma = true;
	    }

	    if (key != null) {
	    	if (bDoComma) sqlUpdate += ",";
	    	sqlUpdate += "key = ?";
	    	bDoComma = true;
	    }

	    if (proxy_account_id != 0) {
	    	if (bDoComma) sqlUpdate += ",";
	    	sqlUpdate += "proxy_account_id = ?";
	    	bDoComma = true;
	    }
	    
	    sqlUpdate += " WHERE entity_shard = ?";
	    sqlUpdate += " AND entity_realm = ?";
	    sqlUpdate += " AND entity_num = ?";
	    sqlUpdate += " RETURNING id";

	    // inserts or returns an existing entity
        PreparedStatement updateEntity = connect.prepareStatement(sqlUpdate);

	    if ((exp_time_seconds != 0) || (exp_time_nanos != 0)) {
        	updateEntity.setLong(1, exp_time_seconds);        	
        	updateEntity.setLong(2, exp_time_nanos);
        	fieldCount = 2;
	    }
	    
	    if (auto_renew_period != 0) {
	    	fieldCount += 1;
	    	updateEntity.setLong(fieldCount, auto_renew_period);
	    }
	    
	    if (admin_key != null) {
	    	fieldCount += 1;
    		updateEntity.setBytes(fieldCount, admin_key);
	    }

	    if (key != null) {
	    	fieldCount += 1;
    		updateEntity.setBytes(fieldCount, key);
	    }

	    if (proxy_account_id != 0) {
	    	fieldCount += 1;
	    	updateEntity.setLong(fieldCount, proxy_account_id);
	    }
	    
    	fieldCount += 1;
	    updateEntity.setLong(fieldCount, shard);
    	fieldCount += 1;
        updateEntity.setLong(fieldCount, realm);
    	fieldCount += 1;
        updateEntity.setLong(fieldCount, num);

        updateEntity.execute();
        
        ResultSet newId = updateEntity.getResultSet();
        newId.next();
        entityId = newId.getLong(1);
        newId.close();
        updateEntity.close();
        
        return entityId;
	    
	}

	public static long updateEntity(FileID fileId, long exp_time_seconds, long exp_time_nanos, long auto_renew_period, byte[] admin_key, byte[] key, long proxy_account_id) throws SQLException {
		return updateEntity(fileId.getShardNum(),fileId.getRealmNum(), fileId.getFileNum(), exp_time_seconds, exp_time_nanos, auto_renew_period, admin_key, key, proxy_account_id);
	}
	public static long updateEntity(ContractID contractId, long exp_time_seconds, long exp_time_nanos, long auto_renew_period, byte[] admin_key, byte[] key, long proxy_account_id) throws SQLException {
		return updateEntity(contractId.getShardNum(),contractId.getRealmNum(), contractId.getContractNum(), exp_time_seconds, exp_time_nanos, auto_renew_period, admin_key, key, proxy_account_id);
	}
	public static long updateEntity(AccountID accountId, long exp_time_seconds, long exp_time_nanos, long auto_renew_period, byte[] admin_key, byte[] key, long proxy_account_id) throws SQLException {
		return updateEntity(accountId.getShardNum(),accountId.getRealmNum(), accountId.getAccountNum(), exp_time_seconds, exp_time_nanos, auto_renew_period, admin_key, key, proxy_account_id);
	}

	public static long deleteEntity(long shard, long realm, long num) throws SQLException {
	    
		long entityId = 0;
		
	    if (shard + realm + num == 0 ) {
	        return 0;
	    }
	    // build the SQL to prepare a statement
	    String sqlDelete = "UPDATE t_entities SET deleted = true";
	    sqlDelete += " WHERE entity_shard = ?";
	    sqlDelete += " AND entity_realm = ?";
	    sqlDelete += " AND entity_num = ?";
	    sqlDelete += " RETURNING id";

	    // inserts or returns an existing entity
        PreparedStatement deleteEntity = connect.prepareStatement(sqlDelete);

	    deleteEntity.setLong(1, shard);
        deleteEntity.setLong(2, realm);
        deleteEntity.setLong(3, num);

        deleteEntity.execute();
        
        ResultSet newId = deleteEntity.getResultSet();
        newId.next();
        entityId = newId.getLong(1);
        newId.close();
        deleteEntity.close();
        
        return entityId;
	    
	}

	public static long deleteEntity(FileID fileId) throws SQLException {
		return deleteEntity(fileId.getShardNum(),fileId.getRealmNum(), fileId.getFileNum());
	}
	public static long deleteEntity(ContractID contractId) throws SQLException {
		return deleteEntity(contractId.getShardNum(),contractId.getRealmNum(), contractId.getContractNum());
	}
	public static long deleteEntity(AccountID accountId) throws SQLException {
		return deleteEntity(accountId.getShardNum(),accountId.getRealmNum(), accountId.getAccountNum());
	}
	
	public static long unDeleteEntity(long shard, long realm, long num) throws SQLException {
	    
		long entityId = 0;
		
	    if (shard + realm + num == 0 ) {
	        return 0;
	    }
	    // build the SQL to prepare a statement
	    String sqlDelete = "UPDATE t_entities SET deleted = false";
	    sqlDelete += " WHERE entity_shard = ?";
	    sqlDelete += " AND entity_realm = ?";
	    sqlDelete += " AND entity_num = ?";
	    sqlDelete += " RETURNING id";

	    // inserts or returns an existing entity
        PreparedStatement deleteEntity = connect.prepareStatement(sqlDelete);

	    deleteEntity.setLong(1, shard);
        deleteEntity.setLong(2, realm);
        deleteEntity.setLong(3, num);

        deleteEntity.execute();
        
        ResultSet newId = deleteEntity.getResultSet();
        newId.next();
        entityId = newId.getLong(1);
        newId.close();
        deleteEntity.close();
        
        return entityId;
	    
	}

	public static long unDeleteEntity(FileID fileId) throws SQLException {
		return unDeleteEntity(fileId.getShardNum(),fileId.getRealmNum(), fileId.getFileNum());
	}
	public static long unDeleteEntity(ContractID contractId) throws SQLException {
		return unDeleteEntity(contractId.getShardNum(),contractId.getRealmNum(), contractId.getContractNum());
	}
	public static long unDeleteEntity(AccountID accountId) throws SQLException {
		return unDeleteEntity(accountId.getShardNum(),accountId.getRealmNum(), accountId.getAccountNum());
	}

	public static long createEntity(long shard, long realm, long num, long exp_time_seconds, long exp_time_nanos, long auto_renew_period, byte[] admin_key, byte[] key, long proxy_account_id) throws SQLException {
	    long entityId = 0;
	    
	    if (shard + realm + num == 0 ) {
	        return 0;
	    }
	    
	    // inserts or returns an existing entity
        PreparedStatement insertEntity = connect.prepareStatement(
                "INSERT INTO t_entities (entity_shard, entity_realm, entity_num, exp_time_seconds, exp_time_nanos, auto_renew_period, admin_key, key, proxy_account_id)"
        		+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                + " ON CONFLICT (entity_shard, entity_realm, entity_num) "
                + " DO UPDATE"
                + " SET entity_shard = EXCLUDED.entity_shard"
                + " RETURNING id");
        
        insertEntity.setLong(F_ENTITIES.SHARD.ordinal(), shard);
        insertEntity.setLong(F_ENTITIES.REALM.ordinal(), realm);
        insertEntity.setLong(F_ENTITIES.NUM.ordinal(), num);
        if ((exp_time_seconds == 0) && (exp_time_nanos == 0)) {
        	insertEntity.setObject(F_ENTITIES.EXP_TIME_SECONDS.ordinal(), null);        	
        	insertEntity.setObject(F_ENTITIES.EXP_TIME_NANOS.ordinal(), null);
        } else {
        	insertEntity.setLong(F_ENTITIES.EXP_TIME_SECONDS.ordinal(), exp_time_seconds);        	
        	insertEntity.setLong(F_ENTITIES.EXP_TIME_NANOS.ordinal(), exp_time_nanos);
        }
        if (auto_renew_period == 0) {
        	insertEntity.setObject(F_ENTITIES.AUTO_RENEW.ordinal(), null);
        } else {
        	insertEntity.setLong(F_ENTITIES.AUTO_RENEW.ordinal(), auto_renew_period);        	
        }

    	if (admin_key == null) {
    		insertEntity.setObject(F_ENTITIES.ADMIN_KEY.ordinal(), null);
    	} else {
    		insertEntity.setBytes(F_ENTITIES.ADMIN_KEY.ordinal(), admin_key);
    	}
    	if (key == null) {
    		insertEntity.setObject(F_ENTITIES.KEY.ordinal(), null);
    	} else {
    		insertEntity.setBytes(F_ENTITIES.KEY.ordinal(), key);
    	}
    	if (proxy_account_id == 0) {
    		insertEntity.setObject(F_ENTITIES.PROXY_ACCOUNT_ID.ordinal(), null);
    	} else {
    		insertEntity.setLong(F_ENTITIES.PROXY_ACCOUNT_ID.ordinal(), proxy_account_id);
    	}
        insertEntity.execute();
        ResultSet newId = insertEntity.getResultSet();
        newId.next();
        entityId = newId.getLong(1);
        newId.close();
        insertEntity.close();
        
        return entityId;
	    
	}

	public static long createEntity(FileID fileId, long exp_time_seconds, long exp_time_nanos, long auto_renew_period, byte[] admin_key, byte[] key, long proxy_account_id) throws SQLException {
		return createEntity(fileId.getShardNum(),fileId.getRealmNum(), fileId.getFileNum(), exp_time_seconds, exp_time_nanos, auto_renew_period, admin_key, key, proxy_account_id);
	}
	public static long createEntity(ContractID contractId, long exp_time_seconds, long exp_time_nanos, long auto_renew_period, byte[] admin_key, byte[] key, long proxy_account_id) throws SQLException {
		return createEntity(contractId.getShardNum(),contractId.getRealmNum(), contractId.getContractNum(), exp_time_seconds, exp_time_nanos, auto_renew_period, admin_key, key, proxy_account_id);
	}
	public static long createEntity(AccountID accountId, long exp_time_seconds, long exp_time_nanos, long auto_renew_period, byte[] admin_key, byte[] key, long proxy_account_id) throws SQLException {
		return createEntity(accountId.getShardNum(),accountId.getRealmNum(), accountId.getAccountNum(), exp_time_seconds, exp_time_nanos, auto_renew_period, admin_key, key, proxy_account_id);
	}
	public static long createOrGetEntity(long shard, long realm, long num) throws SQLException {
	    long entityId = 0;
	    
	    if (shard + realm + num == 0 ) {
	        return 0;
	    }
	    
	    // inserts or returns an existing entity
        PreparedStatement insertEntity = connect.prepareStatement(
                "INSERT INTO t_entities (entity_shard, entity_realm, entity_num)"
        		+ " VALUES (?, ?, ?)"
                + " ON CONFLICT (entity_shard, entity_realm, entity_num) "
                + " DO UPDATE"
                + " SET entity_shard = EXCLUDED.entity_shard"
                + " RETURNING id");
        
        insertEntity.setLong(F_ENTITIES.SHARD.ordinal(), shard);
        insertEntity.setLong(F_ENTITIES.REALM.ordinal(), realm);
        insertEntity.setLong(F_ENTITIES.NUM.ordinal(), num);
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
	public static boolean storeRecord(long counter, Instant consensusTimeStamp, Transaction transaction, TransactionRecord txRecord, ConfigLoader configLoader) throws Exception {
		try {
			PreparedStatement sqlInsertTransaction = connect.prepareStatement("INSERT INTO t_transactions"
						+ " (node_account_id, memo, seconds, nanos, xfer_count, trans_type_id, trans_account_id, result, consensus_seconds, consensus_nanos, crud_entity_id, transaction_fee, initial_balance, transaction_id)"
						+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
						+ " RETURNING id");

			PreparedStatement sqlInsertTransfer = connect.prepareStatement("INSERT INTO t_cryptotransfers"
					+ " (tx_id, from_account_id, to_account_id, amount, payment_type_id)"
					+ " VALUES (?, ?, ?, ?, ?)");
	
			PreparedStatement sqlInsertTransferList = connect.prepareStatement("insert into t_cryptotransferlists"
					+ " (tx_id, account_id, amount, payment_type_id)"
					+ " VALUES (?, ?, ?, ?)");
	
			PreparedStatement sqlInsertFileData = connect.prepareStatement("INSERT INTO t_file_data"
					+ " (tx_id, file_data)"
					+ " VALUES (?, ?)");
			
			PreparedStatement sqlInsertContractCall = connect.prepareStatement("INSERT INTO t_contract_result"
					+ " (tx_id, function_params, gas_supplied, call_result, gas_used)" 
					+ " VALUES (?, ?, ?, ?, ?)");
			
			PreparedStatement sqlInsertClaimData = connect.prepareStatement("INSERT INTO t_claim_data"
					+ " (tx_id, claim_data)"
					+ " VALUES (?, ?)");
			
		
			long transactionId = 0;
			boolean duplicateTX = false;
						
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
            
            if (body.hasContractCall()) {
                if (body.getContractCall().hasContractID()) {
                    entityId = createOrGetEntity(body.getContractCall().getContractID());
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
	            	long proxy_account_id = createOrGetEntity(txMessage.getProxyAccountID());
	            	entityId = createEntity(txRecord.getReceipt().getContractID(), expiration_time_sec, expiration_time_nanos, auto_renew_period, admin_key, key, proxy_account_id);
            	}
            	
                initialBalance = body.getContractCreateInstance().getInitialBalance();
            } else if (body.hasContractDeleteInstance()) {
                if (body.getContractDeleteInstance().hasContractID()) {
                    entityId = createOrGetEntity(body.getContractDeleteInstance().getContractID());
                    entityId = deleteEntity(body.getContractDeleteInstance().getContractID());
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
            	long proxy_account_id = createOrGetEntity(txMessage.getProxyAccountID());
            	
            	entityId = updateEntity(txMessage.getContractID(), expiration_time_sec, expiration_time_nanos, auto_renew_period, admin_key, key, proxy_account_id);
            } else if (body.hasCryptoAddClaim()) {
                if (body.getCryptoAddClaim().hasClaim()) {
                    if (body.getCryptoAddClaim().getClaim().hasAccountID()) {
                        entityId = createOrGetEntity(body.getCryptoAddClaim().getClaim().getAccountID());
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
	            	long proxy_account_id = createOrGetEntity(txMessage.getProxyAccountID());
	            	entityId = createEntity(txRecord.getReceipt().getAccountID(), expiration_time_sec, expiration_time_nanos, auto_renew_period, admin_key, key, proxy_account_id);
            	}

            	initialBalance = body.getCryptoCreateAccount().getInitialBalance();
            } else if (body.hasCryptoDelete()) {
                if (body.getCryptoDelete().hasDeleteAccountID()) {
                    entityId = createOrGetEntity(body.getCryptoDelete().getDeleteAccountID());
                    entityId = deleteEntity(body.getCryptoDelete().getDeleteAccountID());
                }
            } else if (body.hasCryptoDeleteClaim()) {
                if (body.getCryptoDeleteClaim().hasAccountIDToDeleteFrom()) {
                    entityId = createOrGetEntity(body.getCryptoDeleteClaim().getAccountIDToDeleteFrom());
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

            	long proxy_account_id = createOrGetEntity(txMessage.getProxyAccountID());
            	
            	entityId = updateEntity(body.getCryptoUpdateAccount().getAccountIDToUpdate(), expiration_time_sec, expiration_time_nanos, auto_renew_period, admin_key, key, proxy_account_id);
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
	            	entityId = createEntity(txRecord.getReceipt().getFileID(), expiration_time_sec, expiration_time_nanos, auto_renew_period, admin_key, key, proxy_account_id);
            	}
            } else if (body.hasFileAppend()) {
                if (body.getFileAppend().hasFileID()) {
                    entityId = createOrGetEntity(body.getFileAppend().getFileID());
                }
            } else if (body.hasFileDelete()) {
                if (body.getFileDelete().hasFileID()) {
                    entityId = createOrGetEntity(body.getFileDelete().getFileID());
                    entityId = deleteEntity(body.getFileDelete().getFileID());
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
            	
            	entityId = updateEntity(txMessage.getFileID(), expiration_time_sec, expiration_time_nanos, auto_renew_period, admin_key, key, proxy_account_id);
			} else if (body.hasFreeze()) {
				//TODO:
			} else if (body.hasSystemDelete()) {
				if (body.getSystemDelete().hasContractID()) {
					entityId = createOrGetEntity(body.getSystemDelete().getContractID());
                    entityId = deleteEntity(body.getSystemDelete().getContractID());
				} else if (body.getSystemDelete().hasFileID()) {
					entityId = createOrGetEntity(body.getSystemDelete().getFileID());
                    entityId = deleteEntity(body.getSystemDelete().getFileID());
				}
			} else if (body.hasSystemUndelete()) {
				if (body.getSystemUndelete().hasContractID()) {
					entityId = createOrGetEntity(body.getSystemUndelete().getContractID());
                    entityId = unDeleteEntity(body.getSystemDelete().getContractID());
				} else if (body.getSystemDelete().hasFileID()) {
					entityId = createOrGetEntity(body.getSystemUndelete().getFileID());
                    entityId = unDeleteEntity(body.getSystemDelete().getFileID());
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
                    transactionId = insertTransaction(sqlInsertTransaction);
                    sqlInsertTransaction.close();
				} catch (SQLException e) {
				    if (e.getSQLState().contentEquals("23505")) {
                        // duplicate transaction id, that's ok
				    	duplicateTX = true;
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
                    transactionId = insertTransaction(sqlInsertTransaction);
                    sqlInsertTransaction.close();
                    if (configLoader.getPersistCryptoTransferAmounts()) {
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
                    }
                } catch (SQLException e) {
                    if (e.getSQLState().contentEquals("23505")) {
                        // duplicate transaction id, that's ok
                    	duplicateTX = true;
                    } else {
                        // Other SQL Exception
                        log.error(LOGM_EXCEPTION, "Exception {}", e);
                        Exception e2 = e.getNextException();
                        throw e2;
                    }               
                }
			}
			
			if (!duplicateTX) {
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
		            	
		            	insertContractResults(sqlInsertContractCall, transactionId, functionParams, gasSupplied, callResult, gasUsed);
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
		            	
		            	insertContractResults(sqlInsertContractCall, transactionId, functionParams, gasSupplied, callResult, gasUsed);
	            	}
	            } else if (body.hasContractDeleteInstance()) {
	            	//TODO: 
	            } else if (body.hasContractUpdateInstance()) {
	            	//TODO: 
	            } else if (body.hasCryptoAddClaim()) {
	            	if (configLoader.getPersistClaims()) {
		            	byte[] claim = body.getCryptoAddClaim().getClaim().getHash().toByteArray();
		            	sqlInsertClaimData.setLong(F_CLAIM_DATA.TX_ID.ordinal(), transactionId);
		            	sqlInsertClaimData.setBytes(F_CLAIM_DATA.CLAIM_DATA.ordinal(), claim);
		            	sqlInsertClaimData.execute();
	            	}
	            } else if (body.hasCryptoDeleteClaim()) {
	            	//TODO: 
	            } else if (body.hasCryptoCreateAccount()) {
	            	//TODO: 
	            } else if (body.hasCryptoDelete()) {
	            	//TODO: 
	            } else if (body.hasCryptoTransfer()) {
	            	//TODO: 
	            } else if (body.hasCryptoUpdateAccount()) {
	            	//TODO: 
	            } else if (body.hasFileAppend()) {
	            	if (configLoader.getPersistFiles().contentEquals("ALL") || (configLoader.getPersistFiles().contentEquals("SYSTEM") && body.getFileAppend().getFileID().getFileNum() < 1000)) {
		            	byte[] contents = body.getFileAppend().getContents().toByteArray();
		            	sqlInsertFileData.setLong(F_FILE_DATA.TX_ID.ordinal(), transactionId);
		            	sqlInsertFileData.setBytes(F_FILE_DATA.FILE_DATA.ordinal(), contents);
		            	sqlInsertFileData.execute();
	            	}
	            } else if (body.hasFileCreate()) {
	            	if (configLoader.getPersistFiles().contentEquals("ALL") || (configLoader.getPersistFiles().contentEquals("SYSTEM") && txRecord.getReceipt().getFileID().getFileNum() < 1000)) {
		            	byte[] contents = body.getFileCreate().getContents().toByteArray();
		            	sqlInsertFileData.setLong(F_FILE_DATA.TX_ID.ordinal(), transactionId);
		            	sqlInsertFileData.setBytes(F_FILE_DATA.FILE_DATA.ordinal(), contents);
		            	sqlInsertFileData.execute();
	            	}
	            } else if (body.hasFileDelete()) {
	            	//TODO: 
	            } else if (body.hasFileUpdate()) {
	            	if (configLoader.getPersistFiles().contentEquals("ALL") || (configLoader.getPersistFiles().contentEquals("SYSTEM") && body.getFileUpdate().getFileID().getFileNum() < 1000)) {
		            	byte[] contents = body.getFileUpdate().getContents().toByteArray();
		            	sqlInsertFileData.setLong(F_FILE_DATA.TX_ID.ordinal(), transactionId);
		            	sqlInsertFileData.setBytes(F_FILE_DATA.FILE_DATA.ordinal(), contents);
		            	sqlInsertFileData.execute();
	            	}
	            } else if (body.hasFreeze()) {
	            	//TODO: 
	            } else if (body.hasSystemDelete()) {
	            	//TODO: 
	            } else if (body.hasSystemUndelete()) {
	            	//TODO: 
	            }
			}            
            sqlInsertFileData.close();
            sqlInsertTransfer.close();
            sqlInsertTransferList.close();
            sqlInsertContractCall.close();
            sqlInsertClaimData.close();
	
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
		} else if (body.hasCryptoUpdateAccount()) {
			return transactionTypes.get("cryptoUpdate");
		} else if (body.hasFreeze()) {
			return transactionTypes.get("freeze");
		} else if (body.hasSystemDelete()) {
			return transactionTypes.get("systemDelete");
		} else if (body.hasSystemUndelete()) {
			return transactionTypes.get("systemUndelete");
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
	
	private static long insertTransaction(PreparedStatement insertTransaction) throws SQLException {
		insertTransaction.execute();

        ResultSet newId = insertTransaction.getResultSet();
        newId.next();
        Long txId = newId.getLong(1);
        newId.close();
        
        return txId;
	}
	
	public static void insertContractResults(PreparedStatement insert, long txId, byte[] functionParams, long gasSupplied, byte[] callResult, long gasUsed) throws SQLException {
		
		insert.setLong(F_CONTRACT_CALL.TX_ID.ordinal(), txId);
		insert.setBytes(F_CONTRACT_CALL.FUNCTION_PARAMS.ordinal(), functionParams);
		insert.setLong(F_CONTRACT_CALL.GAS_SUPPLIED.ordinal(), gasSupplied);
		insert.setBytes(F_CONTRACT_CALL.CALL_RESULT.ordinal(), callResult);
		insert.setLong(F_CONTRACT_CALL.GAS_USED.ordinal(), gasUsed);
		
		insert.execute();
		
	}
}
