package block

import (
	"fmt"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/jinzhu/gorm"
)

const genesisPreviousHash = "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"

const (
	// selectLatestAddRank - Selects row with the latest consensus_start and adds additional info about the position of that row (in terms of order by consensus_start) using the "rank" and "OVER".
	// The information about the position is used as Block Index
	selectLatestAddRank string = `SELECT * FROM (SELECT *, rank() OVER (ORDER BY consensus_start asc) FROM %s) AS res WHERE consensus_start = (SELECT MAX(consensus_start) FROM %s)`
	// selectByHashAddRank - Selects the row with a given file_hash and adds additional info about the position of that row (in terms of order by consensus_start) using the "rank" and "OVER".
	//The information about the position is used as Block Index
	selectByHashAddRank string = `SELECT * FROM (SELECT *, rank() OVER (ORDER BY consensus_start asc) FROM %s) AS res WHERE res.file_hash ='%s'`
)

type recordFile struct {
	ID             int64  `gorm:"type:bigint;primary_key"`
	Name           string `gorm:"size:250"`
	LoadStart      int64  `gorm:"type:bigint"`
	LoadEnd        int64  `gorm:"type:bigint"`
	FileHash       string `gorm:"size:96"`
	PrevHash       string `gorm:"size:96"`
	ConsensusStart int64  `gorm:"type:bigint"`
	ConsensusEnd   int64  `gorm:"type:bigint"`
	Rank           int64  `gorm:"type:bigint"`
}

// TableName - Set table name to be `record_file`
func (recordFile) TableName() string {
	return "record_file"
}

// BlockRepository struct that has connection to the Database
type BlockRepository struct {
	dbClient *gorm.DB
}

// NewBlockRepository creates an instance of a BlockRepository struct
func NewBlockRepository(dbClient *gorm.DB) *BlockRepository {
	return &BlockRepository{dbClient: dbClient}
}

// FindByIndex retrieves a block by given Index
func (br *BlockRepository) FindByIndex(index int64) (*types.Block, *rTypes.Error) {
	rf := &recordFile{}
	if br.dbClient.Order("consensus_end asc").Offset(index).First(rf).RecordNotFound() {
		return nil, errors.Errors[errors.BlockNotFound]
	}

	return br.constructBlockResponse(rf, index), nil
}

// FindByHash retrieves a block by a given Hash
func (br *BlockRepository) FindByHash(hash string) (*types.Block, *rTypes.Error) {
	rf, err := br.findRecordFileByHash(hash)
	if err != nil {
		return nil, err
	}

	return br.constructBlockResponse(rf, rf.Rank-1), nil
}

// FindByIdentifier retrieves a block by Index && Hash
func (br *BlockRepository) FindByIdentifier(index int64, hash string) (*types.Block, *rTypes.Error) {
	rf, err := br.findRecordFileByHash(hash)
	if err != nil {
		return nil, err
	}
	if rf.Rank-1 != index {
		return nil, errors.Errors[errors.BlockNotFound]
	}
	return br.constructBlockResponse(rf, index), nil
}

// RetrieveGenesis retrieves the genesis block
func (br *BlockRepository) RetrieveGenesis() (*types.Block, *rTypes.Error) {
	rf := &recordFile{}
	if br.dbClient.Where(&recordFile{PrevHash: genesisPreviousHash}).Find(rf).RecordNotFound() {
		return nil, errors.Errors[errors.BlockNotFound]
	}

	return &types.Block{
		Index:               0,
		Hash:                rf.FileHash,
		ConsensusStartNanos: rf.ConsensusStart,
		ConsensusEndNanos:   rf.ConsensusEnd,
	}, nil
}

// RetrieveLatest retrieves the latest block
func (br *BlockRepository) RetrieveLatest() (*types.Block, *rTypes.Error) {
	rf := &recordFile{}
	if br.dbClient.Raw(fmt.Sprintf(selectLatestAddRank, rf.TableName(), rf.TableName())).Scan(rf).RecordNotFound() {
		return nil, errors.Errors[errors.BlockNotFound]
	}

	return br.constructBlockResponse(rf, rf.Rank-1), nil
}

func (br *BlockRepository) findRecordFileByHash(hash string) (*recordFile, *rTypes.Error) {
	rf := &recordFile{}
	if br.dbClient.Raw(fmt.Sprintf(selectByHashAddRank, rf.TableName(), hash)).Scan(rf).RecordNotFound() {
		return nil, errors.Errors[errors.BlockNotFound]
	}
	return rf, nil
}

func (br *BlockRepository) constructBlockResponse(rf *recordFile, blockIndex int64) *types.Block {
	parentIndex := blockIndex - 1
	parentHash := rf.PrevHash

	// Handle the edge case for querying first block
	if rf.PrevHash == genesisPreviousHash {
		parentIndex = 0          //Parent index should be 0, same as current block index
		parentHash = rf.FileHash // Parent hash should be same as current block hash
	}
	return &types.Block{
		Index:               blockIndex,
		Hash:                rf.FileHash,
		ParentIndex:         parentIndex,
		ParentHash:          parentHash,
		ConsensusStartNanos: rf.ConsensusStart,
		ConsensusEndNanos:   rf.ConsensusEnd,
	}
}
