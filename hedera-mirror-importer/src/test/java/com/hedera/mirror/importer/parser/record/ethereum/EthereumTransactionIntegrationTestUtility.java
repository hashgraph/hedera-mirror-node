/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.parser.record.ethereum;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.commons.io.FileUtils;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.util.ResourceUtils;

@UtilityClass
public class EthereumTransactionIntegrationTestUtility {

    // The transactions and the file data are extracted from testnet. The data tests the following scenarios
    // - no call data offloading for legacy, type 1, and type 2
    // - call data offloaded for legacy and type 2
    // - call data inline with call data id in transaction body for legacy
    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private static final String ethereumTransactionJson = readEthereumTransactionJson();

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private static final String fileDataSql = readFileDataSql();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @SneakyThrows
    public static List<EthereumTransaction> loadEthereumTransactions() {
        return objectMapper.readValue(getEthereumTransactionJson(), new TypeReference<>() {});
    }

    public static void populateFileData(JdbcOperations jdbcOperations) {
        jdbcOperations.update(getFileDataSql());
    }

    @SneakyThrows
    private static String readEthereumTransactionJson() {
        var file = ResourceUtils.getFile("classpath:data/ethereumTransaction/ethereum_transaction.json");
        return FileUtils.readFileToString(file, "UTF-8");
    }

    @SneakyThrows
    private static String readFileDataSql() {
        var file = ResourceUtils.getFile("classpath:data/ethereumTransaction/file_data.sql");
        return FileUtils.readFileToString(file, "UTF-8");
    }
}
