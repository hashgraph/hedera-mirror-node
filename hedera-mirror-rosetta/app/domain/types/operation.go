package types

import (
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
)

// Operation is domain level struct used to represent Operation within Transaction
type Operation struct {
	Index   int64
	Type    string
	Status  string
	Account *Account
	Amount  *Amount
}

// FromRosettaOperation populates domain type Operation from Rosetta type Operation
func FromRosettaOperation(rOperation *rTypes.Operation) (*Operation, *rTypes.Error) {
	acc, err := FromRosettaAccount(rOperation.Account)
	if err != nil {
		return nil, err
	}
	amount, err := FromRosettaAmount(rOperation.Amount)
	if err != nil {
		return nil, err
	}
	return &Operation{
		Index:   rOperation.OperationIdentifier.Index,
		Type:    rOperation.Type,
		Status:  rOperation.Status,
		Account: acc,
		Amount:  amount,
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
		Amount:            t.Amount.ToRosettaAmount(),
	}
	return &rOperation
}
