package com.hedera.mirror.importer.repository.upsert;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import java.io.StringWriter;
import java.text.MessageFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

@Log4j2
@RequiredArgsConstructor
public class GenericUpsertQueryGenerator implements UpsertQueryGenerator {

    private static final String UPSERT_TEMPLATE = "/db/template/upsert.vm";

    private static final String UPSERT_HISTORY_TEMPLATE = "/db/template/upsert_history.vm";

    private final UpsertEntity upsertEntity;

    @Override
    public String getCreateTempIndexQuery() {
        String columns = upsertEntity.columns(UpsertColumn::isId, "{0}");
        return MessageFormat.format("create index if not exists {0}_idx on {0} ({1})",
                getTemporaryTableName(), columns);
    }

    @Override
    public String getCreateTempTableQuery() {
        return String.format("create temporary table if not exists %s on commit drop as table %s limit 0",
                getTemporaryTableName(), upsertEntity.getTableName());
    }

    @Override
    public String getFinalTableName() {
        return upsertEntity.getTableName();
    }

    /**
     * Constructs an upsert query using a velocity template with replacement variables for table and column names
     * constructed from the `UpsertEntity` metadata.
     *
     * @return the upsert query
     */
    @Override
    public String getInsertQuery() {
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADERS, RuntimeConstants.RESOURCE_LOADER_CLASS);
        velocityEngine.setProperty("resource.loader.class.class", ClasspathResourceLoader.class.getName());
        velocityEngine.init();

        String templatePath = upsertEntity.getUpsertable().history() ? UPSERT_HISTORY_TEMPLATE : UPSERT_TEMPLATE;
        Template template = velocityEngine.getTemplate(templatePath);

        VelocityContext velocityContext = new VelocityContext();
        velocityContext.put("finalTable", getFinalTableName());
        velocityContext.put("historyTable", getFinalTableName() + "_history");
        velocityContext.put("tempTable", getTemporaryTableName());

        // Columns: {0} is column name and {1} is column default. t is the temporary table alias and e is the existing.
        velocityContext.put("coalesceColumns", upsertEntity.columns("coalesce(t.{0}, e.{0}, {1})"));
        velocityContext.put("conflictColumns", upsertEntity.columns(UpsertColumn::isId, "{0}"));
        velocityContext.put("existingColumns", closeRange(upsertEntity.columns("e.{0}")));
        velocityContext.put("idColumns", upsertEntity.columns(UpsertColumn::isId, "e.{0}"));
        velocityContext.put("idJoin", upsertEntity.columns(UpsertColumn::isId, "e.{0} = t.{0}", " and "));
        velocityContext.put("insertColumns", upsertEntity.columns("{0}"));
        velocityContext.put("notNullableColumn",
                upsertEntity.column(c -> !c.isNullable() && !c.isId(), "coalesce(t.{0}, e.{0})"));
        velocityContext.put("updateColumns", upsertEntity.columns(UpsertColumn::isUpdatable, "{0} = excluded.{0}"));

        StringWriter writer = new StringWriter();
        template.merge(velocityContext, writer);
        return writer.toString();
    }

    @Override
    public String getTemporaryTableName() {
        return getFinalTableName() + "_temp";
    }

    @Override
    public String getUpdateQuery() {
        return "";
    }

    private String closeRange(String input) {
        return input.replace("e.timestamp_range",
                "int8range(min(lower(e.timestamp_range)), min(lower(t.timestamp_range))) as timestamp_range");
    }
}
