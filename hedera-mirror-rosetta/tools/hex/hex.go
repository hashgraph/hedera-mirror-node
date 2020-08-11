package hex

import (
	"fmt"
	"strings"
)

func FormatHex(string string) string {
	if strings.HasPrefix(string, "0x") {
		return string
	}
	return fmt.Sprintf("0x%s", string)
}
