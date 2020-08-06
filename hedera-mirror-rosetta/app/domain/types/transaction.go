package types

import (
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
)

// Transaction is domain level struct used to represent Transaction conceptual mapping in Hedera
type Transaction struct {
	ID         string
	Operations []Operation
}

// FromRosettaTransaction populates domain type Transaction from Rosetta type Transaction
func (t *Transaction) FromRosettaTransaction(rTransaction *rTypes.Transaction) *Transaction {
	return nil // TODO Implement
}

// ToRosettaTransaction returns Rosetta type Transaction from the current domain type Transaction
func (t *Transaction) ToRosettaTransaction(*rTypes.Transaction) {
}
