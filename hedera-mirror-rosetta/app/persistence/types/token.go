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
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	hErrors "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
)

const tableNameToken = "token"

const (
	Finite   TokenSupplyType = "FINITE"
	Infinite TokenSupplyType = "INFINITE"

	FungibleCommon    TokenType = "FUNGIBLE_COMMON"
	NonFungibleUnique TokenType = "NON_FUNGIBLE_UNIQUE"
)

type TokenSupplyType string

type TokenType string

type Token struct {
	TokenId                  int64 `gorm:"primaryKey"`
	CreatedTimestamp         int64
	Decimals                 int64
	FeeScheduleKey           []byte
	FeeScheduleKeyEd25519Hex string
	FreezeDefault            bool
	FreezeKey                []byte
	FreezeKeyEd25519Hex      string
	InitialSupply            int64
	KycKey                   []byte
	KycKeyEd25519Hex         string
	MaxSupply                int64
	ModifiedTimestamp        int64
	Name                     string
	SupplyKey                []byte
	SupplyKeyEd25519Hex      string
	SupplyType               TokenSupplyType
	Symbol                   string
	TotalSupply              int64
	TreasuryAccountId        int64
	Type                     TokenType
	WipeKey                  []byte
	WipeKeyEd25519Hex        string
}

// TableName returns token table name
func (Token) TableName() string {
	return tableNameToken
}

// ToDomainToken converts db token to domain token
func (t Token) ToDomainToken() (*types.Token, *rTypes.Error) {
	tokenId, err := entityid.Decode(t.TokenId)
	if err != nil {
		return nil, hErrors.ErrInvalidToken
	}

	return &types.Token{
		TokenId:  tokenId,
		Decimals: t.Decimals,
		Name:     t.Name,
		Symbol:   t.Symbol,
	}, nil
}
