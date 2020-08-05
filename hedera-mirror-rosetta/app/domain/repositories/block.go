package repositories

import (
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
)

type BlockRepository interface {
	FindByIndex(index int64) *types.Block
	FindByHash(hash string) *types.Block
	FindByIndentifier(index int64, hash string) *types.Block
}
