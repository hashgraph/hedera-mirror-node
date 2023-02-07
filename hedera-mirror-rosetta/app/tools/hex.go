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

package tools

import "strings"

const HexPrefix string = "0x"

// SafeAddHexPrefix - adds 0x prefix to a string if it does not have one
func SafeAddHexPrefix(string string) string {
	if strings.HasPrefix(string, HexPrefix) {
		return string
	}
	return HexPrefix + string
}

// SafeRemoveHexPrefix - removes 0x prefix from a string if it has one
func SafeRemoveHexPrefix(string string) string {
	if strings.HasPrefix(string, HexPrefix) {
		return string[2:]
	}
	return string
}
