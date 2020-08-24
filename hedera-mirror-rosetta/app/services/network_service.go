package services

import (
	"context"
	"github.com/coinbase/rosetta-sdk-go/server"
	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/repositories/addressbook/entry"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/hex"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/maphelper"
)

// NetworkAPIService implements the server.NetworkAPIServicer interface.
type NetworkAPIService struct {
	Commons
	addressBookEntryRepo repositories.AddressBookEntryRepository
	network              *types.NetworkIdentifier
	version              *types.Version
}

// NetworkList implements the /network/list endpoint.
func (n *NetworkAPIService) NetworkList(ctx context.Context, request *types.MetadataRequest) (*types.NetworkListResponse, *types.Error) {
	return &types.NetworkListResponse{
		NetworkIdentifiers: []*types.NetworkIdentifier{
			n.network,
		},
	}, nil
}

// NetworkOptions implements the /network/options endpoint.
func (n *NetworkAPIService) NetworkOptions(ctx context.Context, request *types.NetworkRequest) (*types.NetworkOptionsResponse, *types.Error) {
	// TODO: Remove after migration has been added
	statuses := maphelper.GetStringValuesFromIntStringMap(n.transactionRepo.Statuses())
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
			OperationTypes:          n.transactionRepo.TypesAsArray(),
			Errors:                  maphelper.GetErrorValuesFromStringErrorMap(errors.Errors),
			HistoricalBalanceLookup: false,
		},
	}, nil
}

// NetworkStatus implements the /network/status endpoint.
func (n *NetworkAPIService) NetworkStatus(ctx context.Context, request *types.NetworkRequest) (*types.NetworkStatusResponse, *types.Error) {
	genesisBlock, err := n.blockRepo.RetrieveGenesis()
	if err != nil {
		return nil, err
	}

	latestBlock, err := n.blockRepo.RetrieveLatest()
	if err != nil {
		return nil, err
	}

	peers, err := n.addressBookEntryRepo.Entries()
	if err != nil {
		return nil, err
	}

	return &types.NetworkStatusResponse{
		CurrentBlockIdentifier: &types.BlockIdentifier{
			Index: latestBlock.Index,
			Hash:  hex.SafeAddHexPrefix(latestBlock.Hash),
		},
		CurrentBlockTimestamp: latestBlock.GetTimestampMillis(),
		GenesisBlockIdentifier: &types.BlockIdentifier{
			Index: genesisBlock.Index,
			Hash:  hex.SafeAddHexPrefix(genesisBlock.Hash),
		},
		Peers: peers.ToRosettaPeers(),
	}, nil
}

// NewNetworkAPIService creates a new instance of a NetworkAPIService.
func NewNetworkAPIService(commons Commons,
	addressBookEntryRepo repositories.AddressBookEntryRepository,
	network *types.NetworkIdentifier, version *types.Version) server.NetworkAPIServicer {
	return &NetworkAPIService{
		Commons:              commons,
		addressBookEntryRepo: addressBookEntryRepo,
		network:              network,
		version:              version,
	}
}
