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

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/services/encoding"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/assert"
)

var token = &Token{
	TokenId:  entityid.EntityId{EntityNum: 123, EncodedId: 123},
	Decimals: 10,
	Name:     "teebar",
	Symbol:   "foobar",
}

func TestTokenToHederaTokenId(t *testing.T) {
	// given
	expected := &hedera.TokenID{Token: 123}

	// when
	actual := token.ToHederaTokenId()

	// then
	assert.Equal(t, expected, actual)
}

func TestTokenToRosettaCurrency(t *testing.T) {
	// given
	expected := &rTypes.Currency{
		Symbol:   "0.0.123",
		Decimals: 10,
	}

	// when
	actual := token.ToRosettaCurrency()

	// then
	assert.Equal(t, expected, actual)
}
