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

// Errors - map of all Errors that this API can return
var Errors = map[string]*types.Error{
	AccountNotFound:                New(AccountNotFound, 100, true),
	BlockNotFound:                  New(BlockNotFound, 101, true),
	InvalidAccount:                 New(InvalidAccount, 102, false),
	InvalidAmount:                  New(InvalidAmount, 103, false),
	InvalidOperationsAmount:        New(InvalidOperationsAmount, 104, false),
	InvalidOperationsTotalAmount:   New(InvalidOperationsTotalAmount, 105, false),
	InvalidPublicKey:               New(InvalidPublicKey, 106, false),
	InvalidSignatureVerification:   New(InvalidSignatureVerification, 107, false),
	InvalidTransactionIdentifier:   New(InvalidTransactionIdentifier, 108, false),
	MultipleOperationTypesPresent:  New(MultipleOperationTypesPresent, 109, false),
	MultipleSignaturesPresent:      New(MultipleSignaturesPresent, 110, false),
	NodeIsStarting:                 New(NodeIsStarting, 111, true),
	NotImplemented:                 New(NotImplemented, 112, false),
	OperationStatusesNotFound:      New(OperationStatusesNotFound, 113, true),
	OperationTypesNotFound:         New(OperationTypesNotFound, 114, true),
	StartMustNotBeAfterEnd:         New(StartMustNotBeAfterEnd, 115, false),
	TransactionBuildFailed:         New(TransactionBuildFailed, 116, false),
	TransactionDecodeFailed:        New(TransactionDecodeFailed, 117, false),
	TransactionRecordFetchFailed:   New(TransactionRecordFetchFailed, 118, false),
	TransactionMarshallingFailed:   New(TransactionMarshallingFailed, 119, false),
	TransactionUnmarshallingFailed: New(TransactionUnmarshallingFailed, 120, false),
	TransactionSubmissionFailed:    New(TransactionSubmissionFailed, 121, false),
	TransactionNotFound:            New(TransactionNotFound, 122, true),
	EmptyOperations:                New(EmptyOperations, 123, true),
	TransactionInvalidType:         New(TransactionInvalidType, 124, false),
	TransactionNotSigned:           New(TransactionNotSigned, 125, false),
	TransactionHashFailed:          New(TransactionHashFailed, 126, false),
	TransactionFreezeFailed:        New(TransactionFreezeFailed, 127, false),
	TransactionSignatureFailed:     New(TransactionSignatureFailed, 128, false),
	InternalServerError:            New(InternalServerError, 500, true),
}

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
	OperationStatusesNotFound      string = "Operation Statuses not found"
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
	TransactionSignatureFailed     string = "Transaction signature error"
	InternalServerError            string = "Internal Server Error"
)

func New(message string, statusCode int32, retriable bool) *types.Error {
	return &types.Error{
		Message:   message,
		Code:      statusCode,
		Retriable: retriable,
		Details:   nil,
	}
}
