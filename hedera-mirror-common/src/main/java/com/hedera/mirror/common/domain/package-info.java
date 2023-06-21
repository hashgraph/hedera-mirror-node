@ConverterRegistration(converter = UnknownIdConverter.class)
@TypeRegistration(basicClass = List.class, userType = ListArrayType.class)
@TypeRegistration(basicClass = Range.class, userType = PostgreSQLGuavaRangeType.class)
package com.hedera.mirror.common.domain;

import com.google.common.collect.Range;
import com.hedera.mirror.common.converter.UnknownIdConverter;
import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import java.util.List;
import org.hibernate.annotations.ConverterRegistration;
import org.hibernate.annotations.TypeRegistration;
