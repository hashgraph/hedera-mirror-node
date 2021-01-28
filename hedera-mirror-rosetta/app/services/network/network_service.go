/*-
 * ‌
 * Hedera Mirror Node
 *
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
 *
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

package network

import (
	"context"
	"github.com/coinbase/rosetta-sdk-go/server"
	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/repositories/addressbook/entry"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/services/base"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/hex"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/maphelper"
)

// NetworkAPIService implements the server.NetworkAPIServicer interface.
type NetworkAPIService struct {
	base.BaseService
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
	operationTypes, err := n.TypesAsArray()
	if err != nil {
		return nil, err
	}
	statuses, err := n.Statuses()
	if err != nil {
		return nil, err
	}

	statusesArray := maphelper.GetStringValuesFromIntStringMap(statuses)
	operationStatuses := make([]*types.OperationStatus, 0, len(statusesArray))
	for _, v := range statusesArray {
		operationStatuses = append(operationStatuses, &types.OperationStatus{
			Status:     v,
			Successful: true,
		})
	}

	return &types.NetworkOptionsResponse{
		Version: n.version,
		Allow: &types.Allow{
			OperationStatuses:       operationStatuses,
			OperationTypes:          operationTypes,
			Errors:                  maphelper.GetErrorValuesFromStringErrorMap(errors.Errors),
			HistoricalBalanceLookup: true,
		},
	}, nil
}

// NetworkStatus implements the /network/status endpoint.
func (n *NetworkAPIService) NetworkStatus(ctx context.Context, request *types.NetworkRequest) (*types.NetworkStatusResponse, *types.Error) {
	genesisBlock, err := n.RetrieveGenesis()
	if err != nil {
		return nil, err
	}

	latestBlock, err := n.RetrieveLatest()
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
		Peers: peers.ToRosetta(),
	}, nil
}

// NewNetworkAPIService creates a new instance of a NetworkAPIService.
func NewNetworkAPIService(commons base.BaseService,
	addressBookEntryRepo repositories.AddressBookEntryRepository,
	network *types.NetworkIdentifier, version *types.Version) server.NetworkAPIServicer {
	return &NetworkAPIService{
		BaseService:          commons,
		addressBookEntryRepo: addressBookEntryRepo,
		network:              network,
		version:              version,
	}
}
