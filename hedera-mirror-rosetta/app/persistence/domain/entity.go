/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
 *
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
 */

package domain

import "github.com/jackc/pgtype"

const (
	EntityTypeAccount  = "ACCOUNT"
	EntityTypeFile     = "FILE"
	EntityTypeSchedule = "SCHEDULE"
	EntityTypeToken    = "TOKEN"
	EntityTypeTopic    = "TOPIC"

	entityTableName        = "entity"
	entityHistoryTableName = "entity_history"
)

type Entity struct {
	Alias                         []byte
	AutoRenewAccountId            *EntityId
	AutoRenewPeriod               *int64
	CreatedTimestamp              *int64
	Deleted                       *bool
	ExpirationTimestamp           *int64
	Id                            EntityId
	Key                           []byte
	MaxAutomaticTokenAssociations *int32
	Memo                          string
	Num                           int64
	PublicKey                     *string
	ProxyAccountId                *EntityId
	Realm                         int64
	ReceiverSigRequired           *bool
	Shard                         int64
	TimestampRange                pgtype.Int8range
	Type                          string
}

func (e Entity) GetModifiedTimestamp() int64 {
	return e.TimestampRange.Lower.Int
}

func (Entity) TableName() string {
	return entityTableName
}

func (Entity) HistoryTableName() string {
	return entityHistoryTableName
}
