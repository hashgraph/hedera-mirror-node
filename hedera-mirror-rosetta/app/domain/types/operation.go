package types

import (
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
)

// Operation is domain level struct used to represent Operation within Transaction
type Operation struct {
	Index    int64
	Type     string
	Status   string
	EntityID int64
	Amount   int64
}

// FromRosettaOperation populates domain type Operartion from Rosetta type Operation
func (t *Operation) FromRosettaOperation(rOperation *rTypes.Operation) *Operation {
	return nil // TODO Implement
}

// ToRosettaOperation returns Rosetta type Operation from the current domain type Operation
func (t *Operation) ToRosettaOperation(*rTypes.Operation) {
}
