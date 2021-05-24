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
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/mocks/repository"
	"github.com/stretchr/testify/assert"
	"testing"
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

func getSubject() *BaseService {
	baseService := NewBaseService(repository.MBlockRepository, repository.MTransactionRepository)
	return &baseService
}

func examplePartialBlockIdentifier(index *int64, hash *string) *rTypes.PartialBlockIdentifier {
	return &rTypes.PartialBlockIdentifier{
		Index: index,
		Hash:  hash,
	}
}

func TestRetrieveBlockThrowsNoIdentifiers(t *testing.T) {
	// given:
	repository.Setup()

	// when:
	res, e := getSubject().RetrieveBlock(examplePartialBlockIdentifier(nil, nil))

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrInternalServerError, e)
}

func TestRetrieveBlockFindByIdentifier(t *testing.T) {
	// given:
	repository.Setup()
	repository.MBlockRepository.On("FindByIdentifier").Return(
		block(),
		repository.NilError,
	)

	// when:
	res, e := getSubject().RetrieveBlock(examplePartialBlockIdentifier(&exampleIndex, &exampleHash))

	// then:
	assert.Nil(t, e)
	assert.Equal(t, block(), res)
}

func TestRetrieveBlockThrowsFindByIdentifier(t *testing.T) {
	// given:
	repository.Setup()
	repository.MBlockRepository.On("FindByIdentifier").Return(
		repository.NilBlock,
		&rTypes.Error{},
	)

	// when:
	res, e := getSubject().RetrieveBlock(examplePartialBlockIdentifier(&exampleIndex, &exampleHash))

	// then:
	assert.Nil(t, res)
	assert.NotNil(t, e)
}

func TestRetrieveBlockFindByIndex(t *testing.T) {
	// given:
	repository.Setup()
	repository.MBlockRepository.On("FindByIndex").Return(
		block(),
		repository.NilError,
	)

	// when:
	res, e := getSubject().RetrieveBlock(examplePartialBlockIdentifier(&exampleIndex, nil))

	// then:
	assert.Nil(t, e)
	assert.Equal(t, block(), res)
}

func TestRetrieveBlockThrowsFindByIndex(t *testing.T) {
	// given:
	repository.Setup()
	repository.MBlockRepository.On("FindByIndex").Return(
		repository.NilBlock,
		&rTypes.Error{},
	)

	// when:
	res, e := getSubject().RetrieveBlock(examplePartialBlockIdentifier(&exampleIndex, nil))

	// then:
	assert.Nil(t, res)
	assert.NotNil(t, e)
}

func TestRetrieveBlockFindByHash(t *testing.T) {
	// given:
	repository.Setup()
	repository.MBlockRepository.On("FindByHash").Return(
		block(),
		repository.NilError,
	)

	// when:
	res, e := getSubject().RetrieveBlock(examplePartialBlockIdentifier(nil, &exampleHash))

	// then:
	assert.Nil(t, e)
	assert.Equal(t, block(), res)
}

func TestRetrieveBlockThrowsFindByHash(t *testing.T) {
	// given:
	repository.Setup()
	repository.MBlockRepository.On("FindByHash").Return(
		repository.NilBlock,
		&rTypes.Error{},
	)

	// when:
	res, e := getSubject().RetrieveBlock(examplePartialBlockIdentifier(nil, &exampleHash))

	// then:
	assert.Nil(t, res)
	assert.NotNil(t, e)
}

func TestRetrieveLatest(t *testing.T) {
	// given:
	repository.Setup()
	repository.MBlockRepository.On("RetrieveLatest").Return(
		block(),
		repository.NilError,
	)

	// when:
	res, e := getSubject().RetrieveLatest()

	// then:
	assert.Nil(t, e)
	assert.Equal(t, block(), res)
}

func TestRetrieveLatestThrows(t *testing.T) {
	// given:
	repository.Setup()
	repository.MBlockRepository.On("RetrieveLatest").Return(
		repository.NilBlock,
		&rTypes.Error{},
	)

	// when:
	res, e := getSubject().RetrieveLatest()

	// then:
	assert.Nil(t, res)
	assert.NotNil(t, e)
}

func TestRetrieveGenesis(t *testing.T) {
	// given:
	repository.Setup()
	repository.MBlockRepository.On("RetrieveGenesis").Return(
		block(),
		repository.NilError,
	)

	// when:
	res, e := getSubject().RetrieveGenesis()

	// then:
	assert.Nil(t, e)
	assert.Equal(t, block(), res)
}

func TestRetrieveGenesisThrows(t *testing.T) {
	// given:
	repository.Setup()
	repository.MBlockRepository.On("RetrieveGenesis").Return(
		repository.NilBlock,
		&rTypes.Error{},
	)

	// when:
	res, e := getSubject().RetrieveGenesis()

	// then:
	assert.Nil(t, res)
	assert.NotNil(t, e)
}

func TestFindByIdentifier(t *testing.T) {
	// given:
	repository.Setup()
	repository.MBlockRepository.On("FindByIdentifier").Return(
		block(),
		repository.NilError,
	)

	// when:
	res, e := getSubject().FindByIdentifier(exampleIndex, exampleHash)

	// then:
	assert.Nil(t, e)
	assert.Equal(t, block(), res)
}

func TestFindByIdentifierThrows(t *testing.T) {
	// given:
	repository.Setup()
	repository.MBlockRepository.On("FindByIdentifier").Return(
		repository.NilBlock,
		&rTypes.Error{},
	)

	// when:
	res, e := getSubject().FindByIdentifier(exampleIndex, exampleHash)

	// then:
	assert.Nil(t, res)
	assert.NotNil(t, e)
}

func TestFindByHashInBlock(t *testing.T) {
	// given:
	repository.Setup()
	repository.MTransactionRepository.On("FindByHashInBlock").Return(
		transaction(),
		repository.NilError,
	)

	// when:
	res, e := getSubject().FindByHashInBlock(exampleHash, 1, 2)

	// then:
	assert.Nil(t, e)
	assert.Equal(t, transaction(), res)
}

func TestFindByHashInBlockThrows(t *testing.T) {
	// given:
	repository.Setup()
	repository.MTransactionRepository.On("FindByHashInBlock").Return(
		repository.NilTransaction,
		&rTypes.Error{},
	)

	// when:
	res, e := getSubject().FindByHashInBlock(exampleHash, 1, 2)

	// then:
	assert.Nil(t, res)
	assert.NotNil(t, e)
}

func TestFindBetween(t *testing.T) {
	// given:
	repository.Setup()
	repository.MTransactionRepository.On("FindBetween").Return(
		transactions(),
		repository.NilError,
	)

	// when:
	res, e := getSubject().FindBetween(1, 2)

	// then:
	assert.Nil(t, e)
	assert.Equal(t, transactions(), res)
}

func TestFindBetweenThrows(t *testing.T) {
	// given:
	repository.Setup()
	repository.MTransactionRepository.On("FindBetween").Return(
		[]*types.Transaction{},
		&rTypes.Error{},
	)

	// when:
	res, e := getSubject().FindBetween(1, 2)

	// then:
	assert.Equal(t, []*types.Transaction{}, res)
	assert.NotNil(t, e)
}

func TestStatuses(t *testing.T) {
	// given:
	repository.Setup()
	repository.MTransactionRepository.On("Results").Return(
		exampleMap,
		repository.NilError,
	)

	// when:
	res, e := getSubject().Results()

	// then:
	assert.Nil(t, e)
	assert.Equal(t, exampleMap, res)
}

func TestStatusesThrows(t *testing.T) {
	// given:
	repository.Setup()
	repository.MTransactionRepository.On("Results").Return(
		nilMap,
		&rTypes.Error{},
	)

	// when:
	res, e := getSubject().Results()

	// then:
	assert.Nil(t, res)
	assert.NotNil(t, e)
}

func TestTypesAsArray(t *testing.T) {
	// given:
	repository.Setup()
	repository.MTransactionRepository.On("TypesAsArray").Return(
		exampleTypesArray,
		repository.NilError,
	)

	// when:
	res, e := getSubject().TypesAsArray()

	// then:
	assert.Nil(t, e)
	assert.Equal(t, exampleTypesArray, res)
}

func TestTypesAsArrayThrows(t *testing.T) {
	// given:
	repository.Setup()
	repository.MTransactionRepository.On("TypesAsArray").Return(
		nilArray,
		&rTypes.Error{},
	)

	// when:
	res, e := getSubject().TypesAsArray()

	// then:
	assert.Nil(t, res)
	assert.NotNil(t, e)
}
