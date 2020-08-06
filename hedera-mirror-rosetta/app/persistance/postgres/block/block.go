package block

import (
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/jinzhu/gorm"
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
func (br *BlockRepository) FindByIndex(index int64) *types.Block {
	rf := &recordFile{}
	br.dbClient.Find(rf, index)
	parentRf := br.findRecordFileByHash(rf.PrevHash)

	return &types.Block{ID: rf.ID, Hash: rf.FileHash, ParentID: parentRf.ID, ParentHash: parentRf.FileHash, Timestamp: rf.ConsensusEnd}
}

// FindByHash retrieves a block by a given Hash
func (br *BlockRepository) FindByHash(hash string) *types.Block {
	rf := &recordFile{}
	br.dbClient.Where(&recordFile{FileHash: hash}).Find(rf)
	parentRf := br.findRecordFileByHash(rf.PrevHash)

	return &types.Block{ID: rf.ID, Hash: rf.FileHash, ParentID: parentRf.ID, ParentHash: parentRf.FileHash, Timestamp: rf.ConsensusEnd}
}

// FindByIndentifier retrivies a block by Index && Hash
func (br *BlockRepository) FindByIndentifier(index int64, hash string) *types.Block {
	rf := &recordFile{}
	br.dbClient.Where(&recordFile{ID: index, FileHash: hash}).Find(rf)
	parentRf := br.findRecordFileByHash(rf.PrevHash)

	return &types.Block{ID: rf.ID, Hash: rf.FileHash, ParentID: parentRf.ID, ParentHash: parentRf.FileHash, Timestamp: rf.ConsensusEnd}
}

func (br *BlockRepository) findRecordFileByHash(hash string) *recordFile {
	parentRf := &recordFile{}
	br.dbClient.Where(&recordFile{FileHash: hash}).Find(parentRf)
	return parentRf
}
