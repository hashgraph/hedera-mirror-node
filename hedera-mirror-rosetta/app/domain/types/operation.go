package types

import (
	"strconv"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
)

// Operation is domain level struct used to represent Operation within Transaction
type Operation struct {
	Index   int64
	Type    string
	Status  string
	Account *Account
	Amount  int64
}

// FromRosettaOperation populates domain type Operartion from Rosetta type Operation
func FromRosettaOperation(rOperation *rTypes.Operation) (*Operation, *rTypes.Error) {
	acc, err := FromRosettaAccount(rOperation.Account)
	if err != nil {
		return nil, err
	}
	amount, err1 := strconv.Atoi(rOperation.Amount.Value)
	if err1 != nil {
		return nil, errors.Errors[errors.InvalidAmount]
	}

	return &Operation{
		Index:   rOperation.OperationIdentifier.Index,
		Type:    rOperation.Type,
		Status:  rOperation.Status,
		Account: acc,
		Amount:  int64(amount),
	}, nil
}

// ToRosettaOperation returns Rosetta type Operation from the current domain type Operation
func (t *Operation) ToRosettaOperation() *rTypes.Operation {
	rOperation := rTypes.Operation{
		OperationIdentifier: &rTypes.OperationIdentifier{
			Index: t.Index,
		},
		RelatedOperations: []*rTypes.OperationIdentifier{},
		Type:              t.Type,
		Status:            t.Status,
		Account:           t.Account.ToRosettaAccount(),
		Amount: &rTypes.Amount{
			Value: strconv.FormatInt(t.Amount, 10),
			Currency: &rTypes.Currency{
				Symbol:   "HBAR",
				Decimals: 8,
				Metadata: map[string]interface{}{
					"issuer": "Hedera",
				},
			},
		},
	}
	return &rOperation
}
