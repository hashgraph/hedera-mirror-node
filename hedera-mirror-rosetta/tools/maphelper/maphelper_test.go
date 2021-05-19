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
	expected := []string{
		"abc",
		"asd",
		"aaaa",
		"1",
	}

	// when:
	result := GetStringValuesFromIntStringMap(inputData)

	// then:
	assert.ElementsMatch(t, expected, result)
}
