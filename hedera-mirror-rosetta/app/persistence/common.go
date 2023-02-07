/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import "github.com/jackc/pgtype"

const (
	genesisTimestampQuery = `select consensus_timestamp + time_offset as timestamp
                             from account_balance_file
                             order by consensus_timestamp
                             limit 1`
	genesisTimestampCte = " genesis as (" + genesisTimestampQuery + ") "
)

func getInclusiveInt8Range(lower, upper int64) pgtype.Int8range {
	return pgtype.Int8range{
		Lower:     pgtype.Int8{Int: lower, Status: pgtype.Present},
		Upper:     pgtype.Int8{Int: upper, Status: pgtype.Present},
		LowerType: pgtype.Inclusive,
		UpperType: pgtype.Inclusive,
		Status:    pgtype.Present,
	}
}
