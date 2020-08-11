package services

import (
	"context"
	"github.com/coinbase/rosetta-sdk-go/server"
	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/repositories"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/hex"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/maphelper"
)

type NetworkService struct {
	network         *types.NetworkIdentifier
	blockRepo       repositories.BlockRepository
	transactionRepo repositories.TransactionRepository
	version         *types.Version
}

func (n NetworkService) NetworkList(ctx context.Context, request *types.MetadataRequest) (*types.NetworkListResponse, *types.Error) {
	return &types.NetworkListResponse{
		NetworkIdentifiers: []*types.NetworkIdentifier{
			n.network,
		},
	}, nil
}

func (n NetworkService) NetworkOptions(ctx context.Context, request *types.NetworkRequest) (*types.NetworkOptionsResponse, *types.Error) {
	// TODO: Remove after migration has been added
	statuses := maphelper.GetStringValuesFromIntStringMap(n.transactionRepo.GetStatuses())
	operationStatuses := make([]*types.OperationStatus, 0, len(statuses))

	for _, v := range statuses {
		operationStatuses = append(operationStatuses, &types.OperationStatus{
			Status:     v,
			Successful: true,
		})
	}

	return &types.NetworkOptionsResponse{
		Version: n.version,
		Allow: &types.Allow{
			OperationStatuses:       operationStatuses,
			OperationTypes:          n.transactionRepo.GetTypesAsArray(),
			Errors:                  maphelper.GetErrorValuesFromStringErrorMap(errors.Errors),
			HistoricalBalanceLookup: false,
		},
	}, nil
}

func (n NetworkService) NetworkStatus(ctx context.Context, request *types.NetworkRequest) (*types.NetworkStatusResponse, *types.Error) {
	genesisBlock, err := n.blockRepo.RetrieveGenesis()
	if err != nil {
		return nil, err
	}

	latestBlock, err := n.blockRepo.RetrieveLatest()
	if err != nil {
		return nil, err
	}

	return &types.NetworkStatusResponse{
		CurrentBlockIdentifier: &types.BlockIdentifier{
			Index: latestBlock.ConsensusStart,
			Hash:  hex.FormatHex(latestBlock.Hash),
		},
		CurrentBlockTimestamp: latestBlock.ConsensusStart,
		GenesisBlockIdentifier: &types.BlockIdentifier{
			Index: genesisBlock.ConsensusStart,
			Hash:  hex.FormatHex(genesisBlock.Hash),
		},
		// TODO: Add after migration has been added
		Peers: nil,
	}, nil
}

func NewNetworkAPIService(network *types.NetworkIdentifier, version *types.Version, blockRepo repositories.BlockRepository, transactionRepo repositories.TransactionRepository) server.NetworkAPIServicer {
	return &NetworkService{
		network:         network,
		version:         version,
		blockRepo:       blockRepo,
		transactionRepo: transactionRepo,
	}
}
