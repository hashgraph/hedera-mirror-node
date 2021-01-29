package com.hedera.mirror.grpc.jmeter.handler;

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

import lombok.extern.log4j.Log4j2;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.assertj.core.util.Strings;

/**
 * A utility class for easily retrieving jmeter user properties from a properties file Offers numerous method overloads
 * for cleaner code calls
 */
@Log4j2
public class PropertiesHandler {
    private final String basePattern = "%s.%s";
    private final String clientPattern = "client%s[%d]";
    private final JavaSamplerContext javaSamplerContext;
    private final String propertiesBase;

    public PropertiesHandler(JavaSamplerContext javaSamplerContext) {
        this.javaSamplerContext = javaSamplerContext;
        propertiesBase = javaSamplerContext.getParameter("propertiesBase", "hedera.mirror.test.performance");
    }

    public String getTestParam(String property, String defaultVal) {
        String retrievedValue = getTestParam(property);
        if (retrievedValue == null || retrievedValue.isEmpty()) {
            return defaultVal;
        }

        return retrievedValue;
    }

    public Long getLongTestParam(String property, Long defaultVal) {
        String value = getTestParam(property);

        if (Strings.isNullOrEmpty(value)) {
            return defaultVal;
        }

        return Long.parseLong(value);
    }

    public int getIntTestParam(String property, int defaultVal) {
        String value = getTestParam(property);

        if (Strings.isNullOrEmpty(value)) {
            return defaultVal;
        }

        return Integer.parseInt(value);
    }

    public String getClientTestParam(String property, int num) {
        return getTestParam(String.format(clientPattern, property, num));
    }

    public String getClientTestParam(String property, int num, String defaultVal) {
        String retrievedValue = getTestParam(String.format(clientPattern, property, num));

        if (Strings.isNullOrEmpty(retrievedValue)) {
            return defaultVal;
        }

        return retrievedValue;
    }

    public long getLongClientTestParam(String property, int num) {
        String value = getTestParam(String.format(clientPattern, property, num));
        return Long.parseLong(value);
    }

    public Long getLongClientTestParam(String property, int num, Long defaultVal) {
        return getLongTestParam(String.format(clientPattern, property, num), defaultVal);
    }

    public int getIntClientTestParam(String property, int num) {
        String value = getTestParam(String.format(clientPattern, property, num));
        return Integer.parseInt(value);
    }

    public int getIntClientTestParam(String property, int num, String defaultVal) {
        String value = getTestParam(String.format(clientPattern, property, num), defaultVal);
        return Integer.parseInt(value);
    }

    private String getTestParam(String property) {
        String value = javaSamplerContext.getJMeterProperties()
                .getProperty(String.format(basePattern, propertiesBase, property));
        log.trace("Retrieved {} prop as {}", property, value);
        return value;
    }
}
