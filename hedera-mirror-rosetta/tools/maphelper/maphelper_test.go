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
    inputData := map[int]string{
        1: "abc",
        2: "asd",
        3: "aaaa",
        4: "1",
    }
    // when:
    result := GetStringValuesFromIntStringMap(inputData)
    // then:
    assert.Equal(t, len(inputData), len(result))
    for _, v := range inputData {
        found := false
        for _, e := range result {
            if e == v {
                found = true
                break
            }
        }
        assert.True(t, found)
    }
}

func TestGetsCorrectErrorValuesFromMap(t *testing.T) {
    // given:
    inputData := map[string]*types.Error{
        "error1":  newErrorDummy(32, true),
        "error2":  newErrorDummy(64, false),
        "error3":  newErrorDummy(128, true),
        "error64": newErrorDummy(12341, false),
    }
    // when:
    result := GetErrorValuesFromStringErrorMap(inputData)
    // then:
    assert.Equal(t, len(inputData), len(result))
    for _, v := range inputData {
        found := false
        for _, e := range result {
            if e == v {
                found = true
                break
            }
        }
        assert.True(t, found)
    }
}

func newErrorDummy(code int32, retryable bool) *types.Error {
    return errors.New(fmt.Sprintf("error_dummy_%d", code), code, retryable)
}
