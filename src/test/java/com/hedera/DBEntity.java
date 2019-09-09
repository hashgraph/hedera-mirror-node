package com.hedera;

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

import lombok.extern.log4j.Log4j2;
import com.hedera.databaseUtilities.DatabaseUtilities;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.bouncycastle.util.encoders.Hex;

@Log4j2
public class DBEntity {

	public long entityNum = 0;
	public long entityRealm = 0;
	public long entityShard = 0;
	public String entityType = "";
	public long expiryTimeSeconds = 0;
	public long expiryTimeNanos = 0;
	public long autoRenewPeriod = 0;
	public String key = "";
	public long proxyAccountNum = 0;
	public boolean deleted = false;
	public long expiryTimeNs = 0;
	
    public void getEntityDetails(long entityId) throws Exception {
        Connection connect = null;
        try {
        	connect = DatabaseUtilities.openDatabase(connect);
        	PreparedStatement query = connect.prepareStatement("SELECT e.entity_num, e.entity_realm, e.entity_shard, et.name"
        			+ ", e.exp_time_seconds, e.exp_time_nanos, e.auto_renew_period, e.key, e.fk_prox_acc_id, e.deleted, e.exp_time_ns, e2.entity_num AS prox_acc_num"
        			+ " FROM t_entities e"
        			+ " JOIN t_entity_types et ON et.id = e.fk_entity_type_id"
        			+ " LEFT OUTER JOIN t_entities e2 ON e2.id = e.fk_prox_acc_id"
        			+ " WHERE e.id = ?");
        	
        	query.setLong(1, entityId);
            ResultSet resultSet = query.executeQuery();
            while (resultSet.next()) {
            	entityNum = resultSet.getLong("entity_num");
            	entityRealm = resultSet.getLong("entity_realm");
            	entityShard = resultSet.getLong("entity_shard");
            	entityType = resultSet.getString("name");

            	expiryTimeSeconds = resultSet.getLong("exp_time_seconds");
            	expiryTimeNanos = resultSet.getLong("exp_time_nanos");
            	autoRenewPeriod = resultSet.getLong("auto_renew_period");
            	key = Hex.toHexString(resultSet.getBytes("key"));
            	System.out.println(entityNum + "-" + key);
            	proxyAccountNum = resultSet.getLong("prox_acc_num");
            	deleted = resultSet.getBoolean("deleted");
            	expiryTimeNs = resultSet.getLong("exp_time_ns");
            }       
            resultSet.close();      
        } catch (Exception e) {
        	if (connect != null) {
        		connect.close();
        	}
            throw new RuntimeException(e);
        }
        if (connect != null) {
            connect.close();
        }
    }
}
