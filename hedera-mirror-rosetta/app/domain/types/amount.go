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
	"strconv"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	entityid "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/services/encoding"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
)

type Amount interface {
	ToRosetta() *rTypes.Amount
}

type HbarAmount struct {
	Value int64
}

// ToRosetta returns Rosetta type Amount with hbar currency
func (h *HbarAmount) ToRosetta() *rTypes.Amount {
	return &rTypes.Amount{
		Value:    strconv.FormatInt(h.Value, 10),
		Currency: config.CurrencyHbar,
	}
}

// TokenAmount holds token amount unmarshalled from aggregated json string built by db query
type TokenAmount struct {
	Decimals int64             `json:"decimals"`
	TokenId  entityid.EntityId `json:"token_id"`
	Value    int64             `json:"value"`
}

// ToRosetta returns Rosetta type Amount with the token's currency
func (t *TokenAmount) ToRosetta() *rTypes.Amount {
	return &rTypes.Amount{
		Value: strconv.FormatInt(t.Value, 10),
		Currency: &rTypes.Currency{
			Symbol:   t.TokenId.String(),
			Decimals: int32(t.Decimals),
		},
	}
}
