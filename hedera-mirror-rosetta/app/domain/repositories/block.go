package repositories

import (
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
)

// BlockRepository Interface that all BlockRepository structs must implement
type BlockRepository interface {
	FindByIndex(index int64) (*types.Block, *rTypes.Error)
	FindByHash(hash string) (*types.Block, *rTypes.Error)
	FindByIndentifier(index int64, hash string) (*types.Block, *rTypes.Error)
	RetrieveLatest() (*types.Block, *rTypes.Error)
}
