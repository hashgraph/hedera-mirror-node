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
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	entityid "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/services/encoding"
	"github.com/hashgraph/hedera-sdk-go/v2"
)

// Token is domain level struct used to represent Token conceptual mapping in Hedera
type Token struct {
	TokenId  entityid.EntityId
	Decimals uint32
	Name     string
	Symbol   string
}

func (t Token) ToHederaTokenId() *hedera.TokenID {
	return &hedera.TokenID{
		Shard: uint64(t.TokenId.ShardNum),
		Realm: uint64(t.TokenId.RealmNum),
		Token: uint64(t.TokenId.EntityNum),
	}
}

func (t Token) ToRosettaCurrency() *rTypes.Currency {
	return &rTypes.Currency{
		Symbol:   t.TokenId.String(),
		Decimals: int32(t.Decimals),
	}
}
