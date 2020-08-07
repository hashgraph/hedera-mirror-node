package block

import (
	"errors"
	"fmt"

	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/jinzhu/gorm"
)

const genesisPreviousHash = "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"

type recordFile struct {
	ID             int64  `gorm:"type:bigint;primary_key"`
	Name           string `gorm:"size:250"`
	LoadStart      int64  `gorm:"type:bigint"`
	LoadEnd        int64  `gorm:"type:bigint"`
	FileHash       string `gorm:"size:96"`
	PrevHash       string `gorm:"size:96"`
	ConsensusStart int64  `gorm:"type:bigint"`
	ConsensusEnd   int64  `gorm:"type:bigint"`
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
func (br *BlockRepository) FindByIndex(index int64) (*types.Block, error) {
	rf := &recordFile{}
	if br.dbClient.Where(&recordFile{ConsensusStart: index}).Find(rf).RecordNotFound() {
		return nil, errors.New("block not found")
	}

	parentRf, err := br.constructParentRecordFile(rf)
	if err != nil {
		return nil, err
	}
	return &types.Block{Hash: rf.FileHash, ParentIndex: parentRf.ConsensusStart, ParentHash: parentRf.FileHash, ConsensusStart: rf.ConsensusStart, ConsensusEnd: rf.ConsensusEnd}, nil
}

// FindByHash retrieves a block by a given Hash
func (br *BlockRepository) FindByHash(hash string) (*types.Block, error) {
	rf := &recordFile{}
	if br.dbClient.Where(&recordFile{FileHash: hash}).Find(rf).RecordNotFound() {
		return nil, errors.New("block not found")
	}
	parentRf, err := br.constructParentRecordFile(rf)
	if err != nil {
		return nil, err
	}

	return &types.Block{Hash: rf.FileHash, ParentIndex: parentRf.ConsensusStart, ParentHash: parentRf.FileHash, ConsensusStart: rf.ConsensusStart, ConsensusEnd: rf.ConsensusEnd}, nil
}

// FindByIndentifier retrivies a block by Index && Hash
func (br *BlockRepository) FindByIndentifier(index int64, hash string) (*types.Block, error) {
	rf := &recordFile{}
	if br.dbClient.Where(&recordFile{ConsensusStart: index, FileHash: hash}).Find(rf).RecordNotFound() {
		return nil, errors.New("block not found")
	}
	parentRf, err := br.constructParentRecordFile(rf)
	if err != nil {
		return nil, err
	}

	return &types.Block{Hash: rf.FileHash, ParentIndex: parentRf.ConsensusStart, ParentHash: parentRf.FileHash, ConsensusStart: rf.ConsensusStart, ConsensusEnd: rf.ConsensusEnd}, nil
}

// RetrieveLatest retries the latest block
func (br *BlockRepository) RetrieveLatest() (*types.Block, error) {
	rf := &recordFile{}
	if br.dbClient.Where(fmt.Sprintf("consensus_start = (SELECT MAX(consensus_start) FROM %s)", rf.TableName())).Find(rf).RecordNotFound() {
		return nil, errors.New("block not found")
	}

	parentRf, err := br.constructParentRecordFile(rf)
	if err != nil {
		return nil, err
	}
	return &types.Block{Hash: rf.FileHash, ParentIndex: parentRf.ConsensusStart, ParentHash: parentRf.FileHash, ConsensusStart: rf.ConsensusStart, ConsensusEnd: rf.ConsensusEnd}, nil
}

func (br *BlockRepository) findRecordFileByHash(hash string) (*recordFile, error) {
	parentRf := &recordFile{}
	if br.dbClient.Where(&recordFile{FileHash: hash}).Find(parentRf).RecordNotFound() {
		return nil, errors.New("block not found")
	}
	return parentRf, nil
}

func (br *BlockRepository) constructParentRecordFile(rf *recordFile) (*recordFile, error) {
	var parentRf = &recordFile{}
	var err error
	// Handle the egde case for querying first block
	if rf.PrevHash == genesisPreviousHash {
		parentRf = rf
	} else {
		parentRf, err = br.findRecordFileByHash(rf.PrevHash)
		if err != nil {
			return nil, err
		}
	}
	return parentRf, nil
}
