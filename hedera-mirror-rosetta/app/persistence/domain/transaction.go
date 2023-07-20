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
	"database/sql/driver"
	"encoding/json"
	"errors"
	"fmt"
)

const (
	TransactionTypeCryptoCreateAccount int16 = 11
	TransactionTypeCryptoTransfer      int16 = 14
	TransactionTypeTokenCreation       int16 = 29
	TransactionTypeTokenDeletion       int16 = 35
	TransactionTypeTokenUpdate         int16 = 36
	TransactionTypeTokenMint           int16 = 37
	TransactionTypeTokenDissociate     int16 = 41

	transactionTableName = "transaction"
)

type ItemizedTransfer struct {
	Amount     int64    `json:"amount"`
	EntityId   EntityId `json:"entity_id"`
	IsApproval bool     `json:"is_approval"`
}

type ItemizedTransferSlice []ItemizedTransfer

func (i *ItemizedTransferSlice) Scan(value interface{}) error {
	bytes, ok := value.([]byte)
	if !ok {
		return errors.New(fmt.Sprint("Failed to unmarshal JSONB value", value))
	}

	result := ItemizedTransferSlice{}
	err := json.Unmarshal(bytes, &result)
	*i = result
	return err
}

func (i ItemizedTransferSlice) Value() (driver.Value, error) {
	if len(i) == 0 {
		return nil, nil
	}

	return json.Marshal(i)
}

type JSONB map[string]interface{}

type Transaction struct {
	ConsensusTimestamp       int64 `gorm:"primaryKey"`
	ChargedTxFee             int64
	EntityId                 *EntityId
	Errata                   *string
	InitialBalance           int64
	ItemizedTransfer         ItemizedTransferSlice `gorm:"type:jsonb"`
	MaxFee                   int64
	Memo                     []byte
	NodeAccountId            *EntityId
	Nonce                    int32
	ParentConsensusTimestamp int64
	PayerAccountId           EntityId
	Result                   int16
	Scheduled                bool
	TransactionBytes         []byte
	TransactionHash          []byte
	Type                     int16
	ValidDurationSeconds     int64
	ValidStartNs             int64
}

func (Transaction) TableName() string {
	return transactionTableName
}

func (itemizedTransfer JSONB) Value() (driver.Value, error) {
	return json.Marshal(itemizedTransfer)
}

func (itemizedTransfer *JSONB) Scan(value interface{}) error {
	data, ok := value.([]byte)
	if !ok {
		return errors.New("type assertion to []byte failed")
	}
	return json.Unmarshal(data, &itemizedTransfer)
}
