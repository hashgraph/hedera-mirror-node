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
	"testing"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/mocks"
	"github.com/stretchr/testify/assert"
	"gorm.io/gorm"
)

var (
	accountString        = "0.0.5"
	balanceChangeColumns = mocks.GetFieldsNamesToSnakeCase(dbBalanceChange)
	consensusTimestamp   = int64(2)
	dbAccountBalance     = &accountBalance{
		ConsensusTimestamp: 1,
		Balance:            10,
		AccountId:          5,
	}
	dbAccountBalanceRow = mocks.GetFieldsValuesAsDriverValue(dbAccountBalance)
	dbBalanceChange     = &balanceChange{
		Value:             10,
		NumberOfTransfers: 20,
	}
	dbBalanceChangeRow = mocks.GetFieldsValuesAsDriverValue(dbBalanceChange)
	expectedAmount     = &types.Amount{Value: dbBalanceChange.Value + dbAccountBalance.Balance}
)

func TestShouldReturnValidAccountBalanceTableName(t *testing.T) {
	assert.Equal(t, tableNameAccountBalance, accountBalance{}.TableName())
}

func TestShouldSuccessReturnValidRepository(t *testing.T) {
	// given
	gormDbClient, _ := mocks.DatabaseMock(t)

	// when
	result := NewAccountRepository(gormDbClient)

	// then
	assert.IsType(t, &AccountRepository{}, result)
	assert.Equal(t, result.dbClient, gormDbClient)
}

func TestShouldFailRetrieveBalanceAtBlockDueToInvalidAddress(t *testing.T) {
	// given
	abr, _, mock := setupRepository(t)
	invalidAddressString := "0.0.a"

	// when
	result, err := abr.RetrieveBalanceAtBlock(invalidAddressString, consensusTimestamp)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, result)
	assert.NotNil(t, err)
}

func TestShouldFailRetrieveBalanceAtBlockDueToInvalidAddressNegative(t *testing.T) {
	// given
	abr, _, mock := setupRepository(t)
	invalidAddressString := "0.0.-2"

	// when
	result, err := abr.RetrieveBalanceAtBlock(invalidAddressString, consensusTimestamp)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, result)
	assert.Equal(t, errors.ErrInvalidAccount, err)
}

func TestShouldFailRetrieveBalanceAtBlockDueToInvalidShardComputation(t *testing.T) {
	// given
	abr, _, mock := setupRepository(t)
	invalidAddressString := "32768.0.0"

	// when
	result, err := abr.RetrieveBalanceAtBlock(invalidAddressString, consensusTimestamp)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, result)
	assert.Equal(t, errors.ErrInvalidAccount, err)
}

func TestShouldFailRetrieveBalanceAtBlockDueToInvalidRealmComputation(t *testing.T) {
	// given
	abr, _, mock := setupRepository(t)
	invalidAddressString := "0.65536.0"

	// when
	result, err := abr.RetrieveBalanceAtBlock(invalidAddressString, consensusTimestamp)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, result)
	assert.Equal(t, errors.ErrInvalidAccount, err)
}

func TestShouldFailRetrieveBalanceAtBlockDueToInvalidEntityComputation(t *testing.T) {
	// given
	abr, _, mock := setupRepository(t)
	invalidAddressString := "0.0.4294967296"

	// when
	result, err := abr.RetrieveBalanceAtBlock(invalidAddressString, consensusTimestamp)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, result)
	assert.Equal(t, errors.ErrInvalidAccount, err)
}

func TestShouldSuccessRetrieveBalanceAtBlock(t *testing.T) {
	// given
	abr, columns, mock := setupRepository(t)

	mock.ExpectQuery(latestBalanceBeforeConsensus).
		WithArgs(dbAccountBalance.AccountId, consensusTimestamp).
		WillReturnRows(sqlmock.NewRows(columns).AddRow(dbAccountBalanceRow...))

	mock.ExpectQuery(balanceChangeBetween).
		WithArgs(dbAccountBalance.ConsensusTimestamp, consensusTimestamp, dbAccountBalance.AccountId).
		WillReturnRows(sqlmock.NewRows(balanceChangeColumns).AddRow(dbBalanceChangeRow...))

	// when
	result, err := abr.RetrieveBalanceAtBlock(accountString, consensusTimestamp)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())

	assert.Equal(t, expectedAmount, result)
	assert.Nil(t, err)
}

func TestShouldSuccessRetrieveBalanceAtBlockWithNoSnapshotsBeforeThat(t *testing.T) {
	// given
	abr, _, mock := setupRepository(t)
	dbAccountBalance.ConsensusTimestamp = 0
	expectedAmount.Value = dbBalanceChange.Value

	mock.ExpectQuery(latestBalanceBeforeConsensus).
		WithArgs(dbAccountBalance.AccountId, consensusTimestamp).
		WillReturnError(gorm.ErrRecordNotFound)

	mock.ExpectQuery(balanceChangeBetween).
		WithArgs(dbAccountBalance.ConsensusTimestamp, consensusTimestamp, dbAccountBalance.AccountId).
		WillReturnRows(sqlmock.NewRows(balanceChangeColumns).AddRow(dbBalanceChangeRow...))

	// when
	result, err := abr.RetrieveBalanceAtBlock(accountString, consensusTimestamp)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())

	assert.Equal(t, expectedAmount, result)
	assert.Nil(t, err)
}

func setupRepository(t *testing.T) (*AccountRepository, []string, sqlmock.Sqlmock) {
	gormDbClient, mock := mocks.DatabaseMock(t)

	columns := mocks.GetFieldsNamesToSnakeCase(accountBalance{})

	aber := NewAccountRepository(gormDbClient)
	return aber, columns, mock
}
