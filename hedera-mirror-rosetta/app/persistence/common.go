/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

package persistence

import "database/sql"

const (
	firstFixedOffsetTimestampSqlArgName = "first_fixed_offset_timestamp"
	genesisTimestampQuery               = `select consensus_timestamp + time_offset + fixed_offset.value as timestamp
                             from account_balance_file,
                               lateral (
                                 select
                                   case when consensus_timestamp >= @first_fixed_offset_timestamp then 53
                                        else 0
                                   end value
                               ) fixed_offset
                             order by consensus_timestamp
                             limit 1`
	genesisTimestampCte = " genesis as (" + genesisTimestampQuery + ") "
	mainnet             = "mainnet"
	testnet             = "testnet"
)

var firstAccountBalanceFileFixedOffsetTimestamps = map[string]int64{
	mainnet: 1658420100626004000,
	testnet: 1656693000269913000,
}

func getFirstAccountBalanceFileFixedOffsetTimestampSqlNamedArg(network string) sql.NamedArg {
	nullInt64 := sql.NullInt64{}
	if timestamp, ok := firstAccountBalanceFileFixedOffsetTimestamps[network]; ok {
		nullInt64.Int64 = timestamp
		nullInt64.Valid = true
	}

	return sql.Named(firstFixedOffsetTimestampSqlArgName, nullInt64)
}
