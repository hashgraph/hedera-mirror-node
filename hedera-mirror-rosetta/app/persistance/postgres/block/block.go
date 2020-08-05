package block

import (
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/jinzhu/gorm"
)

type BlockRepository struct {
	dbClient *gorm.DB
}

func NewBlockRepository(dbClient *gorm.DB) *BlockRepository {
	return &BlockRepository{dbClient: dbClient}
}

func (br *BlockRepository) FindById(id string) *types.Block {
	return nil // TODO Make me work
}
