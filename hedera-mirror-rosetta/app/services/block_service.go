package services

import (
	"context"
	"github.com/coinbase/rosetta-sdk-go/server"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/hex"
)

// BlockAPIService implements the server.BlockAPIServicer interface.
type BlockAPIService struct {
	Commons
}

// NewBlockAPIService creates a new instance of a BlockAPIService.
func NewBlockAPIService(commons Commons) server.BlockAPIServicer {
	return &BlockAPIService{
		Commons: commons,
	}
}

// Block implements the /block endpoint.
func (s *BlockAPIService) Block(ctx context.Context, request *rTypes.BlockRequest) (*rTypes.BlockResponse, *rTypes.Error) {
	block, err := s.RetrieveBlock(request.BlockIdentifier)
	if err != nil {
		return nil, err
	}

	transactions, err := s.transactionRepo.FindBetween(block.ConsensusStart, block.ConsensusEnd)
	if err != nil {
		return nil, err
	}

	block.Transactions = transactions
	rBlock := block.ToRosettaBlock()
	return &rTypes.BlockResponse{
		Block: rBlock,
	}, nil
}

// BlockTransaction implements the /block/transaction endpoint.
func (s *BlockAPIService) BlockTransaction(
	ctx context.Context,
	request *rTypes.BlockTransactionRequest,
) (*rTypes.BlockTransactionResponse, *rTypes.Error) {
	h := hex.SafeRemoveHexPrefix(request.BlockIdentifier.Hash)
	block, err := s.blockRepo.FindByIdentifier(request.BlockIdentifier.Index, h)
	if err != nil {
		return nil, err
	}

	transaction, err := s.transactionRepo.FindByHashInBlock(request.TransactionIdentifier.Hash, block.ConsensusStart, block.ConsensusEnd)
	if err != nil {
		return nil, err
	}
	rTransaction := transaction.ToRosettaTransaction()
	return &rTypes.BlockTransactionResponse{
		Transaction: rTransaction,
	}, nil
}
