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

import "github.com/jackc/pgtype"

const (
	tokenAccountTableName        = "token_account"
	tokenAccountHistoryTableName = "token_account_history"
)

type TokenAccount struct {
	AccountId            EntityId `gorm:"primaryKey"`
	Associated           bool
	AutomaticAssociation bool
	CreatedTimestamp     int64
	FreezeStatus         int16
	KycStatus            int16
	TimestampRange       pgtype.Int8range
	TokenId              EntityId `gorm:"primaryKey"`
}

func (TokenAccount) TableName() string {
	return tokenAccountTableName
}

func (TokenAccount) HistoryTableName() string {
	return tokenAccountHistoryTableName
}
