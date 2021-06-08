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

package construction

import (
	"fmt"
	"strconv"
	"testing"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/mocks/repository"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

var (
	accountIdA = hedera.AccountID{Account: 9500}
	accountIdB = hedera.AccountID{Account: 9505}
)

type transferOperation struct {
	account  string
	amount   int64
	currency *rTypes.Currency
}

func TestCryptoTransferTransactionConstructorSuite(t *testing.T) {
	suite.Run(t, new(cryptoTransferTransactionConstructorSuite))
}

type cryptoTransferTransactionConstructorSuite struct {
	suite.Suite
}

func (suite *cryptoTransferTransactionConstructorSuite) TestNewTransactionConstructor() {
	h := newCryptoTransferTransactionConstructor(&repository.MockTokenRepository{})
	assert.NotNil(suite.T(), h)
}

func (suite *cryptoTransferTransactionConstructorSuite) TestGetOperationType() {
	h := newCryptoTransferTransactionConstructor(&repository.MockTokenRepository{})
	assert.Equal(suite.T(), config.OperationTypeCryptoTransfer, h.GetOperationType())
}

func (suite *cryptoTransferTransactionConstructorSuite) TestGetSdkTransactionType() {
	h := newCryptoTransferTransactionConstructor(&repository.MockTokenRepository{})
	assert.Equal(suite.T(), "TransferTransaction", h.GetSdkTransactionType())
}

func (suite *cryptoTransferTransactionConstructorSuite) TestConstruct() {
	var tests = []struct {
		name            string
		transfers       []transferOperation
		expectError     bool
		expectedSigners []hedera.AccountID
	}{
		{
			name: "Success",
			transfers: []transferOperation{
				{account: accountIdA.String(), amount: -15, currency: config.CurrencyHbar},
				{account: accountIdB.String(), amount: 15, currency: config.CurrencyHbar},
				{account: accountIdB.String(), amount: -25, currency: dbTokenA.ToRosettaCurrency()},
				{account: accountIdA.String(), amount: 25, currency: dbTokenA.ToRosettaCurrency()},
				{account: accountIdA.String(), amount: -30, currency: dbTokenB.ToRosettaCurrency()},
				{account: accountIdB.String(), amount: 30, currency: dbTokenB.ToRosettaCurrency()},
			},
			expectedSigners: []hedera.AccountID{accountIdA, accountIdB},
		},
		{
			name:        "EmptyOperations",
			expectError: true,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			operations := suite.makeOperations(tt.transfers)
			mockTokenRepo := &repository.MockTokenRepository{}
			h := newCryptoTransferTransactionConstructor(mockTokenRepo)
			configMockTokenRepo(mockTokenRepo, defaultMockTokenRepoConfigs...)

			// when
			tx, signers, err := h.Construct(nodeAccountId, operations)

			// then
			if tt.expectError {
				assert.NotNil(t, err)
				assert.Nil(t, signers)
				assert.Nil(t, tx)
			} else {
				assert.Nil(t, err)
				assert.ElementsMatch(t, tt.expectedSigners, signers)
				assertCryptoTransferTransaction(t, operations, nodeAccountId, tx)
				mockTokenRepo.AssertExpectations(t)
			}
		})
	}
}

func (suite *cryptoTransferTransactionConstructorSuite) TestParse() {
	defaultGetTransaction := func() ITransaction {
		return hedera.NewTransferTransaction().
			SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
			SetTransactionID(hedera.TransactionIDGenerate(accountIdA)).
			AddHbarTransfer(accountIdA, hedera.HbarFromTinybar(-15)).
			AddHbarTransfer(accountIdB, hedera.HbarFromTinybar(15)).
			AddTokenTransfer(tokenIdA, accountIdA, -25).
			AddTokenTransfer(tokenIdA, accountIdB, 25).
			AddTokenTransfer(tokenIdB, accountIdB, -35).
			AddTokenTransfer(tokenIdB, accountIdA, 35)
	}

	expectedTransfers := []string{
		fmt.Sprintf("%s_%d_%s", accountIdA, -15, config.CurrencySymbol),
		fmt.Sprintf("%s_%d_%s", accountIdB, 15, config.CurrencySymbol),
		fmt.Sprintf("%s_%d_%s", accountIdA, -25, tokenIdA),
		fmt.Sprintf("%s_%d_%s", accountIdB, 25, tokenIdA),
		fmt.Sprintf("%s_%d_%s", accountIdB, -35, tokenIdB),
		fmt.Sprintf("%s_%d_%s", accountIdA, 35, tokenIdB),
	}

	var tests = []struct {
		name           string
		tokenRepoErr   bool
		getTransaction func() ITransaction
		expectError    bool
	}{
		{
			name:           "Success",
			getTransaction: defaultGetTransaction,
		},
		{
			name:           "TokenNotFound",
			tokenRepoErr:   true,
			getTransaction: defaultGetTransaction,
			expectError:    true,
		},
		{
			name: "InvalidTransaction",
			getTransaction: func() ITransaction {
				return hedera.NewTokenCreateTransaction()
			},
			expectError: true,
		},
		{
			name: "TransactionIDNotSet",
			getTransaction: func() ITransaction {
				return hedera.NewTransferTransaction().SetNodeAccountIDs([]hedera.AccountID{nodeAccountId})
			},
			expectError: true,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			mockTokenRepo := &repository.MockTokenRepository{}
			h := newCryptoTransferTransactionConstructor(mockTokenRepo)
			tx := tt.getTransaction()

			if tt.tokenRepoErr {
				configMockTokenRepo(mockTokenRepo, mockTokenRepoNotFoundConfigs...)
			} else {
				configMockTokenRepo(mockTokenRepo, defaultMockTokenRepoConfigs...)
			}

			// when
			operations, signers, err := h.Parse(tx)

			// then
			if tt.expectError {
				assert.NotNil(t, err)
				assert.Nil(t, operations)
				assert.Nil(t, signers)
			} else {
				assert.Nil(t, err)
				assert.ElementsMatch(t, []hedera.AccountID{accountIdA, accountIdB}, signers)
				mockTokenRepo.AssertExpectations(t)

				actualTransfers := make([]string, 0, len(operations))
				for _, operation := range operations {
					actualTransfers = append(
						actualTransfers,
						fmt.Sprintf(
							"%s_%s_%s",
							operation.Account.Address,
							operation.Amount.Value,
							operation.Amount.Currency.Symbol,
						),
					)
				}
				assert.ElementsMatch(t, expectedTransfers, actualTransfers)
			}
		})
	}
}

func (suite *cryptoTransferTransactionConstructorSuite) TestPreprocess() {
	var tests = []struct {
		name            string
		transfers       []transferOperation
		operations      []*rTypes.Operation
		tokenRepoErr    bool
		expectError     bool
		expectedSigners []hedera.AccountID
	}{
		{
			name: "Success",
			transfers: []transferOperation{
				{account: accountIdA.String(), amount: -15, currency: config.CurrencyHbar},
				{account: accountIdB.String(), amount: 15, currency: config.CurrencyHbar},
				{account: accountIdB.String(), amount: -25, currency: dbTokenA.ToRosettaCurrency()},
				{account: accountIdA.String(), amount: 25, currency: dbTokenA.ToRosettaCurrency()},
				{account: accountIdA.String(), amount: -30, currency: dbTokenB.ToRosettaCurrency()},
				{account: accountIdB.String(), amount: 30, currency: dbTokenB.ToRosettaCurrency()},
			},
			expectedSigners: []hedera.AccountID{accountIdA, accountIdB},
		},
		{
			name: "InvalidAccountAddress",
			transfers: []transferOperation{
				{account: "x.y.z", amount: -15, currency: config.CurrencyHbar},
				{account: accountIdB.String(), amount: 15, currency: config.CurrencyHbar},
				{account: accountIdB.String(), amount: -25, currency: dbTokenA.ToRosettaCurrency()},
				{account: accountIdA.String(), amount: 25, currency: dbTokenA.ToRosettaCurrency()},
				{account: accountIdA.String(), amount: -30, currency: dbTokenB.ToRosettaCurrency()},
				{account: accountIdB.String(), amount: 30, currency: dbTokenB.ToRosettaCurrency()},
			},
			expectError: true,
		},
		{
			name: "InvalidTokenAddress",
			transfers: []transferOperation{
				{account: accountIdA.String(), amount: -15, currency: config.CurrencyHbar},
				{account: accountIdB.String(), amount: 15, currency: config.CurrencyHbar},
				{account: accountIdB.String(), amount: -25, currency: &rTypes.Currency{Symbol: "x.y.z", Decimals: 6}},
				{account: accountIdA.String(), amount: 25, currency: dbTokenA.ToRosettaCurrency()},
				{account: accountIdA.String(), amount: -30, currency: dbTokenB.ToRosettaCurrency()},
				{account: accountIdB.String(), amount: 30, currency: dbTokenB.ToRosettaCurrency()},
			},
			expectError: true,
		},
		{
			name: "ZeroAmount",
			transfers: []transferOperation{
				{account: accountIdA.String(), amount: 0, currency: config.CurrencyHbar},
				{account: accountIdB.String(), amount: 15, currency: config.CurrencyHbar},
				{account: accountIdB.String(), amount: -25, currency: dbTokenA.ToRosettaCurrency()},
				{account: accountIdA.String(), amount: 25, currency: dbTokenA.ToRosettaCurrency()},
				{account: accountIdA.String(), amount: -30, currency: dbTokenB.ToRosettaCurrency()},
				{account: accountIdB.String(), amount: 30, currency: dbTokenB.ToRosettaCurrency()},
			},
			expectError: true,
		},

		{
			name: "InvalidHbarSum",
			transfers: []transferOperation{
				{account: accountIdA.String(), amount: -15, currency: config.CurrencyHbar},
				{account: accountIdB.String(), amount: 10, currency: config.CurrencyHbar},
				{account: accountIdB.String(), amount: -25, currency: dbTokenA.ToRosettaCurrency()},
				{account: accountIdA.String(), amount: 25, currency: dbTokenA.ToRosettaCurrency()},
			},
			expectError: true,
		},
		{
			name: "InvalidTokenSum",
			transfers: []transferOperation{
				{account: accountIdA.String(), amount: -15, currency: config.CurrencyHbar},
				{account: accountIdB.String(), amount: 10, currency: config.CurrencyHbar},
				{account: accountIdB.String(), amount: -25, currency: dbTokenA.ToRosettaCurrency()},
				{account: accountIdA.String(), amount: 20, currency: dbTokenA.ToRosettaCurrency()},
			},
			expectError: true,
		},
		{
			name: "TokenDecimalsMismatch",
			transfers: []transferOperation{
				{account: accountIdA.String(), amount: -15, currency: config.CurrencyHbar},
				{account: accountIdB.String(), amount: 10, currency: config.CurrencyHbar},
				{
					account:  accountIdB.String(),
					amount:   -25,
					currency: &rTypes.Currency{Symbol: tokenIdA.String(), Decimals: 1980},
				},
				{account: accountIdA.String(), amount: 25, currency: dbTokenA.ToRosettaCurrency()},
			},
			expectError: true,
		},
		{
			name: "TokenNotFound",
			transfers: []transferOperation{
				{account: accountIdA.String(), amount: -15, currency: config.CurrencyHbar},
				{account: accountIdB.String(), amount: 15, currency: config.CurrencyHbar},
				{account: accountIdB.String(), amount: -25, currency: dbTokenA.ToRosettaCurrency()},
				{account: accountIdA.String(), amount: 25, currency: dbTokenA.ToRosettaCurrency()},
				{account: accountIdA.String(), amount: -30, currency: dbTokenB.ToRosettaCurrency()},
				{account: accountIdB.String(), amount: 30, currency: dbTokenB.ToRosettaCurrency()},
			},
			tokenRepoErr: true,
			expectError:  true,
		},
		{
			name: "InvalidOperationType",
			operations: []*rTypes.Operation{
				{
					OperationIdentifier: &rTypes.OperationIdentifier{Index: 0},
					Type:                config.OperationTypeTokenWipe,
					Account:             &rTypes.AccountIdentifier{Address: "0.0.158"},
					Amount:              &rTypes.Amount{Value: "100", Currency: config.CurrencyHbar},
				},
			},
			expectError: true,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			operations := tt.operations
			if len(tt.operations) == 0 {
				operations = suite.makeOperations(tt.transfers)
			}

			mockTokenRepo := &repository.MockTokenRepository{}
			h := newCryptoTransferTransactionConstructor(mockTokenRepo)

			if !tt.tokenRepoErr {
				configMockTokenRepo(mockTokenRepo, defaultMockTokenRepoConfigs...)
			} else {
				configMockTokenRepo(mockTokenRepo, mockTokenRepoNotFoundConfigs...)
			}

			// when
			signers, err := h.Preprocess(operations)

			// then
			if tt.expectError {
				assert.NotNil(t, err)
				assert.Nil(t, signers)
			} else {
				assert.Nil(t, err)
				assert.ElementsMatch(t, tt.expectedSigners, signers)
				mockTokenRepo.AssertExpectations(t)
			}
		})
	}
}

func (suite *cryptoTransferTransactionConstructorSuite) makeOperations(transfers []transferOperation) []*rTypes.Operation {
	operations := make([]*rTypes.Operation, 0, len(transfers))
	for _, transfer := range transfers {
		operations = append(operations, &rTypes.Operation{
			OperationIdentifier: &rTypes.OperationIdentifier{Index: int64(len(operations))},
			Type:                config.OperationTypeCryptoTransfer,
			Account:             &rTypes.AccountIdentifier{Address: transfer.account},
			Amount: &rTypes.Amount{
				Value:    strconv.FormatInt(transfer.amount, 10),
				Currency: transfer.currency,
			},
		})
	}

	return operations
}

func assertCryptoTransferTransaction(
	t *testing.T,
	operations []*rTypes.Operation,
	nodeAccountId hedera.AccountID,
	actual ITransaction,
) {
	assert.IsType(t, &hedera.TransferTransaction{}, actual)

	expectedTransfers := make([]string, 0, len(operations))
	for _, operation := range operations {
		expectedTransfers = append(
			expectedTransfers,
			fmt.Sprintf(
				"%s_%s_%s",
				operation.Account.Address,
				operation.Amount.Value,
				operation.Amount.Currency.Symbol,
			),
		)
	}

	tx, _ := actual.(*hedera.TransferTransaction)
	actualHbarTransfers := tx.GetHbarTransfers()
	actualTokenTransfers := tx.GetTokenTransfers()

	actualTransfers := make([]string, 0)
	for accountId, amount := range actualHbarTransfers {
		actualTransfers = append(
			actualTransfers,
			fmt.Sprintf("%s_%d_%s", accountId, amount.AsTinybar(), config.CurrencySymbol),
		)
	}

	for token, tokenTransfers := range actualTokenTransfers {
		for _, transfer := range tokenTransfers {
			actualTransfers = append(
				actualTransfers,
				fmt.Sprintf("%s_%d_%s", transfer.AccountID, transfer.Amount, token),
			)
		}
	}

	assert.ElementsMatch(t, expectedTransfers, actualTransfers)
	assert.ElementsMatch(t, []hedera.AccountID{nodeAccountId}, actual.GetNodeAccountIDs())
}
