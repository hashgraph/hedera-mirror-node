package services

import (
	"context"
	"github.com/coinbase/rosetta-sdk-go/server"
	"github.com/coinbase/rosetta-sdk-go/types"
)

type NetworkService struct {
	network *types.NetworkIdentifier
}

func (n NetworkService) NetworkList(ctx context.Context, request *types.MetadataRequest) (*types.NetworkListResponse, *types.Error) {
	return &types.NetworkListResponse{
		NetworkIdentifiers: []*types.NetworkIdentifier{
			n.network,
		},
	}, nil
}

func (n NetworkService) NetworkOptions(ctx context.Context, request *types.NetworkRequest) (*types.NetworkOptionsResponse, *types.Error) {
	panic("implement me")
}

func (n NetworkService) NetworkStatus(ctx context.Context, request *types.NetworkRequest) (*types.NetworkStatusResponse, *types.Error) {
	panic("implement me")
}

func NewNetworkAPIService(network *types.NetworkIdentifier) server.NetworkAPIServicer {
	return &NetworkService{
		network: network,
	}
}
