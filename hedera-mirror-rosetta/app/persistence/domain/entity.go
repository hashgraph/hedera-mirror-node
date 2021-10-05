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

package domain

const entityTableName = "entity"

type Entity struct {
	AutoRenewAccountId  *EntityId
	AutoRenewPeriod     *int64
	CreatedTimestamp    *int64
	Deleted             *bool
	ExpirationTimestamp *int64
	Id                  int64 `gorm:"primaryKey"`
	Key                 []byte
	Memo                string
	ModifiedTimestamp   *int64
	Num                 int64
	PublicKey           string
	ProxyAccountId      *EntityId
	Realm               int64
	Shard               int64
	SubmitKey           []byte
	Type                int
}

func (Entity) TableName() string {
	return entityTableName
}
