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

package block

import (
	"database/sql/driver"
	"regexp"
	"testing"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/mocks"
	"github.com/jinzhu/gorm"
	"github.com/stretchr/testify/assert"
)

var (
	countColumns                                    = []string{"count"}
	expectedSkippedRecordFiles                      = int64(123)
	expectedLatestConsensusTimeStampAccountBalances = int64(42)
	index                                           = int64(1)
	selectRecordFileColumns                         = []string{"hash", "consensus_start", "consensus_end", "index", "prev_hash"}
	recordFileColumns                               = mocks.GetFieldsNamesToSnakeCase(recordFile{})
	dbRecordFile                                    = &recordFile{
		ConsensusStart: 1,
		ConsensusEnd:   2,
		Hash:           "0x12345",
		Index:          index + expectedSkippedRecordFiles,
		PrevHash:       "0x23456",
	}
	expectedBlock = &types.Block{
		Index:               index,
		Hash:                dbRecordFile.Hash,
		ConsensusStartNanos: dbRecordFile.ConsensusStart,
		ConsensusEndNanos:   dbRecordFile.ConsensusEnd,
		ParentHash:          dbRecordFile.PrevHash,
	}
)

func TestShouldSuccessFindByIndex(t *testing.T) {
	// given
	br, mock := setupRepository(t)
	defer br.dbClient.DB().Close()

	mock.ExpectQuery(regexp.QuoteMeta(selectLatestConsensusTimestampAccountBalances)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(expectedLatestConsensusTimeStampAccountBalances))
	mock.ExpectQuery(regexp.QuoteMeta(selectSkippedRecordFilesCount)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(expectedSkippedRecordFiles))
	mock.ExpectQuery(regexp.QuoteMeta(selectRecordFileByIndex)).
		WithArgs(dbRecordFile.Index).
		WillReturnRows(sqlmock.NewRows(recordFileColumns).
			AddRow(mocks.GetFieldsValuesAsDriverValue(dbRecordFile)...))

	// when
	result, err := br.FindByIndex(index)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, err)
	assert.Equal(t, expectedBlock, result)
}

func TestShouldFailFindByIndexNoRecordFile(t *testing.T) {
	// given
	br, mock := setupRepository(t)
	defer br.dbClient.DB().Close()

	mock.ExpectQuery(regexp.QuoteMeta(selectLatestConsensusTimestampAccountBalances)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(expectedLatestConsensusTimeStampAccountBalances))
	mock.ExpectQuery(regexp.QuoteMeta(selectSkippedRecordFilesCount)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(expectedSkippedRecordFiles))
	mock.ExpectQuery(regexp.QuoteMeta(selectRecordFileByIndex)).
		WillReturnError(gorm.ErrRecordNotFound)

	// when
	result, err := br.FindByIndex(0)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, result)
	assert.Equal(t, errors.Errors[errors.BlockNotFound], err)
}

func TestShouldFailFindByIndexNoAccountBalances(t *testing.T) {
	// given
	br, mock := setupRepository(t)
	defer br.dbClient.DB().Close()

	mock.ExpectQuery(regexp.QuoteMeta(selectLatestConsensusTimestampAccountBalances)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(0))

	// when
	result, err := br.FindByIndex(1)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.NotNil(t, err)
	assert.Nil(t, result)
}

func TestShouldFailFindByHashNoAccountBalances(t *testing.T) {
	// given
	br, mock := setupRepository(t)
	defer br.dbClient.DB().Close()

	mock.ExpectQuery(regexp.QuoteMeta(selectByHashWithIndex)).
		WithArgs(dbRecordFile.Hash).
		WillReturnRows(sqlmock.NewRows(recordFileColumns).AddRow(mocks.GetFieldsValuesAsDriverValue(dbRecordFile)...))
	mock.ExpectQuery(regexp.QuoteMeta(selectLatestConsensusTimestampAccountBalances)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(0))

	// when
	result, err := br.FindByHash(dbRecordFile.Hash)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.NotNil(t, err)
	assert.Nil(t, result)
}

func TestShouldSuccessFindByHash(t *testing.T) {
	// given
	br, mock := setupRepository(t)
	defer br.dbClient.DB().Close()

	mock.ExpectQuery(regexp.QuoteMeta(selectByHashWithIndex)).
		WithArgs(dbRecordFile.Hash).
		WillReturnRows(sqlmock.NewRows(recordFileColumns).AddRow(mocks.GetFieldsValuesAsDriverValue(dbRecordFile)...))
	mock.ExpectQuery(regexp.QuoteMeta(selectLatestConsensusTimestampAccountBalances)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(expectedLatestConsensusTimeStampAccountBalances))
	mock.ExpectQuery(regexp.QuoteMeta(selectSkippedRecordFilesCount)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(expectedSkippedRecordFiles))

	// when
	result, err := br.FindByHash(dbRecordFile.Hash)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, err)
	assert.Equal(t, expectedBlock, result)
}

func TestShouldFailFindByIdentifierNoAccountBalances(t *testing.T) {
	// given
	br, mock := setupRepository(t)
	defer br.dbClient.DB().Close()

	mock.ExpectQuery(regexp.QuoteMeta(selectByHashWithIndex)).
		WithArgs(dbRecordFile.Hash).
		WillReturnRows(sqlmock.NewRows(recordFileColumns).AddRow(mocks.GetFieldsValuesAsDriverValue(dbRecordFile)...))
	mock.ExpectQuery(regexp.QuoteMeta(selectLatestConsensusTimestampAccountBalances)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(0))

	// when
	result, err := br.FindByIdentifier(1, dbRecordFile.Hash)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.NotNil(t, err)
	assert.Nil(t, result)
}

func TestShouldSuccessFindByIdentifier(t *testing.T) {
	// given
	br, mock := setupRepository(t)
	defer br.dbClient.DB().Close()

	mock.ExpectQuery(regexp.QuoteMeta(selectByHashWithIndex)).
		WithArgs(dbRecordFile.Hash).
		WillReturnRows(sqlmock.NewRows(recordFileColumns).AddRow(mocks.GetFieldsValuesAsDriverValue(dbRecordFile)...))
	mock.ExpectQuery(regexp.QuoteMeta(selectLatestConsensusTimestampAccountBalances)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(expectedLatestConsensusTimeStampAccountBalances))
	mock.ExpectQuery(regexp.QuoteMeta(selectSkippedRecordFilesCount)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(expectedSkippedRecordFiles))

	// when
	result, err := br.FindByIdentifier(index, dbRecordFile.Hash)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Equal(t, expectedBlock, result)
	assert.Nil(t, err)
}

func TestShouldFailFindByIdentifierMismatchIndices(t *testing.T) {
	// given
	mismatchingRecordFileIndex := &recordFile{
		Index: dbRecordFile.Index + 1,
		Hash:  "0x12345",
	}
	br, mock := setupRepository(t)
	defer br.dbClient.DB().Close()

	mock.ExpectQuery(regexp.QuoteMeta(selectByHashWithIndex)).
		WithArgs(dbRecordFile.Hash).
		WillReturnRows(sqlmock.NewRows(recordFileColumns).
			AddRow(mocks.GetFieldsValuesAsDriverValue(mismatchingRecordFileIndex)...))
	mock.ExpectQuery(regexp.QuoteMeta(selectLatestConsensusTimestampAccountBalances)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(expectedLatestConsensusTimeStampAccountBalances))
	mock.ExpectQuery(regexp.QuoteMeta(selectSkippedRecordFilesCount)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(expectedSkippedRecordFiles))

	// when
	result, err := br.FindByIdentifier(index, dbRecordFile.Hash)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Equal(t, errors.Errors[errors.BlockNotFound], err)
	assert.Nil(t, result)
}

func TestShouldSuccessRetrieveGenesis(t *testing.T) {
	// given
	block := &types.Block{
		Index:               0,
		Hash:                dbRecordFile.Hash,
		ConsensusStartNanos: dbRecordFile.ConsensusStart,
		ConsensusEndNanos:   dbRecordFile.ConsensusEnd,
	}

	br, mock := setupRepository(t)
	defer br.dbClient.DB().Close()

	mock.ExpectQuery(regexp.QuoteMeta(selectLatestConsensusTimestampAccountBalances)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(expectedLatestConsensusTimeStampAccountBalances))
	mock.ExpectQuery(regexp.QuoteMeta(selectSkippedRecordFilesCount)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(expectedSkippedRecordFiles))
	mock.ExpectQuery(regexp.QuoteMeta(selectRecordFileByIndex)).
		WithArgs(expectedSkippedRecordFiles).
		WillReturnRows(sqlmock.NewRows(recordFileColumns).
			AddRow(mocks.GetFieldsValuesAsDriverValue(dbRecordFile)...))

	// when
	result, err := br.RetrieveGenesis()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, err)
	assert.Equal(t, block, result)
}

func TestShouldFailRetrieveGenesisNoRecordFile(t *testing.T) {
	// given
	br, mock := setupRepository(t)
	defer br.dbClient.DB().Close()

	mock.ExpectQuery(regexp.QuoteMeta(selectLatestConsensusTimestampAccountBalances)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(expectedLatestConsensusTimeStampAccountBalances))
	mock.ExpectQuery(regexp.QuoteMeta(selectSkippedRecordFilesCount)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(expectedSkippedRecordFiles))
	mock.ExpectQuery(regexp.QuoteMeta(selectRecordFileByIndex)).
		WillReturnError(gorm.ErrRecordNotFound)

	// when
	result, err := br.RetrieveGenesis()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, result)
	assert.Equal(t, errors.Errors[errors.BlockNotFound], err)
}

func TestShouldFailRetrieveGenesisNoAccountBalances(t *testing.T) {
	// given
	br, mock := setupRepository(t)
	defer br.dbClient.DB().Close()

	mock.ExpectQuery(regexp.QuoteMeta(selectLatestConsensusTimestampAccountBalances)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(0))

	// when
	result, err := br.RetrieveGenesis()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.NotNil(t, err)
	assert.Nil(t, result)
}

func TestShouldSuccessRetrieveLatest(t *testing.T) {
	// given
	dbSelectRecordFile := []driver.Value{
		dbRecordFile.Hash,
		dbRecordFile.ConsensusStart,
		dbRecordFile.ConsensusEnd,
		dbRecordFile.Index,
		dbRecordFile.PrevHash,
	}
	br, mock := setupRepository(t)
	defer br.dbClient.DB().Close()

	mock.ExpectQuery(regexp.QuoteMeta(selectLatestWithIndex)).
		WillReturnRows(sqlmock.NewRows(selectRecordFileColumns).AddRow(dbSelectRecordFile...))
	mock.ExpectQuery(regexp.QuoteMeta(selectLatestConsensusTimestampAccountBalances)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(expectedLatestConsensusTimeStampAccountBalances))
	mock.ExpectQuery(regexp.QuoteMeta(selectSkippedRecordFilesCount)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(expectedSkippedRecordFiles))

	// when
	result, err := br.RetrieveLatest()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Equal(t, expectedBlock, result)
	assert.Nil(t, err)
}

func TestShouldFailRetrieveLatestRecordFileHashIsEmpty(t *testing.T) {
	// given
	dbSelectRecordFile := []driver.Value{
		"",
		dbRecordFile.ConsensusStart,
		dbRecordFile.ConsensusEnd,
		dbRecordFile.Index,
		dbRecordFile.PrevHash,
	}
	br, mock := setupRepository(t)
	defer br.dbClient.DB().Close()

	mock.ExpectQuery(regexp.QuoteMeta(selectLatestWithIndex)).
		WillReturnRows(sqlmock.NewRows(selectRecordFileColumns).AddRow(dbSelectRecordFile...))

	// when
	result, err := br.RetrieveLatest()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Equal(t, errors.Errors[errors.BlockNotFound], err)
	assert.Nil(t, result)
}

func TestShouldFailRetrieveLatestRecordFileNotFound(t *testing.T) {
	// given
	br, mock := setupRepository(t)
	defer br.dbClient.DB().Close()

	mock.ExpectQuery(regexp.QuoteMeta(selectLatestWithIndex)).
		WillReturnError(gorm.ErrRecordNotFound)

	// when
	result, err := br.RetrieveLatest()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Equal(t, errors.Errors[errors.BlockNotFound], err)
	assert.Nil(t, result)
}

func TestShouldFailRetrieveLatestNoAccountBalances(t *testing.T) {
	// given
	dbSelectRecordFile := []driver.Value{
		dbRecordFile.Hash,
		dbRecordFile.ConsensusStart,
		dbRecordFile.ConsensusEnd,
		dbRecordFile.Index,
		dbRecordFile.PrevHash,
	}
	br, mock := setupRepository(t)
	defer br.dbClient.DB().Close()

	mock.ExpectQuery(regexp.QuoteMeta(selectLatestWithIndex)).
		WillReturnRows(sqlmock.NewRows(selectRecordFileColumns).AddRow(dbSelectRecordFile...))
	mock.ExpectQuery(regexp.QuoteMeta(selectLatestConsensusTimestampAccountBalances)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(0))

	// when
	result, err := br.RetrieveLatest()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.NotNil(t, err)
	assert.Nil(t, result)
}

func TestShouldFailFindRecordFileByHashNegativeBlockIndex(t *testing.T) {
	// given
	invalidRecordFile := &recordFile{
		Index: -1,
	}

	br, mock := setupRepository(t)
	defer br.dbClient.DB().Close()

	mock.ExpectQuery(regexp.QuoteMeta(selectByHashWithIndex)).
		WithArgs(dbRecordFile.Hash).
		WillReturnRows(sqlmock.NewRows(recordFileColumns).AddRow(mocks.GetFieldsValuesAsDriverValue(invalidRecordFile)...))

	// when
	result, err := br.findRecordFileByHash(dbRecordFile.Hash)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Equal(t, errors.Errors[errors.BlockNotFound], err)
	assert.Nil(t, result)
}

func TestShouldFailFindRecordFileByHashFileHashIsEmpty(t *testing.T) {
	// given
	invalidRecordFile := &recordFile{}

	br, mock := setupRepository(t)
	defer br.dbClient.DB().Close()

	mock.ExpectQuery(regexp.QuoteMeta(selectByHashWithIndex)).
		WithArgs(dbRecordFile.Hash).
		WillReturnRows(sqlmock.NewRows(recordFileColumns).AddRow(mocks.GetFieldsValuesAsDriverValue(invalidRecordFile)...))

	// when
	result, err := br.findRecordFileByHash(dbRecordFile.Hash)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Equal(t, errors.Errors[errors.BlockNotFound], err)
	assert.Nil(t, result)
}

func TestShouldSuccessFindRecordFileByHash(t *testing.T) {
	// given
	br, mock := setupRepository(t)
	defer br.dbClient.DB().Close()

	mock.ExpectQuery(regexp.QuoteMeta(selectByHashWithIndex)).
		WithArgs(dbRecordFile.Hash).
		WillReturnRows(sqlmock.NewRows(recordFileColumns).AddRow(mocks.GetFieldsValuesAsDriverValue(dbRecordFile)...))

	// when
	result, err := br.findRecordFileByHash(dbRecordFile.Hash)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, err)
	assert.Equal(t, dbRecordFile, result)
}

func TestShouldFailFindRecordFileByHashNoRecordFound(t *testing.T) {
	// given
	br, mock := setupRepository(t)
	defer br.dbClient.DB().Close()

	mock.ExpectQuery(regexp.QuoteMeta(selectByHashWithIndex)).
		WithArgs(dbRecordFile.Hash).
		WillReturnError(gorm.ErrRecordNotFound)

	// when
	result, err := br.findRecordFileByHash(dbRecordFile.Hash)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, result)
	assert.Equal(t, errors.Errors[errors.BlockNotFound], err)
}

func TestShouldSuccessConstructBlockResponse(t *testing.T) {
	// given
	rf := &recordFile{
		Index:          dbRecordFile.Index,
		ConsensusStart: 1,
		ConsensusEnd:   2,
		Hash:           "0x123",
		PrevHash:       "0x234",
	}
	expectedBlock := &types.Block{
		ConsensusStartNanos: 1,
		ConsensusEndNanos:   2,
		Hash:                "0x123",
		Index:               index,
		ParentHash:          "0x234",
		ParentIndex:         index - 1,
	}

	br, mock := setupRepository(t)
	mock.ExpectQuery(regexp.QuoteMeta(selectLatestConsensusTimestampAccountBalances)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(expectedLatestConsensusTimeStampAccountBalances))
	mock.ExpectQuery(regexp.QuoteMeta(selectSkippedRecordFilesCount)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(expectedSkippedRecordFiles))

	// when
	result, err := br.constructBlockResponse(rf)

	// then
	assert.Nil(t, err)
	assert.Equal(t, expectedBlock, result)
}

func TestShouldSuccessConstructBlockResponseQueryingFirstBlock(t *testing.T) {
	// given
	blockIndex := int64(0)
	rf := &recordFile{
		Index:          expectedSkippedRecordFiles,
		Hash:           "0x123",
		PrevHash:       "0x234",
		ConsensusStart: 1,
		ConsensusEnd:   2,
	}
	expectedBlock := &types.Block{
		Index:               blockIndex,
		Hash:                "0x123",
		ConsensusStartNanos: 1,
		ConsensusEndNanos:   2,
		ParentIndex:         blockIndex,
		ParentHash:          "0x123",
	}

	br, mock := setupRepository(t)
	mock.ExpectQuery(regexp.QuoteMeta(selectLatestConsensusTimestampAccountBalances)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(expectedLatestConsensusTimeStampAccountBalances))
	mock.ExpectQuery(regexp.QuoteMeta(selectSkippedRecordFilesCount)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(expectedSkippedRecordFiles))

	// when
	result, err := br.constructBlockResponse(rf)

	// then
	assert.Nil(t, err)
	assert.Equal(t, expectedBlock, result)
}

func TestShouldSuccessGetRecordFilesStartingIndexRepeatedly(t *testing.T) {
	// given
	br, mock := setupRepository(t)
	defer br.dbClient.DB().Close()

	mock.ExpectQuery(regexp.QuoteMeta(selectLatestConsensusTimestampAccountBalances)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(expectedLatestConsensusTimeStampAccountBalances))
	mock.ExpectQuery(regexp.QuoteMeta(selectSkippedRecordFilesCount)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(expectedSkippedRecordFiles))

	// when
	br.getRecordFilesStartingIndex()
	result, err := br.getRecordFilesStartingIndex()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Equal(t, expectedSkippedRecordFiles, result)
	assert.Equal(t, expectedSkippedRecordFiles, *br.recordFileStartingIndex)
	assert.Nil(t, err)
}

func TestShouldSuccessGetRecordFilesStartingIndex(t *testing.T) {
	// given
	br, mock := setupRepository(t)
	defer br.dbClient.DB().Close()

	mock.ExpectQuery(regexp.QuoteMeta(selectLatestConsensusTimestampAccountBalances)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(expectedLatestConsensusTimeStampAccountBalances))

	mock.ExpectQuery(regexp.QuoteMeta(selectSkippedRecordFilesCount)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(expectedSkippedRecordFiles))

	// when
	result, err := br.getRecordFilesStartingIndex()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Equal(t, expectedSkippedRecordFiles, result)
	assert.Equal(t, expectedSkippedRecordFiles, *br.recordFileStartingIndex)
	assert.Nil(t, err)
}

func TestShouldFailGetRecordFilesStartingIndex(t *testing.T) {
	// given
	br, mock := setupRepository(t)
	defer br.dbClient.DB().Close()
	mock.ExpectQuery(regexp.QuoteMeta(selectLatestConsensusTimestampAccountBalances)).
		WillReturnRows(sqlmock.NewRows(countColumns).AddRow(0))

	// when
	result, err := br.getRecordFilesStartingIndex()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Zero(t, result)
	assert.NotNil(t, err)
}

func TestShouldSuccessReturnRecordFileTableName(t *testing.T) {
	assert.Equal(t, tableNameRecordFile, recordFile{}.TableName())
}

func TestShouldSuccessReturnRepository(t *testing.T) {
	// given
	gormDbClient, _ := mocks.DatabaseMock(t)

	// when
	result := NewBlockRepository(gormDbClient)

	// then
	assert.IsType(t, &BlockRepository{}, result)
	assert.Equal(t, result.dbClient, gormDbClient)
}

func setupRepository(t *testing.T) (*BlockRepository, sqlmock.Sqlmock) {
	gormDbClient, mock := mocks.DatabaseMock(t)

	aber := NewBlockRepository(gormDbClient)
	return aber, mock
}
