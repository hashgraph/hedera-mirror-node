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

import (
	"database/sql"

	"github.com/jackc/pgtype"
)

const (
	fixedOffsetTimestampRangeSqlArgName = "fixed_offset_timestamp_range"
	genesisTimestampQuery               = `select consensus_timestamp + time_offset + fixed_offset.value as timestamp
                             from account_balance_file,
                               lateral (
                                 select
                                   case when consensus_timestamp <@ @fixed_offset_timestamp_range ::int8range then 53
                                        else 0
                                   end value
                               ) fixed_offset
                             order by consensus_timestamp
                             limit 1`
	genesisTimestampCte = " genesis as (" + genesisTimestampQuery + ") "
	mainnet             = "mainnet"
	testnet             = "testnet"
)

var nullTimestampRangeSqlNamedArg = sql.Named(fixedOffsetTimestampRangeSqlArgName, pgtype.Int8range{Status: pgtype.Null})
var accountBalanceFileFixedOffsetTimestampRanges = map[string]pgtype.Int8range{
	// the lower is the first 0.27 account balance file, and the upper is the last 0.29 account balance file
	mainnet: getInclusiveInt8Range(1658420100626004000, 1666368000880378770),
	testnet: getInclusiveInt8Range(1656693000269913000, 1665072000124462000),
}

func getAccountBalanceFileFixedOffsetTimestampRangeSqlNamedArg(network string) sql.NamedArg {
	if timestampRange, ok := accountBalanceFileFixedOffsetTimestampRanges[network]; ok {
		return sql.Named(fixedOffsetTimestampRangeSqlArgName, timestampRange)
	}

	return nullTimestampRangeSqlNamedArg
}

func getInclusiveInt8Range(lower, upper int64) pgtype.Int8range {
	return pgtype.Int8range{
		Lower:     pgtype.Int8{Int: lower, Status: pgtype.Present},
		Upper:     pgtype.Int8{Int: upper, Status: pgtype.Present},
		LowerType: pgtype.Inclusive,
		UpperType: pgtype.Inclusive,
		Status:    pgtype.Present,
	}
}
