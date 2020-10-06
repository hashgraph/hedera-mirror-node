package types

import (
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	"strconv"
)

type Amount struct {
	Value int64
}

// ToRosettaAmount returns Rosetta type Amount from the current domain type Amount
func (b *Amount) ToRosettaAmount() *rTypes.Amount {
	return &rTypes.Amount{
		Value:    strconv.FormatInt(b.Value, 10),
		Currency: config.CurrencyHbar,
	}
}
