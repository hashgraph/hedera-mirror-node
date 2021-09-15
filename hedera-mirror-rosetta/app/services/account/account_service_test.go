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

package account

import (
	"testing"

	"github.com/coinbase/rosetta-sdk-go/server"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	entityid "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/services/encoding"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/services/base"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/mocks/repository"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

var (
	token1 = types.Token{
		TokenId:  entityid.EntityId{EncodedId: 2001, EntityNum: 2001},
		Decimals: 5,
		Name:     "foobar1",
		Symbol:   "foobar1",
	}
	token2 = types.Token{
		TokenId:  entityid.EntityId{EncodedId: 2002, EntityNum: 2002},
		Decimals: 6,
		Name:     "foobar2",
		Symbol:   "foobar2",
	}
	token3 = types.Token{
		TokenId:  entityid.EntityId{EncodedId: 2003, EntityNum: 2003},
		Decimals: 7,
		Name:     "foobar3",
		Symbol:   "foobar3",
	}
	token4 = types.Token{
		TokenId:  entityid.EntityId{EncodedId: 2004, EntityNum: 2004},
		Decimals: 8,
		Name:     "foobar4",
		Symbol:   "foobar4",
	}
	token5 = types.Token{
		TokenId:  entityid.EntityId{EncodedId: 2005, EntityNum: 2005},
		Decimals: 9,
		Name:     "foobar5",
		Symbol:   "foobar5",
	}
)

func amount() []types.Amount {
	return []types.Amount{
		&types.HbarAmount{Value: int64(1000)},
		types.NewTokenAmount(token1, 100),
		types.NewTokenAmount(token2, 200),
	}
}

func block() *types.Block {
	return &types.Block{
		Index:               1,
		Hash:                "123jsjs",
		LatestIndex:         2,
		ConsensusStartNanos: 1000000,
		ConsensusEndNanos:   20000000,
		ParentIndex:         0,
		ParentHash:          "parenthash",
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
			Hash:  "0x123jsjs",
		},
		Balances: []*rTypes.Amount{
			{
				Value:    "1000",
				Currency: config.CurrencyHbar,
			},
			types.NewTokenAmount(token1, 100).ToRosetta(),
			types.NewTokenAmount(token2, 200).ToRosetta(),
		},
	}
}

func addGenesisTokenBalances(
	resp *rTypes.AccountBalanceResponse,
	tokens ...types.Token,
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
	mockAccountRepo     *repository.MockAccountRepository
	mockBlockRepo       *repository.MockBlockRepository
	mockTransactionRepo *repository.MockTransactionRepository
}

func (suite *accountServiceSuite) SetupTest() {
	suite.mockAccountRepo = &repository.MockAccountRepository{}
	suite.mockBlockRepo = &repository.MockBlockRepository{}
	suite.mockTransactionRepo = &repository.MockTransactionRepository{}

	baseService := base.NewBaseService(suite.mockBlockRepo, suite.mockTransactionRepo)
	suite.accountService = NewAccountAPIService(baseService, suite.mockAccountRepo)
}

func (suite *accountServiceSuite) TestAccountBalance() {
	// given:
	suite.mockBlockRepo.On("RetrieveLatest").Return(block(), repository.NilError)
	suite.mockAccountRepo.On("RetrieveBalanceAtBlock").Return(amount(), repository.NilError)
	suite.mockAccountRepo.On("RetrieveTransferredTokensInBlockAfter").Return([]types.Token{}, repository.NilError)
	suite.mockAccountRepo.On("RetrieveDissociatedTokens").Return([]types.Token{}, repository.NilError)

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
	tokensInNextBlock := []types.Token{token2, token3, token4}
	dissociatedTokens := []types.Token{token3, token4, token5}
	expected := addGenesisTokenBalances(expectedAccountBalanceResponse(), token3, token4, token5)
	suite.mockBlockRepo.On("RetrieveLatest").Return(block(), repository.NilError)
	suite.mockAccountRepo.On("RetrieveBalanceAtBlock").Return(amount(), repository.NilError)
	suite.mockAccountRepo.On("RetrieveTransferredTokensInBlockAfter").Return(tokensInNextBlock, repository.NilError)
	suite.mockAccountRepo.On("RetrieveDissociatedTokens").Return(dissociatedTokens, repository.NilError)

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
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), repository.NilError)
	suite.mockAccountRepo.On("RetrieveBalanceAtBlock").Return(amount(), repository.NilError)
	suite.mockAccountRepo.On("RetrieveTransferredTokensInBlockAfter").Return([]types.Token{}, repository.NilError)
	suite.mockAccountRepo.On("RetrieveDissociatedTokens").Return([]types.Token{}, repository.NilError)

	// when:
	actual, err := suite.accountService.AccountBalance(nil, request(true))

	// then:
	assert.Equal(suite.T(), expectedAccountBalanceResponse(), actual)
	assert.Nil(suite.T(), err)
	suite.mockBlockRepo.AssertNotCalled(suite.T(), "RetrieveLatest")
}

func (suite *accountServiceSuite) TestAccountBalanceWithBlockIdentifierAndAdditionalTokens() {
	// given:
	tokensInNextBlock := []types.Token{token2, token3, token4}
	dissociatedTokens := []types.Token{token2, token5}
	expected := addGenesisTokenBalances(expectedAccountBalanceResponse(), token3, token4, token5)
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), repository.NilError)
	suite.mockAccountRepo.On("RetrieveBalanceAtBlock").Return(amount(), repository.NilError)
	suite.mockAccountRepo.On("RetrieveTransferredTokensInBlockAfter").Return(tokensInNextBlock, repository.NilError)
	suite.mockAccountRepo.On("RetrieveDissociatedTokens").Return(dissociatedTokens, repository.NilError)

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
	suite.mockBlockRepo.On("FindByIdentifier").Return(genesisBlock(), repository.NilError)

	// when:
	actual, err := suite.accountService.AccountBalance(nil, request(true))

	// then:
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actual)
	suite.mockBlockRepo.AssertNotCalled(suite.T(), "RetrieveLatest")
	suite.mockAccountRepo.AssertNotCalled(suite.T(), "RetrieveBalanceAtBlock")
	suite.mockAccountRepo.AssertNotCalled(suite.T(), "RetrieveTransferredTokensInBlockAfter")
}

func (suite *accountServiceSuite) TestAccountBalanceThrowsWhenRetrieveLatestFails() {
	// given:
	suite.mockBlockRepo.On("RetrieveLatest").Return(repository.NilBlock, &rTypes.Error{})

	// when:
	actual, err := suite.accountService.AccountBalance(nil, request(false))

	// then:
	assert.Nil(suite.T(), actual)
	assert.NotNil(suite.T(), err)
	suite.mockAccountRepo.AssertNotCalled(suite.T(), "RetrieveBalanceAtBlock")
}

func (suite *accountServiceSuite) TestAccountBalanceThrowsWhenRetrieveBlockFails() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(repository.NilBlock, &rTypes.Error{})

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
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), repository.NilError)
	suite.mockAccountRepo.On("RetrieveBalanceAtBlock").Return([]types.Amount{}, &rTypes.Error{})

	// when:
	actual, err := suite.accountService.AccountBalance(nil, request(true))

	// then:
	assert.Nil(suite.T(), actual)
	assert.NotNil(suite.T(), err)
}

func (suite *accountServiceSuite) TestAccountBalanceThrowsWhenRetrieveTransferredTokensInBlockAfterFails() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), repository.NilError)
	suite.mockAccountRepo.On("RetrieveBalanceAtBlock").Return(amount(), repository.NilError)
	suite.mockAccountRepo.On("RetrieveTransferredTokensInBlockAfter").Return([]types.Token{}, &rTypes.Error{})
	suite.mockAccountRepo.On("RetrieveDissociatedTokens").Return([]types.Token{}, repository.NilError)

	// when:
	actual, err := suite.accountService.AccountBalance(nil, request(true))

	// then:
	assert.Nil(suite.T(), actual)
	assert.NotNil(suite.T(), err)
}

func (suite *accountServiceSuite) TestAccountBalanceThrowsWhenRetrieveDissociatedTokensFails() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), repository.NilError)
	suite.mockAccountRepo.On("RetrieveBalanceAtBlock").Return(amount(), repository.NilError)
	suite.mockAccountRepo.On("RetrieveTransferredTokensInBlockAfter").Return([]types.Token{}, repository.NilError)
	suite.mockAccountRepo.On("RetrieveDissociatedTokens").Return([]types.Token{}, &rTypes.Error{})

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
