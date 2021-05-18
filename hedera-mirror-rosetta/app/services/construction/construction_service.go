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
	"crypto/rand"
	"encoding/hex"
	"math/big"
	"sort"
	"strconv"

	"github.com/coinbase/rosetta-sdk-go/server"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	hexutils "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/hex"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/parse"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/validator"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/types"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/hashgraph/hedera-sdk-go/v2/proto"
	log "github.com/sirupsen/logrus"
	"google.golang.org/protobuf/encoding/prototext"
)

// ConstructionAPIService implements the server.ConstructionAPIServicer interface.
type ConstructionAPIService struct {
	hederaClient      *hedera.Client
	nodeAccountIds    []hedera.AccountID
	nodeAccountIdsLen *big.Int
}

// ConstructionCombine implements the /construction/combine endpoint.
func (c *ConstructionAPIService) ConstructionCombine(
	ctx context.Context,
	request *rTypes.ConstructionCombineRequest,
) (*rTypes.ConstructionCombineResponse, *rTypes.Error) {
	if len(request.Signatures) != 1 {
		return nil, errors.ErrMultipleSignaturesPresent
	}

	unsignedTransaction, rErr := unmarshallTransactionFromHexString(request.UnsignedTransaction)
	if rErr != nil {
		return nil, rErr
	}

	frozenBodyBytes, rErr := getFrozenTransactionBodyBytes(unsignedTransaction)
	if rErr != nil {
		return nil, rErr
	}

	signature := request.Signatures[0]
	signatureBytes := signature.Bytes

	pubKey, err := hedera.PublicKeyFromBytes(signature.PublicKey.Bytes)
	if err != nil {
		return nil, errors.ErrInvalidPublicKey
	}

	if !ed25519.Verify(pubKey.Bytes(), frozenBodyBytes, signatureBytes) {
		return nil, errors.ErrInvalidSignatureVerification
	}

	transactionBytes, err := unsignedTransaction.AddSignature(pubKey, signatureBytes).ToBytes()
	if err != nil {
		return nil, errors.ErrTransactionMarshallingFailed
	}

	return &rTypes.ConstructionCombineResponse{
		SignedTransaction: hexutils.SafeAddHexPrefix(hex.EncodeToString(transactionBytes)),
	}, nil
}

// ConstructionDerive implements the /construction/derive endpoint.
func (c *ConstructionAPIService) ConstructionDerive(
	ctx context.Context,
	request *rTypes.ConstructionDeriveRequest,
) (*rTypes.ConstructionDeriveResponse, *rTypes.Error) {
	return nil, errors.ErrNotImplemented
}

// ConstructionHash implements the /construction/hash endpoint.
func (c *ConstructionAPIService) ConstructionHash(
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
		TransactionIdentifier: &rTypes.TransactionIdentifier{
			Hash: hexutils.SafeAddHexPrefix(hex.EncodeToString(hash[:])),
		},
		Metadata: nil,
	}, nil
}

// ConstructionMetadata implements the /construction/metadata endpoint.
func (c *ConstructionAPIService) ConstructionMetadata(
	ctx context.Context,
	request *rTypes.ConstructionMetadataRequest,
) (*rTypes.ConstructionMetadataResponse, *rTypes.Error) {
	return &rTypes.ConstructionMetadataResponse{
		Metadata: make(map[string]interface{}),
	}, nil
}

// ConstructionParse implements the /construction/parse endpoint.
func (c *ConstructionAPIService) ConstructionParse(
	ctx context.Context,
	request *rTypes.ConstructionParseRequest,
) (*rTypes.ConstructionParseResponse, *rTypes.Error) {
	transaction, rErr := unmarshallTransactionFromHexString(request.Transaction)
	if rErr != nil {
		return nil, rErr
	}

	transfers := transaction.GetHbarTransfers()
	var operations []*rTypes.Operation

	accountIds := make([]hedera.AccountID, 0, len(transfers))
	for accountId := range transfers {
		accountIds = append(accountIds, accountId)
	}

	// sort it so the order is stable
	sort.Slice(accountIds, func(i, j int) bool {
		return accountIds[i].String() < accountIds[j].String()
	})

	var signers []*rTypes.AccountIdentifier

	for i, accountId := range accountIds {
		amount := transfers[accountId].AsTinybar()
		operation := &rTypes.Operation{
			OperationIdentifier: &rTypes.OperationIdentifier{
				Index: int64(i),
			},
			Type: config.OperationTypeCryptoTransfer,
			Account: &rTypes.AccountIdentifier{
				Address: accountId.String(),
			},
			Amount: &rTypes.Amount{
				Value:    strconv.FormatInt(amount, 10),
				Currency: config.CurrencyHbar,
			},
		}

		operations = append(operations, operation)

		if request.Signed && amount < 0 {
			signers = append(signers, operation.Account)
		}
	}

	return &rTypes.ConstructionParseResponse{
		Operations:               operations,
		AccountIdentifierSigners: signers,
	}, nil
}

// ConstructionPayloads implements the /construction/payloads endpoint.
func (c *ConstructionAPIService) ConstructionPayloads(
	ctx context.Context,
	request *rTypes.ConstructionPayloadsRequest,
) (*rTypes.ConstructionPayloadsResponse, *rTypes.Error) {
	return c.handleCryptoTransferPayload(request.Operations)
}

// ConstructionPreprocess implements the /construction/preprocess endpoint.
func (c *ConstructionAPIService) ConstructionPreprocess(
	ctx context.Context,
	request *rTypes.ConstructionPreprocessRequest,
) (*rTypes.ConstructionPreprocessResponse, *rTypes.Error) {
	err := validator.ValidateOperationsSum(request.Operations)
	if err != nil {
		return nil, err
	}

	var sender hedera.AccountID

	for _, operation := range request.Operations {
		amount, err := parse.ToInt64(operation.Amount.Value)
		if err != nil {
			return nil, errors.ErrInvalidAmount
		}

		if amount > 0 {
			continue
		}

		sender, err = hedera.AccountIDFromString(operation.Account.Address)
		if err != nil {
			return nil, errors.ErrInvalidAccount
		}
	}

	return &rTypes.ConstructionPreprocessResponse{
		Options: make(map[string]interface{}),
		RequiredPublicKeys: []*rTypes.AccountIdentifier{
			{
				Address: sender.String(),
			},
		},
	}, nil
}

// ConstructionSubmit implements the /construction/submit endpoint.
func (c *ConstructionAPIService) ConstructionSubmit(
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
			Hash: hexutils.SafeAddHexPrefix(hex.EncodeToString(hash[:])),
		},
		Metadata: nil,
	}, nil
}

// handleCryptoTransferPayload handles the parse of all Rosetta Operations to a hedera.Transaction.
func (c *ConstructionAPIService) handleCryptoTransferPayload(operations []*rTypes.Operation) (
	*rTypes.ConstructionPayloadsResponse,
	*rTypes.Error,
) {
	err1 := validator.ValidateOperationsSum(operations)
	if err1 != nil {
		return nil, err1
	}

	transferTransaction := hedera.NewTransferTransaction()

	var sender hedera.AccountID

	for _, operation := range operations {
		account, err := hedera.AccountIDFromString(operation.Account.Address)
		if err != nil {
			return nil, errors.ErrInvalidAccount
		}

		amount, err := parse.ToInt64(operation.Amount.Value)
		if err != nil {
			return nil, errors.ErrInvalidAmount
		}

		transferTransaction.AddHbarTransfer(account, hedera.HbarFromTinybar(amount))

		if amount < 0 {
			sender = account
		}
	}

	network := c.hederaClient.GetNetwork()
	nodeAccountIds := make([]hedera.AccountID, 0, len(network))

	for _, nodeAccountId := range network {
		nodeAccountIds = append(nodeAccountIds, nodeAccountId)
	}

	// set to a single node account ID, so later can add signature
	_, err := transferTransaction.
		SetTransactionID(hedera.TransactionIDGenerate(sender)).
		SetNodeAccountIDs([]hedera.AccountID{c.getRandomNodeAccountId()}).
		Freeze()
	if err != nil {
		return nil, errors.ErrTransactionFreezeFailed
	}

	transactionBytes, err := transferTransaction.ToBytes()
	if err != nil {
		return nil, errors.ErrTransactionMarshallingFailed
	}

	frozenBodyBytes, rErr := getFrozenTransactionBodyBytes(transferTransaction)
	if rErr != nil {
		return nil, rErr
	}

	return &rTypes.ConstructionPayloadsResponse{
		UnsignedTransaction: hexutils.SafeAddHexPrefix(hex.EncodeToString(transactionBytes)),
		Payloads: []*rTypes.SigningPayload{{
			AccountIdentifier: &rTypes.AccountIdentifier{
				Address: sender.String(),
			},
			Bytes:         frozenBodyBytes,
			SignatureType: rTypes.Ed25519,
		}},
	}, nil
}

func (c *ConstructionAPIService) getRandomNodeAccountId() hedera.AccountID {
	index, err := rand.Int(rand.Reader, c.nodeAccountIdsLen)
	if err != nil {
		log.Errorf("Failed to get a random number, use 0 instead: %s", err)
		return c.nodeAccountIds[0]
	}

	return c.nodeAccountIds[index.Int64()]
}

// NewConstructionAPIService creates a new instance of a ConstructionAPIService.
func NewConstructionAPIService(network string, nodes types.NodeMap) (server.ConstructionAPIServicer, error) {
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

	return &ConstructionAPIService{
		hederaClient:      hederaClient,
		nodeAccountIds:    nodeAccountIds,
		nodeAccountIdsLen: big.NewInt(int64(len(nodeAccountIds))),
	}, nil
}

func getFrozenTransactionBodyBytes(transaction *hedera.TransferTransaction) ([]byte, *rTypes.Error) {
	signedTransaction := proto.SignedTransaction{}
	if err := prototext.Unmarshal([]byte(transaction.String()), &signedTransaction); err != nil {
		return nil, errors.ErrTransactionUnmarshallingFailed
	}

	return signedTransaction.BodyBytes, nil
}

func unmarshallTransactionFromHexString(transactionString string) (*hedera.TransferTransaction, *rTypes.Error) {
	transactionBytes, err := hex.DecodeString(hexutils.SafeRemoveHexPrefix(transactionString))
	if err != nil {
		return nil, errors.ErrTransactionDecodeFailed
	}

	transaction, err := hedera.TransactionFromBytes(transactionBytes)
	if err != nil {
		return nil, errors.ErrTransactionUnmarshallingFailed
	}

	transferTransaction, ok := transaction.(hedera.TransferTransaction)
	if !ok {
		return nil, errors.ErrTransactionInvalidType
	}

	return &transferTransaction, nil
}
