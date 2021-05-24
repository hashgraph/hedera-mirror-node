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

package block

import (
	"github.com/coinbase/rosetta-sdk-go/server"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/services/base"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/mocks/repository"
	"github.com/stretchr/testify/assert"
	"testing"
)

func getSubject() server.BlockAPIServicer {
	baseService := base.NewBaseService(repository.MBlockRepository, repository.MTransactionRepository)
	return NewBlockAPIService(baseService)
}

func block() *types.Block {
	return &types.Block{
		Index:               1,
		Hash:                "123jsjs",
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
				Hash:  "0x123jsjs",
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

func TestNewBlockAPIService(t *testing.T) {
	repository.Setup()
	baseService := base.NewBaseService(repository.MBlockRepository, repository.MTransactionRepository)
	blockService := NewBlockAPIService(baseService)

	assert.IsType(t, &BlockAPIService{}, blockService)
}

func TestBlock(t *testing.T) {
	// given:
	exampleTransactions := []*types.Transaction{
		dummyTransaction("123"),
		dummyTransaction("246"),
	}

	repository.Setup()
	repository.MBlockRepository.On("FindByIdentifier").Return(block(), repository.NilError)
	repository.MTransactionRepository.On("FindBetween").Return(exampleTransactions, repository.NilError)

	// when:
	res, e := getSubject().Block(nil, exampleBlockRequest())

	// then:
	assert.Nil(t, e)
	assert.Equal(t, exampleBlockResponse(), res)
}

func TestBlockThrowsWhenFindByIdentifierFails(t *testing.T) {
	// given:
	repository.Setup()
	repository.MBlockRepository.On("FindByIdentifier").Return(
		repository.NilBlock,
		&rTypes.Error{},
	)

	// when:
	res, e := getSubject().Block(nil, exampleBlockRequest())

	// then:
	assert.Nil(t, res)
	assert.NotNil(t, e)
}

func TestBlockThrowsWhenFindBetweenFails(t *testing.T) {
	// given:
	repository.Setup()
	repository.MBlockRepository.On("FindByIdentifier").Return(block(), repository.NilError)
	repository.MTransactionRepository.On("FindBetween").Return(
		[]*types.Transaction{},
		&rTypes.Error{},
	)

	// when:
	res, e := getSubject().Block(nil, exampleBlockRequest())

	// then:
	assert.Nil(t, res)
	assert.NotNil(t, e)
}

func TestBlockTransaction(t *testing.T) {
	// given:
	exampleTransaction := dummyTransaction("somehash")

	expectedResult := &rTypes.BlockTransactionResponse{Transaction: &rTypes.Transaction{
		TransactionIdentifier: &rTypes.TransactionIdentifier{Hash: "somehash"},
		Operations:            []*rTypes.Operation{},
		Metadata:              nil,
	}}

	repository.Setup()
	repository.MBlockRepository.On("FindByIdentifier").Return(block(), repository.NilError)
	repository.MTransactionRepository.On("FindByHashInBlock").Return(exampleTransaction, repository.NilError)

	// when:
	res, e := getSubject().BlockTransaction(nil, transactionRequest())

	// then:
	assert.Equal(t, expectedResult, res)
	assert.Nil(t, e)
}

func TestBlockTransactionThrowsWhenFindByIdentifierFails(t *testing.T) {
	// given:
	repository.Setup()
	repository.MBlockRepository.On("FindByIdentifier").Return(repository.NilBlock, &rTypes.Error{})

	// when:
	res, e := getSubject().BlockTransaction(nil, transactionRequest())

	// then:
	assert.Nil(t, res)
	assert.NotNil(t, e)
}

func TestBlockTransactionThrowsWhenFindByHashInBlockFails(t *testing.T) {
	// given:
	repository.Setup()
	repository.MBlockRepository.On("FindByIdentifier").Return(block(), repository.NilError)
	repository.MTransactionRepository.On("FindByHashInBlock").Return(repository.NilTransaction, &rTypes.Error{})

	// when:
	res, e := getSubject().BlockTransaction(nil, transactionRequest())

	// then:
	assert.Nil(t, res)
	assert.NotNil(t, e)
}
