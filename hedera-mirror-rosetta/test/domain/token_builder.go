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

package domain

import (
	"fmt"

	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
)

type TokenBuilder struct {
	dbClient interfaces.DbClient
	token    domain.Token
}

func (b *TokenBuilder) CreatedTimestamp(createdTimestamp int64) *TokenBuilder {
	b.token.CreatedTimestamp = createdTimestamp
	b.token.ModifiedTimestamp = createdTimestamp
	return b
}

func (b *TokenBuilder) Decimals(decimals int64) *TokenBuilder {
	b.token.Decimals = decimals
	return b
}

func (b *TokenBuilder) InitialSupply(initialSupply int64) *TokenBuilder {
	b.token.InitialSupply = initialSupply
	return b
}

func (b *TokenBuilder) ModifiedTimestamp(modifiedTimestamp int64) *TokenBuilder {
	b.token.ModifiedTimestamp = modifiedTimestamp
	return b
}

func (b *TokenBuilder) Type(tokenType string) *TokenBuilder {
	b.token.Type = tokenType
	return b
}

func (b *TokenBuilder) Persist() domain.Token {
	token := b.token
	b.dbClient.GetDb().Create(&token)
	return b.token
}

func NewTokenBuilder(dbClient interfaces.DbClient, tokenId, createdTimestamp, treasury int64) *TokenBuilder {
	token := domain.Token{
		CreatedTimestamp:  createdTimestamp,
		ModifiedTimestamp: createdTimestamp,
		Name:              fmt.Sprintf("%d_name", tokenId),
		SupplyType:        domain.TokenSupplyTypeInfinite,
		Symbol:            fmt.Sprintf("%d_symbol", tokenId),
		TokenId:           domain.MustDecodeEntityId(tokenId),
		TreasuryAccountId: domain.MustDecodeEntityId(treasury),
		Type:              domain.TokenTypeFungibleCommon,
	}
	return &TokenBuilder{
		dbClient: dbClient,
		token:    token,
	}
}
