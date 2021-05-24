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
	"testing"

	"github.com/DATA-DOG/go-sqlmock"
	entityid "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/services/encoding"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/mocks"
	"github.com/stretchr/testify/assert"
)

const (
	LocalIp = "127.0.0.1"
	MemoA   = "0.0.a"
)

var (
	dbAddressBookEntry = &addressBookEntry{
		Id:                 1,
		ConsensusTimestamp: 1,
		Ip:                 LocalIp,
		Port:               0,
		Memo:               "0.0.5",
		PublicKey:          "",
		NodeId:             0,
		NodeAccountId:      0,
		NodeCertHash:       nil,
	}
	entityId = &entityid.EntityId{
		ShardNum:  0,
		RealmNum:  0,
		EntityNum: 5,
	}
	peerId                   = &types.Account{EntityId: *entityId}
	expectedAddressBookEntry = &types.AddressBookEntry{
		PeerId: peerId,
		Metadata: map[string]interface{}{
			"ip":   LocalIp,
			"port": int32(0),
		},
	}
	expectedResult = &types.AddressBookEntries{
		Entries: []*types.AddressBookEntry{expectedAddressBookEntry, expectedAddressBookEntry},
	}
)

func TestShouldSuccessReturnAddressBookEntryTableName(t *testing.T) {
	assert.Equal(t, tableNameAddressBookEntry, addressBookEntry{}.TableName())
}

func TestShouldSuccessReturnRepository(t *testing.T) {
	// given
	gormDbClient, _ := mocks.DatabaseMock(t)

	// when
	result := NewAddressBookEntryRepository(gormDbClient)

	// then
	assert.IsType(t, &AddressBookEntryRepository{}, result)
	assert.Equal(t, result.dbClient, gormDbClient)
}

func TestShouldSuccessReturnAddressBookEntries(t *testing.T) {
	// given
	aber, columns, mock := setupRepository(t)

	mockedRow := mocks.GetFieldsValuesAsDriverValue(dbAddressBookEntry)
	mockedRows := sqlmock.NewRows(columns).
		AddRow(mockedRow...).
		AddRow(mockedRow...)
	mock.ExpectQuery(latestAddressBookEntries).
		WillReturnRows(mockedRows)

	// when
	result, err := aber.Entries()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())

	assert.Equal(t, expectedResult, result)
	assert.Nil(t, err)
}

func TestShouldFailReturnEntriesDueToInvalidDbData(t *testing.T) {
	// given
	aber, columns, mock := setupRepository(t)

	invalidData := &addressBookEntry{
		Id:                 1,
		ConsensusTimestamp: 1,
		Ip:                 LocalIp,
		Port:               0,
		Memo:               MemoA,
		PublicKey:          "",
		NodeId:             0,
		NodeAccountId:      0,
		NodeCertHash:       nil,
	}

	mock.ExpectQuery(latestAddressBookEntries).
		WillReturnRows(sqlmock.NewRows(columns).AddRow(mocks.GetFieldsValuesAsDriverValue(invalidData)...))

	// when
	result, err := aber.Entries()

	// then
	assert.NoError(t, mock.ExpectationsWereMet())

	assert.Nil(t, result)
	assert.Equal(t, errors.ErrInternalServerError, err)
}

func TestShouldSuccessReturnPeerId(t *testing.T) {
	// given
	abe := addressBookEntry{
		Memo: peerId.String(),
	}

	// when
	result, err := abe.getPeerId()

	// then
	assert.Equal(t, peerId, result)
	assert.Nil(t, err)
}

func TestShouldFailReturnPeerId(t *testing.T) {
	// given
	abe := addressBookEntry{
		Memo: MemoA,
	}

	// when
	result, err := abe.getPeerId()

	// then
	assert.Nil(t, result)
	assert.NotNil(t, err)
	assert.Equal(t, errors.ErrInternalServerError, err)
}

func TestShouldFailReturnPeerIdNegative(t *testing.T) {
	// given
	abe := addressBookEntry{
		Memo: "0.0.-2",
	}

	// when
	result, err := abe.getPeerId()

	// then
	assert.Nil(t, result)
	assert.NotNil(t, err)
	assert.Equal(t, errors.ErrInternalServerError, err)
}

func setupRepository(t *testing.T) (*AddressBookEntryRepository, []string, sqlmock.Sqlmock) {
	gormDbClient, mock := mocks.DatabaseMock(t)

	columns := mocks.GetFieldsNamesToSnakeCase(addressBookEntry{})

	aber := NewAddressBookEntryRepository(gormDbClient)
	return aber, columns, mock
}
