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
import com.hedera.mirror.repository.ApplicationStatusRepository;
import com.hedera.mirror.repository.TransactionsRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

@Log4j2
public class DBHelper {

	private static TransactionsRepository transactionsRepository;
	
    private static long countTableRows(String table_name) throws Exception {
        Connection connect = null;
        long count = 0;
        try {
        	connect = DatabaseUtilities.openDatabase(connect);
            ResultSet resultSet = connect.createStatement().executeQuery("SELECT COUNT(*) AS row_count from " + table_name);
            while (resultSet.next()) {
            	count = resultSet.getLong("row_count");
            }       
            resultSet.close();      
            connect.close();
            return count;
        } catch (Exception e) {
        	if (connect != null) {
        		connect.close();
        	}
            throw new RuntimeException(e);
        }
    }
    public static long countRecordFiles() throws Exception {
    	return countTableRows("t_record_files");
    }
    public static long countTransactions() throws Exception {
    	return countTableRows("t_transactions");
    }
    public static long countCryptoTransferLists() throws Exception {
    	return countTableRows("t_cryptotransferlists");
    }
    public static long countLiveHashes() throws Exception {
    	return countTableRows("t_livehashes");
    }
    public static long countContractResult() throws Exception {
    	return countTableRows("t_contract_result");
    }
    public static long countFileData() throws Exception {
    	return countTableRows("t_file_data");
    }
    public static long countEntities() throws Exception {
    	return countTableRows("t_entities");
    }
    public static void deleteDatabaseData() throws SQLException {
        Connection connect = null;
        try {
        	connect = DatabaseUtilities.openDatabase(connect);

        	connect.createStatement().execute("DELETE FROM t_cryptotransferlists");
        	connect.createStatement().execute("DELETE FROM t_file_data");
        	connect.createStatement().execute("DELETE FROM t_contract_result");
        	connect.createStatement().execute("DELETE FROM t_livehashes");
        	connect.createStatement().execute("DELETE FROM t_transactions");
        	connect.createStatement().execute("DELETE FROM t_record_files");
        	connect.createStatement().execute("DELETE FROM t_entities");
        	connect.createStatement().execute("DELETE FROM t_events");
        	connect.createStatement().execute("DELETE FROM account_balance_sets");
        	connect.createStatement().execute("DELETE FROM account_balances");
        	connect.createStatement().execute("UPDATE t_application_status SET status_value = ''");

        } catch (SQLException e) {
        	if (connect != null) {
        		connect.close();
        	}
            throw new RuntimeException(e);
        }
        if (connect != null) {
            connect.close();
        }
    }
    public static DBTransaction checkTransaction(long validStartNs, long nodeAccountNum, String memo, String transactionType, String result
    		, long payerAccountId, long chargedTxFee, long initialBalance, long cudEntity, String FileName, long consensusNs) throws Exception {
    	//TODO: Add valid duration for transaction
    	DBTransaction transaction = new DBTransaction();
    	DBEntity entity = new DBEntity();
    	transaction.getTransactionDetails(validStartNs);
    	if (nodeAccountNum != 0) {
	    	entity.getEntityDetails(transaction.nodeAccountId);
	    	assertEquals(nodeAccountNum, entity.entityNum);
    	}
    	assertEquals(memo,transaction.memo);
    	assertEquals(transactionType, transaction.transactionType);
    	assertEquals(result, transaction.result);
    	if (payerAccountId != 0) {
    		entity.getEntityDetails(transaction.payerAccountId);
    		assertEquals(payerAccountId, entity.entityNum);
    	}
    	assertEquals(chargedTxFee, transaction.chargedTxFee);
    	assertEquals(initialBalance, transaction.initialBalance);
    	if (cudEntity != 0) {
    		entity.getEntityDetails(transaction.cudEntity);
    		assertEquals(cudEntity, entity.entityNum);
    	}
    	File file = new File(transaction.fileName);
    	assertEquals(FileName, file.getName());
    	assertEquals(consensusNs, transaction.consensusNs);
    	
    	return transaction;
    }
}
