package types

import (
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
)

type Transaction struct {
	Id string
}

func (t *Transaction) FromRosettaTransaction(rTransaction *rTypes.Transaction) *Transaction {
	return nil // TODO Implement
}

func (t *Transaction) ToRosettaTransaction(*rTypes.Transaction) {
}
