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

package account

import (
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/jinzhu/gorm"
)

const (
	tableNameAccountBalance = "account_balance"
)

const (
	balanceChangeBetween string = `SELECT
                                        SUM(amount::bigint) AS value,
                                        COUNT(consensus_timestamp) AS number_of_transfers
                                   FROM crypto_transfer
                                   WHERE consensus_timestamp > $1
                                        AND consensus_timestamp <= $2
                                        AND entity_id = $3`
	latestBalanceBeforeConsensus string = `SELECT *
                                           FROM account_balance
                                           WHERE (account_id = $1 AND consensus_timestamp <= $2)
                                           ORDER BY consensus_timestamp DESC
                                           LIMIT 1`
)

type accountBalance struct {
	ConsensusTimestamp int64 `gorm:"type:bigint;primary_key"`
	Balance            int64 `gorm:"type:bigint"`
	AccountId          int64 `gorm:"type:bigint"`
}

type balanceChange struct {
	Value             int64 `gorm:"type:bigint"`
	NumberOfTransfers int64 `gorm:"type:bigint"`
}

// TableName - Set table name of the accountBalance to be `account_balance`
func (accountBalance) TableName() string {
	return tableNameAccountBalance
}

// AccountRepository struct that has connection to the Database
type AccountRepository struct {
	dbClient *gorm.DB
}

// NewAccountRepository creates an instance of a TransactionRepository struct. Populates the transaction types and
// results on init
func NewAccountRepository(dbClient *gorm.DB) *AccountRepository {
	return &AccountRepository{
		dbClient: dbClient,
	}
}

// RetrieveBalanceAtBlock returns the balance of the account at a given block (provided by consensusEnd timestamp).
// balance = balanceAtLatestBalanceSnapshot + balanceChangeBetweenSnapshotAndBlock
func (ar *AccountRepository) RetrieveBalanceAtBlock(
	addressStr string,
	consensusEnd int64,
) (*types.Amount, *rTypes.Error) {
	acc, err := types.AccountFromString(addressStr)
	if err != nil {
		return nil, err
	}
	entityID, err1 := acc.ComputeEncodedID()
	if err1 != nil {
		return nil, errors.Errors[errors.InvalidAccount]
	}

	// gets the most recent balance before block
	ab := &accountBalance{}
	if ar.dbClient.Raw(latestBalanceBeforeConsensus, entityID, consensusEnd).Find(&ab).RecordNotFound() {
		ab.Balance = 0
	}

	r := &balanceChange{}
	// gets the balance change from the Balance snapshot until the target block
	ar.dbClient.Raw(balanceChangeBetween, ab.ConsensusTimestamp, consensusEnd, entityID).Scan(r)

	return &types.Amount{
		Value: ab.Balance + r.Value,
	}, nil
}
