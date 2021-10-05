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
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/mocks"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

func block() *types.Block {
	return &types.Block{
		Index:               1,
		Hash:                "12345",
		LatestIndex:         5,
		ConsensusStartNanos: 1000000,
		ConsensusEndNanos:   20000000,
		ParentIndex:         2,
		ParentHash:          "parenthash",
	}
}

func exampleBlockRequest() *rTypes.BlockRequest {
	index := int64(100)
	hash := "somehashh"

	return &rTypes.BlockRequest{
		NetworkIdentifier: &rTypes.NetworkIdentifier{
			Blockchain:           "Hedera",
			Network:              "hhh",
			SubNetworkIdentifier: nil,
		},
		BlockIdentifier: &rTypes.PartialBlockIdentifier{
			Index: &index,
			Hash:  &hash,
		},
	}
}

func exampleBlockResponse() *rTypes.BlockResponse {
	return &rTypes.BlockResponse{
		Block: &rTypes.Block{
			BlockIdentifier: &rTypes.BlockIdentifier{
				Index: 1,
				Hash:  "0x12345",
			},
			ParentBlockIdentifier: &rTypes.BlockIdentifier{
				Index: 2,
				Hash:  "0xparenthash",
			},
			Timestamp: 1,
			Transactions: []*rTypes.Transaction{
				{
					TransactionIdentifier: &rTypes.TransactionIdentifier{Hash: "123"},
					Operations:            []*rTypes.Operation{},
					Metadata:              nil,
				},
				{
					TransactionIdentifier: &rTypes.TransactionIdentifier{Hash: "246"},
					Operations:            []*rTypes.Operation{},
					Metadata:              nil,
				},
			},
			Metadata: nil,
		},
		OtherTransactions: nil,
	}
}

func dummyTransaction(hash string) *types.Transaction {
	return &types.Transaction{
		Hash:       hash,
		Operations: nil,
	}
}

func transactionRequest() *rTypes.BlockTransactionRequest {
	return &rTypes.BlockTransactionRequest{
		NetworkIdentifier: &rTypes.NetworkIdentifier{
			Blockchain: "someblockchain",
			Network:    "somenetwork",
			SubNetworkIdentifier: &rTypes.SubNetworkIdentifier{
				Network:  "somesubnetwork",
				Metadata: nil,
			},
		},
		BlockIdentifier: &rTypes.BlockIdentifier{
			Index: 1,
			Hash:  "someblockhash",
		},
		TransactionIdentifier: &rTypes.TransactionIdentifier{Hash: "somehash"},
	}
}

func TestBlockServiceSuite(t *testing.T) {
	suite.Run(t, new(blockServiceSuite))
}

type blockServiceSuite struct {
	suite.Suite
	blockService        server.BlockAPIServicer
	mockBlockRepo       *mocks.MockBlockRepository
	mockTransactionRepo *mocks.MockTransactionRepository
}

func (suite *blockServiceSuite) SetupTest() {
	suite.mockBlockRepo = &mocks.MockBlockRepository{}
	suite.mockTransactionRepo = &mocks.MockTransactionRepository{}

	baseService := NewBaseService(suite.mockBlockRepo, suite.mockTransactionRepo)
	suite.blockService = NewBlockAPIService(baseService)
}

func (suite *blockServiceSuite) TestNewBlockAPIService() {
	baseService := NewBaseService(suite.mockBlockRepo, suite.mockTransactionRepo)
	blockService := NewBlockAPIService(baseService)

	assert.IsType(suite.T(), &blockAPIService{}, blockService)
}

func (suite *blockServiceSuite) TestBlock() {
	// given:
	exampleTransactions := []*types.Transaction{
		dummyTransaction("123"),
		dummyTransaction("246"),
	}

	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), mocks.NilError)
	suite.mockTransactionRepo.On("FindBetween").Return(exampleTransactions, mocks.NilError)

	// when:
	res, e := suite.blockService.Block(nil, exampleBlockRequest())

	// then:
	assert.Nil(suite.T(), e)
	assert.Equal(suite.T(), exampleBlockResponse(), res)
}

func (suite *blockServiceSuite) TestBlockThrowsWhenFindByIdentifierFails() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(
		mocks.NilBlock,
		&rTypes.Error{},
	)

	// when:
	res, e := suite.blockService.Block(nil, exampleBlockRequest())

	// then:
	assert.Nil(suite.T(), res)
	assert.NotNil(suite.T(), e)
}

func (suite *blockServiceSuite) TestBlockThrowsWhenFindBetweenFails() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), mocks.NilError)
	suite.mockTransactionRepo.On("FindBetween").Return(
		[]*types.Transaction{},
		&rTypes.Error{},
	)

	// when:
	res, e := suite.blockService.Block(nil, exampleBlockRequest())

	// then:
	assert.Nil(suite.T(), res)
	assert.NotNil(suite.T(), e)
}

func (suite *blockServiceSuite) TestBlockTransaction() {
	// given:
	exampleTransaction := dummyTransaction("somehash")

	expectedResult := &rTypes.BlockTransactionResponse{Transaction: &rTypes.Transaction{
		TransactionIdentifier: &rTypes.TransactionIdentifier{Hash: "somehash"},
		Operations:            []*rTypes.Operation{},
		Metadata:              nil,
	}}

	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), mocks.NilError)
	suite.mockTransactionRepo.On("FindByHashInBlock").Return(exampleTransaction, mocks.NilError)

	// when:
	res, e := suite.blockService.BlockTransaction(nil, transactionRequest())

	// then:
	assert.Equal(suite.T(), expectedResult, res)
	assert.Nil(suite.T(), e)
}

func (suite *blockServiceSuite) TestBlockTransactionThrowsWhenFindByIdentifierFails() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(mocks.NilBlock, &rTypes.Error{})

	// when:
	res, e := suite.blockService.BlockTransaction(nil, transactionRequest())

	// then:
	assert.Nil(suite.T(), res)
	assert.NotNil(suite.T(), e)
}

func (suite *blockServiceSuite) TestBlockTransactionThrowsWhenFindByHashInBlockFails() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), mocks.NilError)
	suite.mockTransactionRepo.On("FindByHashInBlock").Return(mocks.NilTransaction, &rTypes.Error{})

	// when:
	res, e := suite.blockService.BlockTransaction(nil, transactionRequest())

	// then:
	assert.Nil(suite.T(), res)
	assert.NotNil(suite.T(), e)
}
