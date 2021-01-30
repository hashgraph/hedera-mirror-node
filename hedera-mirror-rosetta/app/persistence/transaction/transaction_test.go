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
	"github.com/DATA-DOG/go-sqlmock"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	entityid "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/services/encoding"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/common"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/mocks"
	hexutils "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/hex"
	"github.com/stretchr/testify/assert"
	"reflect"
	"regexp"
	"strconv"
	"testing"
)

var (
	firstAccount = &types.Account{EntityId: entityid.EntityId{
		ShardNum:  0,
		RealmNum:  0,
		EntityNum: 1,
	}}
	secondAccount = &types.Account{EntityId: entityid.EntityId{
		ShardNum:  0,
		RealmNum:  0,
		EntityNum: 2,
	}}
	hashString               = "1a00223d0a140a0c0891d0fef905109688f3a701120418d8c307120218061880c2d72f2202087872180a160a090a0418d8c30710cf0f0a090a0418fec40710d00f"
	hash, _                  = hex.DecodeString(hexutils.SafeRemoveHexPrefix(hashString))
	amount                   = types.Amount{Value: 1}
	transactionColumns       = mocks.GetFieldsNamesToSnakeCase(transaction{})
	transactionTypeColumns   = mocks.GetFieldsNamesToSnakeCase(transactionType{})
	transactionResultColumns = mocks.GetFieldsNamesToSnakeCase(transactionResult{})
	cryptoTransferColumns    = mocks.GetFieldsNamesToSnakeCase(common.CryptoTransfer{})
	consensusTimestamp       = int64(1)
	dbTransactions           = []transaction{
		{
			ConsensusNS:     1,
			Type:            14,
			Result:          0,
			PayerAccountID:  3,
			TransactionHash: hash,
		},
		{
			ConsensusNS:     2,
			Type:            12,
			Result:          0,
			PayerAccountID:  3,
			TransactionHash: hash,
		},
	}
	mapTransactions = map[int64]transaction{
		1: dbTransactions[0],
		2: dbTransactions[1],
	}
	dbCryptoTransfers = []common.CryptoTransfer{
		{EntityID: 1, ConsensusTimestamp: 1, Amount: 1},
		{EntityID: 2, ConsensusTimestamp: 2, Amount: 1},
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
		{ProtoID: 0, Result: "OK"},
		{ProtoID: 1, Result: "INVALID_TRANSACTION"},
	}
	expectedTransaction = &types.Transaction{
		Hash:       hexutils.SafeAddHexPrefix(hex.EncodeToString(hash)),
		Operations: operations,
	}
	operations = []*types.Operation{
		{
			Index:   0,
			Type:    "CRYPTOTRANSFER",
			Status:  "OK",
			Account: firstAccount,
			Amount:  &amount,
		},
		{
			Index:   1,
			Type:    "CRYPTODELETE",
			Status:  "OK",
			Account: secondAccount,
			Amount:  &amount,
		},
	}
	tRepoTypes = map[int]string{
		12: "CRYPTODELETE",
		14: "CRYPTOTRANSFER",
	}
	tRepoStatuses = map[int]string{
		0: "OK",
		1: "INVALID_TRANSACTION",
	}
	tRepoTypesAsArray = []string{"CRYPTODELETE", "CRYPTOTRANSFER"}
)

func TestShouldSuccessFindBetween(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	defer tr.dbClient.DB().Close()

	mock.ExpectQuery(regexp.QuoteMeta(whereClauseBetweenConsensus)).
		WithArgs(int64(1), int64(2)).
		WillReturnRows(willReturnRows(transactionColumns, dbTransactions))
	mock.ExpectQuery(regexp.QuoteMeta(whereTimestampsInConsensusTimestamp)).
		WithArgs("1,2").
		WillReturnRows(willReturnRows(cryptoTransferColumns, dbCryptoTransfers))
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionTypes)).
		WillReturnRows(willReturnRows(transactionTypeColumns, dbTransactionTypes))
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionResults)).
		WillReturnRows(willReturnRows(transactionResultColumns, dbTransactionResults))

	// when
	result, err := tr.FindBetween(1, 2)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, err)
	assert.Equal(t, []*types.Transaction{expectedTransaction}, result)
}

func TestShouldFailFindBetweenNoTypes(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	defer tr.dbClient.DB().Close()

	mock.ExpectQuery(regexp.QuoteMeta(whereClauseBetweenConsensus)).
		WithArgs(int64(1), int64(2)).
		WillReturnRows(willReturnRows(transactionColumns, dbTransactions))
	mock.ExpectQuery(regexp.QuoteMeta(whereTimestampsInConsensusTimestamp)).
		WithArgs("1,2").
		WillReturnRows(willReturnRows(cryptoTransferColumns, dbCryptoTransfers))
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionTypes)).
		WillReturnRows(willReturnRows(transactionTypeColumns))
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionResults)).
		WillReturnRows(willReturnRows(transactionResultColumns))

	// when
	result, err := tr.FindBetween(1, 2)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, result)
	assert.IsType(t, rTypes.Error{}, *err)
}

func TestShouldFailFindBetweenEndBeforeStart(t *testing.T) {
	// given
	tr, _ := setupRepository(t)
	defer tr.dbClient.DB().Close()

	// when
	result, err := tr.FindBetween(2, 1)

	// then
	assert.Nil(t, result)
	assert.Equal(t, errors.Errors[errors.StartMustNotBeAfterEnd], err)
}

func TestShouldSuccessFindHashInBlock(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	defer tr.dbClient.DB().Close()

	mock.ExpectQuery(regexp.QuoteMeta(whereTransactionsByHashAndConsensusTimestamps)).
		WithArgs(hash, int64(1), int64(2)).
		WillReturnRows(willReturnRows(transactionColumns, dbTransactions))
	rows := willReturnRows(cryptoTransferColumns, dbCryptoTransfers)
	mock.ExpectQuery(regexp.QuoteMeta(whereTimestampsInConsensusTimestamp)).
		WithArgs("1,2").
		WillReturnRows(rows)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionTypes)).
		WillReturnRows(willReturnRows(transactionTypeColumns, dbTransactionTypes))
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionResults)).
		WillReturnRows(willReturnRows(transactionResultColumns, dbTransactionResults))

	// when
	result, err := tr.FindByHashInBlock(hashString, 1, 2)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())

	assert.Nil(t, err)
	assert.Equal(t, expectedTransaction, result)
}

func TestShouldFailFindHashInBlockNoTypes(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	defer tr.dbClient.DB().Close()

	mock.ExpectQuery(regexp.QuoteMeta(whereTransactionsByHashAndConsensusTimestamps)).
		WithArgs(hash, int64(1), int64(2)).
		WillReturnRows(willReturnRows(transactionColumns, dbTransactions))
	rows := willReturnRows(cryptoTransferColumns, dbCryptoTransfers)
	mock.ExpectQuery(regexp.QuoteMeta(whereTimestampsInConsensusTimestamp)).
		WithArgs("1,2").
		WillReturnRows(rows)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionTypes)).
		WillReturnRows(willReturnRows(transactionTypeColumns))
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionResults)).
		WillReturnRows(willReturnRows(transactionResultColumns))

	// when
	result, err := tr.FindByHashInBlock(hashString, 1, 2)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())

	assert.Nil(t, result)
	assert.IsType(t, rTypes.Error{}, *err)
}

func TestShouldFailFindHashInBlockNoReturnTransactions(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	defer tr.dbClient.DB().Close()

	mock.ExpectQuery(regexp.QuoteMeta(whereTransactionsByHashAndConsensusTimestamps)).
		WithArgs(hash, int64(1), int64(2)).
		WillReturnRows(willReturnRows(transactionColumns))

	// when
	result, err := tr.FindByHashInBlock(hashString, 1, 2)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())

	assert.Nil(t, result)
	assert.Equal(t, errors.Errors[errors.TransactionNotFound], err)
}

func TestShouldFailFindHashInBlockInvalidHash(t *testing.T) {
	// given
	tr, _ := setupRepository(t)
	defer tr.dbClient.DB().Close()

	// when
	result, err := tr.FindByHashInBlock("asd", 1, 2)

	// then
	assert.Nil(t, result)
	assert.Equal(t, errors.Errors[errors.InvalidTransactionIdentifier], err)
}

func TestShouldFailConstructTransactionDueToNoStatuses(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	defer tr.dbClient.DB().Close()

	rows := willReturnRows(cryptoTransferColumns, dbCryptoTransfers)
	mock.ExpectQuery(regexp.QuoteMeta(whereTimestampsInConsensusTimestamp)).
		WithArgs("1,2").
		WillReturnRows(rows)
	rows = willReturnRows(transactionTypeColumns, dbTransactionTypes)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionTypes)).
		WillReturnRows(rows)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionResults)).
		WillReturnRows(willReturnRows(transactionResultColumns))

	// when
	result, err := tr.constructTransaction(dbTransactions)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())

	assert.IsType(t, rTypes.Error{}, *err)
	assert.Nil(t, result)
}

func TestShouldSuccessConstructTransaction(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	defer tr.dbClient.DB().Close()

	rows := willReturnRows(cryptoTransferColumns, dbCryptoTransfers)
	mock.ExpectQuery(regexp.QuoteMeta(whereTimestampsInConsensusTimestamp)).
		WithArgs("1,2").
		WillReturnRows(rows)
	rows = willReturnRows(transactionTypeColumns, dbTransactionTypes)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionTypes)).
		WillReturnRows(rows)
	rows = willReturnRows(transactionResultColumns, dbTransactionResults)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionResults)).
		WillReturnRows(rows)

	// when
	result, err := tr.constructTransaction(dbTransactions)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())

	assert.Nil(t, err)
	assert.Equal(t, expectedTransaction, result)
}

func TestShouldSuccessConstructionOperations(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	defer tr.dbClient.DB().Close()

	rows := willReturnRows(transactionTypeColumns, dbTransactionTypes)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionTypes)).
		WillReturnRows(rows)
	rows = willReturnRows(transactionResultColumns, dbTransactionResults)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionResults)).
		WillReturnRows(rows)

	// when
	result, err := tr.constructOperations(dbCryptoTransfers, mapTransactions)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, err)

	assert.Equal(t, operations, result)
}

func TestShouldFailConstructionOperationsInvalidCryptoTransferEntityId(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	defer tr.dbClient.DB().Close()

	rows := willReturnRows(transactionTypeColumns, dbTransactionTypes)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionTypes)).
		WillReturnRows(rows)
	rows = willReturnRows(transactionResultColumns, dbTransactionResults)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionResults)).
		WillReturnRows(rows)

	invalidCryptoTransfers := []common.CryptoTransfer{
		{EntityID: -1, ConsensusTimestamp: 1, Amount: 1},
		{EntityID: -2, ConsensusTimestamp: 2, Amount: 1},
	}

	// when
	result, err := tr.constructOperations(invalidCryptoTransfers, mapTransactions)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, result)

	assert.IsType(t, rTypes.Error{}, *err)
}

func TestShouldFailConstructionOperationsDueToStatusesError(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	defer tr.dbClient.DB().Close()

	rows := willReturnRows(transactionTypeColumns, dbTransactionTypes)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionTypes)).
		WillReturnRows(rows)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionResults)).
		WillReturnRows(willReturnRows(transactionResultColumns))

	// when
	result, err := tr.constructOperations(dbCryptoTransfers, nil)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, result)
	assert.IsType(t, rTypes.Error{}, *err)
}

func TestShouldFailConstructionOperationsDueToTypesError(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	defer tr.dbClient.DB().Close()

	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionTypes)).
		WillReturnRows(willReturnRows(transactionTypeColumns))
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionResults)).
		WillReturnRows(willReturnRows(transactionResultColumns))

	// when
	result, err := tr.constructOperations(dbCryptoTransfers, nil)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, result)
	assert.IsType(t, rTypes.Error{}, *err)
}

func TestShouldSuccessFindCryptoTransfers(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	defer tr.dbClient.DB().Close()

	rows := willReturnRows(cryptoTransferColumns, dbCryptoTransfers)
	mock.ExpectQuery(regexp.QuoteMeta(whereTimestampsInConsensusTimestamp)).
		WithArgs(strconv.FormatInt(consensusTimestamp, 10)).
		WillReturnRows(rows)

	// when
	result := tr.findCryptoTransfers([]int64{consensusTimestamp})

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Equal(t, dbCryptoTransfers, result)
}

func TestShouldSuccessReturnStatuses(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	defer tr.dbClient.DB().Close()

	rows := willReturnRows(transactionTypeColumns, dbTransactionTypes)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionTypes)).
		WillReturnRows(rows)
	rows = willReturnRows(transactionResultColumns, dbTransactionResults)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionResults)).
		WillReturnRows(rows)

	// when
	result, err := tr.Statuses()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, err)

	assert.Equal(t, tRepoStatuses, result)
}

func TestShouldFailReturnStatuses(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	defer tr.dbClient.DB().Close()

	rows := willReturnRows(transactionTypeColumns, dbTransactionTypes)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionTypes)).
		WillReturnRows(rows)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionResults)).
		WillReturnRows(willReturnRows(transactionResultColumns))

	// when
	result, err := tr.Statuses()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.IsType(t, rTypes.Error{}, *err)

	assert.Nil(t, result)
}

func TestShouldFailReturnTypes(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	defer tr.dbClient.DB().Close()

	rows := willReturnRows(transactionTypeColumns, dbTransactionTypes)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionTypes)).
		WillReturnRows(rows)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionResults)).
		WillReturnRows(willReturnRows(transactionResultColumns))

	// when
	result, err := tr.Types()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.IsType(t, rTypes.Error{}, *err)

	assert.Nil(t, result)
}

func TestShouldFailReturnTypesAsArray(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	defer tr.dbClient.DB().Close()

	rows := willReturnRows(transactionTypeColumns, dbTransactionTypes)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionTypes)).
		WillReturnRows(rows)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionResults)).
		WillReturnRows(willReturnRows(transactionResultColumns))

	// when
	result, err := tr.TypesAsArray()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.IsType(t, rTypes.Error{}, *err)

	assert.Nil(t, result)
}

func TestShouldSuccessReturnTypesAsArray(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	defer tr.dbClient.DB().Close()

	rows := willReturnRows(transactionTypeColumns, dbTransactionTypes)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionTypes)).
		WillReturnRows(rows)
	rows = willReturnRows(transactionResultColumns, dbTransactionResults)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionResults)).
		WillReturnRows(rows)

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
	defer tr.dbClient.DB().Close()

	rows := willReturnRows(transactionTypeColumns, dbTransactionTypes)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionTypes)).
		WillReturnRows(rows)
	rows = willReturnRows(transactionResultColumns, dbTransactionResults)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionResults)).
		WillReturnRows(rows)

	// when
	result, err := tr.Types()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, err)

	assert.Equal(t, tRepoTypes, result)
}

func TestShouldSuccessSaveTransactionTypesAndStatuses(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	defer tr.dbClient.DB().Close()

	rows := willReturnRows(transactionTypeColumns, dbTransactionTypes)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionTypes)).
		WillReturnRows(rows)
	rows = willReturnRows(transactionResultColumns, dbTransactionResults)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionResults)).
		WillReturnRows(rows)

	// when
	result := tr.retrieveTransactionTypesAndStatuses()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, result)

	assert.Equal(t, tRepoStatuses, tr.statuses)
	assert.Equal(t, tRepoTypes, tr.types)
}

func TestShouldFailReturnTransactionTypesAndStatusesDueToNoResults(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	defer tr.dbClient.DB().Close()

	rows := willReturnRows(transactionTypeColumns, dbTransactionTypes)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionTypes)).
		WillReturnRows(rows)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionResults)).
		WillReturnRows(willReturnRows(transactionResultColumns))

	// when
	result := tr.retrieveTransactionTypesAndStatuses()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Equal(t, errors.Errors[errors.OperationStatusesNotFound], result)
}

func TestShouldFailReturnTransactionTypesAndStatusesDueToNoTypes(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	defer tr.dbClient.DB().Close()

	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionTypes)).
		WillReturnRows(willReturnRows(transactionTypeColumns))
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionResults)).
		WillReturnRows(willReturnRows(transactionResultColumns))

	// when
	result := tr.retrieveTransactionTypesAndStatuses()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Equal(t, errors.Errors[errors.OperationTypesNotFound], result)
}

func TestShouldSuccessReturnTransactionResults(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	defer tr.dbClient.DB().Close()

	rows := willReturnRows(transactionResultColumns, dbTransactionResults)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionResults)).
		WillReturnRows(rows)

	// when
	result := tr.retrieveTransactionResults()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Equal(t, dbTransactionResults, result)
}

func TestShouldSuccessReturnTransactionTypes(t *testing.T) {
	// given
	tr, mock := setupRepository(t)
	defer tr.dbClient.DB().Close()

	rows := willReturnRows(transactionTypeColumns, dbTransactionTypes)
	mock.ExpectQuery(regexp.QuoteMeta(selectTransactionTypes)).
		WillReturnRows(rows)

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
	expected := errors.Errors[errors.InternalServerError]

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
