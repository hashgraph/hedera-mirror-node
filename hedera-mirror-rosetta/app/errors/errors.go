package errors

import (
	"github.com/coinbase/rosetta-sdk-go/types"
)

// Errors - map of all Errors that this API can return
var Errors = map[string]*types.Error{
	AppendSignatureFailed:          New(AppendSignatureFailed, 100, false),
	AccountNotFound:                New(AccountNotFound, 101, true),
	BlockNotFound:                  New(BlockNotFound, 102, true),
	InvalidAccount:                 New(InvalidAccount, 103, false),
	InvalidAmount:                  New(InvalidAmount, 104, false),
	InvalidOperationsAmount:        New(InvalidOperationsAmount, 105, false),
	InvalidOperationsTotalAmount:   New(InvalidOperationsTotalAmount, 106, false),
	InvalidPublicKey:               New(InvalidPublicKey, 107, false),
	InvalidTransactionIdentifier:   New(InvalidTransactionIdentifier, 108, false),
	MultipleOperationTypesPresent:  New(MultipleOperationTypesPresent, 109, false),
	MultipleSignaturesPresent:      New(MultipleSignaturesPresent, 110, false),
	NotImplemented:                 New(NotImplemented, 111, false),
	StartMustBeBeforeEnd:           New(StartMustBeBeforeEnd, 112, false),
	TransactionBuildFailed:         New(TransactionBuildFailed, 113, false),
	TransactionDecodeFailed:        New(TransactionDecodeFailed, 114, false),
	TransactionRecordFetchFailed:   New(TransactionRecordFetchFailed, 115, false),
	TransactionMarshallingFailed:   New(TransactionMarshallingFailed, 116, false),
	TransactionUnmarshallingFailed: New(TransactionUnmarshallingFailed, 117, false),
	TransactionSubmissionFailed:    New(TransactionSubmissionFailed, 118, false),
	TransactionNotFound:            New(TransactionNotFound, 119, true),
}

const (
	AppendSignatureFailed          string = "Combine unsigned transaction with signature failed"
	AccountNotFound                string = "Account not found"
	BlockNotFound                  string = "Block not found"
	CreateAccountDbIdFailed        string = "Cannot create Account ID from encoded DB ID: %x"
	InvalidAccount                 string = "Invalid Account provided"
	InvalidAmount                  string = "Invalid Amount provided"
	InvalidOperationsAmount        string = "Invalid Operations amount provided"
	InvalidOperationsTotalAmount   string = "Operations total amount must be 0"
	InvalidPublicKey               string = "Invalid Public Key provided"
	InvalidTransactionIdentifier   string = "Invalid Transaction Identifier provided"
	MultipleOperationTypesPresent  string = "Only one Operation Type must be present"
	MultipleSignaturesPresent      string = "Only one signature must be present"
	NotImplemented                 string = "Not implemented"
	StartMustBeBeforeEnd           string = "Start must be before end"
	TransactionBuildFailed         string = "Transaction build failed"
	TransactionDecodeFailed        string = "Transaction Decode failed"
	TransactionRecordFetchFailed   string = "Transaction record fetch failed"
	TransactionMarshallingFailed   string = "Transaction marshalling failed"
	TransactionUnmarshallingFailed string = "Transaction unmarshalling failed"
	TransactionSubmissionFailed    string = "Transaction submission failed"
	TransactionNotFound            string = "Transaction not found"
)

func New(message string, statusCode int32, retriable bool) *types.Error {
	return &types.Error{
		Message:   message,
		Code:      statusCode,
		Retriable: retriable,
		Details:   nil,
	}
}
