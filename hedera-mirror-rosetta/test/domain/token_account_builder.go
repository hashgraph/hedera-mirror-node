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
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/jackc/pgtype"
	"gorm.io/gorm/clause"
)

type TokenAccountBuilder struct {
	dbClient     interfaces.DbClient
	historical   bool
	tokenAccount domain.TokenAccount
}

func (b *TokenAccountBuilder) CreatedTimestamp(createdTimestamp int64) *TokenAccountBuilder {
	b.tokenAccount.CreatedTimestamp = createdTimestamp
	b.tokenAccount.TimestampRange = getTimestampRangeWithLower(createdTimestamp)
	return b
}

func (b *TokenAccountBuilder) Associated(associated bool, modifiedTimestamp int64) *TokenAccountBuilder {
	b.tokenAccount.Associated = associated
	b.tokenAccount.TimestampRange = getTimestampRangeWithLower(modifiedTimestamp)
	return b
}

func (b *TokenAccountBuilder) Historical(historical bool) *TokenAccountBuilder {
	b.historical = historical
	return b
}

func (b *TokenAccountBuilder) TimestampRange(lowerInclusive, upperExclusive int64) *TokenAccountBuilder {
	b.tokenAccount.TimestampRange = pgtype.Int8range{
		Lower:     pgtype.Int8{Int: lowerInclusive, Status: pgtype.Present},
		Upper:     pgtype.Int8{Int: upperExclusive, Status: pgtype.Present},
		LowerType: pgtype.Inclusive,
		UpperType: pgtype.Exclusive,
		Status:    pgtype.Present,
	}
	return b
}

func (b *TokenAccountBuilder) Persist() domain.TokenAccount {
	tableName := b.tokenAccount.TableName()
	if b.historical {
		tableName = b.tokenAccount.HistoryTableName()
	}
	tx := b.dbClient.GetDb().Table(tableName)
	if !b.historical {
		// only token_account table has unique (account_id, token_id)
		tx = tx.Clauses(clause.OnConflict{
			Columns:   []clause.Column{{Name: "account_id"}, {Name: "token_id"}},
			DoUpdates: clause.AssignmentColumns([]string{"associated", "timestamp_range"}),
		})
	}
	tx.Create(&b.tokenAccount)
	return b.tokenAccount
}

func NewTokenAccountBuilder(dbClient interfaces.DbClient, accountId, tokenId, timestamp int64) *TokenAccountBuilder {
	tokenAccount := domain.TokenAccount{
		AccountId:        domain.MustDecodeEntityId(accountId),
		Associated:       true,
		CreatedTimestamp: timestamp,
		TimestampRange:   getTimestampRangeWithLower(timestamp),
		TokenId:          domain.MustDecodeEntityId(tokenId),
	}
	return &TokenAccountBuilder{dbClient: dbClient, tokenAccount: tokenAccount}
}

func NewTokenAccountBuilderFromExisting(
	dbClient interfaces.DbClient,
	tokenAccount domain.TokenAccount,
) *TokenAccountBuilder {
	return &TokenAccountBuilder{dbClient: dbClient, tokenAccount: tokenAccount}
}
