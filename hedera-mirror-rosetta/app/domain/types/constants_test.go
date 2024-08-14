/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
 *
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
 */

package types

import (
	"strings"
	"testing"

	"github.com/hashgraph/hedera-protobufs-go/services"
	"github.com/stretchr/testify/assert"
)

const maxCode = int32(337)
const maxTransactionType = int32(60)

func TestGetTransactionResult(t *testing.T) {
	for code, expected := range services.ResponseCodeEnum_name {
		if code > maxCode {
			continue
		}

		actual := GetTransactionResult(code)
		assert.Equal(t, expected, actual, "Code %d, expect %s, actual %s", code, expected, actual)
	}
}

func TestGetTransactionResultGeneralError(t *testing.T) {
	for code := range services.ResponseCodeEnum_name {
		if code <= maxCode {
			continue
		}

		actual := GetTransactionResult(code)
		assert.Equal(t, generalErrorStatus, actual, "Code %d, expect general error, actual %s", code, actual)
	}
}

func TestTransactionTypes(t *testing.T) {
	sdkTransactionTypes := getSdkTransactionTypes()
	for protoId, expected := range sdkTransactionTypes {
		if protoId > maxTransactionType {
			expected = "UNKNOWN"
		}
		actual := GetTransactionType(protoId)
		assert.Equal(t, expected, actual, "Expected %s for proto id %d", expected, protoId)
	}
}

func TestUnknownTransactionType(t *testing.T) {
	assert.Equal(t, "UNKNOWN", GetTransactionType(0))
	assert.Equal(t, "UNKNOWN", GetTransactionType(maxTransactionType+1))
}

func getSdkTransactionTypes() map[int32]string {
	body := services.TransactionBody{}
	dataFields := body.ProtoReflect().Descriptor().Oneofs().ByName("data").Fields()
	transactionTypes := make(map[int32]string)
	for i := 0; i < dataFields.Len(); i++ {
		dataField := dataFields.Get(i)
		name := strings.ToUpper(string(dataField.Name()))
		transactionTypes[int32(dataField.Number())] = strings.ReplaceAll(name, "_", "")
	}

	return transactionTypes
}
