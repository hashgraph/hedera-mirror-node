/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/tools"
)

// networkAPIService implements the server.NetworkAPIServicer interface.
type networkAPIService struct {
	*BaseService
	addressBookEntryRepo interfaces.AddressBookEntryRepository
	network              *rTypes.NetworkIdentifier
	operationTypes       []string
	version              *rTypes.Version
}

// NetworkList implements the /network/list endpoint.
func (n *networkAPIService) NetworkList(
	ctx context.Context,
	request *rTypes.MetadataRequest,
) (*rTypes.NetworkListResponse, *rTypes.Error) {
	return &rTypes.NetworkListResponse{NetworkIdentifiers: []*rTypes.NetworkIdentifier{n.network}}, nil
}

// NetworkOptions implements the /network/options endpoint.
func (n *networkAPIService) NetworkOptions(
	ctx context.Context,
	request *rTypes.NetworkRequest,
) (*rTypes.NetworkOptionsResponse, *rTypes.Error) {
	operationStatuses := make([]*rTypes.OperationStatus, 0, len(types.TransactionResults))
	for value, name := range types.TransactionResults {
		operationStatuses = append(operationStatuses, &rTypes.OperationStatus{
			Status:     name,
			Successful: persistence.IsTransactionResultSuccessful(value),
		})
	}

	return &rTypes.NetworkOptionsResponse{
		Version: n.version,
		Allow: &rTypes.Allow{
			OperationStatuses:       operationStatuses,
			OperationTypes:          n.operationTypes,
			Errors:                  errors.Errors,
			HistoricalBalanceLookup: true,
		},
	}, nil
}

// NetworkStatus implements the /network/status endpoint.
func (n *networkAPIService) NetworkStatus(
	ctx context.Context,
	request *rTypes.NetworkRequest,
) (*rTypes.NetworkStatusResponse, *rTypes.Error) {
	if !n.IsOnline() {
		return nil, errors.ErrEndpointNotSupportedInOfflineMode
	}

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

	return &rTypes.NetworkStatusResponse{
		CurrentBlockIdentifier: &rTypes.BlockIdentifier{
			Index: currentBlock.Index,
			Hash:  tools.SafeAddHexPrefix(currentBlock.Hash),
		},
		CurrentBlockTimestamp: currentBlock.GetTimestampMillis(),
		GenesisBlockIdentifier: &rTypes.BlockIdentifier{
			Index: genesisBlock.Index,
			Hash:  tools.SafeAddHexPrefix(genesisBlock.Hash),
		},
		Peers: peers.ToRosetta(),
	}, nil
}

// NewNetworkAPIService creates a networkAPIService instance.
func NewNetworkAPIService(
	baseService *BaseService,
	addressBookEntryRepo interfaces.AddressBookEntryRepository,
	network *rTypes.NetworkIdentifier,
	version *rTypes.Version,
) server.NetworkAPIServicer {
	return &networkAPIService{
		BaseService:          baseService,
		addressBookEntryRepo: addressBookEntryRepo,
		operationTypes:       tools.GetStringValuesFromInt32StringMap(types.TransactionTypes),
		network:              network,
		version:              version,
	}
}
