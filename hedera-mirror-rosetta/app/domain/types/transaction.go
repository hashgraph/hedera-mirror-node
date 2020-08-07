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
func (t *Transaction) FromRosettaTransaction(rTransaction *rTypes.Transaction) {
	t.ID = rTransaction.TransactionIdentifier.Hash

	operations := make([]*Operation, len(rTransaction.Operations))
	for i, rosettaO := range rTransaction.Operations {
		o := &Operation{}
		o.FromRosettaOperation(rosettaO)
		operations[i] = o
	}
	t.Operations = operations
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
