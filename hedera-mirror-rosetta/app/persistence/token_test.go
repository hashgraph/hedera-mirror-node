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

	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
	"github.com/thanhpk/randstr"
)

var (
	defaultToken domain.Token
	tokenId      = domain.MustDecodeEntityId(1200)
)

// run the suite
func TestTokenRepositorySuite(t *testing.T) {
	suite.Run(t, new(tokenRepositorySuite))
}

type tokenRepositorySuite struct {
	integrationTest
	suite.Suite
}

func (suite *tokenRepositorySuite) TestFindShouldSucceed() {
	// given
	dbClient := dbResource.GetGormDb()

	expected := domain.Token{
		TokenId:                  tokenId,
		CreatedTimestamp:         10001,
		Decimals:                 9,
		FeeScheduleKey:           randstr.Bytes(10),
		FeeScheduleKeyEd25519Hex: randstr.Hex(10),
		FreezeDefault:            true,
		FreezeKey:                randstr.Bytes(10),
		FreezeKeyEd25519Hex:      randstr.Hex(10),
		InitialSupply:            120,
		KycKey:                   randstr.Bytes(11),
		KycKeyEd25519Hex:         randstr.Hex(11),
		ModifiedTimestamp:        10001,
		Name:                     randstr.Hex(6),
		SupplyKey:                randstr.Bytes(12),
		SupplyKeyEd25519Hex:      randstr.Hex(12),
		SupplyType:               domain.TokenSupplyTypeInfinite,
		Symbol:                   randstr.Hex(4),
		TotalSupply:              200,
		TreasuryAccountId:        domain.MustDecodeEntityId(1100),
		Type:                     domain.TokenTypeFungibleCommon,
		WipeKey:                  randstr.Bytes(19),
		WipeKeyEd25519Hex:        randstr.Hex(19),
	}
	dbClient.Create(&expected)

	repo := NewTokenRepository(dbClient)

	// when
	actual, err := repo.Find(tokenId.String())

	// then
	assert.Equal(suite.T(), expected, actual)
	assert.Nil(suite.T(), err)
}

func (suite *tokenRepositorySuite) TestFindTokenNotFound() {
	// given
	repo := NewTokenRepository(dbResource.GetGormDb())

	// when
	actual, err := repo.Find(tokenId.String())

	// then
	assert.Equal(suite.T(), errors.ErrTokenNotFound, err)
	assert.Equal(suite.T(), defaultToken, actual)
}

func (suite *tokenRepositorySuite) TestFindTokenInvalidToken() {
	// given
	repo := NewTokenRepository(dbResource.GetGormDb())

	// when
	actual, err := repo.Find("abc")

	// then
	assert.Equal(suite.T(), errors.ErrInvalidToken, err)
	assert.Equal(suite.T(), defaultToken, actual)
}

func (suite *tokenRepositorySuite) TestFindTokenDbConnectionError() {
	// given
	repo := NewTokenRepository(invalidDbClient)

	// when
	actual, err := repo.Find(tokenId.String())

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Equal(suite.T(), defaultToken, actual)
}
