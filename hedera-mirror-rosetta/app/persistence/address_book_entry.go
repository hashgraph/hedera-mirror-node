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

package persistence

import (
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/repositories"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	log "github.com/sirupsen/logrus"
	"gorm.io/gorm"
)

const (
	latestNodeServiceEndpoints = `select
                                    abe.node_id,
                                    node_account_id, 
                                    string_agg(
                                      ip_address_v4 || ':' || port::text, ',' order by ip_address_v4, port
                                    ) endpoints
                                  from address_book_entry abe
                                  left join address_book_service_endpoint abse on
                                    abse.node_id = abe.node_id and
                                    abse.consensus_timestamp =
                                      (select max(consensus_timestamp) from address_book_service_endpoint)
                                  where abe.consensus_timestamp =
                                    (select max(consensus_timestamp) from address_book_entry)
                                  group by abe.node_id, node_account_id`
)

type nodeServiceEndpoint struct {
	NodeId        int64
	NodeAccountId int64
	Endpoints     string
}

// addressBookEntryRepository struct that has connection to the Database
type addressBookEntryRepository struct {
	dbClient *gorm.DB
}

// Entries return all found Address Book Entries
func (aber *addressBookEntryRepository) Entries() (*types.AddressBookEntries, *rTypes.Error) {
	nodes := make([]nodeServiceEndpoint, 0)
	if err := aber.dbClient.Raw(latestNodeServiceEndpoints).Scan(&nodes).Error; err != nil {
		log.Error("Failed to get latest node service endpoints", err)
		return nil, errors.ErrDatabaseError
	}

	entries := make([]types.AddressBookEntry, 0, len(nodes))
	for _, node := range nodes {
		nodeAccountId, err := types.NewAccountFromEncodedID(node.NodeAccountId)
		if err != nil {
			return nil, errors.ErrInternalServerError
		}
		entries = append(entries, types.AddressBookEntry{
			NodeId:    node.NodeId,
			AccountId: nodeAccountId,
			Endpoints: node.Endpoints,
		})
	}

	return &types.AddressBookEntries{Entries: entries}, nil
}

// NewAddressBookEntryRepository creates an instance of a addressBookEntryRepository struct.
func NewAddressBookEntryRepository(dbClient *gorm.DB) repositories.AddressBookEntryRepository {
	return &addressBookEntryRepository{dbClient}
}
