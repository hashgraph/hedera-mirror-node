package com.hedera.mirror.dataset;

import lombok.Value;

@Value
public final class NumberedLine {
    int lineNumber;
    String value;

    @Override
    public String toString() {
        return lineNumber + ":" + value;
    }
}