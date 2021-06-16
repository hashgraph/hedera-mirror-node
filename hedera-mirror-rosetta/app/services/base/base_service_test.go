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
package base

import (
	"testing"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/mocks/repository"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

var (
	exampleHash                      = "0x12345"
	exampleIndex                     = int64(1)
	exampleMap                       = map[int]string{1: "value", 2: "otherValue"}
	exampleTypesArray                = []string{"Transfer"}
	nilMap            map[int]string = nil
	nilArray          []string       = nil
)

func block() *types.Block {
	return &types.Block{
		Index:               1,
		Hash:                "0x12345",
		ConsensusStartNanos: 1000000,
		ConsensusEndNanos:   20000000,
		ParentIndex:         2,
		ParentHash:          "0x23456",
	}
}

func transaction() *types.Transaction {
	return &types.Transaction{
		Hash:       exampleHash,
		Operations: nil,
	}
}

func transactions() []*types.Transaction {
	return []*types.Transaction{
		{
			Hash:       exampleHash,
			Operations: nil,
		},
		{
			Hash:       exampleHash,
			Operations: nil,
		},
	}
}

func examplePartialBlockIdentifier(index *int64, hash *string) *rTypes.PartialBlockIdentifier {
	return &rTypes.PartialBlockIdentifier{
		Index: index,
		Hash:  hash,
	}
}

func TestBaseServiceSuite(t *testing.T) {
	suite.Run(t, new(baseServiceSuite))
}

type baseServiceSuite struct {
	suite.Suite
	baseService         *BaseService
	mockBlockRepo       *repository.MockBlockRepository
	mockTransactionRepo *repository.MockTransactionRepository
}

func (suite *baseServiceSuite) SetupTest() {
	suite.mockBlockRepo = &repository.MockBlockRepository{}
	suite.mockTransactionRepo = &repository.MockTransactionRepository{}

	baseService := NewBaseService(suite.mockBlockRepo, suite.mockTransactionRepo)
	suite.baseService = &baseService
}

func (suite *baseServiceSuite) TestRetrieveBlockThrowsNoIdentifiers() {
	// given:

	// when:
	res, e := suite.baseService.RetrieveBlock(examplePartialBlockIdentifier(nil, nil))

	// then:
	assert.Nil(suite.T(), res)
	assert.Equal(suite.T(), errors.ErrInternalServerError, e)
}

func (suite *baseServiceSuite) TestRetrieveBlockFindByIdentifier() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), repository.NilError)

	// when:
	res, e := suite.baseService.RetrieveBlock(examplePartialBlockIdentifier(&exampleIndex, &exampleHash))

	// then:
	assert.Nil(suite.T(), e)
	assert.Equal(suite.T(), block(), res)
}

func (suite *baseServiceSuite) TestRetrieveBlockThrowsFindByIdentifier() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(
		repository.NilBlock,
		&rTypes.Error{},
	)

	// when:
	res, e := suite.baseService.RetrieveBlock(examplePartialBlockIdentifier(&exampleIndex, &exampleHash))

	// then:
	assert.Nil(suite.T(), res)
	assert.NotNil(suite.T(), e)
}

func (suite *baseServiceSuite) TestRetrieveBlockFindByIndex() {
	// given:
	suite.mockBlockRepo.On("FindByIndex").Return(
		block(),
		repository.NilError,
	)

	// when:
	res, e := suite.baseService.RetrieveBlock(examplePartialBlockIdentifier(&exampleIndex, nil))

	// then:
	assert.Nil(suite.T(), e)
	assert.Equal(suite.T(), block(), res)
}

func (suite *baseServiceSuite) TestRetrieveBlockThrowsFindByIndex() {
	// given:
	suite.mockBlockRepo.On("FindByIndex").Return(
		repository.NilBlock,
		&rTypes.Error{},
	)

	// when:
	res, e := suite.baseService.RetrieveBlock(examplePartialBlockIdentifier(&exampleIndex, nil))

	// then:
	assert.Nil(suite.T(), res)
	assert.NotNil(suite.T(), e)
}

func (suite *baseServiceSuite) TestRetrieveBlockFindByHash() {
	// given:
	suite.mockBlockRepo.On("FindByHash").Return(
		block(),
		repository.NilError,
	)

	// when:
	res, e := suite.baseService.RetrieveBlock(examplePartialBlockIdentifier(nil, &exampleHash))

	// then:
	assert.Nil(suite.T(), e)
	assert.Equal(suite.T(), block(), res)
}

func (suite *baseServiceSuite) TestRetrieveBlockThrowsFindByHash() {
	// given:
	suite.mockBlockRepo.On("FindByHash").Return(
		repository.NilBlock,
		&rTypes.Error{},
	)

	// when:
	res, e := suite.baseService.RetrieveBlock(examplePartialBlockIdentifier(nil, &exampleHash))

	// then:
	assert.Nil(suite.T(), res)
	assert.NotNil(suite.T(), e)
}

func (suite *baseServiceSuite) TestRetrieveLatest() {
	// given:
	suite.mockBlockRepo.On("RetrieveLatest").Return(
		block(),
		repository.NilError,
	)

	// when:
	res, e := suite.baseService.RetrieveLatest()

	// then:
	assert.Nil(suite.T(), e)
	assert.Equal(suite.T(), block(), res)
}

func (suite *baseServiceSuite) TestRetrieveLatestThrows() {
	// given:
	suite.mockBlockRepo.On("RetrieveLatest").Return(
		repository.NilBlock,
		&rTypes.Error{},
	)

	// when:
	res, e := suite.baseService.RetrieveLatest()

	// then:
	assert.Nil(suite.T(), res)
	assert.NotNil(suite.T(), e)
}

func (suite *baseServiceSuite) TestRetrieveGenesis() {
	// given:
	suite.mockBlockRepo.On("RetrieveGenesis").Return(
		block(),
		repository.NilError,
	)

	// when:
	res, e := suite.baseService.RetrieveGenesis()

	// then:
	assert.Nil(suite.T(), e)
	assert.Equal(suite.T(), block(), res)
}

func (suite *baseServiceSuite) TestRetrieveGenesisThrows() {
	// given:
	suite.mockBlockRepo.On("RetrieveGenesis").Return(
		repository.NilBlock,
		&rTypes.Error{},
	)

	// when:
	res, e := suite.baseService.RetrieveGenesis()

	// then:
	assert.Nil(suite.T(), res)
	assert.NotNil(suite.T(), e)
}

func (suite *baseServiceSuite) TestFindByIdentifier() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(
		block(),
		repository.NilError,
	)

	// when:
	res, e := suite.baseService.FindByIdentifier(exampleIndex, exampleHash)

	// then:
	assert.Nil(suite.T(), e)
	assert.Equal(suite.T(), block(), res)
}

func (suite *baseServiceSuite) TestFindByIdentifierThrows() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(
		repository.NilBlock,
		&rTypes.Error{},
	)

	// when:
	res, e := suite.baseService.FindByIdentifier(exampleIndex, exampleHash)

	// then:
	assert.Nil(suite.T(), res)
	assert.NotNil(suite.T(), e)
}

func (suite *baseServiceSuite) TestFindByHashInBlock() {
	// given:
	suite.mockTransactionRepo.On("FindByHashInBlock").Return(
		transaction(),
		repository.NilError,
	)

	// when:
	res, e := suite.baseService.FindByHashInBlock(exampleHash, 1, 2)

	// then:
	assert.Nil(suite.T(), e)
	assert.Equal(suite.T(), transaction(), res)
}

func (suite *baseServiceSuite) TestFindByHashInBlockThrows() {
	// given:
	suite.mockTransactionRepo.On("FindByHashInBlock").Return(
		repository.NilTransaction,
		&rTypes.Error{},
	)

	// when:
	res, e := suite.baseService.FindByHashInBlock(exampleHash, 1, 2)

	// then:
	assert.Nil(suite.T(), res)
	assert.NotNil(suite.T(), e)
}

func (suite *baseServiceSuite) TestFindBetween() {
	// given:
	suite.mockTransactionRepo.On("FindBetween").Return(
		transactions(),
		repository.NilError,
	)

	// when:
	res, e := suite.baseService.FindBetween(1, 2)

	// then:
	assert.Nil(suite.T(), e)
	assert.Equal(suite.T(), transactions(), res)
}

func (suite *baseServiceSuite) TestFindBetweenThrows() {
	// given:
	suite.mockTransactionRepo.On("FindBetween").Return([]*types.Transaction{}, &rTypes.Error{})

	// when:
	res, e := suite.baseService.FindBetween(1, 2)

	// then:
	assert.Equal(suite.T(), []*types.Transaction{}, res)
	assert.NotNil(suite.T(), e)
}

func (suite *baseServiceSuite) TestStatuses() {
	// given:
	suite.mockTransactionRepo.On("Results").Return(
		exampleMap,
		repository.NilError,
	)

	// when:
	res, e := suite.baseService.Results()

	// then:
	assert.Nil(suite.T(), e)
	assert.Equal(suite.T(), exampleMap, res)
}

func (suite *baseServiceSuite) TestStatusesThrows() {
	// given:
	suite.mockTransactionRepo.On("Results").Return(
		nilMap,
		&rTypes.Error{},
	)

	// when:
	res, e := suite.baseService.Results()

	// then:
	assert.Nil(suite.T(), res)
	assert.NotNil(suite.T(), e)
}

func (suite *baseServiceSuite) TestTypesAsArray() {
	// given:
	suite.mockTransactionRepo.On("TypesAsArray").Return(
		exampleTypesArray,
		repository.NilError,
	)

	// when:
	res, e := suite.baseService.TypesAsArray()

	// then:
	assert.Nil(suite.T(), e)
	assert.Equal(suite.T(), exampleTypesArray, res)
}

func (suite *baseServiceSuite) TestTypesAsArrayThrows() {
	// given:
	suite.mockTransactionRepo.On("TypesAsArray").Return(
		nilArray,
		&rTypes.Error{},
	)

	// when:
	res, e := suite.baseService.TypesAsArray()

	// then:
	assert.Nil(suite.T(), res)
	assert.NotNil(suite.T(), e)
}
