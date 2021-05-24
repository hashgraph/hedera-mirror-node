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
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/services/base"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/mocks/repository"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

func block() *types.Block {
	return &types.Block{
		Index:               1,
		Hash:                "123jsjs",
		ConsensusStartNanos: 1000000,
		ConsensusEndNanos:   20000000,
		ParentIndex:         0,
		ParentHash:          "parenthash",
	}
}

func amount() *types.Amount {
	return &types.Amount{
		Value: int64(1000),
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
		},
	}
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

	// when:
	actualResult, e := suite.accountService.AccountBalance(nil, request(false))

	// then:
	assert.Equal(suite.T(), expectedAccountBalanceResponse(), actualResult)
	assert.Nil(suite.T(), e)
	suite.mockBlockRepo.AssertNotCalled(suite.T(), "FindByIdentifier")
	suite.mockBlockRepo.AssertNotCalled(suite.T(), "FindByHash")
}

func (suite *accountServiceSuite) TestAccountBalanceWithBlockIdentifier() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), repository.NilError)
	suite.mockAccountRepo.On("RetrieveBalanceAtBlock").Return(amount(), repository.NilError)

	// when:
	actualResult, e := suite.accountService.AccountBalance(nil, request(true))

	// then:
	assert.Equal(suite.T(), expectedAccountBalanceResponse(), actualResult)
	assert.Nil(suite.T(), e)
	suite.mockBlockRepo.AssertNotCalled(suite.T(), "RetrieveLatest")
}

func (suite *accountServiceSuite) TestAccountBalanceThrowsWhenRetrieveLatestFails() {
	// given:
	suite.mockBlockRepo.On("RetrieveLatest").Return(repository.NilBlock, &rTypes.Error{})

	// when:
	actualResult, e := suite.accountService.AccountBalance(nil, request(false))

	// then:
	assert.Nil(suite.T(), actualResult)
	assert.NotNil(suite.T(), e)
	suite.mockAccountRepo.AssertNotCalled(suite.T(), "RetrieveBalanceAtBlock")
}

func (suite *accountServiceSuite) TestAccountBalanceThrowsWhenRetrieveBlockFails() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(repository.NilBlock, &rTypes.Error{})

	// when:
	actualResult, e := suite.accountService.AccountBalance(nil, request(true))

	// then:
	assert.Nil(suite.T(), actualResult)
	assert.NotNil(suite.T(), e)
	suite.mockAccountRepo.AssertNotCalled(suite.T(), "RetrieveBalanceAtBlock")
	suite.mockBlockRepo.AssertNotCalled(suite.T(), "RetrieveLatest")
}

func (suite *accountServiceSuite) TestAccountBalanceThrowsWhenRetrieveBalanceAtBlockFails() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), repository.NilError)
	suite.mockAccountRepo.On("RetrieveBalanceAtBlock").Return(repository.NilAmount, &rTypes.Error{})

	// when:
	actualResult, e := suite.accountService.AccountBalance(nil, request(true))

	// then:
	assert.Nil(suite.T(), actualResult)
	assert.NotNil(suite.T(), e)
}

func (suite *accountServiceSuite) TestAccountCoins() {
	// when:
	result, err := suite.accountService.AccountCoins(nil, &rTypes.AccountCoinsRequest{})

	// then:
	assert.Nil(suite.T(), result)
	assert.Equal(suite.T(), errors.ErrNotImplemented, err)
}
