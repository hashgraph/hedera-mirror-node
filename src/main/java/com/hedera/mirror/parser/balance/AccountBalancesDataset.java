package com.hedera.mirror.parser.balance;

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

import java.time.Instant;
import java.util.stream.Stream;

/**
 * AccountBalanceDatasets initially process the header in the dataset and can return the consensus timestamp from that
 * header, and then stream the CSV lines (post-header) via getRecordStream().
 *
 * There are 2 supported file formats (both name and contents).
 *
 * File format 1 (unsupported at the moment, but in use pre-OA) named: 2019-06-28-19-55.csv
 *   year,month,day,hour,minute,second
 *   2019,JUNE,28,21,39,17
 *   shard,realm,number,balance
 *   0,0,1,0
 *
 * File format 1 is unsupported because we can't accurately determine the consensus timestamp.
 *
 * File format 2 named: 2019-08-22T23_30_00.543536062Z_Balances.csv
 *   TimeStamp:2019-08-22T23:30:00.543536062Z
 *   shardNum,realmNum,accountNum,balance
 *   0,0,1,0
 */
public interface AccountBalancesDataset extends AutoCloseable {
    Instant getConsensusTimestamp();

    Stream<NumberedLine> getRecordStream();
}
