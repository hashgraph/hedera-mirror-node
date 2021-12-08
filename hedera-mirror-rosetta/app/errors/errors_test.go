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

package errors

import (
	"testing"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/stretchr/testify/assert"
)

func TestAddErrorDetails(t *testing.T) {
	base := &types.Error{
		Code:      0,
		Message:   "foobar1",
		Retriable: false,
	}
	expected := &types.Error{
		Code:      0,
		Message:   "foobar1",
		Retriable: false,
		Details: map[string]interface{}{
			"name": "value",
		},
	}

	actual := AddErrorDetails(base, "name", "value")

	assert.Equal(t, expected, actual)
	assert.Nil(t, base.Details)

	// add another detail
	expected.Details["name2"] = "value2"
	actual = AddErrorDetails(actual, "name2", "value2")

	assert.Equal(t, expected, actual)
}
