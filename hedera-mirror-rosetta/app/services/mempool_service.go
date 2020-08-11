package services

import (
	"context"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
)

// MempoolAPIService implements the server.MempoolAPIServicer
type MempoolAPIService struct{}

// NewMempoolAPIService creates a new instance of a MempoolAPIService
func NewMempoolAPIService() *MempoolAPIService {
	return &MempoolAPIService{}
}

// Mempool implements the /mempool endpoint
func (m *MempoolAPIService) Mempool(ctx context.Context, request *rTypes.NetworkRequest) (*rTypes.MempoolResponse, *rTypes.Error) {
	return &rTypes.MempoolResponse{
		TransactionIdentifiers: []*rTypes.TransactionIdentifier{},
	}, nil
}

// Mempool implements the /mempool/transaction endpoint
func (m *MempoolAPIService) MempoolTransaction(ctx context.Context, request *rTypes.MempoolTransactionRequest) (*rTypes.MempoolTransactionResponse, *rTypes.Error) {
	return nil, errors.Errors[errors.TransactionNotFound]
}
