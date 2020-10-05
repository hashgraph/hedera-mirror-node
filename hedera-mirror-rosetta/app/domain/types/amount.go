package types

import (
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/parse"
	"strconv"
)

type Amount struct {
	Value int64
}

// FromRosettaAmount populates domain type Amount from Rosetta type Amount
func FromRosettaAmount(rAmount *rTypes.Amount) (*Amount, *rTypes.Error) {
	amount, err := parse.ToInt64(rAmount.Value)
	if err != nil {
		return nil, errors.Errors[errors.InvalidAmount]
	}

	return &Amount{
		Value: amount,
	}, nil
}

// ToRosettaAmount returns Rosetta type Amount from the current domain type Amount
func (b *Amount) ToRosettaAmount() *rTypes.Amount {
	return &rTypes.Amount{
		Value:    strconv.FormatInt(b.Value, 10),
		Currency: config.CurrencyHbar,
	}
}
