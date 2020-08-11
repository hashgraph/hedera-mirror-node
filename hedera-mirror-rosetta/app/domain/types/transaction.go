package types

import (
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
)

// Transaction is domain level struct used to represent Transaction conceptual mapping in Hedera
type Transaction struct {
	ID         string
	Operations []*Operation
}

// FromRosettaTransaction populates domain type Transaction from Rosetta type Transaction
func FromRosettaTransaction(rTransaction *rTypes.Transaction) (*Transaction, *rTypes.Error) {
	operations := make([]*Operation, len(rTransaction.Operations))
	for i, rosettaO := range rTransaction.Operations {
		o, err := FromRosettaOperation(rosettaO)
		if err != nil {
			return nil, err
		}
		operations[i] = o
	}

	return &Transaction{
		ID:         rTransaction.TransactionIdentifier.Hash, // TODO this must be fixed. The ID must not be the Hash but a more complex construction specified in the TransactionRepository
		Operations: operations,
	}, nil
}

// ToRosettaTransaction returns Rosetta type Transaction from the current domain type Transaction
func (t *Transaction) ToRosettaTransaction() *rTypes.Transaction {
	operations := make([]*rTypes.Operation, len(t.Operations))
	for i, o := range t.Operations {
		operations[i] = o.ToRosettaOperation()
	}

	rTransaction := &rTypes.Transaction{
		TransactionIdentifier: &rTypes.TransactionIdentifier{Hash: t.ID},
		Operations:            operations,
	}
	return rTransaction
}
