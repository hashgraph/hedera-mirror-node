package repositories

import (
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
)

// AccountRepository Interface that all AccountRepository structs must implement
type AccountRepository interface {
	RetrieveBalanceAtBlock(addressStr string, consensusEnd int64) (*types.Amount, *rTypes.Error)
}
