package validator

import (
	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"strconv"
)

func ValidateOperationsTypes(operations []*types.Operation) (*string, *types.Error) {
	typeOperation := operations[0].Type
	length := len(operations)

	for i := 1; i < length; i++ {
		if operations[i].Type != typeOperation {
			return nil, errors.Errors[errors.MultipleOperationTypesPresent]
		}
	}

	return &typeOperation, nil
}

func ValidateOperationsSum(operations []*types.Operation) *types.Error {
	sum := 0
	for _, operation := range operations {
		amount, err := strconv.Atoi(operation.Amount.Value)
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
