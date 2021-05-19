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

package transaction

import (
	"encoding/hex"
	"reflect"
	"strconv"
	"testing"

	"github.com/DATA-DOG/go-sqlmock"
	entityid "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/services/encoding"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/common"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/mocks"
	hexutils "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/hex"
	"github.com/stretchr/testify/assert"
)

const (
	resultSuccess = "SUCCESS"
)

var (
	firstAccount             = &types.Account{EntityId: entityid.EntityId{EntityNum: 12345}}
	secondAccount            = &types.Account{EntityId: entityid.EntityId{EntityNum: 54321}}
	nodeAccount              = &types.Account{EntityId: entityid.EntityId{EntityNum: 3}}
	treasuryAccount          = &types.Account{EntityId: entityid.EntityId{EntityNum: 98}}
	hashString               = "1a00223d0a140a0c0891d0fef905109688f3a701120418d8c307120218061880c2d72f2202087872180a160a090a0418d8c30710cf0f0a090a0418fec40710d00f"
	hash, _                  = hex.DecodeString(hexutils.SafeRemoveHexPrefix(hashString))
	transactionColumns       = mocks.GetFieldsNamesToSnakeCase(transaction{})
	transactionTypeColumns   = mocks.GetFieldsNamesToSnakeCase(transactionType{})
	transactionResultColumns = mocks.GetFieldsNamesToSnakeCase(transactionResult{})
	cryptoTransferColumns    = mocks.GetFieldsNamesToSnakeCase(common.CryptoTransfer{})
	nonFeeTransferColumns    = mocks.GetFieldsNamesToSnakeCase(common.NonFeeTransfer{})
	consensusTimestamp       = int64(1)
	dbTransactions           = []transaction{
		{
			ConsensusNS:     1,
			Type:            14,
			Result:          22,
			PayerAccountID:  firstAccount.EntityNum,
			TransactionHash: hash,
		},
		{
			ConsensusNS:     2,
			Type:            12,
			Result:          22,
			PayerAccountID:  secondAccount.EntityNum,
			TransactionHash: hash,
		},
	}
	mapTransactions = map[int64]transaction{
		1: dbTransactions[0],
		2: dbTransactions[1],
	}
	dbCryptoTransfers = []common.CryptoTransfer{
		{EntityID: firstAccount.EntityNum, ConsensusTimestamp: 1, Amount: -30},
		{EntityID: secondAccount.EntityNum, ConsensusTimestamp: 2, Amount: -40},
		{EntityID: nodeAccount.EntityNum, ConsensusTimestamp: 1, Amount: 5},
		{EntityID: nodeAccount.EntityNum, ConsensusTimestamp: 2, Amount: 5},
	}
	dbNonFeeTransfers = []common.NonFeeTransfer{
		{EntityID: firstAccount.EntityNum, ConsensusTimestamp: 1, Amount: -25},
		{EntityID: secondAccount.EntityNum, ConsensusTimestamp: 2, Amount: -35},
	}
	dbTransactionTypes = []transactionType{
		{
			ProtoID: 12,
			Name:    "CRYPTODELETE",
		},
		{
			ProtoID: 14,
			Name:    "CRYPTOTRANSFER",
		},
	}
	dbTransactionResults = []transactionResult{
		{ProtoID: 1, Result: "INVALID_TRANSACTION"},
		{ProtoID: 11, Result: "DUPLICATE_TRANSACTION"},
		{ProtoID: 22, Result: "SUCCESS"},
	}
	expectedTransaction = &types.Transaction{
		Hash:       hexutils.SafeAddHexPrefix(hex.EncodeToString(hash)),
		Operations: operations,
	}
	operations = []*types.Operation{
		{
			Type:    "CRYPTOTRANSFER",
			Status:  resultSuccess,
			Account: firstAccount,
			Amount:  &types.Amount{Value: -25},
		},
		{
			Type:    "CRYPTOTRANSFER",
			Status:  resultSuccess,
			Account: firstAccount,
			Amount:  &types.Amount{Value: -5},
		},
		{
			Type:    "CRYPTOTRANSFER",
			Status:  resultSuccess,
			Account: nodeAccount,
			Amount:  &types.Amount{Value: 5},
		},
		{
			Type:    "CRYPTODELETE",
			Status:  resultSuccess,
			Account: secondAccount,
			Amount:  &types.Amount{Value: -35},
		},
		{
			Type:    "CRYPTODELETE",
			Status:  resultSuccess,
			Account: secondAccount,
			Amount:  &types.Amount{Value: -5},
		},
		{
			Type:    "CRYPTODELETE",
			Status:  resultSuccess,
			Account: nodeAccount,
			Amount:  &types.Amount{Value: 5},
		},
	}
	tRepoResults = map[int]string{
		1:  "INVALID_TRANSACTION",
		11: "DUPLICATE_TRANSACTION",
		22: resultSuccess,
	}
	tRepoTypes = map[int]string{
		12: "CRYPTODELETE",
		14: "CRYPTOTRANSFER",
	}
	tRepoTypesAsArray = []string{"CRYPTODELETE", "CRYPTOTRANSFER"}
)

func TestShouldSuccessFindBetween(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	mock.ExpectQuery(whereClauseBetweenConsensus).
		WithArgs(int64(1), int64(2)).
		WillReturnRows(willReturnRows(transactionColumns, dbTransactions))
	mock.ExpectQuery(whereCryptoTransferConsensusTimestampInTimestampsAsc).
		WithArgs("1,2").
		WillReturnRows(willReturnRows(cryptoTransferColumns, dbCryptoTransfers))
	mock.ExpectQuery(whereNonFeeTransferConsensusTimestampInTimestampsAsc).
		WithArgs("1,2").
		WillReturnRows(willReturnRows(nonFeeTransferColumns, dbNonFeeTransfers))
	mock.ExpectQuery(selectTransactionTypes).
		WillReturnRows(willReturnRows(transactionTypeColumns, dbTransactionTypes))
	mock.ExpectQuery(selectTransactionResults).
		WillReturnRows(willReturnRows(transactionResultColumns, dbTransactionResults))

	// when
	result, err := tr.FindBetween(1, 2)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, err)
	assertTransactions(t, []*types.Transaction{expectedTransaction}, result)
}

func TestShouldFailFindBetweenNoTypes(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	mock.ExpectQuery(whereClauseBetweenConsensus).
		WithArgs(int64(1), int64(2)).
		WillReturnRows(willReturnRows(transactionColumns, dbTransactions))
	mock.ExpectQuery(whereCryptoTransferConsensusTimestampInTimestampsAsc).
		WithArgs("1,2").
		WillReturnRows(willReturnRows(cryptoTransferColumns, dbCryptoTransfers))
	mock.ExpectQuery(whereNonFeeTransferConsensusTimestampInTimestampsAsc).
		WithArgs("1,2").
		WillReturnRows(willReturnRows(nonFeeTransferColumns, dbNonFeeTransfers))
	mock.ExpectQuery(selectTransactionTypes).
		WillReturnRows(willReturnRows(transactionTypeColumns))
	mock.ExpectQuery(selectTransactionResults).
		WillReturnRows(willReturnRows(transactionResultColumns))

	// when
	result, err := tr.FindBetween(1, 2)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, result)
	assert.NotNil(t, err)
}

func TestShouldFailFindBetweenEndBeforeStart(t *testing.T) {
	// given
	tr, _ := setupRepository(t)

	// when
	result, err := tr.FindBetween(2, 1)

	// then
	assert.Nil(t, result)
	assert.Equal(t, errors.ErrStartMustNotBeAfterEnd, err)
}

func TestShouldSuccessFindHashInBlock(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	mock.ExpectQuery(whereTransactionsByHashAndConsensusTimestamps).
		WithArgs(hash, int64(1), int64(2)).
		WillReturnRows(willReturnRows(transactionColumns, dbTransactions))
	mock.ExpectQuery(whereCryptoTransferConsensusTimestampInTimestampsAsc).
		WithArgs("1,2").
		WillReturnRows(willReturnRows(cryptoTransferColumns, dbCryptoTransfers))
	mock.ExpectQuery(whereNonFeeTransferConsensusTimestampInTimestampsAsc).
		WithArgs("1,2").
		WillReturnRows(willReturnRows(nonFeeTransferColumns, dbNonFeeTransfers))
	mock.ExpectQuery(selectTransactionTypes).
		WillReturnRows(willReturnRows(transactionTypeColumns, dbTransactionTypes))
	mock.ExpectQuery(selectTransactionResults).
		WillReturnRows(willReturnRows(transactionResultColumns, dbTransactionResults))

	// when
	result, err := tr.FindByHashInBlock(hashString, 1, 2)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())

	assert.Nil(t, err)
	assertTransactions(t, []*types.Transaction{expectedTransaction}, []*types.Transaction{result})
}

func TestShouldFailFindHashInBlockNoTypes(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	mock.ExpectQuery(whereTransactionsByHashAndConsensusTimestamps).
		WithArgs(hash, int64(1), int64(2)).
		WillReturnRows(willReturnRows(transactionColumns, dbTransactions))
	mock.ExpectQuery(whereCryptoTransferConsensusTimestampInTimestampsAsc).
		WithArgs("1,2").
		WillReturnRows(willReturnRows(cryptoTransferColumns, dbCryptoTransfers))
	mock.ExpectQuery(whereNonFeeTransferConsensusTimestampInTimestampsAsc).
		WithArgs("1,2").
		WillReturnRows(willReturnRows(nonFeeTransferColumns, dbNonFeeTransfers))
	mock.ExpectQuery(selectTransactionTypes).
		WillReturnRows(willReturnRows(transactionTypeColumns))
	mock.ExpectQuery(selectTransactionResults).
		WillReturnRows(willReturnRows(transactionResultColumns))

	// when
	result, err := tr.FindByHashInBlock(hashString, 1, 2)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())

	assert.Nil(t, result)
	assert.NotNil(t, err)
}

func TestShouldFailFindHashInBlockNoReturnTransactions(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	mock.ExpectQuery(whereTransactionsByHashAndConsensusTimestamps).
		WithArgs(hash, int64(1), int64(2)).
		WillReturnRows(willReturnRows(transactionColumns))

	// when
	result, err := tr.FindByHashInBlock(hashString, 1, 2)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())

	assert.Nil(t, result)
	assert.Equal(t, errors.ErrTransactionNotFound, err)
}

func TestShouldFailFindHashInBlockInvalidHash(t *testing.T) {
	// given
	tr, _ := setupRepository(t)

	// when
	result, err := tr.FindByHashInBlock("asd", 1, 2)

	// then
	assert.Nil(t, result)
	assert.Equal(t, errors.ErrInvalidTransactionIdentifier, err)
}

func TestShouldFailConstructTransactionDueToNoResults(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	mock.ExpectQuery(whereCryptoTransferConsensusTimestampInTimestampsAsc).
		WithArgs("1,2").
		WillReturnRows(willReturnRows(cryptoTransferColumns, dbCryptoTransfers))
	mock.ExpectQuery(whereNonFeeTransferConsensusTimestampInTimestampsAsc).
		WithArgs("1,2").
		WillReturnRows(willReturnRows(nonFeeTransferColumns, dbNonFeeTransfers))
	mock.ExpectQuery(selectTransactionTypes).
		WillReturnRows(willReturnRows(transactionTypeColumns, dbTransactionTypes))
	mock.ExpectQuery(selectTransactionResults).
		WillReturnRows(willReturnRows(transactionResultColumns))

	// when
	result, err := tr.constructTransaction(dbTransactions)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())

	assert.Nil(t, result)
	assert.NotNil(t, err)
}

func TestShouldSuccessConstructTransaction(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	mock.ExpectQuery(whereCryptoTransferConsensusTimestampInTimestampsAsc).
		WithArgs("1,2").
		WillReturnRows(willReturnRows(cryptoTransferColumns, dbCryptoTransfers))
	mock.ExpectQuery(whereNonFeeTransferConsensusTimestampInTimestampsAsc).
		WithArgs("1,2").
		WillReturnRows(willReturnRows(nonFeeTransferColumns, dbNonFeeTransfers))
	mock.ExpectQuery(selectTransactionTypes).
		WillReturnRows(willReturnRows(transactionTypeColumns, dbTransactionTypes))
	mock.ExpectQuery(selectTransactionResults).
		WillReturnRows(willReturnRows(transactionResultColumns, dbTransactionResults))

	// when
	result, err := tr.constructTransaction(dbTransactions)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())

	assert.Nil(t, err)
	assertTransactions(t, []*types.Transaction{expectedTransaction}, []*types.Transaction{result})
}

func TestShouldSuccessConstructionOperations(t *testing.T) {
	var tests = []struct {
		name            string
		cryptoTransfers []common.CryptoTransfer
		nonFeeTransfers []common.NonFeeTransfer
		transactions    map[int64]transaction
		expected        []*types.Operation
	}{
		{
			name:            "Default",
			cryptoTransfers: dbCryptoTransfers,
			nonFeeTransfers: dbNonFeeTransfers,
			transactions:    mapTransactions,
			expected:        operations,
		},
		{
			name: "SingleTransaction",
			cryptoTransfers: []common.CryptoTransfer{
				{
					EntityID:           firstAccount.EntityNum,
					ConsensusTimestamp: 100,
					Amount:             -158,
				},
				{
					EntityID:           nodeAccount.EntityNum,
					ConsensusTimestamp: 100,
					Amount:             10,
				},
				{
					EntityID:           treasuryAccount.EntityNum,
					ConsensusTimestamp: 100,
					Amount:             8,
				},
				{
					EntityID:           secondAccount.EntityNum,
					ConsensusTimestamp: 100,
					Amount:             140,
				},
			},
			nonFeeTransfers: []common.NonFeeTransfer{
				{
					EntityID:           firstAccount.EntityNum,
					ConsensusTimestamp: 100,
					Amount:             -140,
				},
				{
					EntityID:           secondAccount.EntityNum,
					ConsensusTimestamp: 100,
					Amount:             140,
				},
			},
			transactions: map[int64]transaction{
				100: {
					ConsensusNS:          100,
					Memo:                 nil,
					NodeAccountID:        nodeAccount.EntityNum,
					PayerAccountID:       firstAccount.EntityNum,
					Result:               22,
					Scheduled:            false,
					TransactionHash:      hash,
					Type:                 14,
					ValidDurationSeconds: 120,
				},
			},
			expected: []*types.Operation{
				{
					Type:    "CRYPTOTRANSFER",
					Status:  resultSuccess,
					Account: firstAccount,
					Amount:  &types.Amount{Value: -140},
				},
				{
					Type:    "CRYPTOTRANSFER",
					Status:  resultSuccess,
					Account: secondAccount,
					Amount:  &types.Amount{Value: 140},
				},
				{
					Type:    "CRYPTOTRANSFER",
					Status:  resultSuccess,
					Account: firstAccount,
					Amount:  &types.Amount{Value: -18},
				},
				{
					Type:    "CRYPTOTRANSFER",
					Status:  resultSuccess,
					Account: nodeAccount,
					Amount:  &types.Amount{Value: 10},
				},
				{
					Type:    "CRYPTOTRANSFER",
					Status:  resultSuccess,
					Account: treasuryAccount,
					Amount:  &types.Amount{Value: 8},
				},
			},
		},
		{
			name: "SingleTransactionMultipleNonFeeTransferSameEntity",
			cryptoTransfers: []common.CryptoTransfer{
				{
					EntityID:           firstAccount.EntityNum,
					ConsensusTimestamp: 100,
					Amount:             -158,
				},
				{
					EntityID:           nodeAccount.EntityNum,
					ConsensusTimestamp: 100,
					Amount:             10,
				},
				{
					EntityID:           treasuryAccount.EntityNum,
					ConsensusTimestamp: 100,
					Amount:             8,
				},
				{
					EntityID:           secondAccount.EntityNum,
					ConsensusTimestamp: 100,
					Amount:             140,
				},
			},
			nonFeeTransfers: []common.NonFeeTransfer{
				// there are two non fee transfers from the sender
				{
					EntityID:           firstAccount.EntityNum,
					ConsensusTimestamp: 100,
					Amount:             -100,
				},
				{
					EntityID:           firstAccount.EntityNum,
					ConsensusTimestamp: 100,
					Amount:             -40,
				},
				{
					EntityID:           secondAccount.EntityNum,
					ConsensusTimestamp: 100,
					Amount:             140,
				},
			},
			transactions: map[int64]transaction{
				100: {
					ConsensusNS:          100,
					Memo:                 nil,
					NodeAccountID:        nodeAccount.EntityNum,
					PayerAccountID:       firstAccount.EntityNum,
					Result:               22,
					Scheduled:            false,
					TransactionHash:      hash,
					Type:                 14,
					ValidDurationSeconds: 120,
				},
			},
			expected: []*types.Operation{
				{
					Type:    "CRYPTOTRANSFER",
					Status:  resultSuccess,
					Account: firstAccount,
					Amount:  &types.Amount{Value: -100},
				},
				{
					Type:    "CRYPTOTRANSFER",
					Status:  resultSuccess,
					Account: firstAccount,
					Amount:  &types.Amount{Value: -40},
				},
				{
					Type:    "CRYPTOTRANSFER",
					Status:  resultSuccess,
					Account: secondAccount,
					Amount:  &types.Amount{Value: 140},
				},
				{
					Type:    "CRYPTOTRANSFER",
					Status:  resultSuccess,
					Account: firstAccount,
					Amount:  &types.Amount{Value: -18},
				},
				{
					Type:    "CRYPTOTRANSFER",
					Status:  resultSuccess,
					Account: nodeAccount,
					Amount:  &types.Amount{Value: 10},
				},
				{
					Type:    "CRYPTOTRANSFER",
					Status:  resultSuccess,
					Account: treasuryAccount,
					Amount:  &types.Amount{Value: 8},
				},
			},
		},
		{
			name: "SingleTransactionMultipleCryptoTransferSameEntity",
			cryptoTransfers: []common.CryptoTransfer{
				{
					EntityID:           firstAccount.EntityNum,
					ConsensusTimestamp: 100,
					Amount:             -100,
				},
				{
					EntityID:           firstAccount.EntityNum,
					ConsensusTimestamp: 100,
					Amount:             -58,
				},
				{
					EntityID:           nodeAccount.EntityNum,
					ConsensusTimestamp: 100,
					Amount:             10,
				},
				{
					EntityID:           treasuryAccount.EntityNum,
					ConsensusTimestamp: 100,
					Amount:             8,
				},
				{
					EntityID:           secondAccount.EntityNum,
					ConsensusTimestamp: 100,
					Amount:             140,
				},
			},
			nonFeeTransfers: []common.NonFeeTransfer{
				// there are two non fee transfers from the sender
				{
					EntityID:           firstAccount.EntityNum,
					ConsensusTimestamp: 100,
					Amount:             -100,
				},
				{
					EntityID:           firstAccount.EntityNum,
					ConsensusTimestamp: 100,
					Amount:             -40,
				},
				{
					EntityID:           secondAccount.EntityNum,
					ConsensusTimestamp: 100,
					Amount:             140,
				},
			},
			transactions: map[int64]transaction{
				100: {
					ConsensusNS:          100,
					Memo:                 nil,
					NodeAccountID:        nodeAccount.EntityNum,
					PayerAccountID:       firstAccount.EntityNum,
					Result:               22,
					Scheduled:            false,
					TransactionHash:      hash,
					Type:                 14,
					ValidDurationSeconds: 120,
				},
			},
			expected: []*types.Operation{
				{
					Type:    "CRYPTOTRANSFER",
					Status:  resultSuccess,
					Account: firstAccount,
					Amount:  &types.Amount{Value: -100},
				},
				{
					Type:    "CRYPTOTRANSFER",
					Status:  resultSuccess,
					Account: firstAccount,
					Amount:  &types.Amount{Value: -40},
				},
				{
					Type:    "CRYPTOTRANSFER",
					Status:  resultSuccess,
					Account: secondAccount,
					Amount:  &types.Amount{Value: 140},
				},
				{
					Type:    "CRYPTOTRANSFER",
					Status:  resultSuccess,
					Account: firstAccount,
					Amount:  &types.Amount{Value: -18},
				},
				{
					Type:    "CRYPTOTRANSFER",
					Status:  resultSuccess,
					Account: nodeAccount,
					Amount:  &types.Amount{Value: 10},
				},
				{
					Type:    "CRYPTOTRANSFER",
					Status:  resultSuccess,
					Account: treasuryAccount,
					Amount:  &types.Amount{Value: 8},
				},
			},
		},
		{
			name: "OneSuccessfulOneFailed",
			cryptoTransfers: []common.CryptoTransfer{
				// transfers for the successful crypto transfer transaction
				{
					EntityID:           firstAccount.EntityNum,
					ConsensusTimestamp: 100,
					Amount:             -158,
				},
				{
					EntityID:           nodeAccount.EntityNum,
					ConsensusTimestamp: 100,
					Amount:             10,
				},
				{
					EntityID:           treasuryAccount.EntityNum,
					ConsensusTimestamp: 100,
					Amount:             8,
				},
				{
					EntityID:           secondAccount.EntityNum,
					ConsensusTimestamp: 100,
					Amount:             140,
				},
				// transfers for the duplicated crypto transfer transaction
				{
					EntityID:           firstAccount.EntityNum,
					ConsensusTimestamp: 123,
					Amount:             -18,
				},
				{
					EntityID:           nodeAccount.EntityNum,
					ConsensusTimestamp: 123,
					Amount:             10,
				},
				{
					EntityID:           treasuryAccount.EntityNum,
					ConsensusTimestamp: 123,
					Amount:             8,
				},
			},
			nonFeeTransfers: []common.NonFeeTransfer{
				// only the successful transaction has non fee transfers
				{
					EntityID:           firstAccount.EntityNum,
					ConsensusTimestamp: 100,
					Amount:             -140,
				},
				{
					EntityID:           secondAccount.EntityNum,
					ConsensusTimestamp: 100,
					Amount:             140,
				},
			},
			transactions: map[int64]transaction{
				100: {
					ConsensusNS:          100,
					Memo:                 nil,
					NodeAccountID:        nodeAccount.EntityNum,
					PayerAccountID:       firstAccount.EntityNum,
					Result:               22,
					Scheduled:            false,
					TransactionHash:      hash,
					Type:                 14,
					ValidDurationSeconds: 120,
				},
				123: {
					ConsensusNS:          123,
					Memo:                 nil,
					NodeAccountID:        nodeAccount.EntityNum,
					PayerAccountID:       firstAccount.EntityNum,
					Result:               11,
					Scheduled:            false,
					TransactionHash:      hash,
					Type:                 14,
					ValidDurationSeconds: 120,
				},
			},
			expected: []*types.Operation{
				// operations of the successful transaction
				{
					Type:    "CRYPTOTRANSFER",
					Status:  resultSuccess,
					Account: firstAccount,
					Amount:  &types.Amount{Value: -140},
				},
				{
					Type:    "CRYPTOTRANSFER",
					Status:  resultSuccess,
					Account: secondAccount,
					Amount:  &types.Amount{Value: 140},
				},
				{
					Type:    "CRYPTOTRANSFER",
					Status:  resultSuccess,
					Account: firstAccount,
					Amount:  &types.Amount{Value: -18},
				},
				{
					Type:    "CRYPTOTRANSFER",
					Status:  resultSuccess,
					Account: nodeAccount,
					Amount:  &types.Amount{Value: 10},
				},
				{
					Type:    "CRYPTOTRANSFER",
					Status:  resultSuccess,
					Account: treasuryAccount,
					Amount:  &types.Amount{Value: 8},
				},
				// operations of the failed transaction, only fees
				{
					Type:    "CRYPTOTRANSFER",
					Status:  resultSuccess,
					Account: firstAccount,
					Amount:  &types.Amount{Value: -18},
				},
				{
					Type:    "CRYPTOTRANSFER",
					Status:  resultSuccess,
					Account: nodeAccount,
					Amount:  &types.Amount{Value: 10},
				},
				{
					Type:    "CRYPTOTRANSFER",
					Status:  resultSuccess,
					Account: treasuryAccount,
					Amount:  &types.Amount{Value: 8},
				},
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// given
			tr, mock := setupRepository(t)
			mock.ExpectQuery(selectTransactionTypes).
				WillReturnRows(willReturnRows(transactionTypeColumns, dbTransactionTypes))
			mock.ExpectQuery(selectTransactionResults).
				WillReturnRows(willReturnRows(transactionResultColumns, dbTransactionResults))

			// when
			actual, err := tr.constructOperations(tt.cryptoTransfers, tt.nonFeeTransfers, tt.transactions)

			// then
			assert.NoError(t, mock.ExpectationsWereMet())
			assert.Nil(t, err)

			assertOperationIndexes(t, actual)
			assert.ElementsMatch(t, tt.expected, actual)
		})
	}
}

func TestShouldFailConstructionOperationsInvalidTransferEntityId(t *testing.T) {
	invalidCryptoTransfers := []common.CryptoTransfer{
		{EntityID: -1, ConsensusTimestamp: 1, Amount: 1},
		{EntityID: -2, ConsensusTimestamp: 2, Amount: 1},
	}

	invalidNonFeeTransfers := []common.NonFeeTransfer{
		{EntityID: -1, ConsensusTimestamp: 1, Amount: 1},
		{EntityID: -2, ConsensusTimestamp: 2, Amount: 1},
	}

	var tests = []struct {
		name            string
		cryptoTransfers []common.CryptoTransfer
		nonFeeTransfers []common.NonFeeTransfer
	}{
		{
			name:            "InvalidEntityIdInCryptoTransfers",
			cryptoTransfers: invalidCryptoTransfers,
			nonFeeTransfers: dbNonFeeTransfers,
		},
		{
			name:            "InvalidEntityIdInNonFeeTransfers",
			cryptoTransfers: dbCryptoTransfers,
			nonFeeTransfers: invalidNonFeeTransfers,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// given
			tr, mock := setupRepository(t)
			rows := willReturnRows(transactionTypeColumns, dbTransactionTypes)
			mock.ExpectQuery(selectTransactionTypes).WillReturnRows(rows)
			rows = willReturnRows(transactionResultColumns, dbTransactionResults)
			mock.ExpectQuery(selectTransactionResults).WillReturnRows(rows)

			// when
			result, err := tr.constructOperations(tt.cryptoTransfers, tt.nonFeeTransfers, mapTransactions)

			// then
			assert.NoError(t, mock.ExpectationsWereMet())
			assert.Nil(t, result)
			assert.NotNil(t, err)
		})
	}
}

func TestShouldFailConstructionOperationsDueToResultsError(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	rows := willReturnRows(transactionTypeColumns, dbTransactionTypes)
	mock.ExpectQuery(selectTransactionTypes).WillReturnRows(rows)
	mock.ExpectQuery(selectTransactionResults).WillReturnRows(willReturnRows(transactionResultColumns))

	// when
	result, err := tr.constructOperations(dbCryptoTransfers, dbNonFeeTransfers, nil)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, result)
	assert.NotNil(t, err)
}

func TestShouldFailConstructionOperationsDueToTypesError(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	mock.ExpectQuery(selectTransactionTypes).WillReturnRows(willReturnRows(transactionTypeColumns))
	mock.ExpectQuery(selectTransactionResults).WillReturnRows(willReturnRows(transactionResultColumns))

	// when
	result, err := tr.constructOperations(dbCryptoTransfers, dbNonFeeTransfers, nil)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, result)
	assert.NotNil(t, err)
}

func TestShouldSuccessFindCryptoTransfers(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	rows := willReturnRows(cryptoTransferColumns, dbCryptoTransfers)
	mock.ExpectQuery(whereCryptoTransferConsensusTimestampInTimestampsAsc).
		WithArgs(strconv.FormatInt(consensusTimestamp, 10)).
		WillReturnRows(rows)

	// when
	result := tr.findCryptoTransfersAsc([]int64{consensusTimestamp})

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Equal(t, dbCryptoTransfers, result)
}

func TestShouldSuccessFindNonFeeTransfers(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	rows := willReturnRows(nonFeeTransferColumns, dbNonFeeTransfers)
	mock.ExpectQuery(whereNonFeeTransferConsensusTimestampInTimestampsAsc).
		WithArgs(strconv.FormatInt(consensusTimestamp, 10)).
		WillReturnRows(rows)

	// when
	result := tr.findNonFeeTransfersAsc([]int64{consensusTimestamp})

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Equal(t, dbNonFeeTransfers, result)
}

func TestShouldSuccessReturnResults(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	rows := willReturnRows(transactionTypeColumns, dbTransactionTypes)
	mock.ExpectQuery(selectTransactionTypes).WillReturnRows(rows)
	rows = willReturnRows(transactionResultColumns, dbTransactionResults)
	mock.ExpectQuery(selectTransactionResults).WillReturnRows(rows)

	// when
	results, err := tr.Results()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, err)

	assert.Equal(t, tRepoResults, results)
}

func TestShouldFailReturnResults(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	rows := willReturnRows(transactionTypeColumns, dbTransactionTypes)
	mock.ExpectQuery(selectTransactionTypes).WillReturnRows(rows)
	mock.ExpectQuery(selectTransactionResults).WillReturnRows(willReturnRows(transactionResultColumns))

	// when
	results, err := tr.Results()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, results)
	assert.NotNil(t, err)
}

func TestShouldFailReturnTypes(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	rows := willReturnRows(transactionTypeColumns, dbTransactionTypes)
	mock.ExpectQuery(selectTransactionTypes).WillReturnRows(rows)
	mock.ExpectQuery(selectTransactionResults).WillReturnRows(willReturnRows(transactionResultColumns))

	// when
	result, err := tr.Types()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, result)
	assert.NotNil(t, err)
}

func TestShouldFailReturnTypesAsArray(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	rows := willReturnRows(transactionTypeColumns, dbTransactionTypes)
	mock.ExpectQuery(selectTransactionTypes).WillReturnRows(rows)
	mock.ExpectQuery(selectTransactionResults).WillReturnRows(willReturnRows(transactionResultColumns))

	// when
	result, err := tr.TypesAsArray()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, result)
	assert.NotNil(t, err)
}

func TestShouldSuccessReturnTypesAsArray(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	rows := willReturnRows(transactionTypeColumns, dbTransactionTypes)
	mock.ExpectQuery(selectTransactionTypes).WillReturnRows(rows)
	rows = willReturnRows(transactionResultColumns, dbTransactionResults)
	mock.ExpectQuery(selectTransactionResults).WillReturnRows(rows)

	// when
	result, err := tr.TypesAsArray()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, err)

	assert.ElementsMatch(t, tRepoTypesAsArray, result)
}

func TestShouldSuccessReturnTypes(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	rows := willReturnRows(transactionTypeColumns, dbTransactionTypes)
	mock.ExpectQuery(selectTransactionTypes).WillReturnRows(rows)
	rows = willReturnRows(transactionResultColumns, dbTransactionResults)
	mock.ExpectQuery(selectTransactionResults).WillReturnRows(rows)

	// when
	result, err := tr.Types()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, err)

	assert.Equal(t, tRepoTypes, result)
}

func TestShouldSuccessSaveTransactionTypesAndResults(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	rows := willReturnRows(transactionTypeColumns, dbTransactionTypes)
	mock.ExpectQuery(selectTransactionTypes).WillReturnRows(rows)
	rows = willReturnRows(transactionResultColumns, dbTransactionResults)
	mock.ExpectQuery(selectTransactionResults).WillReturnRows(rows)

	// when
	result := tr.retrieveTransactionTypesAndResults()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, result)

	assert.Equal(t, tRepoResults, tr.results)
	assert.Equal(t, tRepoTypes, tr.types)
}

func TestShouldFailReturnTransactionTypesAndResultsDueToNoResults(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	rows := willReturnRows(transactionTypeColumns, dbTransactionTypes)
	mock.ExpectQuery(selectTransactionTypes).WillReturnRows(rows)
	mock.ExpectQuery(selectTransactionResults).WillReturnRows(willReturnRows(transactionResultColumns))

	// when
	result := tr.retrieveTransactionTypesAndResults()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Equal(t, errors.ErrOperationResultsNotFound, result)
}

func TestShouldFailReturnTransactionTypesAndResultsDueToNoTypes(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	mock.ExpectQuery(selectTransactionTypes).WillReturnRows(willReturnRows(transactionTypeColumns))
	mock.ExpectQuery(selectTransactionResults).WillReturnRows(willReturnRows(transactionResultColumns))

	// when
	result := tr.retrieveTransactionTypesAndResults()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Equal(t, errors.ErrOperationTypesNotFound, result)
}

func TestShouldSuccessReturnTransactionResults(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	rows := willReturnRows(transactionResultColumns, dbTransactionResults)
	mock.ExpectQuery(selectTransactionResults).WillReturnRows(rows)

	// when
	result := tr.retrieveTransactionResults()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Equal(t, dbTransactionResults, result)
}

func TestShouldSuccessReturnTransactionTypes(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	rows := willReturnRows(transactionTypeColumns, dbTransactionTypes)
	mock.ExpectQuery(selectTransactionTypes).WillReturnRows(rows)

	// when
	result := tr.retrieveTransactionTypes()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Equal(t, dbTransactionTypes, result)
}

func TestShouldSuccessReturnTransactionTableName(t *testing.T) {
	assert.Equal(t, tableNameTransaction, transaction{}.TableName())
}

func TestShouldSuccessReturnTransactionTypesTableName(t *testing.T) {
	assert.Equal(t, tableNameTransactionTypes, transactionType{}.TableName())
}

func TestShouldSuccessReturnTransactionResultsTableName(t *testing.T) {
	assert.Equal(t, tableNameTransactionResults, transactionResult{}.TableName())
}

func TestShouldSuccessReturnRepository(t *testing.T) {
	// given
	gormDbClient, _ := mocks.DatabaseMock(t)

	// when
	result := NewTransactionRepository(gormDbClient)

	// then
	assert.IsType(t, &TransactionRepository{}, result)
	assert.Equal(t, result.dbClient, gormDbClient)
}

func TestShouldSuccessGetHashString(t *testing.T) {
	// given
	txStr := "0x967f26876ad492cc27b4c384dc962f443bcc9be33cbb7add3844bc864de047340e7a78c0fbaf40ab10948dc570bbc25edb505f112d0926dffb65c93199e6d507"
	bytesTx, _ := hex.DecodeString(hexutils.SafeRemoveHexPrefix(txStr))
	givenTx := transaction{
		TransactionHash: bytesTx,
	}

	// when
	result := givenTx.getHashString()

	// then
	assert.Equal(t, txStr, result)
}

func TestShouldSuccessIntsToString(t *testing.T) {
	data := []int64{1, 2, 2394238471841, 2394143718391293}
	expected := "1,2,2394238471841,2394143718391293"

	result := intsToString(data)

	assert.Equal(t, expected, result)
}

func TestShouldFailConstructAccount(t *testing.T) {
	data := int64(-1)
	expected := errors.ErrInternalServerError

	result, err := constructAccount(data)

	assert.Nil(t, result)
	assert.Equal(t, expected, err)
}

func TestShouldSuccessConstructAccount(t *testing.T) {
	// given
	data := int64(5)
	expected := &types.Account{EntityId: entityid.EntityId{
		ShardNum:  0,
		RealmNum:  0,
		EntityNum: 5,
	}}

	// when
	result, err := constructAccount(data)

	// then
	assert.Nil(t, err)
	assert.Equal(t, expected, result)
}

func assertOperationIndexes(t *testing.T, operations []*types.Operation) {
	makeRange := func(len int) []int64 {
		result := make([]int64, len)
		for i := range result {
			result[i] = int64(i)
		}
		return result
	}

	expected := makeRange(len(operations))
	actual := make([]int64, len(operations))
	for i, operation := range operations {
		actual[i] = operation.Index
		// side effect, clear operation's index
		operation.Index = 0
	}

	assert.Equal(t, expected, actual)
}

func assertTransactions(t *testing.T, expected, actual []*types.Transaction) {
	getTransactionMap := func(transactions []*types.Transaction) map[string]*types.Transaction {
		result := make(map[string]*types.Transaction)
		for _, tx := range transactions {
			result[tx.Hash] = tx
		}
		return result
	}

	assert.Len(t, actual, len(expected))

	for _, tx := range actual {
		// assert the 0-based, unique, contiguous operations indexes
		assertOperationIndexes(t, tx.Operations)
	}

	actualTransactionMap := getTransactionMap(actual)
	expectedTransactionMap := getTransactionMap(expected)

	assert.Len(t, actualTransactionMap, len(expectedTransactionMap))

	for txHash, expectedTx := range actualTransactionMap {
		assert.Contains(t, expectedTransactionMap, txHash)
		actualTx := actualTransactionMap[txHash]
		assert.ElementsMatch(t, actualTx.Operations, expectedTx.Operations)
	}
}

func setupRepository(t *testing.T) (*TransactionRepository, sqlmock.Sqlmock) {
	gormDbClient, mock := mocks.DatabaseMock(t)

	aber := NewTransactionRepository(gormDbClient)
	return aber, mock
}

func willReturnRows(columns []string, data ...interface{}) *sqlmock.Rows {
	converter := sqlmock.NewRows(columns)

	for _, value := range data {
		s := reflect.ValueOf(value)

		for i := 0; i < s.Len(); i++ {
			row := mocks.GetFieldsValuesAsDriverValue(s.Index(i).Interface())
			converter.AddRow(row...)
		}
	}

	return converter
}
