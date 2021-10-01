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

package services

import (
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"encoding/hex"
	"math/big"

	"github.com/coinbase/rosetta-sdk-go/server"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/config"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/services/construction"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/tools"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/hashgraph/hedera-sdk-go/v2/proto"
	log "github.com/sirupsen/logrus"
	"google.golang.org/protobuf/encoding/prototext"
)

const MetadataKeyValidStartNanos = "valid_start_nanos"

// constructionAPIService implements the server.ConstructionAPIServicer interface.
type constructionAPIService struct {
	hederaClient       *hedera.Client
	nodeAccountIds     []hedera.AccountID
	nodeAccountIdsLen  *big.Int
	transactionHandler construction.TransactionConstructor
}

// ConstructionCombine implements the /construction/combine endpoint.
func (c *constructionAPIService) ConstructionCombine(
	ctx context.Context,
	request *rTypes.ConstructionCombineRequest,
) (*rTypes.ConstructionCombineResponse, *rTypes.Error) {
	if len(request.Signatures) == 0 {
		return nil, errors.ErrNoSignature
	}

	transaction, rErr := unmarshallTransactionFromHexString(request.UnsignedTransaction)
	if rErr != nil {
		return nil, rErr
	}

	frozenBodyBytes, rErr := getFrozenTransactionBodyBytes(transaction)
	if rErr != nil {
		return nil, rErr
	}

	for _, signature := range request.Signatures {
		pubKey, err := hedera.PublicKeyFromBytes(signature.PublicKey.Bytes)
		if err != nil {
			return nil, errors.ErrInvalidPublicKey
		}

		if !ed25519.Verify(pubKey.Bytes(), frozenBodyBytes, signature.Bytes) {
			return nil, errors.ErrInvalidSignatureVerification
		}

		if rErr := addSignature(transaction, pubKey, signature.Bytes); rErr != nil {
			return nil, rErr
		}
	}

	transactionBytes, err := transaction.ToBytes()
	if err != nil {
		return nil, errors.ErrTransactionMarshallingFailed
	}

	return &rTypes.ConstructionCombineResponse{
		SignedTransaction: tools.SafeAddHexPrefix(hex.EncodeToString(transactionBytes)),
	}, nil
}

// ConstructionDerive implements the /construction/derive endpoint.
func (c *constructionAPIService) ConstructionDerive(
	ctx context.Context,
	request *rTypes.ConstructionDeriveRequest,
) (*rTypes.ConstructionDeriveResponse, *rTypes.Error) {
	return nil, errors.ErrNotImplemented
}

// ConstructionHash implements the /construction/hash endpoint.
func (c *constructionAPIService) ConstructionHash(
	ctx context.Context,
	request *rTypes.ConstructionHashRequest,
) (*rTypes.TransactionIdentifierResponse, *rTypes.Error) {
	signedTransaction, rErr := unmarshallTransactionFromHexString(request.SignedTransaction)
	if rErr != nil {
		return nil, rErr
	}

	hash, err := signedTransaction.GetTransactionHash()
	if err != nil {
		return nil, errors.ErrTransactionHashFailed
	}

	return &rTypes.TransactionIdentifierResponse{
		TransactionIdentifier: &rTypes.TransactionIdentifier{Hash: tools.SafeAddHexPrefix(hex.EncodeToString(hash[:]))},
	}, nil
}

// ConstructionMetadata implements the /construction/metadata endpoint.
func (c *constructionAPIService) ConstructionMetadata(
	ctx context.Context,
	request *rTypes.ConstructionMetadataRequest,
) (*rTypes.ConstructionMetadataResponse, *rTypes.Error) {
	return &rTypes.ConstructionMetadataResponse{
		Metadata: make(map[string]interface{}),
	}, nil
}

// ConstructionParse implements the /construction/parse endpoint.
func (c *constructionAPIService) ConstructionParse(
	ctx context.Context,
	request *rTypes.ConstructionParseRequest,
) (*rTypes.ConstructionParseResponse, *rTypes.Error) {
	transaction, err := unmarshallTransactionFromHexString(request.Transaction)
	if err != nil {
		return nil, err
	}

	operations, accounts, err := c.transactionHandler.Parse(ctx, transaction)
	if err != nil {
		return nil, err
	}

	signers := make([]*rTypes.AccountIdentifier, 0, len(accounts))
	if request.Signed {
		for _, account := range accounts {
			signers = append(signers, &rTypes.AccountIdentifier{Address: account.String()})
		}
	}

	return &rTypes.ConstructionParseResponse{
		Operations:               operations,
		AccountIdentifierSigners: signers,
	}, nil
}

// ConstructionPayloads implements the /construction/payloads endpoint.
func (c *constructionAPIService) ConstructionPayloads(
	ctx context.Context,
	request *rTypes.ConstructionPayloadsRequest,
) (*rTypes.ConstructionPayloadsResponse, *rTypes.Error) {
	validStartNanos, rErr := c.getValidStartNanos(request.Metadata)
	if rErr != nil {
		return nil, rErr
	}

	transaction, signers, rErr := c.transactionHandler.Construct(
		ctx,
		c.getRandomNodeAccountId(),
		request.Operations,
		validStartNanos,
	)
	if rErr != nil {
		return nil, rErr
	}

	bytes, err := transaction.ToBytes()
	if err != nil {
		return nil, errors.ErrTransactionMarshallingFailed
	}

	frozenBodyBytes, rErr := getFrozenTransactionBodyBytes(transaction)
	if rErr != nil {
		return nil, rErr
	}

	signingPayloads := make([]*rTypes.SigningPayload, 0, len(signers))
	for _, signer := range signers {
		signingPayloads = append(signingPayloads, &rTypes.SigningPayload{
			AccountIdentifier: &rTypes.AccountIdentifier{Address: signer.String()},
			Bytes:             frozenBodyBytes,
			SignatureType:     rTypes.Ed25519,
		})
	}

	return &rTypes.ConstructionPayloadsResponse{
		UnsignedTransaction: tools.SafeAddHexPrefix(hex.EncodeToString(bytes)),
		Payloads:            signingPayloads,
	}, nil
}

// ConstructionPreprocess implements the /construction/preprocess endpoint.
func (c *constructionAPIService) ConstructionPreprocess(
	ctx context.Context,
	request *rTypes.ConstructionPreprocessRequest,
) (*rTypes.ConstructionPreprocessResponse, *rTypes.Error) {
	signers, err := c.transactionHandler.Preprocess(ctx, request.Operations)
	if err != nil {
		return nil, err
	}

	requiredPublicKeys := make([]*rTypes.AccountIdentifier, 0, len(signers))
	for _, signer := range signers {
		requiredPublicKeys = append(requiredPublicKeys, &rTypes.AccountIdentifier{Address: signer.String()})
	}

	return &rTypes.ConstructionPreprocessResponse{
		Options:            make(map[string]interface{}),
		RequiredPublicKeys: requiredPublicKeys,
	}, nil
}

// ConstructionSubmit implements the /construction/submit endpoint.
func (c *constructionAPIService) ConstructionSubmit(
	ctx context.Context,
	request *rTypes.ConstructionSubmitRequest,
) (*rTypes.TransactionIdentifierResponse, *rTypes.Error) {
	transaction, rErr := unmarshallTransactionFromHexString(request.SignedTransaction)
	if rErr != nil {
		return nil, rErr
	}

	hash, err := transaction.GetTransactionHash()
	if err != nil {
		return nil, errors.ErrTransactionHashFailed
	}

	_, err = transaction.Execute(c.hederaClient)
	if err != nil {
		log.Errorf("Failed to execute transaction %s: %s", transaction.GetTransactionID(), err)
		return nil, errors.ErrTransactionSubmissionFailed
	}

	return &rTypes.TransactionIdentifierResponse{
		TransactionIdentifier: &rTypes.TransactionIdentifier{
			Hash: tools.SafeAddHexPrefix(hex.EncodeToString(hash[:])),
		},
		Metadata: nil,
	}, nil
}

func (c *constructionAPIService) getRandomNodeAccountId() hedera.AccountID {
	index, err := rand.Int(rand.Reader, c.nodeAccountIdsLen)
	if err != nil {
		log.Errorf("Failed to get a random number, use 0 instead: %s", err)
		return c.nodeAccountIds[0]
	}

	return c.nodeAccountIds[index.Int64()]
}

func (c *constructionAPIService) getValidStartNanos(metadata map[string]interface{}) (int64, *rTypes.Error) {
	var validStartNanos int64
	if metadata != nil && metadata[MetadataKeyValidStartNanos] != nil {
		nanos, ok := metadata[MetadataKeyValidStartNanos].(string)
		if !ok {
			return validStartNanos, errors.ErrInvalidArgument
		}

		var err error
		if validStartNanos, err = tools.ToInt64(nanos); err != nil {
			return validStartNanos, errors.ErrInvalidArgument
		}
	}

	return validStartNanos, nil
}

// NewConstructionAPIService creates a new instance of a constructionAPIService.
func NewConstructionAPIService(
	network string,
	nodes config.NodeMap,
	transactionConstructor construction.TransactionConstructor,
) (server.ConstructionAPIServicer, error) {
	var err error
	var hederaClient *hedera.Client

	// there is no live demo network, it's only used to run rosetta test, so replace it with testnet
	if network == "demo" {
		log.Info("Use testnet instead of demo")
		network = "testnet"
	}

	if len(nodes) > 0 {
		hederaClient = hedera.ClientForNetwork(nodes)
	} else if hederaClient, err = hedera.ClientForName(network); err != nil {
		return nil, err
	}

	networkMap := hederaClient.GetNetwork()
	nodeAccountIds := make([]hedera.AccountID, 0, len(networkMap))
	for _, nodeAccountId := range networkMap {
		nodeAccountIds = append(nodeAccountIds, nodeAccountId)
	}

	return &constructionAPIService{
		hederaClient:       hederaClient,
		nodeAccountIds:     nodeAccountIds,
		nodeAccountIdsLen:  big.NewInt(int64(len(nodeAccountIds))),
		transactionHandler: transactionConstructor,
	}, nil
}

func addSignature(transaction interfaces.Transaction, pubKey hedera.PublicKey, signature []byte) *rTypes.Error {
	switch tx := transaction.(type) {
	// these transaction types are what the construction service supports
	case *hedera.TokenAssociateTransaction:
		tx.AddSignature(pubKey, signature)
	case *hedera.TokenBurnTransaction:
		tx.AddSignature(pubKey, signature)
	case *hedera.TokenCreateTransaction:
		tx.AddSignature(pubKey, signature)
	case *hedera.TokenDeleteTransaction:
		tx.AddSignature(pubKey, signature)
	case *hedera.TokenDissociateTransaction:
		tx.AddSignature(pubKey, signature)
	case *hedera.TokenFreezeTransaction:
		tx.AddSignature(pubKey, signature)
	case *hedera.TokenGrantKycTransaction:
		tx.AddSignature(pubKey, signature)
	case *hedera.TokenMintTransaction:
		tx.AddSignature(pubKey, signature)
	case *hedera.TokenRevokeKycTransaction:
		tx.AddSignature(pubKey, signature)
	case *hedera.TokenUnfreezeTransaction:
		tx.AddSignature(pubKey, signature)
	case *hedera.TokenUpdateTransaction:
		tx.AddSignature(pubKey, signature)
	case *hedera.TokenWipeTransaction:
		tx.AddSignature(pubKey, signature)
	case *hedera.TransferTransaction:
		tx.AddSignature(pubKey, signature)
	default:
		return errors.ErrTransactionInvalidType
	}

	return nil
}

func getFrozenTransactionBodyBytes(transaction interfaces.Transaction) ([]byte, *rTypes.Error) {
	signedTransaction := proto.SignedTransaction{}
	if err := prototext.Unmarshal([]byte(transaction.String()), &signedTransaction); err != nil {
		return nil, errors.ErrTransactionUnmarshallingFailed
	}

	return signedTransaction.BodyBytes, nil
}

func unmarshallTransactionFromHexString(transactionString string) (interfaces.Transaction, *rTypes.Error) {
	transactionBytes, err := hex.DecodeString(tools.SafeRemoveHexPrefix(transactionString))
	if err != nil {
		return nil, errors.ErrTransactionDecodeFailed
	}

	transaction, err := hedera.TransactionFromBytes(transactionBytes)
	if err != nil {
		return nil, errors.ErrTransactionUnmarshallingFailed
	}

	switch tx := transaction.(type) {
	// these transaction types are what the construction service supports
	case hedera.TokenAssociateTransaction:
		return &tx, nil
	case hedera.TokenBurnTransaction:
		return &tx, nil
	case hedera.TokenCreateTransaction:
		return &tx, nil
	case hedera.TokenDeleteTransaction:
		return &tx, nil
	case hedera.TokenDissociateTransaction:
		return &tx, nil
	case hedera.TokenFreezeTransaction:
		return &tx, nil
	case hedera.TokenGrantKycTransaction:
		return &tx, nil
	case hedera.TokenMintTransaction:
		return &tx, nil
	case hedera.TokenRevokeKycTransaction:
		return &tx, nil
	case hedera.TokenUnfreezeTransaction:
		return &tx, nil
	case hedera.TokenUpdateTransaction:
		return &tx, nil
	case hedera.TokenWipeTransaction:
		return &tx, nil
	case hedera.TransferTransaction:
		return &tx, nil
	default:
		return nil, errors.ErrTransactionInvalidType
	}
}
