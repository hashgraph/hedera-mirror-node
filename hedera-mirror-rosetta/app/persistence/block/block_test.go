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
	"testing"

	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	pTypes "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

const (
	truncateAccountBalanceFileSql = "truncate account_balance_file"
	truncateRecordFileSql         = "truncate record_file"
)

var (
	accountBalanceFiles = []*pTypes.AccountBalanceFile{
		{
			ConsensusTimestamp: 90,
			Count:              10,
			FileHash:           "genesis_account_balance_file_hash",
			Name:               "genesis_account_balance_file",
		},
		{
			ConsensusTimestamp: 10000,
			Count:              10,
			FileHash:           "second_account_balance_file_hash",
			Name:               "second_account_balance_file",
		},
		{
			ConsensusTimestamp: 20000,
			Count:              10,
			FileHash:           "third_account_balance_file_hash",
			Name:               "third_account_balance_file",
		},
	}
	expectedGenesisBlock = &types.Block{
		ConsensusStartNanos: 91,
		ConsensusEndNanos:   100,
		Hash:                "genesis_record_file_hash",
		Index:               0,
		LatestIndex:         1,
		ParentHash:          "genesis_record_file_hash",
		ParentIndex:         0,
	}
	expectedSecondBlock = &types.Block{
		ConsensusStartNanos: 101,
		ConsensusEndNanos:   120,
		Hash:                "second_record_file_hash",
		Index:               1,
		LatestIndex:         1,
		ParentHash:          "genesis_record_file_hash",
		ParentIndex:         0,
	}
	recordFiles = []*pTypes.RecordFile{
		{
			ConsensusStart: 80,
			ConsensusEnd:   100,
			Hash:           "genesis_record_file_hash",
			Index:          3,
			Name:           "genesis_record_file",
			PrevHash:       "previous_record_file_hash",
		},
		{
			ConsensusStart: 101,
			ConsensusEnd:   120,
			Hash:           "second_record_file_hash",
			Index:          4,
			Name:           "second_record_file",
			PrevHash:       "genesis_record_file_hash",
		},
	}
	genesisRecordFile       = recordFiles[0]
	recordFileBeforeGenesis = &pTypes.RecordFile{
		ConsensusStart: 50,
		ConsensusEnd:   79,
		Hash:           "previous_record_file_hash",
		Index:          2,
		Name:           "previous_record_file",
		PrevHash:       "some_hash",
	}
	thirdRecordFile = &pTypes.RecordFile{
		ConsensusStart: 121,
		ConsensusEnd:   130,
		Hash:           "third_record_file_hash",
		Index:          5,
		Name:           "third_record_file",
		PrevHash:       "second_record_file_hash",
	}
)

// run the suite
func TestBlockRepositorySuite(t *testing.T) {
	suite.Run(t, new(blockRepositorySuite))
}

type blockRepositorySuite struct {
	test.IntegrationTest
	suite.Suite
}

func (suite *blockRepositorySuite) SetupSuite() {
	suite.Setup()
}

func (suite *blockRepositorySuite) TearDownSuite() {
	suite.TearDown()
}

func (suite *blockRepositorySuite) SetupTest() {
	suite.CleanupDb()
	db.CreateDbRecords(suite.DbResource.GetGormDb(), accountBalanceFiles, recordFiles)
}

func (suite *blockRepositorySuite) TestFindByHashGenesisBlock() {
	// given
	repo := NewBlockRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := repo.FindByHash(genesisRecordFile.Hash)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expectedGenesisBlock, actual)
}

func (suite *blockRepositorySuite) TestFindByHashNonGenesisBlock() {
	// given
	repo := NewBlockRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := repo.FindByHash(expectedSecondBlock.Hash)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expectedSecondBlock, actual)
}

func (suite *blockRepositorySuite) TestFindByHashNoAccountBalanceFile() {
	// given
	db.ExecSql(suite.DbResource.GetGormDb(), truncateAccountBalanceFileSql)
	repo := NewBlockRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := repo.FindByHash(genesisRecordFile.Hash)

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByHashNoRecordFile() {
	// given
	db.ExecSql(suite.DbResource.GetGormDb(), truncateRecordFileSql)
	repo := NewBlockRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := repo.FindByHash(genesisRecordFile.Hash)

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByHashEmptyHash() {
	// given
	repo := NewBlockRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := repo.FindByHash("")

	// then
	assert.Equal(suite.T(), errors.ErrInvalidArgument, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByHashDbConnectionError() {
	// given
	repo := NewBlockRepository(suite.InvalidDbClient)

	// when
	actual, err := repo.FindByHash(genesisRecordFile.Hash)

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByIdentifierGenesisBlock() {
	// given
	repo := NewBlockRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := repo.FindByIdentifier(0, expectedGenesisBlock.Hash)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expectedGenesisBlock, actual)
}

func (suite *blockRepositorySuite) TestFindByIdentifierNonGenesisBlock() {
	// given
	repo := NewBlockRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := repo.FindByIdentifier(expectedSecondBlock.Index, expectedSecondBlock.Hash)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expectedSecondBlock, actual)
}

func (suite *blockRepositorySuite) TestFindByIdentifierInvalidArgument() {
	// given
	tests := []struct {
		name  string
		index int64
		hash  string
	}{
		{
			"negative index",
			-1,
			"hash",
		},
		{
			"empty hash",
			0,
			"",
		},
	}
	repo := NewBlockRepository(suite.DbResource.GetGormDb())

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// when
			actual, err := repo.FindByIdentifier(tt.index, tt.hash)

			// then
			assert.Equal(t, errors.ErrInvalidArgument, err)
			assert.Nil(t, actual)
		})
	}
}

func (suite *blockRepositorySuite) TestFindByIdentifierIndexHashMismatch() {
	// given
	repo := NewBlockRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := repo.FindByIdentifier(expectedGenesisBlock.Index, expectedSecondBlock.Hash)

	// then
	assert.Equal(suite.T(), errors.ErrBlockNotFound, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByIdentifierNoAccountBalanceFile() {
	// given
	db.ExecSql(suite.DbResource.GetGormDb(), truncateAccountBalanceFileSql)
	repo := NewBlockRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := repo.FindByIdentifier(0, expectedGenesisBlock.Hash)

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByIdentifierNoRecordFile() {
	// given
	db.ExecSql(suite.DbResource.GetGormDb(), truncateRecordFileSql)
	repo := NewBlockRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := repo.FindByIdentifier(0, expectedGenesisBlock.Hash)

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByIdentifierDbConnectionError() {
	// given
	repo := NewBlockRepository(suite.InvalidDbClient)

	// when
	actual, err := repo.FindByIdentifier(0, expectedGenesisBlock.Hash)

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByIndexGenesisBlock() {
	// given
	repo := NewBlockRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := repo.FindByIndex(0)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expectedGenesisBlock, actual)
}

func (suite *blockRepositorySuite) TestFindByIndexNonGenesisBlock() {
	// given
	repo := NewBlockRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := repo.FindByIndex(expectedSecondBlock.Index)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expectedSecondBlock, actual)
}

func (suite *blockRepositorySuite) TestFindByIndexInvalidIndex() {
	// given
	repo := NewBlockRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := repo.FindByIndex(-1)

	// then
	assert.Equal(suite.T(), errors.ErrInvalidArgument, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFineByIndexNoAccountBalanceFile() {
	// given
	db.ExecSql(suite.DbResource.GetGormDb(), truncateAccountBalanceFileSql)
	repo := NewBlockRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := repo.FindByIndex(0)

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByIndexNoRecordFile() {
	// given
	db.ExecSql(suite.DbResource.GetGormDb(), truncateRecordFileSql)
	repo := NewBlockRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := repo.FindByIndex(0)

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByIndexDbConnectionError() {
	// given
	repo := NewBlockRepository(suite.InvalidDbClient)

	// when
	actual, err := repo.FindByIndex(0)

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestRetrieveGenesis() {
	// given
	repo := NewBlockRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := repo.RetrieveGenesis()

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expectedGenesisBlock, actual)
}

func (suite *blockRepositorySuite) TestRetrieveGenesisNoAccountBalanceFile() {
	// given
	db.ExecSql(suite.DbResource.GetGormDb(), truncateAccountBalanceFileSql)
	repo := NewBlockRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := repo.RetrieveGenesis()

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestRetrieveGenesisNoRecordFile() {
	// given
	db.ExecSql(suite.DbResource.GetGormDb(), truncateRecordFileSql)
	repo := NewBlockRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := repo.RetrieveGenesis()

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestRetrieveGenesisDbConnectionError() {
	// given
	repo := NewBlockRepository(suite.InvalidDbClient)

	// when
	actual, err := repo.RetrieveGenesis()

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestRetrieveSecondLatestGenesisBlock() {
	// given
	repo := NewBlockRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := repo.RetrieveSecondLatest()

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expectedGenesisBlock, actual)
}

func (suite *blockRepositorySuite) TestRetrieveSecondLatestNonGenesisBlock() {
	// given
	db.CreateDbRecords(suite.DbResource.GetGormDb(), thirdRecordFile)
	expected := *expectedSecondBlock
	expected.LatestIndex = 2
	repo := NewBlockRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := repo.RetrieveSecondLatest()

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), &expected, actual)
}

func (suite *blockRepositorySuite) TestRetrieveSecondLatestWithOnlyGenesisBlock() {
	// given
	db.ExecSql(suite.DbResource.GetGormDb(), truncateRecordFileSql)
	db.CreateDbRecords(suite.DbResource.GetGormDb(), genesisRecordFile)
	repo := NewBlockRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := repo.RetrieveSecondLatest()

	// then
	assert.Equal(suite.T(), errors.ErrBlockNotFound, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestRetrieveSecondLatestWithBlockBeforeGenesis() {
	// given
	db.ExecSql(suite.DbResource.GetGormDb(), truncateRecordFileSql)
	db.CreateDbRecords(suite.DbResource.GetGormDb(), recordFileBeforeGenesis, genesisRecordFile)
	repo := NewBlockRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := repo.RetrieveSecondLatest()

	// then
	assert.Equal(suite.T(), errors.ErrBlockNotFound, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestRetrieveSecondLatestNoAccountBalanceFile() {
	// given
	db.ExecSql(suite.DbResource.GetGormDb(), truncateAccountBalanceFileSql)
	repo := NewBlockRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := repo.RetrieveSecondLatest()

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestRetrieveSecondLatestNoRecordFile() {
	// given
	db.ExecSql(suite.DbResource.GetGormDb(), truncateRecordFileSql)
	repo := NewBlockRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := repo.RetrieveSecondLatest()

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestetrieveSecondLatestDbConnectionError() {
	// given
	repo := NewBlockRepository(suite.InvalidDbClient)

	// when
	actual, err := repo.RetrieveSecondLatest()

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func TestRecordFileToBlock(t *testing.T) {
	genesisConsensusStart := int64(110)
	genesisIndex := int64(5)
	tests := []struct {
		name     string
		input    recordFile
		expected *types.Block
	}{
		{
			"genesis block",
			recordFile{
				ConsensusStart: 100,
				ConsensusEnd:   200,
				Hash:           "hash",
				Index:          5,
				LatestIndex:    9,
				PrevHash:       "prev_hash",
			},
			&types.Block{
				Index:               0,
				Hash:                "hash",
				LatestIndex:         4,
				ParentIndex:         0,
				ParentHash:          "hash",
				ConsensusStartNanos: genesisConsensusStart,
				ConsensusEndNanos:   200,
			},
		},
		{
			"non-genesis block",
			recordFile{
				ConsensusStart: 201,
				ConsensusEnd:   300,
				Hash:           "hash",
				Index:          8,
				LatestIndex:    9,
				PrevHash:       "prev_hash",
			},
			&types.Block{
				Index:               3,
				Hash:                "hash",
				LatestIndex:         4,
				ParentIndex:         2,
				ParentHash:          "prev_hash",
				ConsensusStartNanos: 201,
				ConsensusEndNanos:   300,
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, tt.input.ToBlock(genesisConsensusStart, genesisIndex))
		})
	}
}
