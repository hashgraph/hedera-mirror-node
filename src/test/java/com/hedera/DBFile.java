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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Log4j2
public class DBFile {

    public static String getFileData(long consensusNs) throws Exception {
    	String fileContents = "";
    	
        Connection connect = null;
        try {
        	connect = DatabaseUtilities.openDatabase(connect);
        	PreparedStatement query = connect.prepareStatement("SELECT file_data "
        			+ " FROM t_file_data"
        			+ " WHERE consensus_timestamp = ?");
        	
        	query.setLong(1, consensusNs);
            ResultSet resultSet = query.executeQuery();
            while (resultSet.next()) {
            	if (null != resultSet.getString("file_data")) {
            		fileContents = new String(resultSet.getBytes("file_data"));
            	}
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

    	
    	return fileContents;
    }
	
}
