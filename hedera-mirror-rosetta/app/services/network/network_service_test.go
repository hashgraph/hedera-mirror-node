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

package network

import (
	"testing"

	"github.com/coinbase/rosetta-sdk-go/server"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/repositories"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/services/base"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/mocks/repository"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

func dummyGenesisBlock() *types.Block {
	return &types.Block{
		Index:               1,
		Hash:                "0x123jsjs",
		ConsensusStartNanos: 1000000,
		ConsensusEndNanos:   20000000,
		ParentIndex:         0,
		ParentHash:          "",
	}
}

func dummyLatestBlock() *types.Block {
	return &types.Block{
		Index:               2,
		Hash:                "0x1323jsjs",
		ConsensusStartNanos: 40000000,
		ConsensusEndNanos:   70000000,
		ParentIndex:         1,
		ParentHash:          "0x123jsjs",
	}
}

func networkAPIService(abr repositories.AddressBookEntryRepository, base base.BaseService) server.NetworkAPIServicer {
	return NewNetworkAPIService(
		base,
		abr,
		&rTypes.NetworkIdentifier{
			Blockchain: "SomeBlockchain",
			Network:    "SomeNetwork",
			SubNetworkIdentifier: &rTypes.SubNetworkIdentifier{
				Network:  "SomeSubNetwork",
				Metadata: nil,
			},
		},
		&rTypes.Version{
			RosettaVersion:    "1",
			NodeVersion:       "1",
			MiddlewareVersion: nil,
			Metadata:          nil,
		},
	)
}

func TestNetworkServiceSuite(t *testing.T) {
	suite.Run(t, new(networkServiceSuite))
}

type networkServiceSuite struct {
	suite.Suite
	mockAddressBookEntryRepo *repository.MockAddressBookEntryRepository
	mockBlockRepo            *repository.MockBlockRepository
	mockTransactionRepo      *repository.MockTransactionRepository
	networkService           server.NetworkAPIServicer
}

func (suite *networkServiceSuite) BeforeTest(suiteName string, testName string) {
	suite.mockAddressBookEntryRepo = &repository.MockAddressBookEntryRepository{}
	suite.mockBlockRepo = &repository.MockBlockRepository{}
	suite.mockTransactionRepo = &repository.MockTransactionRepository{}

	baseService := base.NewBaseService(suite.mockBlockRepo, suite.mockTransactionRepo)
	suite.networkService = networkAPIService(suite.mockAddressBookEntryRepo, baseService)
}

func (suite *networkServiceSuite) TestNetworkList() {
	// given:
	expectedResult := &rTypes.NetworkListResponse{
		NetworkIdentifiers: []*rTypes.NetworkIdentifier{
			{
				Blockchain: "SomeBlockchain",
				Network:    "SomeNetwork",
				SubNetworkIdentifier: &rTypes.SubNetworkIdentifier{
					Network:  "SomeSubNetwork",
					Metadata: nil,
				},
			},
		},
	}

	// when:
	res, e := suite.networkService.NetworkList(nil, nil)

	// then:
	assert.Equal(suite.T(), expectedResult, res)
	assert.Nil(suite.T(), e)
}

func (suite *networkServiceSuite) TestNetworkOptions() {
	// given:
	expectedErrors := []*rTypes.Error{
		errors.ErrAccountNotFound,
		errors.ErrBlockNotFound,
		errors.ErrInvalidAccount,
		errors.ErrInvalidAmount,
		errors.ErrInvalidOperationsAmount,
		errors.ErrInvalidOperationsTotalAmount,
		errors.ErrInvalidPublicKey,
		errors.ErrInvalidSignatureVerification,
		errors.ErrInvalidTransactionIdentifier,
		errors.ErrMultipleOperationTypesPresent,
		errors.ErrMultipleSignaturesPresent,
		errors.ErrNodeIsStarting,
		errors.ErrNotImplemented,
		errors.ErrOperationResultsNotFound,
		errors.ErrOperationTypesNotFound,
		errors.ErrStartMustNotBeAfterEnd,
		errors.ErrTransactionBuildFailed,
		errors.ErrTransactionDecodeFailed,
		errors.ErrTransactionRecordFetchFailed,
		errors.ErrTransactionMarshallingFailed,
		errors.ErrTransactionUnmarshallingFailed,
		errors.ErrTransactionSubmissionFailed,
		errors.ErrTransactionNotFound,
		errors.ErrEmptyOperations,
		errors.ErrTransactionInvalidType,
		errors.ErrTransactionNotSigned,
		errors.ErrTransactionHashFailed,
		errors.ErrTransactionFreezeFailed,
		errors.ErrInvalidArgument,
		errors.ErrDatabaseError,
		errors.ErrInternalServerError,
	}

	expectedResult := &rTypes.NetworkOptionsResponse{
		Version: &rTypes.Version{
			RosettaVersion:    "1",
			NodeVersion:       "1",
			MiddlewareVersion: nil,
			Metadata:          nil,
		},
		Allow: &rTypes.Allow{
			OperationStatuses: []*rTypes.OperationStatus{
				{
					Status:     "Pending",
					Successful: false,
				},
				{
					Status:     "Success",
					Successful: true,
				},
			},
			OperationTypes:          []string{"Transfer"},
			Errors:                  expectedErrors,
			HistoricalBalanceLookup: true,
		},
	}

	suite.mockTransactionRepo.
		On("Results").
		Return(map[int]string{1: "Pending", 22: "Success"}, repository.NilError)
	suite.mockTransactionRepo.On("TypesAsArray").Return([]string{"Transfer"}, repository.NilError)

	// when:
	res, e := suite.networkService.NetworkOptions(nil, nil)

	// then:
	assert.Equal(suite.T(), expectedResult.Version, res.Version)
	assert.Equal(suite.T(), expectedResult.Allow.HistoricalBalanceLookup, res.Allow.HistoricalBalanceLookup)
	assert.ElementsMatch(suite.T(), expectedResult.Allow.OperationStatuses, res.Allow.OperationStatuses)
	assert.ElementsMatch(suite.T(), expectedResult.Allow.OperationTypes, res.Allow.OperationTypes)
	assert.ElementsMatch(suite.T(), expectedResult.Allow.Errors, res.Allow.Errors)
	assert.Nil(suite.T(), e)
}

func (suite *networkServiceSuite) TestNetworkOptionsThrowsWhenStatusesFails() {
	var nilStatuses map[int]string = nil
	suite.mockTransactionRepo.On("TypesAsArray").Return([]string{"Transfer"}, repository.NilError)
	suite.mockTransactionRepo.On("Results").Return(nilStatuses, &rTypes.Error{})

	// when:
	res, e := suite.networkService.NetworkOptions(nil, nil)

	assert.Nil(suite.T(), res)
	assert.NotNil(suite.T(), e)
}

func (suite *networkServiceSuite) TestNetworkOptionsThrowsWhenTypesAsArrayFails() {
	var NilTypesAsArray []string = nil
	suite.mockTransactionRepo.On("TypesAsArray").Return(NilTypesAsArray, &rTypes.Error{})

	// when:
	res, e := suite.networkService.NetworkOptions(nil, nil)

	assert.Nil(suite.T(), res)
	assert.NotNil(suite.T(), e)
	suite.mockTransactionRepo.AssertNotCalled(suite.T(), "Results")
}

func (suite *networkServiceSuite) TestNetworkStatus() {
	// given:
	exampleEntries := &types.AddressBookEntries{Entries: []*types.AddressBookEntry{}}

	expectedResult := &rTypes.NetworkStatusResponse{
		CurrentBlockIdentifier: &rTypes.BlockIdentifier{
			Index: 2,
			Hash:  "0x1323jsjs",
		},
		CurrentBlockTimestamp: 40,
		GenesisBlockIdentifier: &rTypes.BlockIdentifier{
			Index: 1,
			Hash:  "0x123jsjs",
		},
		Peers: []*rTypes.Peer{},
	}

	suite.mockBlockRepo.On("RetrieveGenesis").Return(dummyGenesisBlock(), repository.NilError)
	suite.mockBlockRepo.On("RetrieveLatest").Return(dummyLatestBlock(), repository.NilError)
	suite.mockAddressBookEntryRepo.On("Entries").Return(exampleEntries, repository.NilError)

	// when:
	res, e := suite.networkService.NetworkStatus(nil, nil)

	// then:
	assert.Equal(suite.T(), expectedResult, res)
	assert.Nil(suite.T(), e)
}

func (suite *networkServiceSuite) TestNetworkStatusThrowsWhenRetrieveGenesisFails() {
	// given:
	suite.mockBlockRepo.On("RetrieveGenesis").Return(repository.NilBlock, &rTypes.Error{})

	// when:
	res, e := suite.networkService.NetworkStatus(nil, nil)

	// then
	assert.Nil(suite.T(), res)
	assert.NotNil(suite.T(), e)
}

func (suite *networkServiceSuite) TestNetworkStatusThrowsWhenRetrieveLatestFails() {
	// given:
	suite.mockBlockRepo.On("RetrieveGenesis").Return(dummyGenesisBlock(), repository.NilError)
	suite.mockBlockRepo.On("RetrieveLatest").Return(repository.NilBlock, &rTypes.Error{})

	// when:
	res, e := suite.networkService.NetworkStatus(nil, nil)

	// then:
	assert.Nil(suite.T(), res)
	assert.NotNil(suite.T(), e)
}

func (suite *networkServiceSuite) TestNetworkStatusThrowsWhenEntriesFail() {
	// given:
	suite.mockBlockRepo.On("RetrieveGenesis").Return(dummyGenesisBlock(), repository.NilError)
	suite.mockBlockRepo.On("RetrieveLatest").Return(dummyLatestBlock(), repository.NilError)
	suite.mockAddressBookEntryRepo.On("Entries").Return(repository.NilEntries, &rTypes.Error{})

	// when:
	res, e := suite.networkService.NetworkStatus(nil, nil)

	// then:
	assert.Nil(suite.T(), res)
	assert.NotNil(suite.T(), e)
}
