package types

import (
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
)

// Transaction is domain level struct used to represent Transaction conceptual mapping in Hedera
type Transaction struct {
	Hash       string
	Operations []*Operation
}

// ToRosettaTransaction returns Rosetta type Transaction from the current domain type Transaction
func (t *Transaction) ToRosettaTransaction() *rTypes.Transaction {
	operations := make([]*rTypes.Operation, len(t.Operations))
	for i, o := range t.Operations {
		operations[i] = o.ToRosettaOperation()
	}

	rTransaction := &rTypes.Transaction{
		TransactionIdentifier: &rTypes.TransactionIdentifier{Hash: t.Hash},
		Operations:            operations,
	}
	return rTransaction
}
