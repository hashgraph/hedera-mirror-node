package hex

import (
	"strings"
)

const (
	hexPrefix string = "0x"
)

// SafeAddHexPrefix - adds 0x prefix to a string if it does not have one
func SafeAddHexPrefix(string string) string {
	if strings.HasPrefix(string, hexPrefix) {
		return string
	}
	return hexPrefix + string
}

// SafeRemoveHexPrefix - removes 0x prefix from a string if it has one
func SafeRemoveHexPrefix(string string) string {
	if strings.HasPrefix(string, hexPrefix) {
		return string[2:]
	}
	return string
}
