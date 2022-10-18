package com.hedera.mirror.test.e2e.acceptance.props;

public enum MirrorFreezeStatus {

    NOT_APPLICABLE("NOT_APPLICABLE"),

    FROZEN("FROZEN"),

    UNFROZEN("UNFROZEN");


    private String value;

    MirrorFreezeStatus(String value) {
        this.value = value;
    }
}
