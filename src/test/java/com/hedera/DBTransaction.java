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
public class DBTransaction {

	public long nodeAccountId = 0;
	public String memo = "";
	public String transactionType = "";
	public String result = "";
	public long consensusNs = 0;
	public long payerAccountId = 0;
	public long chargedTxFee = 0;
	public long initialBalance = 0;
	public long cudEntity = 0;
	public String fileName = "";
	
    public void getTransactionDetails(long valid_start_ns) throws Exception {
        Connection connect = null;
        try {
        	connect = DatabaseUtilities.openDatabase(connect);
        	PreparedStatement query = connect.prepareStatement("SELECT t.fk_node_acc_id, t.memo, tt.name as transaction_type, tr.result "
        			+ ", t.fk_payer_acc_id, t.charged_tx_fee, t.initial_balance, t.fk_cud_entity_id, trf.name file_name, t.consensus_ns"
        			+ " FROM t_transactions t"
        			+ "    , t_transaction_results tr"
        			+ "    , t_transaction_types tt"
        			+ "    , t_record_files trf"
        			+ " WHERE t.fk_trans_type_id = tt.id"
        			+ " AND t.fk_result_id = tr.id"
        			+ " AND t.fk_rec_file_id = trf.id"
        			+ " AND t.valid_start_ns = ?");
        	
        	query.setLong(1, valid_start_ns);
            ResultSet resultSet = query.executeQuery();
            while (resultSet.next()) {
            	nodeAccountId = resultSet.getLong("fk_node_acc_id");
            	memo = new String(resultSet.getBytes("memo"));
            	transactionType = resultSet.getString("transaction_type");
            	result = resultSet.getString("result");
            	consensusNs = resultSet.getLong("consensus_ns");
            	payerAccountId = resultSet.getLong("fk_payer_acc_id");
            	chargedTxFee = resultSet.getLong("charged_tx_fee");
            	initialBalance = resultSet.getLong("initial_balance");
            	cudEntity = resultSet.getLong("fk_cud_entity_id");
            	fileName = resultSet.getString("file_name");
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
