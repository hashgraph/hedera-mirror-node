/*-
 * ‌
 * Hedera Mirror Node
 *
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
 * ‍
 */

package maphelper

import (
	"fmt"
	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestGetsCorrectStringValuesFromMap(t *testing.T) {
	// given:
	testData := map[int]string{
		1: "abc",
	}

	// when:
	result := GetStringValuesFromIntStringMap(testData)

	// then:
	assert.Equal(t, 1, len(result))
	assert.Equal(t, "abc", result[0])
}

func TestGetsCorrectErrorValuesFromMap(t *testing.T) {
	// given:
	error := newErrorDummy(32, true)

	testData := map[string]*types.Error{
		"error": error,
	}

	// when:
	result := GetErrorValuesFromStringErrorMap(testData)

	// then:
	assert.Equal(t, 1, len(result))
	assert.Equal(t, error, result[0])
}

func newErrorDummy(code int32, retryable bool) *types.Error {
	return errors.New(fmt.Sprintf("error_dummy_%d", code), code, retryable)
}
