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

	"github.com/coinbase/rosetta-sdk-go/server"
	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/tools"
)

// networkAPIService implements the server.NetworkAPIServicer interface.
type networkAPIService struct {
	BaseService
	addressBookEntryRepo interfaces.AddressBookEntryRepository
	network              *types.NetworkIdentifier
	version              *types.Version
}

// NetworkList implements the /network/list endpoint.
func (n *networkAPIService) NetworkList(
	ctx context.Context,
	request *types.MetadataRequest,
) (*types.NetworkListResponse, *types.Error) {
	return &types.NetworkListResponse{NetworkIdentifiers: []*types.NetworkIdentifier{n.network}}, nil
}

// NetworkOptions implements the /network/options endpoint.
func (n *networkAPIService) NetworkOptions(
	ctx context.Context,
	request *types.NetworkRequest,
) (*types.NetworkOptionsResponse, *types.Error) {
	operationTypes, err := n.TypesAsArray(ctx)
	if err != nil {
		return nil, err
	}
	results, err := n.Results(ctx)
	if err != nil {
		return nil, err
	}

	operationStatuses := make([]*types.OperationStatus, 0, len(results))
	for value, name := range results {
		operationStatuses = append(operationStatuses, &types.OperationStatus{
			Status:     name,
			Successful: persistence.IsTransactionResultSuccessful(value),
		})
	}

	return &types.NetworkOptionsResponse{
		Version: n.version,
		Allow: &types.Allow{
			OperationStatuses:       operationStatuses,
			OperationTypes:          operationTypes,
			Errors:                  errors.Errors,
			HistoricalBalanceLookup: true,
		},
	}, nil
}

// NetworkStatus implements the /network/status endpoint.
func (n *networkAPIService) NetworkStatus(
	ctx context.Context,
	request *types.NetworkRequest,
) (*types.NetworkStatusResponse, *types.Error) {
	genesisBlock, err := n.RetrieveGenesis(ctx)
	if err != nil {
		return nil, err
	}

	currentBlock, err := n.RetrieveLatest(ctx)
	if err != nil {
		return nil, err
	}

	peers, err := n.addressBookEntryRepo.Entries(ctx)
	if err != nil {
		return nil, err
	}

	return &types.NetworkStatusResponse{
		CurrentBlockIdentifier: &types.BlockIdentifier{
			Index: currentBlock.Index,
			Hash:  tools.SafeAddHexPrefix(currentBlock.Hash),
		},
		CurrentBlockTimestamp: currentBlock.GetTimestampMillis(),
		GenesisBlockIdentifier: &types.BlockIdentifier{
			Index: genesisBlock.Index,
			Hash:  tools.SafeAddHexPrefix(genesisBlock.Hash),
		},
		Peers: peers.ToRosetta(),
	}, nil
}

// NewNetworkAPIService creates a new instance of a networkAPIService.
func NewNetworkAPIService(
	baseService BaseService,
	addressBookEntryRepo interfaces.AddressBookEntryRepository,
	network *types.NetworkIdentifier,
	version *types.Version,
) server.NetworkAPIServicer {
	return &networkAPIService{
		BaseService:          baseService,
		addressBookEntryRepo: addressBookEntryRepo,
		network:              network,
		version:              version,
	}
}
