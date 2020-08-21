package services

import (
	"context"
	"crypto/sha512"
	"encoding/hex"
	"fmt"
	"github.com/coinbase/rosetta-sdk-go/server"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	hexutils "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/hex"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/validator"
	"github.com/hashgraph/hedera-sdk-go"
	"strconv"
)

type ConstructionService struct {
	hederaClient *hedera.Client
}

func (c *ConstructionService) ConstructionCombine(ctx context.Context, request *rTypes.ConstructionCombineRequest) (*rTypes.ConstructionCombineResponse, *rTypes.Error) {
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

	resultTransaction, err := transaction.AppendSignature(pubKey, signature.SigningPayload.Bytes)
	if err != nil {
		return nil, errors.Errors[errors.AppendSignatureFailed]
	}

	bytesTransaction, err = resultTransaction.MarshalBinary()
	if err != nil {
		return nil, errors.Errors[errors.TransactionMarshallingFailed]
	}

	return &rTypes.ConstructionCombineResponse{
		SignedTransaction: hexutils.SafeAddHexPrefix(hex.EncodeToString(bytesTransaction)),
	}, nil
}

func (c *ConstructionService) ConstructionDerive(ctx context.Context, request *rTypes.ConstructionDeriveRequest) (*rTypes.ConstructionDeriveResponse, *rTypes.Error) {
	return nil, errors.Errors[errors.NotImplemented]
}

func (c *ConstructionService) ConstructionHash(ctx context.Context, request *rTypes.ConstructionHashRequest) (*rTypes.TransactionIdentifierResponse, *rTypes.Error) {
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

func (c *ConstructionService) ConstructionMetadata(ctx context.Context, request *rTypes.ConstructionMetadataRequest) (*rTypes.ConstructionMetadataResponse, *rTypes.Error) {
	return &rTypes.ConstructionMetadataResponse{
		Metadata: make(map[string]interface{}),
	}, nil
}

func (c *ConstructionService) ConstructionParse(ctx context.Context, request *rTypes.ConstructionParseRequest) (*rTypes.ConstructionParseResponse, *rTypes.Error) {
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

	var signers []string

	if request.Signed {
		signaturePairs := transaction.SignaturePairs()
		for _, signaturePair := range signaturePairs {
			signers = append(signers, hex.EncodeToString(signaturePair.PubKeyPrefix))
		}
	}

	return &rTypes.ConstructionParseResponse{
		Operations: operations,
		Signers:    signers,
	}, nil
}

func (c *ConstructionService) ConstructionPayloads(ctx context.Context, request *rTypes.ConstructionPayloadsRequest) (*rTypes.ConstructionPayloadsResponse, *rTypes.Error) {
	operationType, err := validator.ValidateOperationsTypes(request.Operations)
	if err != nil {
		return nil, err
	}

	switch *operationType {
	case config.OperationTypeCryptoTransfer:
		return c.handleCryptoTransferPayload(request.Operations)
	default:
		return c.handleCryptoCreateAccountPayload(request.Operations)
	}
}
func (c *ConstructionService) ConstructionPreprocess(ctx context.Context, request *rTypes.ConstructionPreprocessRequest) (*rTypes.ConstructionPreprocessResponse, *rTypes.Error) {
	operationType, err := validator.ValidateOperationsTypes(request.Operations)
	if err != nil {
		return nil, err
	}

	switch *operationType {
	case config.OperationTypeCryptoTransfer:
		return c.handleCryptoTransferPreProcess(request.Operations)
	default:
		return c.handleCryptoCreateAccountPreProcess(request.Operations)
	}
}

func (c *ConstructionService) ConstructionSubmit(ctx context.Context, request *rTypes.ConstructionSubmitRequest) (*rTypes.TransactionIdentifierResponse, *rTypes.Error) {
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

func (c *ConstructionService) handleCryptoCreateAccountPayload(operations []*rTypes.Operation) (*rTypes.ConstructionPayloadsResponse, *rTypes.Error) {
	operationsLength := len(operations)
	if operationsLength != 1 {
		return nil, errors.Errors[errors.InvalidOperationsAmount]
	}

	operation := operations[0]
	sender, err := hedera.AccountIDFromString(operation.Account.Address)
	if err != nil {
		return nil, errors.Errors[errors.InvalidAccount]
	}

	amount, err := strconv.Atoi(operation.Amount.Value)
	if err != nil {
		return nil, errors.Errors[errors.InvalidAmount]
	}

	transaction, err := hedera.
		NewAccountCreateTransaction().
		SetInitialBalance(hedera.HbarFromTinybar(int64(amount))).
		SetTransactionID(hedera.NewTransactionID(sender)).
		Build(c.hederaClient)

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
			Address: sender.String(),
			Bytes:   transaction.BodyBytes(),
		}},
	}, nil
}

func (c *ConstructionService) handleCryptoTransferPayload(operations []*rTypes.Operation) (*rTypes.ConstructionPayloadsResponse, *rTypes.Error) {
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

		amount, err := strconv.Atoi(operation.Amount.Value)
		if err != nil {
			return nil, errors.Errors[errors.InvalidAmount]
		}

		if amount < 0 {
			sender = account
			builderTransaction.AddSender(
				sender,
				hedera.HbarFromTinybar(int64(-amount)))
		} else {
			builderTransaction.AddRecipient(account,
				hedera.HbarFromTinybar(int64(amount)))
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
			Address: sender.String(),
			Bytes:   transaction.BodyBytes(),
		}},
	}, nil
}

func (c *ConstructionService) handleCryptoCreateAccountPreProcess(operations []*rTypes.Operation) (*rTypes.ConstructionPreprocessResponse, *rTypes.Error) {
	operationsLength := len(operations)
	if operationsLength != 1 {
		return nil, errors.Errors[errors.InvalidOperationsAmount]
	}

	return &rTypes.ConstructionPreprocessResponse{
		Options: make(map[string]interface{}),
	}, nil
}

func (c *ConstructionService) handleCryptoTransferPreProcess(operations []*rTypes.Operation) (*rTypes.ConstructionPreprocessResponse, *rTypes.Error) {
	err1 := validator.ValidateOperationsSum(operations)
	if err1 != nil {
		return nil, err1
	}

	return &rTypes.ConstructionPreprocessResponse{
		Options: make(map[string]interface{}),
	}, nil
}

func NewConstructionAPIService() server.ConstructionAPIServicer {
	return &ConstructionService{
		hederaClient: hedera.ClientForTestnet(),
	}
}
