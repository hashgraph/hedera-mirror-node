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
	"log"
	"sync"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/jinzhu/gorm"
)

const (
	tableNameRecordFile = "record_file"
)

const (
	// selectLatestWithIndex - Selects the latest row
	selectLatestWithIndex string = `SELECT block_index,
                                           consensus_start,
                                           consensus_end,
                                           hash,
                                           prev_hash
                                    FROM record_file
                                    ORDER BY consensus_end DESC
                                    LIMIT 1`

	// selectByHashWithIndex - Selects the row by given hash
	selectByHashWithIndex string = `SELECT block_index,
                                           consensus_start,
                                           consensus_end,
                                           hash,
                                           prev_hash
                                    FROM record_file
                                    WHERE hash = $1`

	// selectSkippedRecordFilesCount - Selects the count of rows from the record_file table,
	// where each one's consensus_end is before the MIN consensus_timestamp of account_balance table (the first one added).
	// This way, record files before that timestamp are considered non-existent,
	// and the first record_file (block) will be considered equal or bigger
	// than the consensus_timestamp of the first account_balance
	selectSkippedRecordFilesCount string = `SELECT block_index + 1
                                            FROM record_file
                                            WHERE consensus_end <= (SELECT consensus_timestamp
                                                                    FROM account_balance_file
                                                                    ORDER BY consensus_timestamp ASC
                                                                    LIMIT 1)
                                            ORDER BY consensus_end DESC
                                            LIMIT 1`

	// selectLatestConsensusTimestampAccountBalances - Selects the latest consensus_timestamp from account_balance table
	selectLatestConsensusTimestampAccountBalances string = `SELECT consensus_timestamp
                                                            FROM account_balance
                                                            ORDER BY consensus_timestamp DESC
                                                            LIMIT 1`

	// selectRecordFileByBlockIndex - Selects the record_file by its block_index
	selectRecordFileByBlockIndex string = `SELECT block_index,
                                                  consensus_start,
                                                  consensus_end,
                                                  hash,
                                                  prev_hash
                                           FROM record_file
                                           WHERE block_index = $1`
)

type recordFile struct {
	BlockIndex       int64  `gorm:"type:bigint"`
	ConsensusStart   int64  `gorm:"type:bigint"`
	ConsensusEnd     int64  `gorm:"type:bigint"`
	Count            int64  `gorm:"type:bigint"`
	DigestAlgorithm  int    `gorm:"type:int"`
	FileHash         string `gorm:"size:96"`
	HapiVersionMajor int    `gorm:"type:int"`
	HapiVersionMinor int    `gorm:"type:int"`
	HapiVersionPatch int    `gorm:"type:int"`
	Hash             string `gorm:"size:96"`
	ID               int64  `gorm:"type:bigint;primary_key"`
	LoadEnd          int64  `gorm:"type:bigint"`
	LoadStart        int64  `gorm:"type:bigint"`
	Name             string `gorm:"size:250"`
	NodeAccountID    int64  `gorm:"type:bigint"`
	PrevHash         string `gorm:"size:96"`
	Version          int    `gorm:"type:int"`
}

// TableName - Set table name to be `record_file`
func (recordFile) TableName() string {
	return tableNameRecordFile
}

// BlockRepository struct that has connection to the Database
type BlockRepository struct {
	once                    sync.Once
	dbClient                *gorm.DB
	recordFileStartingIndex *int64
}

// NewBlockRepository creates an instance of a BlockRepository struct
func NewBlockRepository(dbClient *gorm.DB) *BlockRepository {
	return &BlockRepository{dbClient: dbClient}
}

// FindByIndex retrieves a block by given Index
func (br *BlockRepository) FindByIndex(index int64) (*types.Block, *rTypes.Error) {
	startingIndex, err := br.getRecordFilesStartingIndex()
	if err != nil {
		return nil, err
	}

	blockIndex := index + startingIndex
	rf := &recordFile{}
	if br.dbClient.Raw(selectRecordFileByBlockIndex, blockIndex).Find(rf).RecordNotFound() {
		return nil, errors.Errors[errors.BlockNotFound]
	}

	return br.constructBlockResponse(rf)
}

// FindByHash retrieves a block by a given Hash
func (br *BlockRepository) FindByHash(hash string) (*types.Block, *rTypes.Error) {
	rf, err := br.findRecordFileByHash(hash)
	if err != nil {
		return nil, err
	}

	return br.constructBlockResponse(rf)
}

// FindByIdentifier retrieves a block by Index && Hash
func (br *BlockRepository) FindByIdentifier(index int64, hash string) (*types.Block, *rTypes.Error) {
	rf, err := br.findRecordFileByHash(hash)
	if err != nil {
		return nil, err
	}

	block, err := br.constructBlockResponse(rf)
	if err != nil {
		return nil, err
	}

	if block.Index != index {
		return nil, errors.Errors[errors.BlockNotFound]
	}

	return block, nil
}

// RetrieveGenesis retrieves the genesis block
func (br *BlockRepository) RetrieveGenesis() (*types.Block, *rTypes.Error) {
	startingIndex, err := br.getRecordFilesStartingIndex()
	if err != nil {
		return nil, err
	}

	rf := &recordFile{}
	if br.dbClient.Raw(selectRecordFileByBlockIndex, startingIndex).Find(rf).RecordNotFound() {
		return nil, errors.Errors[errors.BlockNotFound]
	}

	return &types.Block{
		Index:               0,
		Hash:                rf.Hash,
		ConsensusStartNanos: rf.ConsensusStart,
		ConsensusEndNanos:   rf.ConsensusEnd,
	}, nil
}

// RetrieveLatest retrieves the latest block
func (br *BlockRepository) RetrieveLatest() (*types.Block, *rTypes.Error) {
	rf := &recordFile{}
	if br.dbClient.Raw(selectLatestWithIndex).Scan(rf).RecordNotFound() {
		return nil, errors.Errors[errors.BlockNotFound]
	}
	if rf.Hash == "" {
		return nil, errors.Errors[errors.BlockNotFound]
	}

	return br.constructBlockResponse(rf)
}

func (br *BlockRepository) findRecordFileByHash(hash string) (*recordFile, *rTypes.Error) {
	rf := &recordFile{}
	if br.dbClient.Raw(selectByHashWithIndex, hash).Scan(rf).RecordNotFound() {
		return nil, errors.Errors[errors.BlockNotFound]
	}

	if rf.BlockIndex < 0 || rf.Hash == "" {
		return nil, errors.Errors[errors.BlockNotFound]
	}

	return rf, nil
}

// constructBlockResponse returns the constructed Block
func (br *BlockRepository) constructBlockResponse(rf *recordFile) (*types.Block, *rTypes.Error) {
	startingIndex, err := br.getRecordFilesStartingIndex()
	if err != nil {
		return nil, err
	}

	index := rf.BlockIndex - startingIndex
	parentIndex := index - 1
	parentHash := rf.PrevHash

	// Handle the edge case for querying first block
	if parentIndex < 0 {
		parentIndex = 0      // Parent index should be 0, same as current block index
		parentHash = rf.Hash // Parent hash should be same as current block hash
	}
	return &types.Block{
		Index:               index,
		Hash:                rf.Hash,
		ParentIndex:         parentIndex,
		ParentHash:          parentHash,
		ConsensusStartNanos: rf.ConsensusStart,
		ConsensusEndNanos:   rf.ConsensusEnd,
	}, nil
}

func (br *BlockRepository) getRecordFilesStartingIndex() (int64, *rTypes.Error) {
	if br.recordFileStartingIndex != nil {
		return *br.recordFileStartingIndex, nil
	}

	var latestConsensusTimeStampAccountBalances int64

	br.dbClient.Raw(selectLatestConsensusTimestampAccountBalances).Count(&latestConsensusTimeStampAccountBalances)
	if latestConsensusTimeStampAccountBalances == 0 {
		return 0, errors.Errors[errors.NodeIsStarting]
	}

	var startingIndex int64
	br.dbClient.Raw(selectSkippedRecordFilesCount).Count(&startingIndex)

	br.once.Do(func() {
		br.recordFileStartingIndex = &startingIndex
	})

	log.Printf(`Fetched Record Files starting index: %d`, *br.recordFileStartingIndex)

	return *br.recordFileStartingIndex, nil
}
