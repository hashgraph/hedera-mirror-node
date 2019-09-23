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
const config = {
    limits: {
        RESPONSE_ROWS: 1000,
        MAX_BIGINT: 9223372036854775807
    },

    // Time to Live for cache entries for each type of API
    ttls: {
        transactions: 10,
        balances: 60,
        accounts: 60,
        events: 10
    },

    // Refresh times for each type of files (in seconds)
    fileUpdateRefreshTimes: {
        records: 2 * 60, // Record files are updated this often
        balances: 15 * 60, // Balance files are updated this often
        events: 5 * 60 // Event files are updated this often
    },

    resultUpdateTimeout: 60, // Raise error if no results in this much time
}

module.exports = config;