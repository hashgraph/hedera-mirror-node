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
	"testing"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/go-playground/validator/v10"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/mocks/repository"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/assert"
)

const accountAddress = "0.0.123"

func TestCompareCurrency(t *testing.T) {
	var tests = []struct {
		name      string
		currencyA *rTypes.Currency
		currencyB *rTypes.Currency
		expected  bool
	}{
		{
			name:      "SamePointer",
			currencyA: config.CurrencyHbar,
			currencyB: config.CurrencyHbar,
			expected:  true,
		},
		{
			name:      "SameValue",
			currencyA: &rTypes.Currency{Symbol: "foobar", Decimals: 12},
			currencyB: &rTypes.Currency{Symbol: "foobar", Decimals: 12},
			expected:  true,
		},
		{
			name: "SameValueWithMetadata",
			currencyA: &rTypes.Currency{
				Symbol:   "foobar",
				Decimals: 12,
				Metadata: map[string]interface{}{
					"meta1": 1,
				},
			},
			currencyB: &rTypes.Currency{
				Symbol:   "foobar",
				Decimals: 12,
				Metadata: map[string]interface{}{
					"meta1": 1,
				},
			},
			expected: true,
		},
		{
			name:      "OneIsNil",
			currencyA: &rTypes.Currency{},
			currencyB: nil,
		},
		{
			name:      "DifferentSymbol",
			currencyA: &rTypes.Currency{Symbol: "A"},
			currencyB: &rTypes.Currency{Symbol: "B"},
		},
		{
			name:      "DifferentDecimals",
			currencyA: &rTypes.Currency{Decimals: 1},
			currencyB: &rTypes.Currency{Decimals: 2},
		},
		{
			name: "DifferentMetadata",
			currencyA: &rTypes.Currency{
				Metadata: map[string]interface{}{
					"meta1": 1,
				}},
			currencyB: &rTypes.Currency{
				Metadata: map[string]interface{}{
					"meta2": 1,
				}},
		},
		{
			name: "DifferentMetadataValue",
			currencyA: &rTypes.Currency{
				Metadata: map[string]interface{}{
					"meta1": 1,
				}},
			currencyB: &rTypes.Currency{
				Metadata: map[string]interface{}{
					"meta1": 2,
				}},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, compareCurrency(tt.currencyA, tt.currencyB))
		})
	}
}

func TestIsEmptyPublicKey(t *testing.T) {
	var tests = []struct {
		name     string
		key      hedera.Key
		expected bool
	}{
		{
			name:     "Success",
			key:      hedera.PublicKey{},
			expected: true,
		},
		{
			name:     "NotEmptyPublicKey",
			key:      adminKey,
			expected: false,
		},
		{
			name:     "NotPublicKey",
			key:      hedera.PrivateKey{},
			expected: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, isEmptyPublicKey(tt.key))
		})
	}
}

func TestIsZeroAccountId(t *testing.T) {
	var tests = []struct {
		name      string
		accountId hedera.AccountID
		expected  bool
	}{
		{
			name:      "ZeroAccountId",
			accountId: hedera.AccountID{},
			expected:  true,
		},
		{
			name:      "NonZeroAccountId",
			accountId: hedera.AccountID{Account: 101},
			expected:  false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, isZeroAccountId(tt.accountId))
		})
	}
}

func TestIsZeroTokenId(t *testing.T) {
	var tests = []struct {
		name     string
		tokenId  hedera.TokenID
		expected bool
	}{
		{
			name:     "ZeroAccountId",
			tokenId:  hedera.TokenID{},
			expected: true,
		},
		{
			name:     "NonZeroAccountId",
			tokenId:  hedera.TokenID{Token: 105},
			expected: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, isZeroTokenId(tt.tokenId))
		})
	}
}

func TestParseOperationMetadata(t *testing.T) {
	type data struct {
		Name  string `json:"name" validate:"required"`
		Value int    `json:"value" validate:"required"`
	}

	name := "foobar"
	value := 10

	expected := &data{
		Name:  name,
		Value: value,
	}

	var tests = []struct {
		name        string
		metadatas   []map[string]interface{}
		expectError bool
	}{
		{
			name: "Success",
			metadatas: []map[string]interface{}{{
				"name":  name,
				"value": value,
			}},
		},
		{
			name: "SuccessMultiple",
			metadatas: []map[string]interface{}{
				{"name": name},
				{"value": value},
			},
		},
		{
			name: "SuccessMultipleHonorLast",
			metadatas: []map[string]interface{}{
				{
					"name":  "bad",
					"value": 50,
				},
				{
					"name":  name,
					"value": value,
				},
			},
		},
		{
			name: "MissingField",
			metadatas: []map[string]interface{}{
				{"value": value},
			},
			expectError: true,
		},
		{
			name:        "EmptyMetadata",
			expectError: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			output := &data{}
			err := parseOperationMetadata(validator.New(), output, tt.metadatas...)

			if tt.expectError {
				assert.NotNil(t, err)
			} else {
				assert.Nil(t, err)
				assert.Equal(t, expected, output)
			}

		})
	}
}

func TestValidateOperationsWithType(t *testing.T) {
	var tests = []struct {
		name            string
		operations      []*rTypes.Operation
		size            int
		operationType   string
		expectNilAmount bool
		expectError     bool
	}{
		{
			name:          "SuccessSingleOperation",
			operations:    []*rTypes.Operation{getOperation(0, config.OperationTypeCryptoTransfer)},
			size:          1,
			operationType: config.OperationTypeCryptoTransfer,
		},
		{
			name: "SuccessMultipleOperations",
			operations: []*rTypes.Operation{
				getOperation(0, config.OperationTypeCryptoTransfer),
				getOperation(1, config.OperationTypeCryptoTransfer),
			},
			size:          0,
			operationType: config.OperationTypeCryptoTransfer,
		},
		{
			name: "SuccessExpectNilAmount",
			operations: []*rTypes.Operation{
				{
					OperationIdentifier: &rTypes.OperationIdentifier{Index: 0},
					Account:             &rTypes.AccountIdentifier{Address: accountAddress},
					Type:                config.OperationTypeCryptoTransfer,
				},
			},
			size:            0,
			operationType:   config.OperationTypeCryptoTransfer,
			expectNilAmount: true,
		},
		{
			name: "NonNilAmount",
			operations: []*rTypes.Operation{
				{
					OperationIdentifier: &rTypes.OperationIdentifier{Index: 0},
					Account:             &rTypes.AccountIdentifier{Address: accountAddress},
					Type:                config.OperationTypeCryptoTransfer,
					Amount:              &rTypes.Amount{},
				},
			},
			size:            0,
			operationType:   config.OperationTypeCryptoTransfer,
			expectNilAmount: true,
			expectError:     true,
		},
		{
			name:          "EmptyOperations",
			operationType: config.OperationTypeCryptoTransfer,
			expectError:   true,
		},
		{
			name: "OperationsSizeMismatch",
			operations: []*rTypes.Operation{
				getOperation(0, config.OperationTypeCryptoTransfer),
				getOperation(1, config.OperationTypeCryptoTransfer),
			},
			size:          1,
			operationType: config.OperationTypeCryptoTransfer,
			expectError:   true,
		},
		{
			name:          "OperationTypeMismatch",
			operations:    []*rTypes.Operation{getOperation(0, config.OperationTypeCryptoTransfer)},
			size:          1,
			operationType: config.OperationTypeTokenCreate,
			expectError:   true,
		},
		{
			name: "MultipleOperationTypes",
			operations: []*rTypes.Operation{
				getOperation(0, config.OperationTypeCryptoTransfer),
				getOperation(0, config.OperationTypeTokenCreate),
			},
			size:          0,
			operationType: config.OperationTypeCryptoTransfer,
			expectError:   true,
		},
		{
			name: "OperationMissingOperationIdentifier",
			operations: []*rTypes.Operation{
				{
					Account: &rTypes.AccountIdentifier{Address: accountAddress},
					Amount: &rTypes.Amount{
						Value: "0",
						Currency: &rTypes.Currency{
							Symbol:   "foobar",
							Decimals: 10,
						},
					},
					Type: config.OperationTypeCryptoTransfer,
				},
			},
			size:          1,
			operationType: config.OperationTypeCryptoTransfer,
			expectError:   true,
		},
		{
			name: "OperationMissingAccount",
			operations: []*rTypes.Operation{
				{
					OperationIdentifier: &rTypes.OperationIdentifier{Index: 0},
					Amount: &rTypes.Amount{
						Value: "0",
						Currency: &rTypes.Currency{
							Symbol:   "foobar",
							Decimals: 10,
						},
					},
					Type: config.OperationTypeCryptoTransfer,
				},
			},
			size:          1,
			operationType: config.OperationTypeCryptoTransfer,
			expectError:   true,
		},
		{
			name: "OperationMissingAmount",
			operations: []*rTypes.Operation{
				{
					OperationIdentifier: &rTypes.OperationIdentifier{Index: 0},
					Account:             &rTypes.AccountIdentifier{Address: accountAddress},
					Type:                config.OperationTypeCryptoTransfer,
				},
			},
			size:          1,
			operationType: config.OperationTypeCryptoTransfer,
			expectError:   true,
		},
		{
			name: "OperationMissingAmountCurrency",
			operations: []*rTypes.Operation{
				{
					OperationIdentifier: &rTypes.OperationIdentifier{Index: 0},
					Account:             &rTypes.AccountIdentifier{Address: accountAddress},
					Amount:              &rTypes.Amount{Value: "0"},
					Type:                config.OperationTypeCryptoTransfer,
				},
			},
			size:          1,
			operationType: config.OperationTypeCryptoTransfer,
			expectError:   true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := validateOperations(tt.operations, tt.size, tt.operationType, tt.expectNilAmount)

			if tt.expectError {
				assert.NotNil(t, err)
			} else {
				assert.Nil(t, err)
			}
		})
	}
}

func TestValidateToken(t *testing.T) {
	var tests = []struct {
		name         string
		currency     *rTypes.Currency
		tokenRepoErr bool
		expectError  bool
	}{
		{
			name:     "Success",
			currency: dbTokenA.ToRosettaCurrency(),
		},
		{
			name:         "TokenNotFound",
			currency:     dbTokenA.ToRosettaCurrency(),
			tokenRepoErr: true,
			expectError:  true,
		},
		{
			name:        "DecimalsMismatch",
			currency:    &rTypes.Currency{Symbol: "0.0.212", Decimals: 19867},
			expectError: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			mockTokenRepo := &repository.MockTokenRepository{}

			if tt.tokenRepoErr {
				configMockTokenRepo(mockTokenRepo, mockTokenRepoNotFoundConfigs[0])
			} else {
				configMockTokenRepo(mockTokenRepo, defaultMockTokenRepoConfigs[0])
			}

			token, err := validateToken(mockTokenRepo, tt.currency)

			if tt.expectError {
				assert.NotNil(t, err)
			} else {
				assert.Nil(t, err)
				assert.Equal(t, dbTokenA.ToHederaTokenId(), token)
				mockTokenRepo.AssertExpectations(t)
			}
		})
	}
}

func getOperation(index int64, operationType string) *rTypes.Operation {
	return &rTypes.Operation{
		OperationIdentifier: &rTypes.OperationIdentifier{Index: index},
		Account:             &rTypes.AccountIdentifier{Address: "0.0.100"},
		Amount: &rTypes.Amount{
			Value:    "20",
			Currency: &rTypes.Currency{Symbol: "foobar", Decimals: 9},
		},
		Type: operationType,
	}
}
