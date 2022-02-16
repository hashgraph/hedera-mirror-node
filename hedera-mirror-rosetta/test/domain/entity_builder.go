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
	"github.com/jackc/pgtype"
	"gorm.io/gorm/clause"
)

type EntityBuilder struct {
	dbClient interfaces.DbClient
	entity   domain.Entity
}

func (b *EntityBuilder) Deleted(deleted bool) *EntityBuilder {
	b.entity.Deleted = &deleted
	return b
}

func (b *EntityBuilder) ModifiedAfter(delta int64) *EntityBuilder {
	b.entity.TimestampRange = getTimestampRangeWithLower(*b.entity.CreatedTimestamp + delta)
	return b
}

func (b *EntityBuilder) ModifiedTimestamp(timestamp int64) *EntityBuilder {
	b.entity.TimestampRange = getTimestampRangeWithLower(timestamp)
	return b
}

func (b *EntityBuilder) Persist() domain.Entity {
	b.dbClient.GetDb().Clauses(clause.OnConflict{
		Columns:   []clause.Column{{Name: "id"}},
		DoUpdates: clause.AssignmentColumns([]string{"deleted", "timestamp_range"}),
	}).Create(&b.entity)
	return b.entity
}

func NewEntityBuilder(dbClient interfaces.DbClient, id, timestamp int64, entityType string) *EntityBuilder {
	entityId := domain.MustDecodeEntityId(id)
	entity := domain.Entity{
		CreatedTimestamp: &timestamp,
		Id:               entityId,
		Num:              entityId.EntityNum,
		Realm:            entityId.RealmNum,
		Shard:            entityId.ShardNum,
		TimestampRange:   getTimestampRangeWithLower(timestamp),
		Type:             entityType,
	}
	return &EntityBuilder{dbClient: dbClient, entity: entity}
}

func NewEntityBuilderFromToken(dbClient interfaces.DbClient, token domain.Token) *EntityBuilder {
	entity := domain.Entity{
		CreatedTimestamp: &token.CreatedTimestamp,
		Id:               token.TokenId,
		Num:              token.TokenId.EntityNum,
		Realm:            token.TokenId.RealmNum,
		Shard:            token.TokenId.ShardNum,
		TimestampRange:   getTimestampRangeWithLower(token.CreatedTimestamp),
		Type:             domain.EntityTypeToken,
	}
	return &EntityBuilder{dbClient: dbClient, entity: entity}
}

func getTimestampRangeWithLower(lower int64) pgtype.Int8range {
	return pgtype.Int8range{
		Lower:     pgtype.Int8{Int: lower, Status: pgtype.Present},
		Upper:     pgtype.Int8{Status: pgtype.Null},
		LowerType: pgtype.Inclusive,
		UpperType: pgtype.Unbounded,
		Status:    pgtype.Present,
	}
}
