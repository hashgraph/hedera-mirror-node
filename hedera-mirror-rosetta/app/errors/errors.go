package errors

import (
	"net/http"

	"github.com/coinbase/rosetta-sdk-go/types"
)

// Errors - map of all Errors that this API can return
var Errors = map[string]*types.Error{
	BlockNotFound:                New(BlockNotFound, http.StatusNotFound, true),
	TransactionNotFound:          New(TransactionNotFound, http.StatusNotFound, true),
	StartMustBeBeforeEnd:         New(StartMustBeBeforeEnd, http.StatusBadRequest, false),
	InvalidAccount:               New(InvalidAccount, http.StatusBadRequest, false),
	InvalidAmount:                New(InvalidAmount, http.StatusBadRequest, false),
	InvalidTransactionIdentifier: New(InvalidTransactionIdentifier, http.StatusBadRequest, false),
}

const (
	BlockNotFound                string = "Block not found"
	TransactionNotFound          string = "Transaction not found"
	StartMustBeBeforeEnd         string = "Start must be before end"
	InvalidAccount               string = "Invalid Account provided"
	InvalidAmount                string = "Invalid Amount provided"
	InvalidTransactionIdentifier string = "Invalid Transaction Identifier provided"
)

func New(message string, statusCode int32, retriable bool) *types.Error {
	return &types.Error{
		Message:   message,
		Code:      statusCode,
		Retriable: retriable,
		Details:   nil,
	}
}
