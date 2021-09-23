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

const (
	TransactionTypeCryptoTransfer  int16 = 14
	TransactionTypeTokenCreation   int16 = 29
	TransactionTypeTokenDeletion   int16 = 35
	TransactionTypeTokenUpdate     int16 = 36
	TransactionTypeTokenMint       int16 = 37
	TransactionTypeTokenDissociate int16 = 41

	transactionTableName = "transaction"
)

type Transaction struct {
	ConsensusNs          int64 `gorm:"primaryKey"`
	ChargedTxFee         int64
	EntityId             *EntityId
	InitialBalance       int64
	MaxFee               int64
	Memo                 []byte
	NodeAccountId        *EntityId
	PayerAccountId       EntityId
	Result               int16
	Scheduled            bool
	TransactionBytes     []byte
	TransactionHash      []byte
	Type                 int16
	ValidDurationSeconds int64
	ValidStartNs         int64
}

func (Transaction) TableName() string {
	return transactionTableName
}
