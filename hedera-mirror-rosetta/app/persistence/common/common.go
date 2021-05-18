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

package common

const (
	tableNameCryptoTransfer = "crypto_transfer"
	tableNameNonFeeTransfer = "non_fee_transfer"
)

type Transfer interface {
	GetAmount() int64
	GetConsensusTimestamp() int64
	GetEntityID() int64
}

type CryptoTransfer struct {
	Amount             int64 `gorm:"type:bigint"`
	ConsensusTimestamp int64 `gorm:"type:bigint"`
	EntityID           int64 `gorm:"type:bigint"`
}

// TableName - Set table name of the CryptoTransfer to be `crypto_transfer`
func (CryptoTransfer) TableName() string {
	return tableNameCryptoTransfer
}

// GetAmount - Get the amount of the crypto transfer
func (c CryptoTransfer) GetAmount() int64 {
	return c.Amount
}

// GetConsensusTimestamp - Get the consensus timestamp of the crypto transfer
func (c CryptoTransfer) GetConsensusTimestamp() int64 {
	return c.ConsensusTimestamp
}

// GetEntityID - Get the entity ID of the crypto transfer
func (c CryptoTransfer) GetEntityID() int64 {
	return c.EntityID
}

type NonFeeTransfer struct {
	Amount             int64 `gorm:"type:bigint"`
	ConsensusTimestamp int64 `gorm:"type:bigint"`
	EntityID           int64 `gorm:"type:bigint"`
}

// TableName - Set table name of the NonFeeTransfer to be `account_balance`
func (NonFeeTransfer) TableName() string {
	return tableNameNonFeeTransfer
}

// GetAmount - Get the amount of the non-fee transfer
func (n NonFeeTransfer) GetAmount() int64 {
	return n.Amount
}

// GetConsensusTimestamp - Get the consensus timestamp of the non-fee transfer
func (n NonFeeTransfer) GetConsensusTimestamp() int64 {
	return n.ConsensusTimestamp
}

// GetEntityID - Get the entity ID of the non-fee transfer
func (n NonFeeTransfer) GetEntityID() int64 {
	return n.EntityID
}
