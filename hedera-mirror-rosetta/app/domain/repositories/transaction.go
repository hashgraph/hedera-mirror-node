package repositories

import (
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
)

type TransactionRepository interface {
	FindById(id string) *types.Transaction
}
