package com.hedera.faker.common;
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

import javax.annotation.PostConstruct;
import javax.inject.Named;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import com.hedera.faker.sampling.NumberDistributionConfig;

@Data
@EqualsAndHashCode(callSuper = false)
@Named
@ConfigurationProperties("faker.transaction.file")
public class FileTransactionProperties {

    private final NumberDistributionConfig fileDataSize = new NumberDistributionConfig();

    /**
     * Max size of file data
     */
    private int maxFileDataSize;

    /**
     * Relative frequency of FILECREATE transactions
     */
    private int createsFrequency;

    /**
     * Relative frequency of FILEAPPEND transactions
     */
    private int appendsFrequency;

    /**
     * Relative frequency of FILEUPDATE transactions
     */
    private int updatesFrequency;

    /**
     * Relative frequency of FILEDELETE transactions
     */
    private int deletesFrequency;

    /**
     * When generating transactions, first 'numSeedFiles' number of transactions will be of type FILECREATE only. This
     * is to seed the system with some files so that file append/update/delete transactions have valid files to operate
     * on.
     */
    private int numSeedFiles;

    @PostConstruct
    void initDistributions() {
        fileDataSize.initDistribution();
    }
}
