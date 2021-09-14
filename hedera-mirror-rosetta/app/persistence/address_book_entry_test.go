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
	"testing"

	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	pTypes "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

var (
	accountId3, _  = types.NewAccountFromEncodedID(3)
	accountId4, _  = types.NewAccountFromEncodedID(4)
	accountId80, _ = types.NewAccountFromEncodedID(80)
	accountId70, _ = types.NewAccountFromEncodedID(70)
	// needed for foreign key constraint
	addressBooks = []*pTypes.AddressBook{
		getAddressBook(10, 19),
		getAddressBook(20, 0),
	}
	addressBookEntries = []*pTypes.AddressBookEntry{
		getAddressBookEntry(10, 0, accountId3.EncodedId),
		getAddressBookEntry(10, 1, accountId4.EncodedId),
		getAddressBookEntry(20, 0, accountId80.EncodedId),
		getAddressBookEntry(20, 1, accountId70.EncodedId),
	}
	addressBookServiceEndpoints = []*pTypes.AddressBookServiceEndpoint{
		{10, "192.168.0.10", 0, 50211},
		{10, "192.168.1.10", 1, 50211},
		{20, "192.168.0.10", 0, 50211},
		{20, "192.168.0.1", 0, 50217},
		{20, "192.168.0.1", 0, 50211},
		{20, "192.168.1.10", 1, 50211},
	}
)

// run the suite
func TestAddressBookEntryRepositorySuite(t *testing.T) {
	suite.Run(t, new(addressBookEntryRepositorySuite))
}

type addressBookEntryRepositorySuite struct {
	test.IntegrationTest
	suite.Suite
}

func (suite *addressBookEntryRepositorySuite) SetupSuite() {
	suite.Setup()
}

func (suite *addressBookEntryRepositorySuite) TearDownSuite() {
	suite.TearDown()
}

func (suite *addressBookEntryRepositorySuite) SetupTest() {
	suite.CleanupDb()
}

func (suite *addressBookEntryRepositorySuite) TestEntries() {
	// given
	dbClient := suite.DbResource.GetGormDb()
	db.CreateDbRecords(dbClient, addressBooks, addressBookEntries, addressBookServiceEndpoints)

	expected := &types.AddressBookEntries{
		Entries: []types.AddressBookEntry{
			{0, accountId80, "192.168.0.1:50211,192.168.0.1:50217,192.168.0.10:50211"},
			{1, accountId70, "192.168.1.10:50211"},
		},
	}
	repo := NewAddressBookEntryRepository(dbClient)

	// when
	actual, err := repo.Entries()

	// then
	assert.Equal(suite.T(), expected, actual)
	assert.Nil(suite.T(), err)
}

func (suite *addressBookEntryRepositorySuite) TestEntriesNoEntries() {
	// given
	dbClient := suite.DbResource.GetGormDb()
	db.CreateDbRecords(dbClient, addressBooks, addressBookServiceEndpoints)

	expected := &types.AddressBookEntries{Entries: []types.AddressBookEntry{}}
	repo := NewAddressBookEntryRepository(dbClient)

	// when
	actual, err := repo.Entries()

	// then
	assert.Equal(suite.T(), expected, actual)
	assert.Nil(suite.T(), err)
}

func (suite *addressBookEntryRepositorySuite) TestEntriesNoServiceEndpoints() {
	// given
	dbClient := suite.DbResource.GetGormDb()
	db.CreateDbRecords(dbClient, addressBooks, getAddressBookEntry(20, 0, -1))

	repo := NewAddressBookEntryRepository(dbClient)

	// when
	actual, err := repo.Entries()

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actual)
}

func (suite *addressBookEntryRepositorySuite) TestEntriesDbInvalidNodeAccountId() {
	// given
	repo := NewAddressBookEntryRepository(suite.InvalidDbClient)
	// when
	actual, err := repo.Entries()

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func (suite *addressBookEntryRepositorySuite) TestEntriesDbConnectionError() {
	// given
	repo := NewAddressBookEntryRepository(suite.InvalidDbClient)
	// when
	actual, err := repo.Entries()

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func getAddressBook(start int64, end int64) *pTypes.AddressBook {
	if end != 0 {
		return &pTypes.AddressBook{StartConsensusTimestamp: start, EndConsensusTimestamp: &end}
	}
	return &pTypes.AddressBook{StartConsensusTimestamp: start}
}

func getAddressBookEntry(consensusTimestamp int64, nodeId int64, nodeAccountId int64) *pTypes.AddressBookEntry {
	return &pTypes.AddressBookEntry{
		ConsensusTimestamp: consensusTimestamp,
		NodeId:             nodeId,
		NodeAccountId:      nodeAccountId,
	}
}
