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
	"database/sql"
	"errors"
	"sync"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	hErrors "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	log "github.com/sirupsen/logrus"
	"gorm.io/gorm"
)

const (
	tableNameRecordFile = "record_file"
)

const (
	// selectLatestWithIndex - Selects the latest row
	selectLatestWithIndex string = `SELECT consensus_start,
                                           consensus_end,
                                           hash,
                                           index,
                                           prev_hash
                                    FROM record_file
                                    ORDER BY consensus_end DESC
                                    LIMIT 1`

	// selectByHashWithIndex - Selects the row by given hash
	selectByHashWithIndex string = `SELECT consensus_start,
                                           consensus_end,
                                           hash,
                                           index,
                                           prev_hash
                                    FROM record_file
                                    WHERE hash = @hash`

	// selectGenesis - Selects the first block whose consensus_end is after the genesis account balance
	// timestamp. Return the record file with adjusted consensus start
	selectGenesis string = `SELECT
                              consensus_end,
                              hash,
                              index,
                              prev_hash,
                              CASE
                                WHEN genesis.min >= rf.consensus_start THEN genesis.min + 1
                                ELSE rf.consensus_start
                              END AS consensus_start
                            FROM record_file AS rf
                            JOIN (SELECT MIN(consensus_timestamp) FROM account_balance_file) AS genesis
                              ON consensus_end > genesis.min
                            ORDER BY consensus_end
                            LIMIT 1`

	// selectRecordFileByIndex - Selects the record_file by its index
	selectRecordFileByIndex string = `SELECT consensus_start,
                                             consensus_end,
                                             hash,
                                             index,
                                             prev_hash
                                      FROM record_file
                                      WHERE index = @index`
)

type recordFile struct {
	ConsensusStart   int64  `gorm:"type:bigint"`
	ConsensusEnd     int64  `gorm:"type:bigint;primary_key"`
	Count            int64  `gorm:"type:bigint"`
	DigestAlgorithm  int    `gorm:"type:int"`
	FileHash         string `gorm:"size:96"`
	HapiVersionMajor int    `gorm:"type:int"`
	HapiVersionMinor int    `gorm:"type:int"`
	HapiVersionPatch int    `gorm:"type:int"`
	Hash             string `gorm:"size:96"`
	Index            int64  `gorm:"type:bigint"`
	LoadEnd          int64  `gorm:"type:bigint"`
	LoadStart        int64  `gorm:"type:bigint"`
	Name             string `gorm:"size:250"`
	NodeAccountID    int64  `gorm:"type:bigint"`
	PrevHash         string `gorm:"size:96"`
	Version          int    `gorm:"type:int"`
}

// TableName - Set table name to be `record_file`
func (rf *recordFile) TableName() string {
	return tableNameRecordFile
}

func (rf *recordFile) ToBlock(genesisIndex int64) *types.Block {
	index := rf.Index - genesisIndex
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
	}
}

// BlockRepository struct that has connection to the Database
type BlockRepository struct {
	once                   sync.Once
	dbClient               *gorm.DB
	genesisRecordFile      *recordFile
	genesisRecordFileIndex int64
}

// NewBlockRepository creates an instance of a BlockRepository struct
func NewBlockRepository(dbClient *gorm.DB) *BlockRepository {
	return &BlockRepository{dbClient: dbClient}
}

// FindByIndex retrieves a block by given Index
func (br *BlockRepository) FindByIndex(index int64) (*types.Block, *rTypes.Error) {
	if index < 0 {
		return nil, hErrors.ErrInvalidArgument
	}

	if _, err := br.getGenesisRecordFile(); err != nil {
		return nil, err
	}

	rf := &recordFile{}
	index += br.genesisRecordFileIndex
	if index == br.genesisRecordFileIndex {
		rf = br.genesisRecordFile
	} else if err := br.dbClient.Raw(selectRecordFileByIndex, sql.Named("index", index)).First(rf).Error; err != nil {
		return nil, handleDatabaseError(err, hErrors.ErrBlockNotFound)
	}

	return rf.ToBlock(br.genesisRecordFileIndex), nil
}

// FindByHash retrieves a block by a given Hash
func (br *BlockRepository) FindByHash(hash string) (*types.Block, *rTypes.Error) {
	if hash == "" {
		return nil, hErrors.ErrInvalidArgument
	}

	if _, err := br.getGenesisRecordFile(); err != nil {
		return nil, err
	}

	return br.findBlockByHash(hash)
}

// FindByIdentifier retrieves a block by Index && Hash
func (br *BlockRepository) FindByIdentifier(index int64, hash string) (*types.Block, *rTypes.Error) {
	if index < 0 || hash == "" {
		return nil, hErrors.ErrInvalidArgument
	}

	if _, err := br.getGenesisRecordFile(); err != nil {
		return nil, err
	}

	block, err := br.findBlockByHash(hash)
	if err != nil {
		return nil, err
	}

	if block.Index != index {
		return nil, hErrors.ErrBlockNotFound
	}

	return block, nil
}

// RetrieveGenesis retrieves the genesis block
func (br *BlockRepository) RetrieveGenesis() (*types.Block, *rTypes.Error) {
	if _, err := br.getGenesisRecordFile(); err != nil {
		return nil, err
	}

	return br.genesisRecordFile.ToBlock(br.genesisRecordFileIndex), nil
}

// RetrieveLatest retrieves the latest block
func (br *BlockRepository) RetrieveLatest() (*types.Block, *rTypes.Error) {
	if _, err := br.getGenesisRecordFile(); err != nil {
		return nil, err
	}

	rf := &recordFile{}
	if err := br.dbClient.Raw(selectLatestWithIndex).First(rf).Error; err != nil {
		return nil, handleDatabaseError(err, hErrors.ErrBlockNotFound)
	}

	return rf.ToBlock(br.genesisRecordFileIndex), nil
}

func (br *BlockRepository) findBlockByHash(hash string) (*types.Block, *rTypes.Error) {
	rf := &recordFile{}
	if hash == br.genesisRecordFile.Hash {
		rf = br.genesisRecordFile
	} else if err := br.dbClient.Raw(selectByHashWithIndex, sql.Named("hash", hash)).First(rf).Error; err != nil {
		return nil, handleDatabaseError(err, hErrors.ErrBlockNotFound)
	}

	return rf.ToBlock(br.genesisRecordFileIndex), nil
}

func (br *BlockRepository) getGenesisRecordFile() (*recordFile, *rTypes.Error) {
	if br.genesisRecordFile != nil {
		return br.genesisRecordFile, nil
	}

	rf := &recordFile{}
	if err := br.dbClient.Raw(selectGenesis).First(rf).Error; err != nil {
		return nil, handleDatabaseError(err, hErrors.ErrNodeIsStarting)
	}

	br.once.Do(func() {
		br.genesisRecordFile = rf
		br.genesisRecordFileIndex = rf.Index
	})

	log.Infof("Fetched genesis record file, index - %d", rf.Index)
	return br.genesisRecordFile, nil
}

func handleDatabaseError(err error, recordNotFoundErr *rTypes.Error) *rTypes.Error {
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return recordNotFoundErr
	}

	log.Errorf("%s: %s", hErrors.ErrDatabaseError.Message, err)
	return hErrors.ErrDatabaseError
}
