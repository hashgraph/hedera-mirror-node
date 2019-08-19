package com.hedera.recordFileLogger;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.HashMap;

import com.hedera.utilities.Utility;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;

public class Entities {
	private static int FK_ACCOUNT = 0;
	private static int FK_CONTRACT = 0;
	private static int FK_FILE = 0;
	HashMap<String, Long> entities = new HashMap<String, Long>();
	
    enum F_ENTITIES {
    	ZERO
    	,SHARD
    	,REALM
    	,NUM
    	,FK_ENTITY_TYPE_ID
    	,EXP_TIME_SECONDS
    	,EXP_TIME_NANOS
    	,EXP_TIME_NS
    	,AUTO_RENEW
    	,ADMIN_KEY
    	,KEY
    	,FK_PROXY_ACCOUNT_ID
    }

	private static Connection connect = null;
	
	public Entities(Connection connect) throws SQLException {
		Entities.connect = connect;
		if (Entities.connect != null) {
	        if (FK_ACCOUNT + FK_CONTRACT + FK_FILE == 0) {
	            ResultSet resultSet;
				resultSet = Entities.connect.createStatement().executeQuery("SELECT id, name FROM t_entity_types ORDER BY id");
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
	    	sqlUpdate += ",exp_time_ns = ?";
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
        PreparedStatement updateEntity = Entities.connect.prepareStatement(sqlUpdate);

	    if ((exp_time_seconds != 0) || (exp_time_nanos != 0)) {
        	updateEntity.setLong(1, exp_time_seconds);        	
        	updateEntity.setLong(2, exp_time_nanos);
        	Instant expiryTime = Instant.ofEpochSecond(exp_time_seconds, exp_time_nanos);
        	updateEntity.setLong(3, Utility.convertInstantToNanos(expiryTime));
        	fieldCount = 3;
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
        PreparedStatement deleteEntity = Entities.connect.prepareStatement(sqlDelete);

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
        PreparedStatement deleteEntity = Entities.connect.prepareStatement(sqlDelete);

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

		long entityId = getCachedEntityId(shard, realm, num, fk_entity_type);
		if (entityId != -1) {
			return entityId;
		}
	    
		CallableStatement entityCreate = connect.prepareCall("{? = call f_entity_create ( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? ) }");
		entityCreate.registerOutParameter(1, Types.BIGINT);
		entityCreate.setLong(2, shard);
		entityCreate.setLong(3, realm);
		entityCreate.setLong(4, num);
		entityCreate.setInt(5, fk_entity_type);
		entityCreate.setLong(6, exp_time_seconds);
		entityCreate.setLong(7, exp_time_nanos);

    	Instant expiryTime = Instant.ofEpochSecond(exp_time_seconds, exp_time_nanos);

    	entityCreate.setLong(8, Utility.convertInstantToNanos(expiryTime));
		entityCreate.setLong(9, auto_renew_period);

		if (admin_key == null) {
    		entityCreate.setBytes(10, new byte[0]);
    	} else {
    		entityCreate.setBytes(10, admin_key);
    	}

    	if (key == null) {
    		entityCreate.setBytes(11, new byte[0]);
    	} else {
    		entityCreate.setBytes(11, key);
    	}

		entityCreate.setLong(12, fk_proxy_account_id);

		
		entityCreate.execute();
		entityId = entityCreate.getLong(1);
		entityCreate.close();

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
		
		long entityId = getCachedEntityId(shard, realm, num, fk_entity_type);
		if (entityId != -1) {
			return entityId;
		}

		CallableStatement entityCreate = connect.prepareCall("{? = call f_entity_create ( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? ) }");
		entityCreate.registerOutParameter(1, Types.BIGINT);
		entityCreate.setLong(2, shard);
		entityCreate.setLong(3, realm);
		entityCreate.setLong(4, num);
		entityCreate.setInt(5, fk_entity_type);
		entityCreate.setLong(6, 0);
		entityCreate.setLong(7, 0);
    	entityCreate.setLong(8, 0);
		entityCreate.setLong(9, 0);
		entityCreate.setBytes(10, new byte[0]);
		entityCreate.setBytes(11, new byte[0]);
		entityCreate.setLong(12, 0);
		
		entityCreate.execute();
		entityId = entityCreate.getLong(1);
		entityCreate.close();

        String entity = shard + "-" + realm + "-" + num + "-" + fk_entity_type;
        entities.put(entity, entityId);
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

    private long getCachedEntityId(long shard, long realm, long num, int fk_entity_type) {
        String entity = shard + "-" + realm + "-" + num + "-" + fk_entity_type;
    	
        if (shard + realm + num == 0 ) {
            return 0;
        } else if (entities.containsKey(entity)) {
    		return entities.get(entity);
    	} else {
    		return -1;
    	}
    }
}
