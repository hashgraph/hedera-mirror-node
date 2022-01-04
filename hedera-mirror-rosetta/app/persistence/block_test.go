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

package persistence

import (
	"testing"

	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

var (
	accountBalanceFiles = []*domain.AccountBalanceFile{
		{
			ConsensusTimestamp: 90,
			Count:              10,
			FileHash:           "genesis_account_balance_file_hash",
			Name:               "genesis_account_balance_file",
			NodeAccountId:      nodeAccountId,
		},
		{
			ConsensusTimestamp: 10000,
			Count:              10,
			FileHash:           "second_account_balance_file_hash",
			Name:               "second_account_balance_file",
			NodeAccountId:      nodeAccountId,
		},
		{
			ConsensusTimestamp: 20000,
			Count:              10,
			FileHash:           "third_account_balance_file_hash",
			Name:               "third_account_balance_file",
			NodeAccountId:      nodeAccountId,
		},
	}
	expectedGenesisBlock = &types.Block{
		ConsensusStartNanos: 91,
		ConsensusEndNanos:   100,
		Hash:                "genesis_record_file_hash",
		Index:               0,
		ParentHash:          "genesis_record_file_hash",
		ParentIndex:         0,
	}
	expectedSecondBlock = &types.Block{
		ConsensusStartNanos: 101,
		ConsensusEndNanos:   120,
		Hash:                "second_record_file_hash",
		Index:               1,
		ParentHash:          "genesis_record_file_hash",
		ParentIndex:         0,
	}
	nodeAccountId = domain.MustDecodeEntityId(3)
	recordFiles   = []*domain.RecordFile{
		{
			ConsensusStart: 80,
			ConsensusEnd:   100,
			Hash:           "genesis_record_file_hash",
			Index:          3,
			Name:           "genesis_record_file",
			NodeAccountID:  nodeAccountId,
			PrevHash:       "previous_record_file_hash",
		},
		{
			ConsensusStart: 101,
			ConsensusEnd:   120,
			Hash:           "second_record_file_hash",
			Index:          4,
			Name:           "second_record_file",
			NodeAccountID:  nodeAccountId,
			PrevHash:       "genesis_record_file_hash",
		},
	}
	genesisRecordFile       = recordFiles[0]
	recordFileBeforeGenesis = &domain.RecordFile{
		ConsensusStart: 50,
		ConsensusEnd:   79,
		Hash:           "previous_record_file_hash",
		Index:          2,
		Name:           "previous_record_file",
		NodeAccountID:  nodeAccountId,
		PrevHash:       "some_hash",
	}
)

// run the suite
func TestBlockRepositorySuite(t *testing.T) {
	suite.Run(t, new(blockRepositorySuite))
}

type blockRepositorySuite struct {
	integrationTest
	suite.Suite
}

func (suite *blockRepositorySuite) SetupTest() {
	suite.integrationTest.SetupTest()
	db.CreateDbRecords(dbClient, accountBalanceFiles, recordFiles)
}

func (suite *blockRepositorySuite) TestFindByHashGenesisBlock() {
	// given
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByHash(defaultContext, genesisRecordFile.Hash)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expectedGenesisBlock, actual)
}

func (suite *blockRepositorySuite) TestFindByHashNonGenesisBlock() {
	// given
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByHash(defaultContext, expectedSecondBlock.Hash)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expectedSecondBlock, actual)
}

func (suite *blockRepositorySuite) TestFindByHashNoAccountBalanceFile() {
	// given
	db.ExecSql(dbClient, truncateAccountBalanceFileSql)
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByHash(defaultContext, genesisRecordFile.Hash)

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByHashNoRecordFile() {
	// given
	db.ExecSql(dbClient, truncateRecordFileSql)
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByHash(defaultContext, genesisRecordFile.Hash)

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByHashEmptyHash() {
	// given
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByHash(defaultContext, "")

	// then
	assert.Equal(suite.T(), errors.ErrInvalidArgument, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByHashDbConnectionError() {
	// given
	repo := NewBlockRepository(invalidDbClient)

	// when
	actual, err := repo.FindByHash(defaultContext, genesisRecordFile.Hash)

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByIdentifierGenesisBlock() {
	// given
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByIdentifier(defaultContext, 0, expectedGenesisBlock.Hash)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expectedGenesisBlock, actual)
}

func (suite *blockRepositorySuite) TestFindByIdentifierNonGenesisBlock() {
	// given
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByIdentifier(defaultContext, expectedSecondBlock.Index, expectedSecondBlock.Hash)

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
	repo := NewBlockRepository(dbClient)

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// when
			actual, err := repo.FindByIdentifier(defaultContext, tt.index, tt.hash)

			// then
			assert.Equal(t, errors.ErrInvalidArgument, err)
			assert.Nil(t, actual)
		})
	}
}

func (suite *blockRepositorySuite) TestFindByIdentifierIndexHashMismatch() {
	// given
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByIdentifier(defaultContext, expectedGenesisBlock.Index, expectedSecondBlock.Hash)

	// then
	assert.Equal(suite.T(), errors.ErrBlockNotFound, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByIdentifierNoAccountBalanceFile() {
	// given
	db.ExecSql(dbClient, truncateAccountBalanceFileSql)
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByIdentifier(defaultContext, 0, expectedGenesisBlock.Hash)

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByIdentifierNoRecordFile() {
	// given
	db.ExecSql(dbClient, truncateRecordFileSql)
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByIdentifier(defaultContext, 0, expectedGenesisBlock.Hash)

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByIdentifierNotFound() {
	// given
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByIdentifier(defaultContext, 1000, "foobar")

	// then
	assert.Equal(suite.T(), errors.ErrBlockNotFound, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByIdentifierDbConnectionError() {
	// given
	repo := NewBlockRepository(invalidDbClient)

	// when
	actual, err := repo.FindByIdentifier(defaultContext, 0, expectedGenesisBlock.Hash)

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByIndexGenesisBlock() {
	// given
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByIndex(defaultContext, 0)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expectedGenesisBlock, actual)
}

func (suite *blockRepositorySuite) TestFindByIndexNonGenesisBlock() {
	// given
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByIndex(defaultContext, expectedSecondBlock.Index)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expectedSecondBlock, actual)
}

func (suite *blockRepositorySuite) TestFindByIndexInvalidIndex() {
	// given
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByIndex(defaultContext, -1)

	// then
	assert.Equal(suite.T(), errors.ErrInvalidArgument, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFineByIndexNoAccountBalanceFile() {
	// given
	db.ExecSql(dbClient, truncateAccountBalanceFileSql)
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByIndex(defaultContext, 0)

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByIndexNoRecordFile() {
	// given
	db.ExecSql(dbClient, truncateRecordFileSql)
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByIndex(defaultContext, 0)

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByIndexNotFound() {
	// given
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByIndex(defaultContext, 1000)

	// then
	assert.Equal(suite.T(), errors.ErrBlockNotFound, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByIndexDbConnectionError() {
	// given
	repo := NewBlockRepository(invalidDbClient)

	// when
	actual, err := repo.FindByIndex(defaultContext, 0)

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestRetrieveGenesis() {
	// given
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.RetrieveGenesis(defaultContext)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expectedGenesisBlock, actual)
}

func (suite *blockRepositorySuite) TestRetrieveGenesisNoAccountBalanceFile() {
	// given
	db.ExecSql(dbClient, truncateAccountBalanceFileSql)
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.RetrieveGenesis(defaultContext)

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestRetrieveGenesisNoRecordFile() {
	// given
	db.ExecSql(dbClient, truncateRecordFileSql)
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.RetrieveGenesis(defaultContext)

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestRetrieveGenesisDbConnectionError() {
	// given
	repo := NewBlockRepository(invalidDbClient)

	// when
	actual, err := repo.RetrieveGenesis(defaultContext)

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestRetrieveLatestNonGenesisBlock() {
	// given
	expected := *expectedSecondBlock
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.RetrieveLatest(defaultContext)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), &expected, actual)
}

func (suite *blockRepositorySuite) TestRetrieveLatestWithOnlyGenesisBlock() {
	// given
	db.ExecSql(dbClient, truncateRecordFileSql)
	db.CreateDbRecords(dbClient, genesisRecordFile)
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.RetrieveLatest(defaultContext)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), actual, expectedGenesisBlock)
}

func (suite *blockRepositorySuite) TestRetrieveLatestWithBlockBeforeGenesis() {
	// given
	db.ExecSql(dbClient, truncateRecordFileSql)
	db.CreateDbRecords(dbClient, recordFileBeforeGenesis)
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.RetrieveLatest(defaultContext)

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestRetrieveLatestNoAccountBalanceFile() {
	// given
	db.ExecSql(dbClient, truncateAccountBalanceFileSql)
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.RetrieveLatest(defaultContext)

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestRetrieveLatestNoRecordFile() {
	// given
	db.ExecSql(dbClient, truncateRecordFileSql)
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.RetrieveLatest(defaultContext)

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestRetrieveLatestRecordFileTableInconsistent() {
	// given
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.RetrieveLatest(defaultContext)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expectedSecondBlock, actual)

	// when
	db.ExecSql(dbClient, truncateRecordFileSql)
	actual, err = repo.RetrieveLatest(defaultContext)

	// then
	assert.Equal(suite.T(), errors.ErrBlockNotFound, err)
	assert.Nil(suite.T(), actual)

	// when
	db.CreateDbRecords(dbClient, recordFileBeforeGenesis)
	actual, err = repo.RetrieveLatest(defaultContext)

	// then
	assert.Equal(suite.T(), errors.ErrBlockNotFound, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestRetrieveLatestDbConnectionError() {
	// given
	repo := NewBlockRepository(invalidDbClient)

	// when
	actual, err := repo.RetrieveLatest(defaultContext)

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func TestRecordFileToBlock(t *testing.T) {
	genesisConsensusStart := int64(110)
	genesisIndex := int64(5)
	tests := []struct {
		name     string
		input    recordBlock
		expected *types.Block
	}{
		{
			"genesis block",
			recordBlock{
				ConsensusStart: 100,
				ConsensusEnd:   200,
				Hash:           "hash",
				Index:          5,
				PrevHash:       "prev_hash",
			},
			&types.Block{
				Index:               0,
				Hash:                "hash",
				ParentIndex:         0,
				ParentHash:          "hash",
				ConsensusStartNanos: genesisConsensusStart,
				ConsensusEndNanos:   200,
			},
		},
		{
			"non-genesis block",
			recordBlock{
				ConsensusStart: 201,
				ConsensusEnd:   300,
				Hash:           "hash",
				Index:          8,
				PrevHash:       "prev_hash",
			},
			&types.Block{
				Index:               3,
				Hash:                "hash",
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
