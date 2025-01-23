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

import "errors"

func CastToInt64(value uint64) (int64, error) {
	if value > 9223372036854775807 {
		return 0, errors.New("uint64 out of range")
	}

	return int64(value), nil
}

func CastToUint64(value int64) (uint64, error) {
	if value < 0 {
		return 0, errors.New("int64 out of range")
	}

	return uint64(value), nil
}
