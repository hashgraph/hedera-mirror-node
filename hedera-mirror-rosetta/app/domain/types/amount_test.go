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

	"github.com/coinbase/rosetta-sdk-go/types"
	entityid "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/services/encoding"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	"github.com/stretchr/testify/assert"
)

var (
	hbarAmount        = &HbarAmount{Value: 400}
	hbarRosettaAmount = &types.Amount{
		Value:    "400",
		Currency: config.CurrencyHbar,
	}
	tokenAmount = &TokenAmount{
		TokenId:  entityid.EntityId{EntityNum: 1580, EncodedId: 1580},
		Decimals: 9,
		Value:    6000,
	}
	tokenRosettaAmount = &types.Amount{
		Value: "6000",
		Currency: &types.Currency{
			Symbol:   "0.0.1580",
			Decimals: 9,
		},
	}
)

func TestHbarAmountToRosettaAmount(t *testing.T) {
	// given

	// when:
	actual := hbarAmount.ToRosetta()

	// then:
	assert.Equal(t, hbarRosettaAmount, actual)
}

func TestTokenAmountToRosettaAmount(t *testing.T) {
	// given

	// when:
	actual := tokenAmount.ToRosetta()

	// then:
	assert.Equal(t, tokenRosettaAmount, actual)
}
