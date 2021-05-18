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

package entry

import (
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	log "github.com/sirupsen/logrus"
	"gorm.io/gorm"
)

const (
	latestAddressBookEntries = `SELECT abe.* FROM address_book_entry AS abe
		                        JOIN address_book AS ab
                                ON ab.start_consensus_timestamp = abe.consensus_timestamp
                                WHERE ab.end_consensus_timestamp IS NULL`
)

const (
	tableNameAddressBookEntry = "address_book_entry"
)

type addressBookEntry struct {
	Id                 int32  `gorm:"type:integer;primary_key"`
	ConsensusTimestamp int64  `gorm:"type:bigint"`
	Ip                 string `gorm:"size:128"`
	Port               int32  `gorm:"type:integer"`
	Memo               string `gorm:"size:128"`
	PublicKey          string `gorm:"size:1024"`
	NodeId             int64  `gorm:"type:bigint"`
	NodeAccountId      int64  `gorm:"type:bigint"`
	NodeCertHash       []byte `gorm:"type:bytea"`
}

func (addressBookEntry) TableName() string {
	return tableNameAddressBookEntry
}

// AddressBookEntryRepository struct that has connection to the Database
type AddressBookEntryRepository struct {
	dbClient *gorm.DB
}

// Entries return all found Address Book Entries
func (aber *AddressBookEntryRepository) Entries() (*types.AddressBookEntries, *rTypes.Error) {
	dbEntries := aber.retrieveEntries()

	entries := make([]*types.AddressBookEntry, len(dbEntries))
	for i, e := range dbEntries {
		peerId, err := e.getPeerId()
		if err != nil {
			return nil, err
		}
		entries[i] = &types.AddressBookEntry{
			PeerId: peerId,
			Metadata: map[string]interface{}{
				"ip":   e.Ip,
				"port": e.Port,
			},
		}
	}

	return &types.AddressBookEntries{
		Entries: entries}, nil
}

func (abe *addressBookEntry) getPeerId() (*types.Account, *rTypes.Error) {
	acc, err := types.AccountFromString(abe.Memo)
	if err != nil {
		log.Errorf(errors.CreateAccountDbIdFailed, abe.Memo)
		return nil, errors.ErrInternalServerError
	}
	return acc, nil
}

func (aber *AddressBookEntryRepository) retrieveEntries() []addressBookEntry {
	var entries []addressBookEntry
	aber.dbClient.Raw(latestAddressBookEntries).Scan(&entries)
	return entries
}

// NewAddressBookEntryRepository creates an instance of a AddressBookEntryRepository struct.
func NewAddressBookEntryRepository(dbClient *gorm.DB) *AddressBookEntryRepository {
	return &AddressBookEntryRepository{
		dbClient: dbClient,
	}
}
