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

package types

import (
	"testing"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/stretchr/testify/assert"
)

var (
	hbarAmount        = &HbarAmount{Value: 400}
	hbarRosettaAmount = &types.Amount{Value: "400", Currency: CurrencyHbar}
	tokenId           = domain.MustDecodeEntityId(1580)
	tokenIdStr        = tokenId.String()
	metadatasBytes    = [][]byte{[]byte("foo"), []byte("bar")}
	metadatasBase64   = []interface{}{"Zm9v", "YmFy"}
)

func TestHbarAmountGetValue(t *testing.T) {
	assert.Equal(t, int64(400), hbarAmount.GetValue())
}

func TestHbarAmountToRosettaAmount(t *testing.T) {
	assert.Equal(t, hbarRosettaAmount, hbarAmount.ToRosetta())
}

func TestTokenAmountGetValue(t *testing.T) {
	tokenAmount := TokenAmount{Value: 100}
	assert.Equal(t, int64(100), tokenAmount.GetValue())
}

func TestTokenAmountToRosettaAmount(t *testing.T) {
	tests := []struct {
		name        string
		tokenAmount TokenAmount
		expected    *types.Amount
	}{
		{
			name: domain.TokenTypeFungibleCommon,
			tokenAmount: TokenAmount{
				Decimals: 8,
				TokenId:  tokenId,
				Type:     domain.TokenTypeFungibleCommon,
				Value:    15,
			},
			expected: &types.Amount{
				Value: "15",
				Currency: &types.Currency{
					Symbol:   tokenId.String(),
					Decimals: 8,
					Metadata: map[string]interface{}{MetadataKeyType: domain.TokenTypeFungibleCommon},
				},
			},
		},
		{
			name: domain.TokenTypeNonFungibleUnique + "+SerialNumbers",
			tokenAmount: TokenAmount{
				Decimals:      0,
				SerialNumbers: []int64{1, 2, 3, 4, 5, 6},
				TokenId:       tokenId,
				Type:          domain.TokenTypeNonFungibleUnique,
				Value:         6,
			},
			expected: &types.Amount{
				Value: "6",
				Currency: &types.Currency{
					Symbol:   tokenId.String(),
					Decimals: 0,
					Metadata: map[string]interface{}{MetadataKeyType: domain.TokenTypeNonFungibleUnique},
				},
				Metadata: map[string]interface{}{
					MetadataKeySerialNumbers: []interface{}{"1", "2", "3", "4", "5", "6"},
				},
			},
		},
		{
			name: domain.TokenTypeNonFungibleUnique + "+Metadatas",
			tokenAmount: TokenAmount{
				Decimals:  0,
				Metadatas: metadatasBytes,
				TokenId:   tokenId,
				Type:      domain.TokenTypeNonFungibleUnique,
				Value:     2,
			},
			expected: &types.Amount{
				Value: "2",
				Currency: &types.Currency{
					Symbol:   tokenId.String(),
					Decimals: 0,
					Metadata: map[string]interface{}{MetadataKeyType: domain.TokenTypeNonFungibleUnique},
				},
				Metadata: map[string]interface{}{MetadataKeyMetadatas: metadatasBase64},
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, tt.tokenAmount.ToRosetta())
		})
	}
}

func TestNewAmountSuccess(t *testing.T) {
	tests := []struct {
		name     string
		input    *types.Amount
		expected Amount
	}{
		{
			name: "HbarAmount",
			input: &types.Amount{
				Value:    "5",
				Currency: CurrencyHbar,
			},
			expected: &HbarAmount{Value: 5},
		},
		{
			name: domain.TokenTypeFungibleCommon,
			input: &types.Amount{
				Value: "6",
				Currency: &types.Currency{
					Symbol:   tokenIdStr,
					Decimals: 5,
					Metadata: map[string]interface{}{MetadataKeyType: domain.TokenTypeFungibleCommon},
				},
			},
			expected: &TokenAmount{
				Decimals: 5,
				TokenId:  tokenId,
				Type:     domain.TokenTypeFungibleCommon,
				Value:    6,
			},
		},
		{
			name: domain.TokenTypeNonFungibleUnique + "+ZeroValue",
			input: &types.Amount{
				Value: "0",
				Currency: &types.Currency{
					Symbol:   tokenIdStr,
					Decimals: 0,
					Metadata: map[string]interface{}{MetadataKeyType: domain.TokenTypeNonFungibleUnique},
				},
			},
			expected: &TokenAmount{
				TokenId: tokenId,
				Type:    domain.TokenTypeNonFungibleUnique,
				Value:   0,
			},
		},
		{
			name: domain.TokenTypeNonFungibleUnique + "+SerialNumbers",
			input: &types.Amount{
				Value: "2",
				Currency: &types.Currency{
					Symbol:   tokenIdStr,
					Decimals: 0,
					Metadata: map[string]interface{}{MetadataKeyType: domain.TokenTypeNonFungibleUnique},
				},
				Metadata: map[string]interface{}{MetadataKeySerialNumbers: []interface{}{"1", "2"}},
			},
			expected: &TokenAmount{
				SerialNumbers: []int64{1, 2},
				TokenId:       tokenId,
				Type:          domain.TokenTypeNonFungibleUnique,
				Value:         2,
			},
		},
		{
			name: domain.TokenTypeNonFungibleUnique + "+Metadatas",
			input: &types.Amount{
				Value: "2",
				Currency: &types.Currency{
					Symbol:   tokenIdStr,
					Decimals: 0,
					Metadata: map[string]interface{}{MetadataKeyType: domain.TokenTypeNonFungibleUnique},
				},
				Metadata: map[string]interface{}{MetadataKeyMetadatas: metadatasBase64},
			},
			expected: &TokenAmount{
				Metadatas: metadatasBytes,
				TokenId:   tokenId,
				Type:      domain.TokenTypeNonFungibleUnique,
				Value:     2,
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			actual, err := NewAmount(tt.input)

			assert.Nil(t, err)
			assert.Equal(t, tt.expected, actual)
		})
	}
}

func TestNewAmountFailure(t *testing.T) {
	tests := []struct {
		name  string
		input *types.Amount
	}{
		{name: "InvalidAmount", input: &types.Amount{Value: "abc", Currency: CurrencyHbar}},
		{name: "InvalidCurrencySymbol", input: &types.Amount{Value: "1", Currency: &types.Currency{Symbol: "foobar"}}},
		{
			name: "InvalidTypeForTokenType",
			input: &types.Amount{
				Value: "2",
				Currency: &types.Currency{
					Symbol:   tokenIdStr,
					Decimals: 0,
					Metadata: map[string]interface{}{MetadataKeyType: 100},
				},
			},
		},
		{
			name: "NegativeCurrencyDecimals",
			input: &types.Amount{
				Value:    "1",
				Currency: &types.Currency{Decimals: -1, Symbol: CurrencyHbar.Symbol},
			},
		},
		{
			name: "InvalidCurrencyDecimalsForHbar",
			input: &types.Amount{
				Value:    "1",
				Currency: &types.Currency{Decimals: 2, Symbol: CurrencyHbar.Symbol},
			},
		},
		{
			name: "InvalidCurrencyMetadataForHbar",
			input: &types.Amount{
				Value: "1",
				Currency: &types.Currency{
					Symbol: CurrencyHbar.Symbol,
					Metadata: map[string]interface{}{
						"issuer": "group",
					},
				},
			},
		},
		{
			name: "InvalidTokenType",
			input: &types.Amount{
				Value: "2",
				Currency: &types.Currency{
					Symbol:   tokenIdStr,
					Metadata: map[string]interface{}{MetadataKeyType: "unknown"},
				},
			},
		},
		{
			name: "NonZeroDecimalsForNFT",
			input: &types.Amount{
				Value: "0",
				Currency: &types.Currency{
					Decimals: 2,
					Symbol:   tokenIdStr,
					Metadata: map[string]interface{}{MetadataKeyType: domain.TokenTypeNonFungibleUnique},
				},
			},
		},
		{
			name: "NilMetadataForNFT",
			input: &types.Amount{
				Value: "2",
				Currency: &types.Currency{
					Symbol:   tokenIdStr,
					Metadata: map[string]interface{}{MetadataKeyType: domain.TokenTypeNonFungibleUnique},
				},
			},
		},
		{
			name: "TooManyMetadataForNFT",
			input: &types.Amount{
				Value: "2",
				Currency: &types.Currency{
					Symbol:   tokenIdStr,
					Metadata: map[string]interface{}{MetadataKeyType: domain.TokenTypeNonFungibleUnique},
				},
				Metadata: map[string]interface{}{"m1": 1, "m2": 2},
			},
		},
		{
			name: "InvalidSerialNumbersTypeForNFT",
			input: &types.Amount{
				Value: "2",
				Currency: &types.Currency{
					Symbol:   tokenIdStr,
					Metadata: map[string]interface{}{MetadataKeyType: domain.TokenTypeNonFungibleUnique},
				},
				Metadata: map[string]interface{}{MetadataKeySerialNumbers: 1},
			},
		},
		{
			name: "InvalidSerialNumbersFormatForNFT",
			input: &types.Amount{
				Value: "2",
				Currency: &types.Currency{
					Symbol:   tokenIdStr,
					Metadata: map[string]interface{}{MetadataKeyType: domain.TokenTypeNonFungibleUnique},
				},
				Metadata: map[string]interface{}{MetadataKeySerialNumbers: []interface{}{"abc", "def"}},
			},
		},
		{
			name: "InvalidSerialNumberType",
			input: &types.Amount{
				Value: "2",
				Currency: &types.Currency{
					Symbol:   tokenIdStr,
					Metadata: map[string]interface{}{MetadataKeyType: domain.TokenTypeNonFungibleUnique},
				},
				Metadata: map[string]interface{}{MetadataKeySerialNumbers: []interface{}{1, 2}},
			},
		},
		{
			name: "SerialNumbersCountMismatchForNFT",
			input: &types.Amount{
				Value: "2",
				Currency: &types.Currency{
					Symbol:   tokenIdStr,
					Metadata: map[string]interface{}{MetadataKeyType: domain.TokenTypeNonFungibleUnique},
				},
				Metadata: map[string]interface{}{MetadataKeySerialNumbers: []interface{}{"1"}},
			},
		},
		{
			name: "InvalidNftMetadatasType",
			input: &types.Amount{
				Value: "2",
				Currency: &types.Currency{
					Symbol:   tokenIdStr,
					Metadata: map[string]interface{}{MetadataKeyType: domain.TokenTypeNonFungibleUnique},
				},
				Metadata: map[string]interface{}{MetadataKeyMetadatas: 1},
			},
		},
		{
			name: "InvalidNftMetadatasEncoding",
			input: &types.Amount{
				Value: "2",
				Currency: &types.Currency{
					Symbol:   tokenIdStr,
					Metadata: map[string]interface{}{MetadataKeyType: domain.TokenTypeNonFungibleUnique},
				},
				Metadata: map[string]interface{}{MetadataKeyMetadatas: []interface{}{"0xabcd", "0xa0b0"}},
			},
		},
		{
			name: "InvalidNftMetadataType",
			input: &types.Amount{
				Value: "2",
				Currency: &types.Currency{
					Symbol:   tokenIdStr,
					Metadata: map[string]interface{}{MetadataKeyType: domain.TokenTypeNonFungibleUnique},
				},
				Metadata: map[string]interface{}{MetadataKeyMetadatas: []interface{}{10, 12}},
			},
		},
		{
			name: "NftMetadatasCountMismatch",
			input: &types.Amount{
				Value: "2",
				Currency: &types.Currency{
					Symbol:   tokenIdStr,
					Metadata: map[string]interface{}{MetadataKeyType: domain.TokenTypeNonFungibleUnique},
				},
				Metadata: map[string]interface{}{MetadataKeyMetadatas: []interface{}{metadatasBase64[0]}},
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			actual, err := NewAmount(tt.input)
			assert.NotNil(t, err)
			assert.Nil(t, actual)
		})
	}
}

func TestNewTokenAmount(t *testing.T) {
	tests := []struct {
		name          string
		token         domain.Token
		amount        int64
		metadatas     [][]byte
		serialNumbers []int64
		expected      *TokenAmount
	}{
		{
			name:   domain.TokenTypeFungibleCommon,
			token:  newToken(5, domain.TokenTypeFungibleCommon),
			amount: 20,
			expected: &TokenAmount{
				Decimals: 5,
				TokenId:  tokenId,
				Type:     domain.TokenTypeFungibleCommon,
				Value:    20,
			},
		},
		{
			name:   domain.TokenTypeNonFungibleUnique,
			token:  newToken(0, domain.TokenTypeNonFungibleUnique),
			amount: 0,
			expected: &TokenAmount{
				TokenId: tokenId,
				Type:    domain.TokenTypeNonFungibleUnique,
			},
		},
		{
			name:          domain.TokenTypeNonFungibleUnique + "+SerialNumbers",
			token:         newToken(0, domain.TokenTypeNonFungibleUnique),
			amount:        5,
			serialNumbers: []int64{1, 2, 3, 4, 5},
			expected: &TokenAmount{
				SerialNumbers: []int64{1, 2, 3, 4, 5},
				TokenId:       tokenId,
				Type:          domain.TokenTypeNonFungibleUnique,
				Value:         5,
			},
		},
		{
			name:      domain.TokenTypeNonFungibleUnique + "+Metadatas",
			token:     newToken(0, domain.TokenTypeNonFungibleUnique),
			amount:    2,
			metadatas: metadatasBytes,
			expected: &TokenAmount{
				Metadatas: metadatasBytes,
				TokenId:   tokenId,
				Type:      domain.TokenTypeNonFungibleUnique,
				Value:     2,
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			actual := NewTokenAmount(tt.token, tt.amount).SetSerialNumbers(tt.serialNumbers).SetMetadatas(tt.metadatas)
			assert.Equal(t, tt.expected, actual)
		})
	}
}

func newToken(decimals int64, tokenType string) domain.Token {
	return domain.Token{
		TokenId:  tokenId,
		Decimals: decimals,
		Name:     "foobar",
		Symbol:   "xoobar",
		Type:     tokenType,
	}
}
