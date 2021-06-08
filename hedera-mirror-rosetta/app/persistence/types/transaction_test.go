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

package types

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestTransactionTableName(t *testing.T) {
	assert.Equal(t, "transaction", Transaction{}.TableName())
}

func TestTransactionHasTokenOperation(t *testing.T) {
	var tests = []struct {
		name     string
		txType   int
		expected bool
	}{
		{
			name:     "TokenCreation",
			txType:   29,
			expected: true,
		},
		{
			name:     "TokenDeletion",
			txType:   35,
			expected: true,
		},
		{
			name:     "TokenUpdate",
			txType:   36,
			expected: true,
		},
		{
			name:   "TokenAssociate",
			txType: 40,
		},
		{
			name:   "CryptoTransfer",
			txType: 14,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, Transaction{Type: tt.txType}.HasTokenOperation())
		})
	}
}

func TestTransactionGetHashString(t *testing.T) {
	tx := Transaction{TransactionHash: []byte{1, 2, 3, 0xaa, 0xff}}
	assert.Equal(t, "0x010203aaff", tx.GetHashString())
}
