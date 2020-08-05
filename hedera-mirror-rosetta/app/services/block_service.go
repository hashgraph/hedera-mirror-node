package services

import (
	"context"

	"github.com/coinbase/rosetta-sdk-go/server"
	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/repositories"
)

// BlockAPIService implements the server.BlockAPIServicer interface.
type BlockAPIService struct {
	network   *types.NetworkIdentifier
	blockRepo repositories.BlockRepository
}

// NewBlockAPIService creates a new instance of a BlockAPIService.
func NewBlockAPIService(network *types.NetworkIdentifier, blockRepo repositories.BlockRepository) server.BlockAPIServicer {
	return &BlockAPIService{
		network:   network,
		blockRepo: blockRepo,
	}
}

// Block implements the /block endpoint.
func (s *BlockAPIService) Block(ctx context.Context, request *types.BlockRequest) (*types.BlockResponse, *types.Error) {

	block := s.blockRepo.FindByIndex(*request.BlockIdentifier.Index) // TODO

	rBlock := block.ToRosettaBlock()

	return &types.BlockResponse{
		Block: rBlock,
	}, nil
}

// BlockTransaction implements the /block/transaction endpoint.
func (s *BlockAPIService) BlockTransaction(
	ctx context.Context,
	request *types.BlockTransactionRequest,
) (*types.BlockTransactionResponse, *types.Error) {

	// TODO
	return &types.BlockTransactionResponse{}, nil
}
