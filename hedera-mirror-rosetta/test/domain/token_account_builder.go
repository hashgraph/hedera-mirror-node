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

package domain

import (
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
)

type TokenAccountBuilder struct {
	dbClient     interfaces.DbClient
	tokenAccount domain.TokenAccount
}

func (b *TokenAccountBuilder) CreatedTimestamp(createdTimestamp int64) *TokenAccountBuilder {
	b.tokenAccount.CreatedTimestamp = createdTimestamp
	b.tokenAccount.ModifiedTimestamp = createdTimestamp
	return b
}

func (b *TokenAccountBuilder) Associated(associated bool, modifiedTimestamp int64) *TokenAccountBuilder {
	b.tokenAccount.Associated = associated
	b.tokenAccount.ModifiedTimestamp = modifiedTimestamp
	return b
}

func (b *TokenAccountBuilder) Persist() domain.TokenAccount {
	b.dbClient.GetDb().Create(&b.tokenAccount)
	return b.tokenAccount
}

func NewTokenAccountBuilder(dbClient interfaces.DbClient, accountId, tokenId, timestamp int64) *TokenAccountBuilder {
	tokenAccount := domain.TokenAccount{
		AccountId:         domain.MustDecodeEntityId(accountId),
		Associated:        true,
		CreatedTimestamp:  timestamp,
		ModifiedTimestamp: timestamp,
		TokenId:           domain.MustDecodeEntityId(tokenId),
	}
	return &TokenAccountBuilder{dbClient: dbClient, tokenAccount: tokenAccount}
}

func NewTokenAccountBuilderFromExisting(
	dbClient interfaces.DbClient,
	tokenAccount domain.TokenAccount,
) *TokenAccountBuilder {
	return &TokenAccountBuilder{dbClient: dbClient, tokenAccount: tokenAccount}
}
