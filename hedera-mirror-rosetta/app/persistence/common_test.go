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

	"github.com/stretchr/testify/assert"
)

func TestGetFirstAccountBalanceFileFixedOffsetTimestampSqlNamedArg(t *testing.T) {
	tests := []struct {
		network  string
		expected sql.NamedArg
	}{
		{
			network:  "mainnet",
			expected: sql.Named(firstFixedOffsetTimestampSqlArgName, sql.NullInt64{Int64: 1658420100626004000, Valid: true}),
		},
		{
			network:  "testnet",
			expected: sql.Named(firstFixedOffsetTimestampSqlArgName, sql.NullInt64{Int64: 1656693000269913000, Valid: true}),
		},
		{
			network:  "",
			expected: sql.Named(firstFixedOffsetTimestampSqlArgName, sql.NullInt64{}),
		},
		{
			network:  "demo",
			expected: sql.Named(firstFixedOffsetTimestampSqlArgName, sql.NullInt64{}),
		},
	}

	for _, tt := range tests {
		t.Run(tt.network, func(t *testing.T) {
			actual := getFirstAccountBalanceFileFixedOffsetTimestampSqlNamedArg(tt.network)
			assert.Equal(t, tt.expected, actual)
		})
	}
}
