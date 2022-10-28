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
	"testing"

	"github.com/jackc/pgtype"
	"github.com/stretchr/testify/assert"
)

func TestGetAccountBalanceFileFixedOffsetTimestampRangeSqlNamedArg(t *testing.T) {
	tests := []struct {
		network  string
		expected sql.NamedArg
	}{
		{
			network: "mainnet",
			expected: sql.Named(fixedOffsetTimestampRangeSqlArgName, pgtype.Int8range{
				Lower:     pgtype.Int8{Int: 1658420100626004000, Status: pgtype.Present},
				Upper:     pgtype.Int8{Int: 1666368000880378770, Status: pgtype.Present},
				LowerType: pgtype.Inclusive,
				UpperType: pgtype.Inclusive,
				Status:    pgtype.Present,
			}),
		},
		{
			network: "testnet",
			expected: sql.Named(fixedOffsetTimestampRangeSqlArgName, pgtype.Int8range{
				Lower:     pgtype.Int8{Int: 1656693000269913000, Status: pgtype.Present},
				Upper:     pgtype.Int8{Int: 1665072000124462000, Status: pgtype.Present},
				LowerType: pgtype.Inclusive,
				UpperType: pgtype.Inclusive,
				Status:    pgtype.Present,
			}),
		},
		{
			network:  "",
			expected: sql.Named(fixedOffsetTimestampRangeSqlArgName, pgtype.Int8range{Status: pgtype.Null}),
		},
		{
			network:  "demo",
			expected: sql.Named(fixedOffsetTimestampRangeSqlArgName, pgtype.Int8range{Status: pgtype.Null}),
		},
	}

	for _, tt := range tests {
		t.Run(tt.network, func(t *testing.T) {
			actual := getAccountBalanceFileFixedOffsetTimestampRangeSqlNamedArg(tt.network)
			assert.Equal(t, tt.expected, actual)
		})
	}
}
