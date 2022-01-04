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
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/mocks"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

var (
	account, _    = types.NewAccountFromEncodedID(500)
	entityId      = domain.MustDecodeEntityId(600)
	hbarAmount    = types.HbarAmount{Value: 300}
	statusSuccess = types.TransactionResults[22]
)

func block() *types.Block {
	return &types.Block{
		Index:               1,
		Hash:                "12345",
		ConsensusStartNanos: 1000000,
		ConsensusEndNanos:   20000000,
		ParentIndex:         2,
		ParentHash:          "parenthash",
	}
}

func blockRequest() *rTypes.BlockRequest {
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

func expectedBlockResponse(transactions ...*rTypes.Transaction) *rTypes.BlockResponse {
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
			Timestamp:    1,
			Transactions: transactions,
			Metadata:     nil,
		},
		OtherTransactions: nil,
	}
}

func expectedTransaction(entityId *domain.EntityId, hash string) *rTypes.Transaction {
	response := &rTypes.Transaction{
		TransactionIdentifier: &rTypes.TransactionIdentifier{Hash: hash},
		Operations: []*rTypes.Operation{
			{
				OperationIdentifier: &rTypes.OperationIdentifier{Index: 0},
				Type:                types.TransactionTypes[14],
				Status:              &statusSuccess,
				Account:             account.ToRosetta(),
				Amount:              hbarAmount.ToRosetta(),
			},
		},
	}
	if entityId != nil {
		response.Metadata = map[string]interface{}{"entity_id": entityId.String()}
	}

	return response
}

func makeTransaction(entityId *domain.EntityId, hash string) *types.Transaction {
	return &types.Transaction{
		EntityId: entityId,
		Hash:     hash,
		Operations: []*types.Operation{
			{
				Index:   0,
				Type:    types.TransactionTypes[14],
				Status:  statusSuccess,
				Account: account,
				Amount:  &hbarAmount,
			},
		},
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

	baseService := NewOnlineBaseService(suite.mockBlockRepo, suite.mockTransactionRepo)
	suite.blockService = NewBlockAPIService(baseService)
}

func (suite *blockServiceSuite) TestNewBlockAPIService() {
	baseService := NewOnlineBaseService(suite.mockBlockRepo, suite.mockTransactionRepo)
	blockService := NewBlockAPIService(baseService)

	assert.IsType(suite.T(), &blockAPIService{}, blockService)
}

func (suite *blockServiceSuite) TestBlock() {
	// given:
	exampleTransactions := []*types.Transaction{
		makeTransaction(nil, "123"),
		makeTransaction(&entityId, "246"),
	}
	expected := expectedBlockResponse(
		expectedTransaction(nil, "123"),
		expectedTransaction(&entityId, "246"),
	)
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), mocks.NilError)
	suite.mockTransactionRepo.On("FindBetween").Return(exampleTransactions, mocks.NilError)

	// when:
	actual, e := suite.blockService.Block(nil, blockRequest())

	// then:
	assert.Nil(suite.T(), e)
	assert.Equal(suite.T(), expected, actual)
}

func (suite *blockServiceSuite) TestBlockThrowsWhenFindByIdentifierFails() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(
		mocks.NilBlock,
		&rTypes.Error{},
	)

	// when:
	actual, err := suite.blockService.Block(nil, blockRequest())

	// then:
	assert.Nil(suite.T(), actual)
	assert.NotNil(suite.T(), err)
}

func (suite *blockServiceSuite) TestBlockThrowsWhenFindBetweenFails() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), mocks.NilError)
	suite.mockTransactionRepo.On("FindBetween").Return([]*types.Transaction{}, &rTypes.Error{})

	// when:
	actual, err := suite.blockService.Block(nil, blockRequest())

	// then:
	assert.Nil(suite.T(), actual)
	assert.NotNil(suite.T(), err)
}

func (suite *blockServiceSuite) TestBlockTransaction() {
	// given:
	exampleTransaction := makeTransaction(nil, "somehash")
	expected := &rTypes.BlockTransactionResponse{Transaction: expectedTransaction(nil, "somehash")}

	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), mocks.NilError)
	suite.mockTransactionRepo.On("FindByHashInBlock").Return(exampleTransaction, mocks.NilError)

	// when:
	actual, err := suite.blockService.BlockTransaction(nil, transactionRequest())

	// then:
	assert.Equal(suite.T(), expected, actual)
	assert.Nil(suite.T(), err)
}

func (suite *blockServiceSuite) TestBlockTransactionThrowsWhenFindByIdentifierFails() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(mocks.NilBlock, &rTypes.Error{})

	// when:
	actual, err := suite.blockService.BlockTransaction(nil, transactionRequest())

	// then:
	assert.Nil(suite.T(), actual)
	assert.NotNil(suite.T(), err)
}

func (suite *blockServiceSuite) TestBlockTransactionThrowsWhenFindByHashInBlockFails() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), mocks.NilError)
	suite.mockTransactionRepo.On("FindByHashInBlock").Return(mocks.NilTransaction, &rTypes.Error{})

	// when:
	actual, err := suite.blockService.BlockTransaction(nil, transactionRequest())

	// then:
	assert.Nil(suite.T(), actual)
	assert.NotNil(suite.T(), err)
}
