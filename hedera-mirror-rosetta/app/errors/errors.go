/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

package errors

import (
	"github.com/coinbase/rosetta-sdk-go/types"
)

const (
	AccountNotFound                string = "Account not found"
	BlockNotFound                  string = "Block not found"
	CreateAccountDbIdFailed        string = "An error occurred while creating Account ID from encoded DB ID: %x"
	EmptyOperations                string = "Empty operations provided"
	InvalidAccount                 string = "Invalid Account provided"
	InvalidAmount                  string = "Invalid Amount provided"
	InvalidOperationsAmount        string = "Invalid Operations amount provided"
	InvalidOperationsTotalAmount   string = "Operations total amount must be 0"
	InvalidPublicKey               string = "Invalid Public Key provided"
	InvalidSignatureVerification   string = "Invalid signature verification"
	InvalidTransactionIdentifier   string = "Invalid Transaction Identifier provided"
	MultipleOperationTypesPresent  string = "Only one Operation Type must be present"
	NodeIsStarting                 string = "Node is starting"
	NotImplemented                 string = "Not implemented"
	OperationResultsNotFound       string = "Operation Results not found"
	OperationTypesNotFound         string = "Operation Types not found"
	StartMustNotBeAfterEnd         string = "Start must not be after end"
	TransactionDecodeFailed        string = "Transaction Decode failed"
	TransactionMarshallingFailed   string = "Transaction marshalling failed"
	TransactionUnmarshallingFailed string = "Transaction unmarshalling failed"
	TransactionSubmissionFailed    string = "Transaction submission failed"
	TransactionNotFound            string = "Transaction not found"
	TransactionInvalidType         string = "Transaction invalid type"
	TransactionHashFailed          string = "Transaction hash failed"
	TransactionFreezeFailed        string = "Transaction freeze failed"
	InvalidArgument                string = "Invalid argument"
	DatabaseError                  string = "Database error"
	InvalidOperationMetadata       string = "Invalid operation metadata"
	OperationTypeUnsupported       string = "Operation type unsupported"
	InvalidOperationType           string = "Invalid operation type"
	NoSignature                    string = "No signature"
	InvalidOperations              string = "Invalid operations"
	InvalidToken                   string = "Invalid token"
	TokenNotFound                  string = "Token not found"
	InvalidTransaction             string = "Invalid transaction"
	InvalidCurrency                string = "Invalid currency"
	InternalServerError            string = "Internal Server Error"
)

var (
	ErrAccountNotFound                = newError(AccountNotFound, 100, true)
	ErrBlockNotFound                  = newError(BlockNotFound, 101, true)
	ErrInvalidAccount                 = newError(InvalidAccount, 102, false)
	ErrInvalidAmount                  = newError(InvalidAmount, 103, false)
	ErrInvalidOperationsAmount        = newError(InvalidOperationsAmount, 104, false)
	ErrInvalidOperationsTotalAmount   = newError(InvalidOperationsTotalAmount, 105, false)
	ErrInvalidPublicKey               = newError(InvalidPublicKey, 106, false)
	ErrInvalidSignatureVerification   = newError(InvalidSignatureVerification, 107, false)
	ErrInvalidTransactionIdentifier   = newError(InvalidTransactionIdentifier, 108, false)
	ErrMultipleOperationTypesPresent  = newError(MultipleOperationTypesPresent, 109, false)
	ErrNodeIsStarting                 = newError(NodeIsStarting, 110, true)
	ErrNotImplemented                 = newError(NotImplemented, 111, false)
	ErrOperationResultsNotFound       = newError(OperationResultsNotFound, 112, true)
	ErrOperationTypesNotFound         = newError(OperationTypesNotFound, 113, true)
	ErrStartMustNotBeAfterEnd         = newError(StartMustNotBeAfterEnd, 114, false)
	ErrTransactionDecodeFailed        = newError(TransactionDecodeFailed, 115, false)
	ErrTransactionMarshallingFailed   = newError(TransactionMarshallingFailed, 116, false)
	ErrTransactionUnmarshallingFailed = newError(TransactionUnmarshallingFailed, 117, false)
	ErrTransactionSubmissionFailed    = newError(TransactionSubmissionFailed, 118, false)
	ErrTransactionNotFound            = newError(TransactionNotFound, 119, true)
	ErrEmptyOperations                = newError(EmptyOperations, 120, false)
	ErrTransactionInvalidType         = newError(TransactionInvalidType, 121, false)
	ErrTransactionHashFailed          = newError(TransactionHashFailed, 122, false)
	ErrTransactionFreezeFailed        = newError(TransactionFreezeFailed, 123, false)
	ErrInvalidArgument                = newError(InvalidArgument, 124, false)
	ErrDatabaseError                  = newError(DatabaseError, 125, true)
	ErrInvalidOperationMetadata       = newError(InvalidOperationMetadata, 126, false)
	ErrOperationTypeUnsupported       = newError(OperationTypeUnsupported, 127, false)
	ErrInvalidOperationType           = newError(InvalidOperationType, 128, false)
	ErrNoSignature                    = newError(NoSignature, 129, false)
	ErrInvalidOperations              = newError(InvalidOperations, 130, false)
	ErrInvalidToken                   = newError(InvalidToken, 131, false)
	ErrTokenNotFound                  = newError(TokenNotFound, 132, false)
	ErrInvalidTransaction             = newError(InvalidTransaction, 133, false)
	ErrInvalidCurrency                = newError(InvalidCurrency, 134, false)
	ErrInternalServerError            = newError(InternalServerError, 500, true)

	Errors = make([]*types.Error, 0)
)

func newError(message string, statusCode int32, retriable bool) *types.Error {
	err := &types.Error{
		Message:   message,
		Code:      statusCode,
		Retriable: retriable,
		Details:   nil,
	}
	Errors = append(Errors, err)

	return err
}
