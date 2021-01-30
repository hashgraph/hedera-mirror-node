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

package construction

import (
	"context"
	"crypto/ed25519"
	"crypto/sha512"
	"encoding/hex"
	"fmt"
	"github.com/coinbase/rosetta-sdk-go/server"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	hexutils "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/hex"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/parse"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/validator"
	"github.com/hashgraph/hedera-sdk-go"
	"strconv"
)

// ConstructionAPIService implements the server.ConstructionAPIServicer interface.
type ConstructionAPIService struct {
	hederaClient *hedera.Client
}

// ConstructionCombine implements the /construction/combine endpoint.
func (c *ConstructionAPIService) ConstructionCombine(ctx context.Context, request *rTypes.ConstructionCombineRequest) (*rTypes.ConstructionCombineResponse, *rTypes.Error) {
	if len(request.Signatures) != 1 {
		return nil, errors.Errors[errors.MultipleSignaturesPresent]
	}

	request.UnsignedTransaction = hexutils.SafeRemoveHexPrefix(request.UnsignedTransaction)
	bytesTransaction, err := hex.DecodeString(request.UnsignedTransaction)
	if err != nil {
		return nil, errors.Errors[errors.TransactionDecodeFailed]
	}

	var transaction hedera.Transaction
	err = transaction.UnmarshalBinary(bytesTransaction)
	if err != nil {
		return nil, errors.Errors[errors.TransactionUnmarshallingFailed]
	}

	signature := request.Signatures[0]

	pubKey, err := hedera.Ed25519PublicKeyFromBytes(signature.PublicKey.Bytes)
	if err != nil {
		return nil, errors.Errors[errors.InvalidPublicKey]
	}

	verifiedSignature := ed25519.Verify(pubKey.Bytes(), transaction.BodyBytes(), signature.SigningPayload.Bytes)
	if verifiedSignature != true {
		return nil, errors.Errors[errors.InvalidSignatureVerification]
	}

	resultTransaction := transaction.SignWith(pubKey, func(bodyBytes []byte) []byte {
		return signature.SigningPayload.Bytes
	})

	bytesTransaction, err = resultTransaction.MarshalBinary()
	if err != nil {
		return nil, errors.Errors[errors.TransactionMarshallingFailed]
	}

	return &rTypes.ConstructionCombineResponse{
		SignedTransaction: hexutils.SafeAddHexPrefix(hex.EncodeToString(bytesTransaction)),
	}, nil
}

// ConstructionDerive implements the /construction/derive endpoint.
func (c *ConstructionAPIService) ConstructionDerive(ctx context.Context, request *rTypes.ConstructionDeriveRequest) (*rTypes.ConstructionDeriveResponse, *rTypes.Error) {
	return nil, errors.Errors[errors.NotImplemented]
}

// ConstructionHash implements the /construction/hash endpoint.
func (c *ConstructionAPIService) ConstructionHash(ctx context.Context, request *rTypes.ConstructionHashRequest) (*rTypes.TransactionIdentifierResponse, *rTypes.Error) {
	request.SignedTransaction = hexutils.SafeRemoveHexPrefix(request.SignedTransaction)

	bytesTransaction, err := hex.DecodeString(request.SignedTransaction)
	if err != nil {
		return nil, errors.Errors[errors.TransactionDecodeFailed]
	}

	digest := sha512.Sum384(bytesTransaction)

	return &rTypes.TransactionIdentifierResponse{
		TransactionIdentifier: &rTypes.TransactionIdentifier{
			Hash: hexutils.SafeAddHexPrefix(hex.EncodeToString(digest[:])),
		},
		Metadata: nil,
	}, nil
}

// ConstructionMetadata implements the /construction/metadata endpoint.
func (c *ConstructionAPIService) ConstructionMetadata(ctx context.Context, request *rTypes.ConstructionMetadataRequest) (*rTypes.ConstructionMetadataResponse, *rTypes.Error) {
	return &rTypes.ConstructionMetadataResponse{
		Metadata: make(map[string]interface{}),
	}, nil
}

// ConstructionParse implements the /construction/parse endpoint.
func (c *ConstructionAPIService) ConstructionParse(ctx context.Context, request *rTypes.ConstructionParseRequest) (*rTypes.ConstructionParseResponse, *rTypes.Error) {
	request.Transaction = hexutils.SafeRemoveHexPrefix(request.Transaction)
	bytesTransaction, err := hex.DecodeString(request.Transaction)
	if err != nil {
		return nil, errors.Errors[errors.TransactionDecodeFailed]
	}

	var transaction hedera.Transaction

	err = transaction.UnmarshalBinary(bytesTransaction)
	if err != nil {
		return nil, errors.Errors[errors.TransactionUnmarshallingFailed]
	}

	transfers := transaction.Body().GetCryptoTransfer().Transfers
	var operations []*rTypes.Operation

	for i, tx := range transfers.AccountAmounts {
		operations = append(operations, &rTypes.Operation{
			OperationIdentifier: &rTypes.OperationIdentifier{
				Index: int64(i),
			},
			Type: config.OperationTypeCryptoTransfer,
			Account: &rTypes.AccountIdentifier{
				Address: fmt.Sprintf("%d.%d.%d", tx.AccountID.ShardNum, tx.AccountID.RealmNum, tx.AccountID.AccountNum),
			},
			Amount: &rTypes.Amount{
				Value:    strconv.FormatInt(tx.Amount, 10),
				Currency: config.CurrencyHbar,
			},
		})
	}

	var accountIdentifiers []*rTypes.AccountIdentifier

	if request.Signed {
		signaturePairs := transaction.SignaturePairs()
		for _, signaturePair := range signaturePairs {
			accountIdentifiers = append(accountIdentifiers, &rTypes.AccountIdentifier{
				Address: hex.EncodeToString(signaturePair.PubKeyPrefix),
			})
		}
	}

	return &rTypes.ConstructionParseResponse{
		Operations:               operations,
		AccountIdentifierSigners: accountIdentifiers,
	}, nil
}

// ConstructionPayloads implements the /construction/payloads endpoint.
func (c *ConstructionAPIService) ConstructionPayloads(ctx context.Context, request *rTypes.ConstructionPayloadsRequest) (*rTypes.ConstructionPayloadsResponse, *rTypes.Error) {
	return c.handleCryptoTransferPayload(request.Operations)
}

// ConstructionPreprocess implements the /construction/preprocess endpoint.
func (c *ConstructionAPIService) ConstructionPreprocess(ctx context.Context, request *rTypes.ConstructionPreprocessRequest) (*rTypes.ConstructionPreprocessResponse, *rTypes.Error) {
	return c.handleCryptoTransferPreProcess(request.Operations)
}

// ConstructionSubmit implements the /construction/submit endpoint.
func (c *ConstructionAPIService) ConstructionSubmit(ctx context.Context, request *rTypes.ConstructionSubmitRequest) (*rTypes.TransactionIdentifierResponse, *rTypes.Error) {
	request.SignedTransaction = hexutils.SafeRemoveHexPrefix(request.SignedTransaction)
	bytesTransaction, err := hex.DecodeString(request.SignedTransaction)
	if err != nil {
		return nil, errors.Errors[errors.TransactionDecodeFailed]
	}

	var transaction hedera.Transaction

	err = transaction.UnmarshalBinary(bytesTransaction)
	if err != nil {
		return nil, errors.Errors[errors.TransactionUnmarshallingFailed]
	}

	_, err = transaction.Execute(c.hederaClient)
	if err != nil {
		return nil, errors.Errors[errors.TransactionSubmissionFailed]
	}

	digest := sha512.Sum384(bytesTransaction)

	return &rTypes.TransactionIdentifierResponse{
		TransactionIdentifier: &rTypes.TransactionIdentifier{
			Hash: hexutils.SafeAddHexPrefix(hex.EncodeToString(digest[:])),
		},
		Metadata: nil,
	}, nil
}

// handleCryptoTransferPayload handles the parse of all Rosetta Operations to a hedera.Transaction.
func (c *ConstructionAPIService) handleCryptoTransferPayload(operations []*rTypes.Operation) (*rTypes.ConstructionPayloadsResponse, *rTypes.Error) {
	err1 := validator.ValidateOperationsSum(operations)
	if err1 != nil {
		return nil, err1
	}

	builderTransaction := hedera.NewCryptoTransferTransaction()
	var sender hedera.AccountID

	for _, operation := range operations {
		account, err := hedera.AccountIDFromString(operation.Account.Address)
		if err != nil {
			return nil, errors.Errors[errors.InvalidAccount]
		}

		amount, err := parse.ToInt64(operation.Amount.Value)
		if err != nil {
			return nil, errors.Errors[errors.InvalidAmount]
		}

		if amount < 0 {
			sender = account
			builderTransaction.AddSender(
				sender,
				hedera.HbarFromTinybar(-amount))
		} else {
			builderTransaction.AddRecipient(account,
				hedera.HbarFromTinybar(amount))
		}
	}

	transaction, err := builderTransaction.SetTransactionID(hedera.NewTransactionID(sender)).Build(c.hederaClient)
	if err != nil {
		return nil, errors.Errors[errors.TransactionBuildFailed]
	}

	bytesTransaction, err := transaction.MarshalBinary()
	if err != nil {
		return nil, errors.Errors[errors.TransactionMarshallingFailed]
	}

	return &rTypes.ConstructionPayloadsResponse{
		UnsignedTransaction: hexutils.SafeAddHexPrefix(hex.EncodeToString(bytesTransaction)),
		Payloads: []*rTypes.SigningPayload{{
			AccountIdentifier: &rTypes.AccountIdentifier{
				Address: sender.String(),
			},
			Bytes: transaction.BodyBytes(),
		}},
	}, nil
}

// handleCryptoTransferPreProcess validates all Rosetta Operations.
func (c *ConstructionAPIService) handleCryptoTransferPreProcess(operations []*rTypes.Operation) (*rTypes.ConstructionPreprocessResponse, *rTypes.Error) {
	err := validator.ValidateOperationsSum(operations)
	if err != nil {
		return nil, err
	}

	return &rTypes.ConstructionPreprocessResponse{
		Options: make(map[string]interface{}),
	}, nil
}

// NewConstructionAPIService creates a new instance of a ConstructionAPIService.
func NewConstructionAPIService() server.ConstructionAPIServicer {
	return &ConstructionAPIService{
		hederaClient: hedera.ClientForTestnet(),
	}
}
