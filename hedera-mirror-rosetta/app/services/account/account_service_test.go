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
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/services/base"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/mocks/repository"
	"github.com/stretchr/testify/assert"
	"testing"
)

func getSubject() *AccountAPIService {
	return NewAccountAPIService(baseService(), repository.MAccountRepository)
}

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

func baseService() base.BaseService {
	return base.NewBaseService(repository.MBlockRepository, repository.MTransactionRepository)
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

func TestNewAccountAPIService(t *testing.T) {
	repository.Setup()
	assert.IsType(t, &AccountAPIService{}, getSubject())
	assert.Equal(t, baseService(), getSubject().BaseService, "BaseService was not populated correctly")
	assert.Equal(
		t,
		repository.MAccountRepository,
		getSubject().accountRepo,
		"AccountsRepository was not populated correctly",
	)
}

func TestAccountBalance(t *testing.T) {
	// given:
	repository.Setup()
	repository.MBlockRepository.On("RetrieveLatest").Return(block(), repository.NilError)
	repository.MAccountRepository.On("RetrieveBalanceAtBlock").Return(amount(), repository.NilError)

	// when:
	actualResult, e := getSubject().AccountBalance(nil, request(false))

	// then:
	assert.Equal(t, expectedAccountBalanceResponse(), actualResult)
	assert.Nil(t, e)
	repository.MBlockRepository.AssertNotCalled(t, "FindByIdentifier")
	repository.MBlockRepository.AssertNotCalled(t, "FindByHash")
}

func TestAccountBalanceWithBlockIdentifier(t *testing.T) {
	// given:
	repository.Setup()
	repository.MBlockRepository.On("FindByIdentifier").Return(block(), repository.NilError)
	repository.MAccountRepository.On("RetrieveBalanceAtBlock").Return(amount(), repository.NilError)

	// when:
	actualResult, e := getSubject().AccountBalance(nil, request(true))

	// then:
	assert.Equal(t, expectedAccountBalanceResponse(), actualResult)
	assert.Nil(t, e)
	repository.MBlockRepository.AssertNotCalled(t, "RetrieveLatest")
}

func TestAccountBalanceThrowsWhenRetrieveLatestFails(t *testing.T) {
	// given:
	repository.Setup()
	repository.MBlockRepository.On("RetrieveLatest").Return(repository.NilBlock, &rTypes.Error{})

	// when:
	actualResult, e := getSubject().AccountBalance(nil, request(false))

	// then:
	assert.Nil(t, actualResult)
	assert.NotNil(t, e)
	repository.MAccountRepository.AssertNotCalled(t, "RetrieveBalanceAtBlock")
}

func TestAccountBalanceThrowsWhenRetrieveBlockFails(t *testing.T) {
	// given:
	repository.Setup()
	repository.MBlockRepository.On("FindByIdentifier").Return(repository.NilBlock, &rTypes.Error{})

	// when:
	actualResult, e := getSubject().AccountBalance(nil, request(true))

	// then:
	assert.Nil(t, actualResult)
	assert.NotNil(t, e)
	repository.MAccountRepository.AssertNotCalled(t, "RetrieveBalanceAtBlock")
	repository.MBlockRepository.AssertNotCalled(t, "RetrieveLatest")
}

func TestAccountBalanceThrowsWhenRetrieveBalanceAtBlockFails(t *testing.T) {
	// given:
	repository.Setup()
	repository.MBlockRepository.On("FindByIdentifier").Return(block(), repository.NilError)
	repository.MAccountRepository.On("RetrieveBalanceAtBlock").Return(repository.NilAmount, &rTypes.Error{})

	// when:
	actualResult, e := getSubject().AccountBalance(nil, request(true))

	// then:
	assert.Nil(t, actualResult)
	assert.NotNil(t, e)
}

func TestAccountCoins(t *testing.T) {
	// when:
	result, err := getSubject().AccountCoins(nil, &rTypes.AccountCoinsRequest{})

	// then:
	assert.Nil(t, result)
	assert.Equal(t, errors.ErrNotImplemented, err)
}
