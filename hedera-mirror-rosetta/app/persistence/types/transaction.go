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

package types

import (
	"encoding/hex"

	hexUtils "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/hex"
)

const (
	TransactionTypeTokenCreation = 29
	TransactionTypeTokenDeletion = 35
	TransactionTypeTokenUpdate   = 36

	transactionTableName = "transaction"
)

type Transaction struct {
	ConsensusNs          int64 `gorm:"primaryKey"`
	ChargedTxFee         int64
	EntityId             int64
	InitialBalance       int64
	MaxFee               int64
	Memo                 []byte
	NodeAccountId        int64
	PayerAccountId       int64
	Result               int
	Scheduled            bool
	TransactionBytes     []byte
	TransactionHash      []byte
	Type                 int
	ValidDurationSeconds int64
	ValidStartNs         int64
}

func (Transaction) TableName() string {
	return transactionTableName
}

func (t Transaction) HasTokenOperation() bool {
	// these three transaction types have token id saved and require an extra operation in addition to any transfer
	// token mint, token burn, and token wipe can be fully represented by the operation built from the token transfer
	// record so they don't need an extra operation
	return t.Type == TransactionTypeTokenCreation ||
		t.Type == TransactionTypeTokenDeletion ||
		t.Type == TransactionTypeTokenUpdate
}

func (t Transaction) GetHashString() string {
	return hexUtils.SafeAddHexPrefix(hex.EncodeToString(t.TransactionHash))
}
