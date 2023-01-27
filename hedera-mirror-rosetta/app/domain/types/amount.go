/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
	"encoding/base64"
	"reflect"
	"strconv"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/tools"
	"github.com/hashgraph/hedera-sdk-go/v2"
)

const (
	MetadataKeyMetadatas     = "metadatas"
	MetadataKeySerialNumbers = "serial_numbers"
	MetadataKeyType          = "type"
)

type Amount interface {
	GetDecimals() int64
	GetSymbol() string
	GetValue() int64
	ToRosetta() *types.Amount
}

type AmountSlice []Amount

func (a AmountSlice) ToRosetta() []*types.Amount {
	rosettaAmounts := make([]*types.Amount, 0, len(a))
	for _, amount := range a {
		rosettaAmounts = append(rosettaAmounts, amount.ToRosetta())
	}
	return rosettaAmounts
}

type HbarAmount struct {
	Value int64
}

func (h *HbarAmount) GetDecimals() int64 {
	return int64(CurrencyHbar.Decimals)
}

func (h *HbarAmount) GetSymbol() string {
	return CurrencyHbar.Symbol
}

func (h *HbarAmount) GetValue() int64 {
	return h.Value
}

// ToRosetta returns Rosetta type Amount with hbar currency
func (h *HbarAmount) ToRosetta() *types.Amount {
	return &types.Amount{
		Value:    strconv.FormatInt(h.Value, 10),
		Currency: CurrencyHbar,
	}
}

// TokenAmount holds token amount unmarshalled from aggregated json string built by db query
type TokenAmount struct {
	Decimals      int64 `json:"decimals"`
	Metadatas     [][]byte
	SerialNumbers []int64         `json:"serial_numbers"`
	TokenId       domain.EntityId `json:"token_id"`
	Type          string          `json:"type"`
	Value         int64           `json:"value"`
}

func (t *TokenAmount) GetSdkTokenId() hedera.TokenID {
	return hedera.TokenID{
		Shard: uint64(t.TokenId.ShardNum),
		Realm: uint64(t.TokenId.RealmNum),
		Token: uint64(t.TokenId.EntityNum),
	}
}

func (t *TokenAmount) GetDecimals() int64 {
	return t.Decimals
}

func (t *TokenAmount) GetSymbol() string {
	return t.TokenId.String()
}

func (t *TokenAmount) GetValue() int64 {
	return t.Value
}

func (t *TokenAmount) SetMetadatas(metadatas [][]byte) *TokenAmount {
	t.Metadatas = metadatas
	return t
}

func (t *TokenAmount) SetSerialNumbers(serialNumbers []int64) *TokenAmount {
	t.SerialNumbers = serialNumbers
	return t
}

// ToRosetta returns Rosetta type Amount with the token's currency
func (t *TokenAmount) ToRosetta() *types.Amount {
	amount := types.Amount{
		Value: strconv.FormatInt(t.Value, 10),
		Currency: &types.Currency{
			Symbol:   t.TokenId.String(),
			Decimals: int32(t.Decimals),
			Metadata: map[string]interface{}{MetadataKeyType: t.Type},
		},
	}
	if t.Type == domain.TokenTypeNonFungibleUnique {
		metadata := make(map[string]interface{})
		if len(t.SerialNumbers) > 0 {
			serialNumbers := make([]interface{}, 0, len(t.SerialNumbers))
			for _, serialNumber := range t.SerialNumbers {
				serialNumbers = append(serialNumbers, strconv.FormatInt(serialNumber, 10))
			}
			metadata[MetadataKeySerialNumbers] = serialNumbers
		}

		if len(t.Metadatas) > 0 {
			nftMetadatas := make([]interface{}, 0, len(t.Metadatas))
			for _, nftMetadata := range t.Metadatas {
				nftMetadatas = append(nftMetadatas, base64.StdEncoding.EncodeToString(nftMetadata))
			}
			metadata[MetadataKeyMetadatas] = nftMetadatas
		}

		amount.Metadata = metadata
	}

	return &amount
}

func NewTokenAmount(token domain.Token, amount int64) *TokenAmount {
	return &TokenAmount{
		Decimals: token.Decimals,
		TokenId:  token.TokenId,
		Type:     token.Type,
		Value:    amount,
	}
}

func NewAmount(amount *types.Amount) (Amount, *types.Error) {
	value, err := tools.ToInt64(amount.Value)
	if err != nil {
		return nil, errors.ErrInvalidOperationsAmount
	}

	currency := amount.Currency
	if currency.Decimals < 0 {
		return nil, errors.ErrInvalidCurrency
	}

	if currency.Symbol == CurrencyHbar.Symbol {
		if !reflect.DeepEqual(currency, CurrencyHbar) {
			return nil, errors.ErrInvalidCurrency
		}

		return &HbarAmount{Value: value}, nil
	}

	tokenId, err := domain.EntityIdFromString(currency.Symbol)
	if err != nil || tokenId.IsZero() {
		return nil, errors.ErrInvalidToken
	}

	tokenType, ok := currency.Metadata[MetadataKeyType].(string)
	if !ok {
		return nil, errors.ErrInvalidCurrency
	}

	tokenAmount := &TokenAmount{
		Decimals: int64(currency.Decimals),
		TokenId:  tokenId,
		Type:     tokenType,
		Value:    value,
	}

	if tokenType == domain.TokenTypeNonFungibleUnique {
		if err := parseNftMetadata(amount.Metadata, tokenAmount); err != nil {
			return nil, err
		}
	} else if tokenType != domain.TokenTypeFungibleCommon {
		return nil, errors.ErrInvalidCurrency
	}

	return tokenAmount, nil
}

func parseNftMetadata(metadata map[string]interface{}, tokenAmount *TokenAmount) *types.Error {
	if tokenAmount.Decimals != 0 {
		return errors.ErrInvalidCurrency
	}

	if tokenAmount.Value == 0 {
		return nil
	}

	// one metadata entry, either "serial_numbers" or "metadatas"
	if len(metadata) != 1 {
		return errors.ErrInvalidOperationsAmount
	}

	if serialNumbers, ok := metadata[MetadataKeySerialNumbers].([]interface{}); ok {
		tokenAmount.SerialNumbers = make([]int64, 0, len(serialNumbers))
		for _, serialNumber := range serialNumbers {
			serialNumberStr, ok := serialNumber.(string)
			if !ok {
				return errors.ErrInvalidOperationsAmount
			}
			number, err := strconv.ParseInt(serialNumberStr, 10, 64)
			if err != nil {
				return errors.ErrInvalidOperationsAmount
			}
			tokenAmount.SerialNumbers = append(tokenAmount.SerialNumbers, number)
		}
	} else if metadatas, ok := metadata[MetadataKeyMetadatas].([]interface{}); ok {
		tokenAmount.Metadatas = make([][]byte, 0, len(metadatas))
		for _, metadata := range metadatas {
			metaStr, ok := metadata.(string)
			if !ok {
				return errors.ErrInvalidOperationsAmount
			}
			metaBytes, err := base64.StdEncoding.DecodeString(metaStr)
			if err != nil {
				return errors.ErrInvalidOperationsAmount
			}
			tokenAmount.Metadatas = append(tokenAmount.Metadatas, metaBytes)
		}
	} else {
		return errors.ErrInvalidOperationsAmount
	}

	expectedValue := int64(len(tokenAmount.SerialNumbers) + len(tokenAmount.Metadatas))
	if tokenAmount.Value != expectedValue && tokenAmount.Value != -expectedValue {
		return errors.ErrInvalidOperationsAmount
	}

	return nil
}
