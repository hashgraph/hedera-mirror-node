package types

import (
	"strconv"

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
func (t *Operation) ToRosettaOperation() *rTypes.Operation {
	rOperation := rTypes.Operation{
		OperationIdentifier: &rTypes.OperationIdentifier{
			Index: t.Index,
		},
		RelatedOperations: []*rTypes.OperationIdentifier{}, //TODO populate the other operations that are related to this one as per the Rosetta API
		Type:              t.Type,
		Status:            t.Status,
		Account: &rTypes.AccountIdentifier{
			Address: strconv.FormatInt(t.EntityID, 10), //TODO format the address properly (with realm and shard)
		},
		Amount: &rTypes.Amount{
			Value:    strconv.FormatInt(t.Amount, 10),
			Currency: &rTypes.Currency{}, //TODO populate the currency info (Decimals, Symbol etc.)
		},
	}
	return &rOperation
}
