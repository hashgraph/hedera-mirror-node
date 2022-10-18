package com.hedera.mirror.test.e2e.acceptance.props;

public enum MirrorKycStatus {

        NOT_APPLICABLE("NOT_APPLICABLE"),

        GRANTED("GRANTED"),

        REVOKED("REVOKED");

        private String value;

        MirrorKycStatus(String value) {
                this.value = value;
        }
}
