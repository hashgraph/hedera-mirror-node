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

	entityid "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/services/encoding"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/stretchr/testify/assert"
)

const (
	tokenName   = "foobar name"
	tokenSymbol = "foobar symbol"
)

func TestTokenTableName(t *testing.T) {
	assert.Equal(t, "token", Token{}.TableName())
}

func TestTokenToDomainToken(t *testing.T) {
	var tests = []struct {
		name        string
		token       Token
		expectError bool
		expected    *types.Token
	}{
		{
			name: "Success",
			token: Token{
				TokenID:  1001,
				Decimals: 10,
				Name:     tokenName,
				Symbol:   tokenSymbol,
			},
			expected: &types.Token{
				TokenId:  entityid.EntityId{EntityNum: 1001, EncodedId: 1001},
				Decimals: 10,
				Name:     tokenName,
				Symbol:   tokenSymbol,
			},
		},
		{
			name: "InvalidTokenId",
			token: Token{
				TokenID:  -1,
				Decimals: 10,
				Name:     tokenName,
				Symbol:   tokenSymbol,
			},
			expectError: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			actual, err := tt.token.ToDomainToken()

			if !tt.expectError {
				assert.Nil(t, err)
				assert.Equal(t, tt.expected, actual)
			} else {
				assert.NotNil(t, err)
				assert.Nil(t, actual)
			}
		})
	}
}
