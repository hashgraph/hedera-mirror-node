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
	"github.com/coinbase/rosetta-sdk-go/server"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/services/base"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/mocks/repository"
	"github.com/stretchr/testify/assert"
	"testing"
)

func getSubject() server.NetworkAPIServicer {
	baseService := base.NewBaseService(repository.MBlockRepository, repository.MTransactionRepository)
	return networkAPIService(baseService)
}

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

func networkAPIService(base base.BaseService) server.NetworkAPIServicer {
	return NewNetworkAPIService(
		base,
		repository.MAddressBookEntryRepository,
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

func TestNewNetworkAPIService(t *testing.T) {
	repository.Setup()
	assert.IsType(t, &NetworkAPIService{}, getSubject())
}

func TestNetworkList(t *testing.T) {
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

	repository.Setup()

	// when:
	res, e := getSubject().NetworkList(nil, nil)

	// then:
	assert.Equal(t, expectedResult, res)
	assert.Nil(t, e)
}

func TestNetworkOptions(t *testing.T) {
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

	repository.Setup()
	repository.MTransactionRepository.
		On("Results").
		Return(map[int]string{1: "Pending", 22: "Success"}, repository.NilError)
	repository.MTransactionRepository.On("TypesAsArray").Return([]string{"Transfer"}, repository.NilError)

	// when:
	res, e := getSubject().NetworkOptions(nil, nil)

	// then:
	assert.Equal(t, expectedResult.Version, res.Version)
	assert.Equal(t, expectedResult.Allow.HistoricalBalanceLookup, res.Allow.HistoricalBalanceLookup)
	assert.ElementsMatch(t, expectedResult.Allow.OperationStatuses, res.Allow.OperationStatuses)
	assert.ElementsMatch(t, expectedResult.Allow.OperationTypes, res.Allow.OperationTypes)
	assert.ElementsMatch(t, expectedResult.Allow.Errors, res.Allow.Errors)
	assert.Nil(t, e)
}

func TestNetworkOptionsThrowsWhenStatusesFails(t *testing.T) {
	var nilStatuses map[int]string = nil
	repository.Setup()
	repository.MTransactionRepository.On("TypesAsArray").Return([]string{"Transfer"}, repository.NilError)
	repository.MTransactionRepository.On("Results").Return(nilStatuses, &rTypes.Error{})

	// when:
	res, e := getSubject().NetworkOptions(nil, nil)

	assert.Nil(t, res)
	assert.NotNil(t, e)
}

func TestNetworkOptionsThrowsWhenTypesAsArrayFails(t *testing.T) {
	var NilTypesAsArray []string = nil
	repository.Setup()
	repository.MTransactionRepository.On("TypesAsArray").Return(NilTypesAsArray, &rTypes.Error{})

	// when:
	res, e := getSubject().NetworkOptions(nil, nil)

	assert.Nil(t, res)
	assert.NotNil(t, e)
	repository.MTransactionRepository.AssertNotCalled(t, "Results")
}

func TestNetworkStatus(t *testing.T) {
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

	repository.Setup()
	repository.MBlockRepository.On("RetrieveGenesis").Return(dummyGenesisBlock(), repository.NilError)
	repository.MBlockRepository.On("RetrieveLatest").Return(dummyLatestBlock(), repository.NilError)
	repository.MAddressBookEntryRepository.On("Entries").Return(exampleEntries, repository.NilError)

	// when:
	res, e := getSubject().NetworkStatus(nil, nil)

	// then:
	assert.Equal(t, expectedResult, res)
	assert.Nil(t, e)
}

func TestNetworkStatusThrowsWhenRetrieveGenesisFails(t *testing.T) {
	// given:
	repository.Setup()
	repository.MBlockRepository.On("RetrieveGenesis").Return(repository.NilBlock, &rTypes.Error{})

	// when:
	res, e := getSubject().NetworkStatus(nil, nil)

	// then
	assert.Nil(t, res)
	assert.NotNil(t, e)
}

func TestNetworkStatusThrowsWhenRetrieveLatestFails(t *testing.T) {
	// given:
	repository.Setup()
	repository.MBlockRepository.On("RetrieveGenesis").Return(dummyGenesisBlock(), repository.NilError)
	repository.MBlockRepository.On("RetrieveLatest").Return(repository.NilBlock, &rTypes.Error{})

	// when:
	res, e := getSubject().NetworkStatus(nil, nil)

	// then:
	assert.Nil(t, res)
	assert.NotNil(t, e)
}

func TestNetworkStatusThrowsWhenEntriesFail(t *testing.T) {
	// given:
	repository.Setup()
	repository.MBlockRepository.On("RetrieveGenesis").Return(dummyGenesisBlock(), repository.NilError)
	repository.MBlockRepository.On("RetrieveLatest").Return(dummyLatestBlock(), repository.NilError)
	repository.MAddressBookEntryRepository.On("Entries").Return(repository.NilEntries, &rTypes.Error{})

	// when:
	res, e := getSubject().NetworkStatus(nil, nil)

	// then:
	assert.Nil(t, res)
	assert.NotNil(t, e)
}
