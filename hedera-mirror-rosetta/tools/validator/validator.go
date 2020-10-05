package validator

import (
	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/parse"
)

func ValidateOperationsSum(operations []*types.Operation) *types.Error {
	var sum int64 = 0
	for _, operation := range operations {
		amount, err := parse.ToInt64(operation.Amount.Value)
		if err != nil {
			return errors.Errors[errors.InvalidAmount]
		}
		sum += amount
	}

	if sum != 0 {
		return errors.Errors[errors.InvalidOperationsTotalAmount]
	}

	return nil
}
