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
	"testing"

	"github.com/coinbase/rosetta-sdk-go/server"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/mocks"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

var (
	token1 = domain.Token{
		TokenId:  domain.MustDecodeEntityId(2001),
		Decimals: 5,
		Name:     "foobar1",
		Symbol:   "foobar1",
		Type:     domain.TokenTypeFungibleCommon,
	}
	token2 = domain.Token{
		TokenId:  domain.MustDecodeEntityId(2002),
		Decimals: 6,
		Name:     "foobar2",
		Symbol:   "foobar2",
		Type:     domain.TokenTypeFungibleCommon,
	}
	token3 = domain.Token{
		TokenId:  domain.MustDecodeEntityId(2003),
		Decimals: 7,
		Name:     "foobar3",
		Symbol:   "foobar3",
		Type:     domain.TokenTypeFungibleCommon,
	}
	token4 = domain.Token{
		TokenId:  domain.MustDecodeEntityId(2004),
		Decimals: 8,
		Name:     "foobar4",
		Symbol:   "foobar4",
		Type:     domain.TokenTypeFungibleCommon,
	}
)

func amount() []types.Amount {
	return []types.Amount{
		&types.HbarAmount{Value: int64(1000)},
		types.NewTokenAmount(token1, 100),
		types.NewTokenAmount(token2, 200),
	}
}

func genesisBlock() *types.Block {
	return &types.Block{
		Index:               0,
		Hash:                "genesis",
		LatestIndex:         0,
		ConsensusStartNanos: 1000000,
		ConsensusEndNanos:   20000000,
		ParentIndex:         0,
		ParentHash:          "genesis",
	}
}

func request(withBlockIdentifier bool) *rTypes.AccountBalanceRequest {
	var blockIdentifier *rTypes.PartialBlockIdentifier = nil
	if withBlockIdentifier {
		index := int64(1)
		hash := "0x123"
		blockIdentifier = &rTypes.PartialBlockIdentifier{
			Index: &index,
			Hash:  &hash,
		}
	}
	return &rTypes.AccountBalanceRequest{
		AccountIdentifier: &rTypes.AccountIdentifier{Address: "0.0.1"},
		BlockIdentifier:   blockIdentifier,
	}
}

func expectedAccountBalanceResponse() *rTypes.AccountBalanceResponse {
	return &rTypes.AccountBalanceResponse{
		BlockIdentifier: &rTypes.BlockIdentifier{
			Index: 1,
			Hash:  "0x12345",
		},
		Balances: []*rTypes.Amount{
			{
				Value:    "1000",
				Currency: types.CurrencyHbar,
			},
			types.NewTokenAmount(token1, 100).ToRosetta(),
			types.NewTokenAmount(token2, 200).ToRosetta(),
		},
	}
}

func addGenesisTokenBalances(
	resp *rTypes.AccountBalanceResponse,
	tokens ...domain.Token,
) *rTypes.AccountBalanceResponse {
	for _, token := range tokens {
		resp.Balances = append(resp.Balances, types.NewTokenAmount(token, 0).ToRosetta())
	}

	return resp
}

func TestAccountServiceSuite(t *testing.T) {
	suite.Run(t, new(accountServiceSuite))
}

type accountServiceSuite struct {
	suite.Suite
	accountService      server.AccountAPIServicer
	mockAccountRepo     *mocks.MockAccountRepository
	mockBlockRepo       *mocks.MockBlockRepository
	mockTransactionRepo *mocks.MockTransactionRepository
}

func (suite *accountServiceSuite) SetupTest() {
	suite.mockAccountRepo = &mocks.MockAccountRepository{}
	suite.mockBlockRepo = &mocks.MockBlockRepository{}
	suite.mockTransactionRepo = &mocks.MockTransactionRepository{}

	baseService := NewBaseService(suite.mockBlockRepo, suite.mockTransactionRepo)
	suite.accountService = NewAccountAPIService(baseService, suite.mockAccountRepo)
}

func (suite *accountServiceSuite) TestAccountBalance() {
	// given:
	suite.mockBlockRepo.On("RetrieveLatest").Return(block(), mocks.NilError)
	suite.mockAccountRepo.On("RetrieveBalanceAtBlock").Return(amount(), mocks.NilError)
	suite.mockAccountRepo.On("RetrieveEverOwnedTokensByBlockAfter").Return([]domain.Token{}, mocks.NilError)

	// when:
	actual, err := suite.accountService.AccountBalance(nil, request(false))

	// then:
	assert.Equal(suite.T(), expectedAccountBalanceResponse(), actual)
	assert.Nil(suite.T(), err)
	suite.mockBlockRepo.AssertNotCalled(suite.T(), "FindByIdentifier")
	suite.mockBlockRepo.AssertNotCalled(suite.T(), "FindByHash")
}

func (suite *accountServiceSuite) TestAccountBalanceWithGenesisTokenBalance() {
	// given:
	everOwnedTokens := []domain.Token{token1, token2, token3, token4}
	expected := addGenesisTokenBalances(expectedAccountBalanceResponse(), token3, token4)
	suite.mockBlockRepo.On("RetrieveLatest").Return(block(), mocks.NilError)
	suite.mockAccountRepo.On("RetrieveBalanceAtBlock").Return(amount(), mocks.NilError)
	suite.mockAccountRepo.On("RetrieveEverOwnedTokensByBlockAfter").Return(everOwnedTokens, mocks.NilError)

	// when:
	actual, err := suite.accountService.AccountBalance(nil, request(false))

	// then:
	assert.Equal(suite.T(), expected, actual)
	assert.Nil(suite.T(), err)
	suite.mockBlockRepo.AssertNotCalled(suite.T(), "FindByIdentifier")
	suite.mockBlockRepo.AssertNotCalled(suite.T(), "FindByHash")
}

func (suite *accountServiceSuite) TestAccountBalanceWithBlockIdentifier() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), mocks.NilError)
	suite.mockAccountRepo.On("RetrieveBalanceAtBlock").Return(amount(), mocks.NilError)
	suite.mockAccountRepo.On("RetrieveEverOwnedTokensByBlockAfter").Return([]domain.Token{}, mocks.NilError)

	// when:
	actual, err := suite.accountService.AccountBalance(nil, request(true))

	// then:
	assert.Equal(suite.T(), expectedAccountBalanceResponse(), actual)
	assert.Nil(suite.T(), err)
	suite.mockBlockRepo.AssertNotCalled(suite.T(), "RetrieveLatest")
}

func (suite *accountServiceSuite) TestAccountBalanceWithBlockIdentifierAndAdditionalTokens() {
	// given:
	everOwnedTokens := []domain.Token{token1, token2, token3, token4}
	expected := addGenesisTokenBalances(expectedAccountBalanceResponse(), token3, token4)
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), mocks.NilError)
	suite.mockAccountRepo.On("RetrieveBalanceAtBlock").Return(amount(), mocks.NilError)
	suite.mockAccountRepo.On("RetrieveEverOwnedTokensByBlockAfter").Return(everOwnedTokens, mocks.NilError)

	// when:
	actual, err := suite.accountService.AccountBalance(nil, request(true))

	// then:
	assert.Equal(suite.T(), expected, actual)
	assert.Nil(suite.T(), err)
	suite.mockBlockRepo.AssertNotCalled(suite.T(), "RetrieveLatest")
	suite.mockBlockRepo.AssertNotCalled(suite.T(), "FindByHash")
}

func (suite *accountServiceSuite) TestAccountBalanceWithOnlyGenesisBlock() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(genesisBlock(), mocks.NilError)

	// when:
	actual, err := suite.accountService.AccountBalance(nil, request(true))

	// then:
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actual)
	suite.mockBlockRepo.AssertNotCalled(suite.T(), "RetrieveLatest")
	suite.mockAccountRepo.AssertNotCalled(suite.T(), "RetrieveBalanceAtBlock")
	suite.mockAccountRepo.AssertNotCalled(suite.T(), "RetrieveEverOwnedTokensByBlockAfter")
}

func (suite *accountServiceSuite) TestAccountBalanceThrowsWhenRetrieveLatestFails() {
	// given:
	suite.mockBlockRepo.On("RetrieveLatest").Return(mocks.NilBlock, &rTypes.Error{})

	// when:
	actual, err := suite.accountService.AccountBalance(nil, request(false))

	// then:
	assert.Nil(suite.T(), actual)
	assert.NotNil(suite.T(), err)
	suite.mockAccountRepo.AssertNotCalled(suite.T(), "RetrieveBalanceAtBlock")
}

func (suite *accountServiceSuite) TestAccountBalanceThrowsWhenRetrieveBlockFails() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(mocks.NilBlock, &rTypes.Error{})

	// when:
	actual, err := suite.accountService.AccountBalance(nil, request(true))

	// then:
	assert.Nil(suite.T(), actual)
	assert.NotNil(suite.T(), err)
	suite.mockAccountRepo.AssertNotCalled(suite.T(), "RetrieveBalanceAtBlock")
	suite.mockBlockRepo.AssertNotCalled(suite.T(), "RetrieveLatest")
}

func (suite *accountServiceSuite) TestAccountBalanceThrowsWhenRetrieveBalanceAtBlockFails() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), mocks.NilError)
	suite.mockAccountRepo.On("RetrieveBalanceAtBlock").Return([]types.Amount{}, &rTypes.Error{})

	// when:
	actual, err := suite.accountService.AccountBalance(nil, request(true))

	// then:
	assert.Nil(suite.T(), actual)
	assert.NotNil(suite.T(), err)
}

func (suite *accountServiceSuite) TestAccountBalanceThrowsWhenRetrieveEverOwnedTokensByBlockAfterFails() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), mocks.NilError)
	suite.mockAccountRepo.On("RetrieveBalanceAtBlock").Return(amount(), mocks.NilError)
	suite.mockAccountRepo.On("RetrieveEverOwnedTokensByBlockAfter").Return([]domain.Token{}, &rTypes.Error{})

	// when:
	actual, err := suite.accountService.AccountBalance(nil, request(true))

	// then:
	assert.Nil(suite.T(), actual)
	assert.NotNil(suite.T(), err)
}

func (suite *accountServiceSuite) TestAccountBalanceThrowsWhenAddressInvalid() {
	for _, invalidAddress := range []string{"abc", "-1"} {
		suite.T().Run(invalidAddress, func(t *testing.T) {
			// given
			// when
			actual, err := suite.accountService.AccountBalance(nil, &rTypes.AccountBalanceRequest{
				AccountIdentifier: &rTypes.AccountIdentifier{Address: invalidAddress},
			})

			// then
			assert.Equal(t, errors.ErrInvalidAccount, err)
			assert.Nil(t, actual)

		})
	}
}

func (suite *accountServiceSuite) TestAccountCoins() {
	// when:
	result, err := suite.accountService.AccountCoins(nil, &rTypes.AccountCoinsRequest{})

	// then:
	assert.Nil(suite.T(), result)
	assert.Equal(suite.T(), errors.ErrNotImplemented, err)
}
