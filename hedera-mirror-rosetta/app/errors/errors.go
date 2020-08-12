package errors

import (
	"net/http"

	"github.com/coinbase/rosetta-sdk-go/types"
)

// Errors - map of all Errors that this API can return
var Errors = map[string]*types.Error{
	BlockNotFound:                  New(BlockNotFound, http.StatusNotFound, true),
	TransactionBuildFailed:         New(TransactionBuildFailed, http.StatusBadRequest, false),
	TransactionDecodeFailed:        New(TransactionDecodeFailed, http.StatusBadRequest, false),
	TransactionMarshallingFailed:   New(TransactionMarshallingFailed, http.StatusBadRequest, false),
	TransactionUnmarshallingFailed: New(TransactionUnmarshallingFailed, http.StatusBadRequest, false),
	TransactionNotFound:            New(TransactionNotFound, http.StatusNotFound, true),
	MultipleOperationTypesPresent:  New(MultipleOperationTypesPresent, http.StatusBadRequest, false),
	StartMustBeBeforeEnd:           New(StartMustBeBeforeEnd, http.StatusBadRequest, false),
	InvalidAccount:                 New(InvalidAccount, http.StatusBadRequest, false),
	InvalidAmount:                  New(InvalidAmount, http.StatusBadRequest, false),
	InvalidOperationsAmount:        New(InvalidOperationsAmount, http.StatusBadRequest, false),
	InvalidOperationsTotalAmount:   New(InvalidOperationsTotalAmount, http.StatusBadRequest, false),
	InvalidTransactionIdentifier:   New(InvalidTransactionIdentifier, http.StatusBadRequest, false),
	NotImplemented:                 New(NotImplemented, http.StatusNotImplemented, false),
}

const (
	BlockNotFound                  string = "Block not found"
	TransactionBuildFailed         string = "Transaction build failed"
	TransactionDecodeFailed        string = "Transaction Decode failed"
	TransactionMarshallingFailed   string = "Transaction marshalling failed"
	TransactionUnmarshallingFailed string = "Transaction unmarshalling failed"
	TransactionNotFound            string = "Transaction not found"
	MultipleOperationTypesPresent  string = "Only one Operation Type must be present"
	StartMustBeBeforeEnd           string = "Start must be before end"
	InvalidAccount                 string = "Invalid Account provided"
	InvalidAmount                  string = "Invalid Amount provided"
	InvalidOperationsAmount        string = "Invalid Operations amount provided"
	InvalidOperationsTotalAmount   string = "Operations total amount must be 0"
	InvalidTransactionIdentifier   string = "Invalid Transaction Identifier provided"
	NotImplemented                 string = "Not implemented"
)

func New(message string, statusCode int32, retriable bool) *types.Error {
	return &types.Error{
		Message:   message,
		Code:      statusCode,
		Retriable: retriable,
		Details:   nil,
	}
}
