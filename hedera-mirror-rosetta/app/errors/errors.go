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
	MultipleSignaturesPresent      string = "Only one signature must be present"
	NodeIsStarting                 string = "Node is starting"
	NotImplemented                 string = "Not implemented"
	OperationResultsNotFound       string = "Operation Results not found"
	OperationTypesNotFound         string = "Operation Types not found"
	StartMustNotBeAfterEnd         string = "Start must not be after end"
	TransactionBuildFailed         string = "Transaction build failed"
	TransactionDecodeFailed        string = "Transaction Decode failed"
	TransactionRecordFetchFailed   string = "Transaction record fetch failed"
	TransactionMarshallingFailed   string = "Transaction marshalling failed"
	TransactionUnmarshallingFailed string = "Transaction unmarshalling failed"
	TransactionSubmissionFailed    string = "Transaction submission failed"
	TransactionNotFound            string = "Transaction not found"
	TransactionInvalidType         string = "Transaction invalid type"
	TransactionNotSigned           string = "Transaction not signed"
	TransactionHashFailed          string = "Transaction hash failed"
	TransactionFreezeFailed        string = "Transaction freeze failed"
	InvalidArgument                string = "Invalid argument"
	DatabaseError                  string = "Database error"
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
	ErrMultipleSignaturesPresent      = newError(MultipleSignaturesPresent, 110, false)
	ErrNodeIsStarting                 = newError(NodeIsStarting, 111, true)
	ErrNotImplemented                 = newError(NotImplemented, 112, false)
	ErrOperationResultsNotFound       = newError(OperationResultsNotFound, 113, true)
	ErrOperationTypesNotFound         = newError(OperationTypesNotFound, 114, true)
	ErrStartMustNotBeAfterEnd         = newError(StartMustNotBeAfterEnd, 115, false)
	ErrTransactionBuildFailed         = newError(TransactionBuildFailed, 116, false)
	ErrTransactionDecodeFailed        = newError(TransactionDecodeFailed, 117, false)
	ErrTransactionRecordFetchFailed   = newError(TransactionRecordFetchFailed, 118, false)
	ErrTransactionMarshallingFailed   = newError(TransactionMarshallingFailed, 119, false)
	ErrTransactionUnmarshallingFailed = newError(TransactionUnmarshallingFailed, 120, false)
	ErrTransactionSubmissionFailed    = newError(TransactionSubmissionFailed, 121, false)
	ErrTransactionNotFound            = newError(TransactionNotFound, 122, true)
	ErrEmptyOperations                = newError(EmptyOperations, 123, true)
	ErrTransactionInvalidType         = newError(TransactionInvalidType, 124, false)
	ErrTransactionNotSigned           = newError(TransactionNotSigned, 125, false)
	ErrTransactionHashFailed          = newError(TransactionHashFailed, 126, false)
	ErrTransactionFreezeFailed        = newError(TransactionFreezeFailed, 127, false)
	ErrInvalidArgument                = newError(InvalidArgument, 128, false)
	ErrDatabaseError                  = newError(DatabaseError, 129, true)
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
