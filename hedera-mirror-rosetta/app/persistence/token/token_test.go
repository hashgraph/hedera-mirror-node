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

package token

import (
	"testing"

	entityid "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/services/encoding"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	dbTypes "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
	"github.com/thanhpk/randstr"
)

var tokenId = entityid.EntityId{EncodedId: 1200, EntityNum: 1200}

// run the suite
func TestTokenRepositorySuite(t *testing.T) {
	suite.Run(t, new(tokenRepositorySuite))
}

type tokenRepositorySuite struct {
	test.IntegrationTest
	suite.Suite
}

func (suite *tokenRepositorySuite) SetupSuite() {
	suite.Setup()
}

func (suite *tokenRepositorySuite) TearDownSuite() {
	suite.TearDown()
}

func (suite *tokenRepositorySuite) SetupTest() {
	suite.CleanupDb()
}

func (suite *tokenRepositorySuite) TestFindShouldSucceed() {
	// given
	dbClient := suite.DbResource.GetGormDb()

	token := &dbTypes.Token{
		TokenId:             tokenId.EncodedId,
		CreatedTimestamp:    10001,
		Decimals:            9,
		FreezeDefault:       true,
		FreezeKey:           randstr.Bytes(10),
		FreezeKeyEd25519Hex: randstr.Hex(10),
		InitialSupply:       120,
		KycKey:              randstr.Bytes(11),
		KycKeyEd25519Hex:    randstr.Hex(11),
		ModifiedTimestamp:   10001,
		Name:                randstr.Hex(6),
		SupplyKey:           randstr.Bytes(12),
		SupplyKeyEd25519Hex: randstr.Hex(12),
		Symbol:              randstr.Hex(4),
		TotalSupply:         200,
		TreasuryAccountId:   1100,
		WipeKey:             randstr.Bytes(19),
		WipeKeyEd25519Hex:   randstr.Hex(19),
	}
	dbClient.Create(token)

	expected := &types.Token{
		TokenId: entityid.EntityId{
			EntityNum: 1200,
			EncodedId: 1200,
		},
		Decimals: 9,
		Name:     token.Name,
		Symbol:   token.Symbol,
	}

	repo := NewTokenRepository(dbClient)

	// when
	actual, err := repo.Find(tokenId.String())

	// then
	assert.Equal(suite.T(), expected, actual)
	assert.Nil(suite.T(), err)
}

func (suite *tokenRepositorySuite) TestFindTokenNotFound() {
	// given
	repo := NewTokenRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := repo.Find(tokenId.String())

	// then
	assert.Equal(suite.T(), errors.ErrTokenNotFound, err)
	assert.Nil(suite.T(), actual)
}

func (suite *tokenRepositorySuite) TestFindTokenInvalidToken() {
	// given
	repo := NewTokenRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := repo.Find("abc")

	// then
	assert.Equal(suite.T(), errors.ErrInvalidToken, err)
	assert.Nil(suite.T(), actual)
}

func (suite *tokenRepositorySuite) TestFindTokenDbConnectionError() {
	// given
	repo := NewTokenRepository(suite.InvalidDbClient)

	// when
	actual, err := repo.Find(tokenId.String())

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}
