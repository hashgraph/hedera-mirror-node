package domain

import (
	"github.com/coinbase/rosetta-sdk-go/types"
)

type Transaction struct {

}

funct (t *Transaction) fromRosettaTransaction(rTransaction *types.)

type TransactionRepository interface {
	findById(id string) (*Transaction)
}
