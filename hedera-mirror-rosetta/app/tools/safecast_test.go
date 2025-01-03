/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package tools

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestCastToInt64(t *testing.T) {
	tests := []struct {
		name     string
		input    uint64
		expected int64
	}{
		{
			name:     "Max",
			input:    9223372036854775807,
			expected: 9223372036854775807,
		},
		{
			name:     "One",
			input:    1,
			expected: 1,
		},
		{
			name:     "Zero",
			input:    0,
			expected: 0,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			actual, err := CastToInt64(tt.input)
			assert.NoError(t, err)
			assert.Equal(t, tt.expected, actual)
		})
	}
}

func TestCastToInt64OutOfRange(t *testing.T) {
	_, err := CastToInt64(9223372036854775808)
	assert.Error(t, err)
}

func TestCastToUint64(t *testing.T) {
	tests := []struct {
		name     string
		input    int64
		expected uint64
	}{
		{
			name:     "Max",
			input:    9223372036854775807,
			expected: 9223372036854775807,
		},
		{
			name:     "One",
			input:    1,
			expected: 1,
		},
		{
			name:     "Zero",
			input:    0,
			expected: 0,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			actual, err := CastToUint64(tt.input)
			assert.NoError(t, err)
			assert.Equal(t, tt.expected, actual)
		})
	}
}

func TestCastToUint64OutOfRange(t *testing.T) {
	_, err := CastToUint64(-1)
	assert.Error(t, err)
}
