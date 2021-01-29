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

package hex

import (
    "github.com/stretchr/testify/assert"
    "testing"
)

func TestAddsPrefixCorrectly(t *testing.T) {
    // given:
    var testData = []struct {
        string string
    }{
        {"addprefix"},
        {""},
        {"123"},
        {"0x"},
        {"0x "},
        {"0x123aasd"},
    }

    var expectedData = []struct {
        result string
    }{
        {"0xaddprefix"},
        {"0x"},
        {"0x123"},
        {"0x"},
        {"0x "},
        {"0x123aasd"},
    }

    for i, tt := range testData {
        // when:
        result := SafeAddHexPrefix(tt.string)

        // then:
        assert.Equal(t, expectedData[i].result, result)
    }
}

func TestRemovesPrefixCorrectly(t *testing.T) {
    // given:
    var testData = []struct {
        string string
    }{
        {"0xaddprefix"},
        {"0x"},
        {"0x123"},
        {"0x "},
        {"0x123aasd"},
        {"0xaasd"},
        {"234123"},
    }

    var expectedData = []struct {
        result string
    }{
        {"addprefix"},
        {""},
        {"123"},
        {" "},
        {"123aasd"},
        {"aasd"},
        {"234123"},
    }

    for i, tt := range testData {
        // when:
        result := SafeRemoveHexPrefix(tt.string)
        // then:
        assert.Equal(t, expectedData[i].result, result)
    }
}
