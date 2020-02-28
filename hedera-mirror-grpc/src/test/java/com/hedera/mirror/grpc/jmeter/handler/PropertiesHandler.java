package com.hedera.mirror.grpc.jmeter.handler;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2020 Hedera Hashgraph, LLC
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

    public long getLongTestParam(String property, String defaultVal) {
        String value = getTestParam(property, defaultVal);
        return Long.parseLong(value);
    }

    public int getIntTestParam(String property, String defaultVal) {
        String value = getTestParam(property, defaultVal);
        return Integer.parseInt(value);
    }

    public String getClientTestParam(String property, int num) {
        return getTestParam(String.format(clientPattern, property, num));
    }

    public String getClientTestParam(String property, int num, String defaultVal) {
        String retrievedValue = getTestParam(String.format(clientPattern, property, num));

        if (retrievedValue == null || retrievedValue.isEmpty()) {
            return defaultVal;
        }

        return retrievedValue;
    }

    public long getLongClientTestParam(String property, int num) {
        String value = getTestParam(String.format(clientPattern, property, num));
        return Long.parseLong(value);
    }

    public long getLongClientTestParam(String property, int num, String defaultVal) {
        String value = getTestParam(String.format(clientPattern, property, num), defaultVal);
        return Long.parseLong(value);
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
