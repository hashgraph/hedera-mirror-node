package repositories

import (
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
)

type BlockRepository interface {
	FindById(id string) *types.Block
}
