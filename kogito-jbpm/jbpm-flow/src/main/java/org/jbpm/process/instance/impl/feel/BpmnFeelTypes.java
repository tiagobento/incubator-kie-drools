/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jbpm.process.instance.impl.feel;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;

/**
 * The boundary between the process's Java values and FEEL's own.
 *
 * DMN needs nothing like this: its values live in a map, its declared types <em>are</em> FEEL types, and its dates are
 * parsed into FEEL temporals to begin with - which is why there is no FEEL-to-Java conversion anywhere in kie-dmn. A
 * BPMN process is the opposite: its variables are declared with Java types and code generation gives them typed
 * accessors, so values have to be translated in both directions.
 *
 * Two things need translating. FEEL does not recognise {@link Date} as a temporal value, so it falls back to reading it
 * as a bean and answers with the deprecated accessors - <code>aDate.year</code> gives 126 rather than 2026 - and FEEL
 * has one number type, {@link BigDecimal}, which no typed variable will accept.
 */
public final class BpmnFeelTypes {

    private BpmnFeelTypes() {
    }

    /**
     * A Java value as the FEEL value that means the same thing.
     *
     * Numbers are left alone: FEEL widens any {@link Number} to its own on the way in - {@code NumberEvalHelper}
     * normalises every function result the same way. Only the temporal types need help, since FEEL would otherwise
     * read them as ordinary objects and answer with whatever their getters return.
     */
    public static Object toFeel(Object value) {
        if (value instanceof Date) {
            return toFeelDateAndTime(((Date) value).getTime());
        }
        if (value instanceof Calendar) {
            return toFeelDateAndTime(((Calendar) value).getTimeInMillis());
        }
        return value;
    }

    /**
     * Deliberately the same conversion {@code EvalHelper} already applies to a {@link Date} returned by a getter, down
     * to producing a {@link LocalDateTime} rather than a zoned one.
     *
     * A date must not depend on how the expression reached it: were a variable holding a date to become a
     * <code>date and time</code> with a zone while the same date read through a property became one without, the two
     * would render differently and compare badly against each other.
     */
    private static LocalDateTime toFeelDateAndTime(long epochMilli) {
        return Instant.ofEpochMilli(epochMilli).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * A FEEL result as the type the process declared for it.
     *
     * Converting a number to an integral type only succeeds when nothing is lost: a result of 20.5 must not become 20
     * behind the author's back. Floating-point targets are converted as they are, since approximation is what they are
     * for.
     *
     * @param declaredType the variable's declared type, or <code>null</code> when it has none
     * @param name the variable being written, for the message when the value does not fit
     */
    public static Object fromFeel(Object value, Class<?> declaredType, String name) {
        if (value == null || declaredType == null || declaredType == Object.class || declaredType.isInstance(value)) {
            return value;
        }
        if (value instanceof BigDecimal) {
            return fromFeelNumber((BigDecimal) value, declaredType, name);
        }
        if (Date.class.isAssignableFrom(declaredType)) {
            return toDate(value, declaredType, name);
        }
        return value;
    }

    private static Object fromFeelNumber(BigDecimal value, Class<?> declaredType, String name) {
        try {
            if (declaredType == Integer.class || declaredType == int.class) {
                return value.intValueExact();
            }
            if (declaredType == Long.class || declaredType == long.class) {
                return value.longValueExact();
            }
            if (declaredType == Short.class || declaredType == short.class) {
                return value.shortValueExact();
            }
            if (declaredType == Byte.class || declaredType == byte.class) {
                return value.byteValueExact();
            }
            if (declaredType == BigInteger.class) {
                return value.toBigIntegerExact();
            }
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(String.format(
                    "FEEL produced %s for '%s', which does not fit its declared type %s without losing part of the value. "
                            + "Declare the variable as a decimal type, or round the expression yourself.",
                    value.toPlainString(), name, declaredType.getSimpleName()));
        }
        if (declaredType == Double.class || declaredType == double.class) {
            return value.doubleValue();
        }
        if (declaredType == Float.class || declaredType == float.class) {
            return value.floatValue();
        }
        if (declaredType == String.class) {
            return value.toPlainString();
        }
        return value;
    }

    private static Object toDate(Object value, Class<?> declaredType, String name) {
        ZonedDateTime moment = null;
        if (value instanceof ZonedDateTime) {
            moment = (ZonedDateTime) value;
        } else if (value instanceof LocalDateTime) {
            moment = ((LocalDateTime) value).atZone(ZoneId.systemDefault());
        } else if (value instanceof LocalDate) {
            moment = ((LocalDate) value).atStartOfDay(ZoneId.systemDefault());
        } else if (value instanceof LocalTime) {
            moment = ((LocalTime) value).atDate(LocalDate.now()).atZone(ZoneId.systemDefault());
        } else if (value instanceof java.time.OffsetDateTime) {
            moment = ((java.time.OffsetDateTime) value).toZonedDateTime();
        }
        if (moment == null) {
            throw new IllegalArgumentException(String.format(
                    "FEEL produced %s for '%s', which cannot be read as the declared type %s.",
                    value, name, declaredType.getSimpleName()));
        }
        return Date.from(moment.toInstant());
    }
}
