package repositories

import (
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
)

// TransactionRepository Interface that all TransactionRepository structs must implement
type TransactionRepository interface {
	FindByTimestamp(timestamp int64) *types.Transaction
	FindBetween(start int64, end int64) ([]*types.Transaction, *rTypes.Error)
	GetTypes() map[int]string
	GetStatuses() map[int]string
}
