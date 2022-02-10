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

const tableNameToken = "token"

const (
	TokenSupplyTypeUnknown  string = ""
	TokenSupplyTypeFinite   string = "FINITE"
	TokenSupplyTypeInfinite string = "INFINITE"

	TokenTypeUnknown           string = ""
	TokenTypeFungibleCommon    string = "FUNGIBLE_COMMON"     // #nosec
	TokenTypeNonFungibleUnique string = "NON_FUNGIBLE_UNIQUE" // #nosec
)

type Token struct {
	CreatedTimestamp  int64    `json:"created_timestamp"`
	Decimals          int64    `json:"decimals"`
	FeeScheduleKey    []byte   `json:"fee_schedule_key"`
	FreezeDefault     bool     `json:"freeze_default"`
	FreezeKey         []byte   `json:"freeze_key"`
	InitialSupply     int64    `json:"initial_supply"`
	KycKey            []byte   `json:"kyc_key"`
	MaxSupply         int64    `json:"max_supply"`
	ModifiedTimestamp int64    `json:"modified_timestamp"`
	Name              string   `json:"name"`
	SupplyKey         []byte   `json:"supply_key"`
	SupplyType        string   `json:"supply_type"`
	Symbol            string   `json:"symbol"`
	TokenId           EntityId `gorm:"primaryKey" json:"token_id"`
	TotalSupply       int64    `json:"total_supply"`
	TreasuryAccountId EntityId `json:"treasury_account_id"`
	Type              string   `json:"type"`
	WipeKey           []byte   `json:"wipe_key"`
}

// TableName returns token table name
func (Token) TableName() string {
	return tableNameToken
}
