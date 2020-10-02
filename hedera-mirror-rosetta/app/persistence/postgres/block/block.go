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
	// selectByHashAddRank - Selects the row with a given file_hash and adds additional info about the poistion of that row (in terms of order by consensus_start) using the "rank" and "OVER".
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
	if br.dbClient.Order("consensus_start asc").Offset(index).First(rf).RecordNotFound() {
		return nil, errors.Errors[errors.BlockNotFound]
	}

	parentRf, err := br.constructParentRecordFile(rf)
	if err != nil {
		return nil, err
	}
	return &types.Block{
		Index:               index,
		Hash:                rf.FileHash,
		ParentIndex:         parentRf.Rank - 1,
		ParentHash:          parentRf.FileHash,
		ConsensusStartNanos: rf.ConsensusStart,
		ConsensusEndNanos:   rf.ConsensusEnd,
	}, nil
}

// FindByHash retrieves a block by a given Hash
func (br *BlockRepository) FindByHash(hash string) (*types.Block, *rTypes.Error) {
	rf, err := br.findRecordFileByHash(hash)
	if err != nil {
		return nil, err
	}
	parentRf, err := br.constructParentRecordFile(rf)
	if err != nil {
		return nil, err
	}

	return &types.Block{
		Index:               rf.Rank - 1,
		Hash:                rf.FileHash,
		ParentIndex:         parentRf.Rank - 1,
		ParentHash:          parentRf.FileHash,
		ConsensusStartNanos: rf.ConsensusStart,
		ConsensusEndNanos:   rf.ConsensusEnd,
	}, nil
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
	parentRf, err := br.constructParentRecordFile(rf)
	if err != nil {
		return nil, err
	}

	return &types.Block{
		Index:               rf.Rank - 1,
		Hash:                rf.FileHash,
		ParentIndex:         parentRf.Rank - 1,
		ParentHash:          parentRf.FileHash,
		ConsensusStartNanos: rf.ConsensusStart,
		ConsensusEndNanos:   rf.ConsensusEnd,
	}, nil
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

	parentRf, err := br.constructParentRecordFile(rf)
	if err != nil {
		return nil, err
	}
	return &types.Block{
		Index:               rf.Rank - 1,
		Hash:                rf.FileHash,
		ParentIndex:         parentRf.ConsensusStart,
		ParentHash:          parentRf.FileHash,
		ConsensusStartNanos: rf.ConsensusStart,
		ConsensusEndNanos:   rf.ConsensusEnd,
	}, nil
}

func (br *BlockRepository) findRecordFileByHash(hash string) (*recordFile, *rTypes.Error) {
	rf := &recordFile{}
	if br.dbClient.Raw(fmt.Sprintf(selectByHashAddRank, rf.TableName(), hash)).Scan(rf).RecordNotFound() {
		return nil, errors.Errors[errors.BlockNotFound]
	}
	return rf, nil
}

func (br *BlockRepository) constructParentRecordFile(rf *recordFile) (*recordFile, *rTypes.Error) {
	var parentRf = &recordFile{}
	var err *rTypes.Error
	// Handle the edge case for querying first block
	if rf.PrevHash == genesisPreviousHash {
		parentRf = rf
		rf.Rank = 1
	} else {
		parentRf, err = br.findRecordFileByHash(rf.PrevHash)
		if err != nil {
			return nil, err
		}
	}
	return parentRf, nil
}
