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
	"database/sql"
	"errors"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	hErrors "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"gorm.io/gorm"
)

const (
	tableNameAccountBalance = "account_balance"
)

const (
	balanceChangeBetween string = `SELECT
                                        SUM(amount::bigint) AS value,
                                        COUNT(consensus_timestamp) AS number_of_transfers
                                   FROM crypto_transfer
                                   WHERE consensus_timestamp > @start
                                        AND consensus_timestamp <= @end
                                        AND entity_id = @account_id`
	latestBalanceBeforeConsensus string = `SELECT *
                                           FROM account_balance
                                           WHERE account_id = @account_id AND consensus_timestamp <= @timestamp
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
		return nil, hErrors.ErrInvalidAccount
	}

	// gets the most recent balance before block
	ab := &accountBalance{}
	result := ar.dbClient.Raw(
		latestBalanceBeforeConsensus,
		sql.Named("account_id", entityID),
		sql.Named("timestamp", consensusEnd),
	).
		First(&ab)
	if result.Error != nil && errors.Is(result.Error, gorm.ErrRecordNotFound) {
		ab.Balance = 0
	}

	r := &balanceChange{}
	// gets the balance change from the Balance snapshot until the target block
	ar.dbClient.Raw(
		balanceChangeBetween,
		sql.Named("start", ab.ConsensusTimestamp),
		sql.Named("end", consensusEnd),
		sql.Named("account_id", entityID),
	).
		Scan(r)

	return &types.Amount{
		Value: ab.Balance + r.Value,
	}, nil
}
