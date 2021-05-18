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
	"testing"

	"github.com/DATA-DOG/go-sqlmock"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/mocks"
	"github.com/stretchr/testify/assert"
	"gorm.io/gorm"
)

const (
	indexZero = int64(0)
)

var (
	recordFileColumns       = mocks.GetFieldsNamesToSnakeCase(recordFile{})
	selectRecordFileColumns = []string{"hash", "consensus_start", "consensus_end", "index", "prev_hash"}

	expectedBlock = &types.Block{
		ConsensusStartNanos: dbRecordFile.ConsensusStart,
		ConsensusEndNanos:   dbRecordFile.ConsensusEnd,
		Hash:                dbRecordFile.Hash,
		Index:               index,
		ParentHash:          dbRecordFile.PrevHash,
		ParentIndex:         index - 1,
	}
	expectedGenesisBlock = &types.Block{
		ConsensusStartNanos: dbGenesis.ConsensusStart,
		ConsensusEndNanos:   dbGenesis.ConsensusEnd,
		Hash:                dbGenesis.Hash,
		Index:               0,
		ParentHash:          dbGenesis.Hash,
		ParentIndex:         0,
	}

	index     = int64(1)
	dbGenesis = &recordFile{
		ConsensusStart: 100,
		ConsensusEnd:   199,
		Hash:           "0x12345",
		Index:          int64(7),
		PrevHash:       "0x23456",
	}
	dbRecordFile = &recordFile{
		ConsensusStart: 200,
		ConsensusEnd:   299,
		Hash:           "0x200200",
		Index:          dbGenesis.Index + 1,
		PrevHash:       "0x12345",
	}
)

func TestShouldSuccessFindByIndex(t *testing.T) {
	var tests = []struct {
		name     string
		index    int64
		expected *types.Block
	}{
		{
			name:     "GenesisBlock",
			index:    0,
			expected: expectedGenesisBlock,
		},
		{
			name:     "SecondBlock",
			index:    1,
			expected: expectedBlock,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// given
			br, mock := setupRepository(t)
			mock.ExpectQuery(selectGenesis).
				WillReturnRows(sqlmock.NewRows(recordFileColumns).
					AddRow(mocks.GetFieldsValuesAsDriverValue(dbGenesis)...))
			if tt.index != 0 {
				mock.ExpectQuery(selectRecordFileByIndex).
					WithArgs(dbRecordFile.Index).
					WillReturnRows(sqlmock.NewRows(recordFileColumns).
						AddRow(mocks.GetFieldsValuesAsDriverValue(dbRecordFile)...))
			}

			// when
			result, err := br.FindByIndex(tt.index)

			// then
			assert.NoError(t, mock.ExpectationsWereMet())
			assert.Nil(t, err)
			assert.Equal(t, tt.expected, result)
		})
	}
}

func TestShouldSuccessFindByIndexNegativeIndex(t *testing.T) {
	// given
	br, mock := setupRepository(t)

	// when
	result, err := br.FindByIndex(-1)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, result)
	assert.Equal(t, errors.ErrInvalidArgument, err)
}

func TestShouldFailFindByIndexNoRecordFile(t *testing.T) {
	var tests = []struct {
		name     string
		dbErr    error
		expected *rTypes.Error
	}{
		{
			name:     "DbRecordNotFound",
			dbErr:    gorm.ErrRecordNotFound,
			expected: errors.ErrBlockNotFound,
		},
		{
			name:     "OtherDbError",
			dbErr:    gorm.ErrInvalidTransaction,
			expected: errors.ErrDatabaseError,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// given
			br, mock := setupRepository(t)
			mock.ExpectQuery(selectGenesis).
				WillReturnRows(sqlmock.NewRows(recordFileColumns).
					AddRow(mocks.GetFieldsValuesAsDriverValue(dbGenesis)...))
			mock.ExpectQuery(selectRecordFileByIndex).WillReturnError(tt.dbErr)

			// when
			result, err := br.FindByIndex(1)

			// then
			assert.NoError(t, mock.ExpectationsWereMet())
			assert.Nil(t, result)
			assert.Equal(t, tt.expected, err)
		})
	}
}

func TestShouldFailFindByIndexNoGenesisRecordFile(t *testing.T) {
	var tests = []struct {
		name     string
		dbErr    error
		expected *rTypes.Error
	}{
		{
			name:     "DbRecordNotFound",
			dbErr:    gorm.ErrRecordNotFound,
			expected: errors.ErrNodeIsStarting,
		},
		{
			name:     "OtherDbError",
			dbErr:    gorm.ErrInvalidTransaction,
			expected: errors.ErrDatabaseError,
		},
	}

	for _, tt := range tests {
		// given
		br, mock := setupRepository(t)
		mock.ExpectQuery(selectGenesis).WillReturnError(tt.dbErr)

		// when
		result, err := br.FindByIndex(1)

		// then
		assert.NoError(t, mock.ExpectationsWereMet())
		assert.Nil(t, result)
		assert.Equal(t, tt.expected, err)
	}
}

func TestShouldSuccessFindByHash(t *testing.T) {
	var tests = []struct {
		name     string
		hash     string
		expected *types.Block
	}{
		{
			name:     "GenesisBlock",
			hash:     dbGenesis.Hash,
			expected: expectedGenesisBlock,
		},
		{
			name:     "SecondBlock",
			hash:     dbRecordFile.Hash,
			expected: expectedBlock,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// given
			br, mock := setupRepository(t)
			mock.ExpectQuery(selectGenesis).
				WillReturnRows(sqlmock.NewRows(recordFileColumns).
					AddRow(mocks.GetFieldsValuesAsDriverValue(dbGenesis)...))
			if tt.hash != dbGenesis.Hash {
				mock.ExpectQuery(selectByHashWithIndex).
					WithArgs(dbRecordFile.Hash).
					WillReturnRows(sqlmock.NewRows(recordFileColumns).
						AddRow(mocks.GetFieldsValuesAsDriverValue(dbRecordFile)...))
			}

			// when
			result, err := br.FindByHash(tt.hash)

			// then
			assert.NoError(t, mock.ExpectationsWereMet())
			assert.Nil(t, err)
			assert.Equal(t, tt.expected, result)
		})
	}
}

func TestShouldFailFindByHasEmptyHash(t *testing.T) {
	// given
	br, mock := setupRepository(t)

	// when
	result, err := br.FindByHash("")

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, result)
	assert.Equal(t, errors.ErrInvalidArgument, err)
}

func TestShouldFailFindByHashNoGenesisRecordFile(t *testing.T) {
	var tests = []struct {
		name     string
		dbErr    error
		expected *rTypes.Error
	}{
		{
			name:     "DbRecordNotFound",
			dbErr:    gorm.ErrRecordNotFound,
			expected: errors.ErrNodeIsStarting,
		},
		{
			name:     "OtherDbError",
			dbErr:    gorm.ErrInvalidTransaction,
			expected: errors.ErrDatabaseError,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// given
			br, mock := setupRepository(t)

			mock.ExpectQuery(selectGenesis).WillReturnError(tt.dbErr)

			// when
			result, err := br.FindByHash(dbRecordFile.Hash)

			// then
			assert.NoError(t, mock.ExpectationsWereMet())
			assert.Nil(t, result)
			assert.Equal(t, tt.expected, err)
		})
	}
}

func TestShouldSuccessFindByIdentifier(t *testing.T) {
	var tests = []struct {
		name     string
		hash     string
		index    int64
		expected *types.Block
	}{
		{
			name:     "GenesisBlock",
			hash:     dbGenesis.Hash,
			index:    0,
			expected: expectedGenesisBlock,
		},
		{
			name:     "SecondBlock",
			hash:     dbRecordFile.Hash,
			index:    1,
			expected: expectedBlock,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// given
			br, mock := setupRepository(t)

			mock.ExpectQuery(selectGenesis).
				WillReturnRows(sqlmock.NewRows(recordFileColumns).
					AddRow(mocks.GetFieldsValuesAsDriverValue(dbGenesis)...))
			if tt.index != 0 {
				mock.ExpectQuery(selectByHashWithIndex).
					WithArgs(dbRecordFile.Hash).
					WillReturnRows(sqlmock.NewRows(recordFileColumns).
						AddRow(mocks.GetFieldsValuesAsDriverValue(dbRecordFile)...))
			}

			// when
			result, err := br.FindByIdentifier(tt.index, tt.hash)

			// then
			assert.NoError(t, mock.ExpectationsWereMet())
			assert.Equal(t, tt.expected, result)
			assert.Nil(t, err)
		})
	}
}

func TestShouldFailFindByIdentifierInvalidArgument(t *testing.T) {
	var tests = []struct {
		name  string
		index int64
		hash  string
	}{
		{
			name:  "NegativeIndex",
			index: -1,
			hash:  "0x12345",
		},
		{
			name:  "EmptyHash",
			index: 1,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// given
			br, mock := setupRepository(t)

			// when
			result, err := br.FindByIdentifier(tt.index, tt.hash)

			// then
			assert.NoError(t, mock.ExpectationsWereMet())
			assert.Nil(t, result)
			assert.Equal(t, errors.ErrInvalidArgument, err)
		})
	}
}

func TestShouldFailFindByIdentifierNoGenesisRecordFile(t *testing.T) {
	var tests = []struct {
		name     string
		dbErr    error
		expected *rTypes.Error
	}{
		{
			name:     "DbRecordNotFound",
			dbErr:    gorm.ErrRecordNotFound,
			expected: errors.ErrNodeIsStarting,
		},
		{
			name:     "OtherDbError",
			dbErr:    gorm.ErrInvalidTransaction,
			expected: errors.ErrDatabaseError,
		},
	}

	for _, tt := range tests {
		// given
		br, mock := setupRepository(t)

		mock.ExpectQuery(selectGenesis).WillReturnError(tt.dbErr)

		// when
		result, err := br.FindByIdentifier(1, dbRecordFile.Hash)

		// then
		assert.NoError(t, mock.ExpectationsWereMet())
		assert.Nil(t, result)
		assert.Equal(t, tt.expected, err)
	}
}

func TestShouldFailFindByIdentifierMismatchIndices(t *testing.T) {
	// given
	mismatchingRecordFileIndex := &recordFile{
		Index: dbRecordFile.Index + 1,
		Hash:  "0x12345",
	}
	br, mock := setupRepository(t)

	mock.ExpectQuery(selectGenesis).
		WillReturnRows(sqlmock.NewRows(recordFileColumns).
			AddRow(mocks.GetFieldsValuesAsDriverValue(dbGenesis)...))
	mock.ExpectQuery(selectByHashWithIndex).
		WithArgs(dbRecordFile.Hash).
		WillReturnRows(sqlmock.NewRows(recordFileColumns).
			AddRow(mocks.GetFieldsValuesAsDriverValue(mismatchingRecordFileIndex)...))

	// when
	result, err := br.FindByIdentifier(index, dbRecordFile.Hash)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Equal(t, errors.ErrBlockNotFound, err)
	assert.Nil(t, result)
}

func TestShouldSuccessRetrieveGenesis(t *testing.T) {
	// given
	expected := &types.Block{
		ConsensusStartNanos: dbGenesis.ConsensusStart,
		ConsensusEndNanos:   dbGenesis.ConsensusEnd,
		Hash:                dbGenesis.Hash,
		Index:               indexZero,
		ParentHash:          dbGenesis.Hash,
		ParentIndex:         indexZero,
	}

	br, mock := setupRepository(t)

	mock.ExpectQuery(selectGenesis).
		WillReturnRows(sqlmock.NewRows(recordFileColumns).
			AddRow(mocks.GetFieldsValuesAsDriverValue(dbGenesis)...))

	// when
	result, err := br.RetrieveGenesis()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, err)
	assert.Equal(t, expected, result)
}

func TestShouldFailRetrieveGenesisNoGenesisRecordFile(t *testing.T) {
	var tests = []struct {
		name     string
		dbErr    error
		expected *rTypes.Error
	}{
		{
			name:     "DbRecordNotFound",
			dbErr:    gorm.ErrRecordNotFound,
			expected: errors.ErrNodeIsStarting,
		},
		{
			name:     "OtherDbError",
			dbErr:    gorm.ErrInvalidTransaction,
			expected: errors.ErrDatabaseError,
		},
	}

	for _, tt := range tests {
		// given
		br, mock := setupRepository(t)

		mock.ExpectQuery(selectGenesis).WillReturnError(tt.dbErr)

		// when
		result, err := br.RetrieveGenesis()

		// then
		assert.NoError(t, mock.ExpectationsWereMet())
		assert.Nil(t, result)
		assert.Equal(t, tt.expected, err)
	}
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

	mock.ExpectQuery(selectGenesis).
		WillReturnRows(sqlmock.NewRows(recordFileColumns).
			AddRow(mocks.GetFieldsValuesAsDriverValue(dbGenesis)...))
	mock.ExpectQuery(selectLatestWithIndex).
		WillReturnRows(sqlmock.NewRows(selectRecordFileColumns).AddRow(dbSelectRecordFile...))

	// when
	result, err := br.RetrieveLatest()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Equal(t, expectedBlock, result)
	assert.Nil(t, err)
}

func TestShouldFailRetrieveLatestRecordFileNotFound(t *testing.T) {
	var tests = []struct {
		name     string
		dbErr    error
		expected *rTypes.Error
	}{
		{
			name:     "DbRecordNotFound",
			dbErr:    gorm.ErrRecordNotFound,
			expected: errors.ErrBlockNotFound,
		},
		{
			name:     "OtherDbError",
			dbErr:    gorm.ErrInvalidTransaction,
			expected: errors.ErrDatabaseError,
		},
	}

	for _, tt := range tests {
		// given
		br, mock := setupRepository(t)

		mock.ExpectQuery(selectGenesis).
			WillReturnRows(sqlmock.NewRows(recordFileColumns).
				AddRow(mocks.GetFieldsValuesAsDriverValue(dbGenesis)...))
		mock.ExpectQuery(selectLatestWithIndex).WillReturnError(tt.dbErr)

		// when
		result, err := br.RetrieveLatest()

		// then
		assert.NoError(t, mock.ExpectationsWereMet())
		assert.Nil(t, result)
		assert.Equal(t, tt.expected, err)
	}
}

func TestShouldFailRetrieveLatestNoGenesisRecordFile(t *testing.T) {
	var tests = []struct {
		name     string
		dbErr    error
		expected *rTypes.Error
	}{
		{
			name:     "DbRecordNotFound",
			dbErr:    gorm.ErrRecordNotFound,
			expected: errors.ErrNodeIsStarting,
		},
		{
			name:     "OtherDbError",
			dbErr:    gorm.ErrInvalidTransaction,
			expected: errors.ErrDatabaseError,
		},
	}

	for _, tt := range tests {
		// given
		br, mock := setupRepository(t)

		mock.ExpectQuery(selectGenesis).WillReturnError(tt.dbErr)

		// when
		result, err := br.RetrieveLatest()

		// then
		assert.NoError(t, mock.ExpectationsWereMet())
		assert.Nil(t, result)
		assert.Equal(t, tt.expected, err)
	}
}

func TestShouldSuccessFindRecordFileByHash(t *testing.T) {
	// given
	br, mock := setupRepositoryWithGenesisRecordFile(t, dbGenesis)

	mock.ExpectQuery(selectByHashWithIndex).
		WithArgs(dbRecordFile.Hash).
		WillReturnRows(sqlmock.NewRows(recordFileColumns).AddRow(mocks.GetFieldsValuesAsDriverValue(dbRecordFile)...))

	// when
	result, err := br.findBlockByHash(dbRecordFile.Hash)

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Nil(t, err)
	assert.Equal(t, expectedBlock, result)
}

func TestShouldFailFindRecordFileByHashNoRecordFound(t *testing.T) {
	var tests = []struct {
		name     string
		dbErr    error
		expected *rTypes.Error
	}{
		{
			name:     "DbRecordNotFound",
			dbErr:    gorm.ErrRecordNotFound,
			expected: errors.ErrBlockNotFound,
		},
		{
			name:     "OtherDbError",
			dbErr:    gorm.ErrInvalidTransaction,
			expected: errors.ErrDatabaseError,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// given
			br, mock := setupRepositoryWithGenesisRecordFile(t, dbGenesis)

			mock.ExpectQuery(selectByHashWithIndex).WithArgs(dbRecordFile.Hash).WillReturnError(tt.dbErr)

			// when
			result, err := br.findBlockByHash(dbRecordFile.Hash)

			// then
			assert.NoError(t, mock.ExpectationsWereMet())
			assert.Nil(t, result)
			assert.Equal(t, tt.expected, err)
		})
	}
}

func TestShouldSuccessGetGenesisRecordFileRepeatedly(t *testing.T) {
	// given
	br, mock := setupRepository(t)

	mock.ExpectQuery(selectGenesis).
		WillReturnRows(sqlmock.NewRows(recordFileColumns).
			AddRow(mocks.GetFieldsValuesAsDriverValue(dbGenesis)...))

	// when
	br.getGenesisRecordFile()
	result, err := br.getGenesisRecordFile()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Equal(t, dbGenesis, result)
	assert.Equal(t, dbGenesis, br.genesisRecordFile)
	assert.Equal(t, dbGenesis.Index, br.genesisRecordFileIndex)
	assert.Nil(t, err)
}

func TestShouldSuccessGetGenesisRecordFile(t *testing.T) {
	// given
	br, mock := setupRepository(t)

	mock.ExpectQuery(selectGenesis).
		WillReturnRows(sqlmock.NewRows(recordFileColumns).
			AddRow(mocks.GetFieldsValuesAsDriverValue(dbGenesis)...))

	// when
	result, err := br.getGenesisRecordFile()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())
	assert.Equal(t, dbGenesis, result)
	assert.Equal(t, dbGenesis, br.genesisRecordFile)
	assert.Equal(t, dbGenesis.Index, br.genesisRecordFileIndex)
	assert.Nil(t, err)
}

func TestShouldFailGetGenesis(t *testing.T) {
	var tests = []struct {
		name     string
		dbErr    error
		expected *rTypes.Error
	}{
		{
			name:     "DbRecordNotFound",
			dbErr:    gorm.ErrRecordNotFound,
			expected: errors.ErrNodeIsStarting,
		},
		{
			name:     "OtherDbError",
			dbErr:    gorm.ErrInvalidTransaction,
			expected: errors.ErrDatabaseError,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// given
			br, mock := setupRepository(t)

			mock.ExpectQuery(selectGenesis).WillReturnError(tt.dbErr)

			// when
			result, err := br.getGenesisRecordFile()

			// then
			assert.NoError(t, mock.ExpectationsWereMet())
			assert.Nil(t, result)
			assert.Equal(t, tt.expected, err)

		})
	}
}

func TestShouldSuccessReturnRecordFileTableName(t *testing.T) {
	assert.Equal(t, tableNameRecordFile, (&recordFile{}).TableName())
}

func TestShouldSuccessReturnBlock(t *testing.T) {
	rf := &recordFile{
		ConsensusStart: 100,
		ConsensusEnd:   199,
		Hash:           "0x12345",
		Index:          15,
		PrevHash:       "0x54321",
	}

	var tests = []struct {
		name                   string
		genesisRecordFileIndex int64
		expected               *types.Block
	}{
		{
			name:                   "GenesisBlock",
			genesisRecordFileIndex: rf.Index,
			expected: &types.Block{
				ConsensusStartNanos: rf.ConsensusStart,
				ConsensusEndNanos:   rf.ConsensusEnd,
				Hash:                rf.Hash,
				Index:               0,
				ParentHash:          rf.Hash,
				ParentIndex:         0,
			},
		},
		{
			name:                   "NonGenesisBlock",
			genesisRecordFileIndex: rf.Index - 2,
			expected: &types.Block{
				ConsensusStartNanos: rf.ConsensusStart,
				ConsensusEndNanos:   rf.ConsensusEnd,
				Hash:                rf.Hash,
				Index:               2,
				ParentHash:          rf.PrevHash,
				ParentIndex:         1,
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, rf.ToBlock(tt.genesisRecordFileIndex))
		})
	}
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
	return setupRepositoryWithGenesisRecordFile(t, nil)
}

func setupRepositoryWithGenesisRecordFile(
	t *testing.T,
	genesisRecordFile *recordFile,
) (*BlockRepository, sqlmock.Sqlmock) {
	gormDbClient, mock := mocks.DatabaseMock(t)

	aber := NewBlockRepository(gormDbClient)
	if genesisRecordFile != nil {
		aber.genesisRecordFile = genesisRecordFile
		aber.genesisRecordFileIndex = genesisRecordFile.Index
	}

	return aber, mock
}
