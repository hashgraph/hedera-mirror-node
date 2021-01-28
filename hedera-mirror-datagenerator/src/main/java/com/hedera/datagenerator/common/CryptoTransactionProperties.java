package com.hedera.datagenerator.common;
/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import javax.inject.Named;
import lombok.Data;

import com.hedera.datagenerator.sampling.NumberDistributionConfig;

/**
 * Fields' generators and properties used by CryptoTransactionGenerator.
 */
@Data
@Named
public class CryptoTransactionProperties {

    private final NumberDistributionConfig numTransferLists = new NumberDistributionConfig();

    /**
     * Relative frequency of crypto type transactions among all generated transactions.
     */
    private int frequency;

    /**
     * Relative frequency of CRYPTOCREATEACCOUNT transactions
     */
    private int createsFrequency;

    /**
     * Relative frequency of CRYPTOTRANSFER transactions
     */
    private int transfersFrequency;

    /**
     * Relative frequency of CRYPTOUPDATEACCOUNT transactions
     */
    private int updatesFrequency;

    /**
     * Relative frequency of CRYPTODELETE transactions
     */
    private int deletesFrequency;

    /**
     * When generating transactions, first 'numSeedAccounts' number of transactions will be of type CRYPTOCREATEACCOUNT
     * only. This is to seed the system with some accounts so crypto transfer lists can sample receiver accounts ids.
     */
    private int numSeedAccounts;

    void initDistributions() {
        numTransferLists.initDistribution();
    }
}
