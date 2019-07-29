package com.hedera.recordFileLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;

public class Entities {
	private static int FK_ACCOUNT = 0;
	private static int FK_CONTRACT = 0;
	private static int FK_FILE = 0;
	
    enum F_ENTITIES {
    	ZERO
    	,SHARD
    	,REALM
    	,NUM
    	,FK_ENTITY_TYPE_ID
    	,EXP_TIME_SECONDS
    	,EXP_TIME_NANOS
    	,AUTO_RENEW
    	,ADMIN_KEY
    	,KEY
    	,FK_PROXY_ACCOUNT_ID
    }

	private static final Logger log = LogManager.getLogger("recordStream-log");
	static final Marker LOGM_EXCEPTION = MarkerManager.getMarker("EXCEPTION");
	
	private static Connection connect = null;
	
	public Entities(Connection connect) throws SQLException {
		this.connect = connect;
		if (this.connect != null) {
	        if (FK_ACCOUNT + FK_CONTRACT + FK_FILE == 0) {
	            ResultSet resultSet;
				resultSet = this.connect.createStatement().executeQuery("SELECT id, name FROM t_entity_types ORDER BY id");
	            while (resultSet.next()) {
	            	if (resultSet.getString("name").contentEquals("account")) {
	            		FK_ACCOUNT = resultSet.getInt("id");
	            	} else if (resultSet.getString("name").contentEquals("contract")) {
	            		FK_CONTRACT = resultSet.getInt("id");
	            	} else if (resultSet.getString("name").contentEquals("file")) {
	            		FK_FILE = resultSet.getInt("id");
	            	}
	            }       
	            resultSet.close();      
			}
		}
    }       

	private long updateEntity(long shard, long realm, long num, long exp_time_seconds, long exp_time_nanos, long auto_renew_period, byte[] admin_key, byte[] key, long fk_proxy_account_id) throws SQLException {
	    
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

	    if (fk_proxy_account_id != 0) {
	    	if (bDoComma) sqlUpdate += ",";
	    	sqlUpdate += "proxy_account_id = ?";
	    	bDoComma = true;
	    }
	    
	    sqlUpdate += " WHERE entity_shard = ?";
	    sqlUpdate += " AND entity_realm = ?";
	    sqlUpdate += " AND entity_num = ?";
	    sqlUpdate += " RETURNING id";

	    // inserts or returns an existing entity
        PreparedStatement updateEntity = this.connect.prepareStatement(sqlUpdate);

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

	    if (fk_proxy_account_id != 0) {
	    	fieldCount += 1;
	    	updateEntity.setLong(fieldCount, fk_proxy_account_id);
	    }
	    
    	fieldCount += 1;
	    updateEntity.setLong(fieldCount, shard);
    	fieldCount += 1;
        updateEntity.setLong(fieldCount, realm);
    	fieldCount += 1;
        updateEntity.setLong(fieldCount, num);

        updateEntity.execute();
        
        ResultSet newId = updateEntity.getResultSet();
        if (newId.next()) {
        	entityId = newId.getLong(1);
        	newId.close();
        	updateEntity.close();
        } else {
        	// expected entity not found
        	updateEntity.close();
        	throw new IllegalStateException("Expected entity not found, shard " + shard + ", realm " + realm + ", num " + num);
        }
        
        return entityId;
	    
	}

	public long updateEntity(FileID fileId, long exp_time_seconds, long exp_time_nanos, long auto_renew_period, byte[] admin_key, byte[] key, long fk_proxy_account_id) throws SQLException {
		return updateEntity(fileId.getShardNum(),fileId.getRealmNum(), fileId.getFileNum(), exp_time_seconds, exp_time_nanos, auto_renew_period, admin_key, key, fk_proxy_account_id);
	}
	public long updateEntity(ContractID contractId, long exp_time_seconds, long exp_time_nanos, long auto_renew_period, byte[] admin_key, byte[] key, long fk_proxy_account_id) throws SQLException {
		return updateEntity(contractId.getShardNum(),contractId.getRealmNum(), contractId.getContractNum(), exp_time_seconds, exp_time_nanos, auto_renew_period, admin_key, key, fk_proxy_account_id);
	}
	public long updateEntity(AccountID accountId, long exp_time_seconds, long exp_time_nanos, long auto_renew_period, byte[] admin_key, byte[] key, long fk_proxy_account_id) throws SQLException {
		return updateEntity(accountId.getShardNum(),accountId.getRealmNum(), accountId.getAccountNum(), exp_time_seconds, exp_time_nanos, auto_renew_period, admin_key, key, fk_proxy_account_id);
	}

	private long deleteEntity(long shard, long realm, long num) throws SQLException {
	    
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
        PreparedStatement deleteEntity = this.connect.prepareStatement(sqlDelete);

	    deleteEntity.setLong(1, shard);
        deleteEntity.setLong(2, realm);
        deleteEntity.setLong(3, num);

        deleteEntity.execute();
        
        
        
        ResultSet newId = deleteEntity.getResultSet();
        if (newId.next()) {
            entityId = newId.getLong(1);
            newId.close();
            deleteEntity.close();
        } else {
        	// expected entity not found
            deleteEntity.close();
        	throw new IllegalStateException("Expected entity not found, shard " + shard + ", realm " + realm + ", num " + num);
        }
        
        return entityId;
	    
	}

	public long deleteEntity(FileID fileId) throws SQLException {
		return deleteEntity(fileId.getShardNum(),fileId.getRealmNum(), fileId.getFileNum());
	}
	public long deleteEntity(ContractID contractId) throws SQLException {
		return deleteEntity(contractId.getShardNum(),contractId.getRealmNum(), contractId.getContractNum());
	}
	public long deleteEntity(AccountID accountId) throws SQLException {
		return deleteEntity(accountId.getShardNum(),accountId.getRealmNum(), accountId.getAccountNum());
	}
	
	private long unDeleteEntity(long shard, long realm, long num) throws SQLException {
	    
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
        PreparedStatement deleteEntity = this.connect.prepareStatement(sqlDelete);

	    deleteEntity.setLong(1, shard);
        deleteEntity.setLong(2, realm);
        deleteEntity.setLong(3, num);

        deleteEntity.execute();
        
        ResultSet newId = deleteEntity.getResultSet();
        if (newId.next()) {
            entityId = newId.getLong(1);
            newId.close();
            deleteEntity.close();
        } else {
        	// expected entity not found
            deleteEntity.close();
        	throw new IllegalStateException("Expected entity not found, shard " + shard + ", realm " + realm + ", num " + num);
        }
        
        return entityId;
	    
	}

	public long unDeleteEntity(FileID fileId) throws SQLException {
		return unDeleteEntity(fileId.getShardNum(),fileId.getRealmNum(), fileId.getFileNum());
	}
	public long unDeleteEntity(ContractID contractId) throws SQLException {
		return unDeleteEntity(contractId.getShardNum(),contractId.getRealmNum(), contractId.getContractNum());
	}
	public long unDeleteEntity(AccountID accountId) throws SQLException {
		return unDeleteEntity(accountId.getShardNum(),accountId.getRealmNum(), accountId.getAccountNum());
	}

	private long createEntity(long shard, long realm, long num, long exp_time_seconds, long exp_time_nanos, long auto_renew_period, byte[] admin_key, byte[] key, long fk_proxy_account_id, int fk_entity_type) throws SQLException {
	    long entityId = 0;
	    
	    if (shard + realm + num == 0 ) {
	        return 0;
	    }
	    
	    // inserts or returns an existing entity
        PreparedStatement insertEntity = this.connect.prepareStatement(
                "INSERT INTO t_entities (entity_shard, entity_realm, entity_num, fk_entity_type_id, exp_time_seconds, exp_time_nanos, auto_renew_period, admin_key, key, fk_prox_acc_id)"
        		+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                + " ON CONFLICT (entity_shard, entity_realm, entity_num) "
                + " DO UPDATE"
                + " SET exp_time_seconds = EXCLUDED.exp_time_seconds"
                + ", exp_time_nanos = EXCLUDED.exp_time_nanos"
                + ", auto_renew_period = EXCLUDED.auto_renew_period"
                + ", admin_key = EXCLUDED.admin_key"
                + ", key = EXCLUDED.key"
                + ", fk_prox_acc_id = EXCLUDED.fk_prox_acc_id"
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
    	if (fk_proxy_account_id == 0) {
    		insertEntity.setObject(F_ENTITIES.FK_PROXY_ACCOUNT_ID.ordinal(), null);
    	} else {
    		insertEntity.setLong(F_ENTITIES.FK_PROXY_ACCOUNT_ID.ordinal(), fk_proxy_account_id);
    	}
    	insertEntity.setInt(F_ENTITIES.FK_ENTITY_TYPE_ID.ordinal(), fk_entity_type);
        insertEntity.execute();
        ResultSet newId = insertEntity.getResultSet();
        if (newId.next()) {
            entityId = newId.getLong(1);
            newId.close();
            insertEntity.close();
        } else {
        	// expected entity not found
            insertEntity.close();
        	throw new IllegalStateException("Expected entity not found, shard " + shard + ", realm " + realm + ", num " + num);
        }
        
        return entityId;
	    
	}

	public long createEntity(FileID fileId, long exp_time_seconds, long exp_time_nanos, long auto_renew_period, byte[] admin_key, byte[] key, long fk_proxy_account_id) throws SQLException {
		return createEntity(fileId.getShardNum(),fileId.getRealmNum(), fileId.getFileNum(), exp_time_seconds, exp_time_nanos, auto_renew_period, admin_key, key, fk_proxy_account_id, FK_FILE);
	}
	public long createEntity(ContractID contractId, long exp_time_seconds, long exp_time_nanos, long auto_renew_period, byte[] admin_key, byte[] key, long fk_proxy_account_id) throws SQLException {
		return createEntity(contractId.getShardNum(),contractId.getRealmNum(), contractId.getContractNum(), exp_time_seconds, exp_time_nanos, auto_renew_period, admin_key, key, fk_proxy_account_id, FK_CONTRACT);
	}
	public long createEntity(AccountID accountId, long exp_time_seconds, long exp_time_nanos, long auto_renew_period, byte[] admin_key, byte[] key, long fk_proxy_account_id) throws SQLException {
		return createEntity(accountId.getShardNum(),accountId.getRealmNum(), accountId.getAccountNum(), exp_time_seconds, exp_time_nanos, auto_renew_period, admin_key, key, fk_proxy_account_id, FK_ACCOUNT);
	}
	private long createOrGetEntity(long shard, long realm, long num, int fk_entity_type) throws SQLException {
	    long entityId = 0;
	    
	    if (shard + realm + num == 0 ) {
	        return 0;
	    }
	    
	    // inserts or returns an existing entity
        PreparedStatement insertEntity = this.connect.prepareStatement(
                "INSERT INTO t_entities (entity_shard, entity_realm, entity_num, fk_entity_type_id)"
        		+ " VALUES (?, ?, ?, ?)"
                + " ON CONFLICT (entity_shard, entity_realm, entity_num) "
                + " DO UPDATE"
                + " SET entity_shard = EXCLUDED.entity_shard"
                + " RETURNING id");
        
        insertEntity.setLong(F_ENTITIES.SHARD.ordinal(), shard);
        insertEntity.setLong(F_ENTITIES.REALM.ordinal(), realm);
        insertEntity.setLong(F_ENTITIES.NUM.ordinal(), num);
        insertEntity.setInt(F_ENTITIES.FK_ENTITY_TYPE_ID.ordinal(), fk_entity_type);

        insertEntity.execute();
        ResultSet newId = insertEntity.getResultSet();
        newId.next();
        entityId = newId.getLong(1);
        newId.close();
        insertEntity.close();
        
        return entityId;
	}

	public long createOrGetEntity(FileID fileId) throws SQLException {
        return createOrGetEntity(fileId.getShardNum(), fileId.getRealmNum(), fileId.getFileNum(), FK_FILE);
    }
    public long createOrGetEntity(ContractID contractId) throws SQLException {
        return createOrGetEntity(contractId.getShardNum(), contractId.getRealmNum(), contractId.getContractNum(), FK_CONTRACT);
    }
    public long createOrGetEntity(AccountID accountId) throws SQLException {
        return createOrGetEntity(accountId.getShardNum(), accountId.getRealmNum(), accountId.getAccountNum(), FK_ACCOUNT);
    }
	
}
