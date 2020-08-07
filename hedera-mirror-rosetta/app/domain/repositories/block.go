package repositories

import (
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
)

// BlockRepository Interface that all BlockRepository structs must implement
type BlockRepository interface {
	FindByIndex(index int64) (*types.Block, error)
	FindByHash(hash string) (*types.Block, error)
	FindByIndentifier(index int64, hash string) (*types.Block, error)
	RetrieveLatest() (*types.Block, error)
}
