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

@Log4j2
public class DBCryptoTransfers {

    public static long checkExists(long transactionId, long accountNum, long amount) throws Exception {
        Connection connect = null;
        long count = 0;
        try {
        	connect = DatabaseUtilities.openDatabase(connect);
        	PreparedStatement query = connect.prepareStatement("SELECT count(*) AS row_count"
        			+ " FROM t_cryptotransferlists ctl"
        			+ "     ,t_entities e"
        			+ " WHERE ctl.account_id = e.id"
        			+ " AND e.entity_num = ?"
        			+ " AND ctl.amount = ?"
        			+ " AND ctl.fk_trans_id = ?");
        	
        	query.setLong(1, accountNum);
        	query.setLong(2, amount);
        	query.setLong(3, transactionId);
            ResultSet resultSet = query.executeQuery();
            while (resultSet.next()) {
            	count = resultSet.getLong("row_count");
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
        return count;
    }
    public static long checkCount(long transactionId) throws Exception {
        Connection connect = null;
        long count = 0;
        try {
        	connect = DatabaseUtilities.openDatabase(connect);
        	PreparedStatement query = connect.prepareStatement("SELECT count(*) AS row_count"
        			+ " FROM t_cryptotransferlists ctl"
        			+ " WHERE ctl.fk_trans_id = ?");
        	
        	query.setLong(1, transactionId);
            ResultSet resultSet = query.executeQuery();
            while (resultSet.next()) {
            	count = resultSet.getLong("row_count");
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
        return count;
    }
}
