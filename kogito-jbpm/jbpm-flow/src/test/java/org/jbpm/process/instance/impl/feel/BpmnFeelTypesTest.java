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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * The translation between the process's Java values and FEEL's own.
 */
class BpmnFeelTypesTest {

    @Test
    void aDateBecomesSomethingFeelCanDoDateArithmeticOn() {
        Date date = new Date();
        assertThat(BpmnFeelTypes.toFeel(date)).isInstanceOf(LocalDateTime.class);
        assertThat(BpmnFeelTypes.toFeel(date))
                .isEqualTo(Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDateTime());
    }

    @Test
    void aDateConvertsTheSameWayFeelAlreadyConvertsOneReadFromAGetter() {
        // EvalHelper turns a Date returned by a getter into a LocalDateTime; a date must not depend on how the
        // expression reached it
        Date date = new Date();
        Map<String, Object> scope = Map.of("direct", BpmnFeelTypes.toFeel(date), "holder", new DateHolder(date));
        assertThat(BpmnFeel.newFeel().evaluate("direct = holder.when", scope)).isEqualTo(Boolean.TRUE);
    }

    @Test
    void theSqlDateTypesConvertToo() {
        // they extend java.util.Date, and getTime() answers for all of them
        assertThat(BpmnFeelTypes.toFeel(java.sql.Date.valueOf("2026-03-10"))).isInstanceOf(LocalDateTime.class);
        assertThat(BpmnFeelTypes.toFeel(java.sql.Timestamp.valueOf("2026-03-10 12:30:00"))).isInstanceOf(LocalDateTime.class);
    }

    @Test
    void aCalendarConvertsToo() {
        Calendar calendar = Calendar.getInstance();
        assertThat(BpmnFeelTypes.toFeel(calendar)).isInstanceOf(LocalDateTime.class);
    }

    public static class DateHolder {
        private final Date when;

        public DateHolder(Date when) {
            this.when = when;
        }

        public Date getWhen() {
            return when;
        }
    }

    @Test
    void everythingElseIsLeftAlone() {
        // FEEL widens any Number to its own on the way in, so numbers need no help here
        assertThat(BpmnFeelTypes.toFeel(41)).isEqualTo(41);
        assertThat(BpmnFeelTypes.toFeel("hello")).isEqualTo("hello");
        assertThat(BpmnFeelTypes.toFeel(null)).isNull();
        assertThat(BpmnFeelTypes.toFeel(Map.of("a", 1))).isEqualTo(Map.of("a", 1));
    }

    @Test
    void feelReadsAConvertedDateCorrectly() {
        // the whole point: aDate.year used to answer 126, from the deprecated Date.getYear()
        Calendar calendar = Calendar.getInstance();
        calendar.set(2026, Calendar.MARCH, 10, 12, 0, 0);
        Map<String, Object> scope = Map.of("d", BpmnFeelTypes.toFeel(calendar.getTime()));
        assertThat(BpmnFeel.newFeel().evaluate("d.year", scope)).isEqualTo(BigDecimal.valueOf(2026));
        assertThat(BpmnFeel.newFeel().evaluate("d.month", scope)).isEqualTo(BigDecimal.valueOf(3));
        assertThat(BpmnFeel.newFeel().evaluate("d + duration(\"P1D\")", scope)).isInstanceOf(LocalDateTime.class);
    }

    @Test
    void aNumberIsWrittenBackAsTheDeclaredIntegralType() {
        assertThat(BpmnFeelTypes.fromFeel(BigDecimal.valueOf(42), Integer.class, "n")).isEqualTo(42);
        assertThat(BpmnFeelTypes.fromFeel(BigDecimal.valueOf(42), Long.class, "n")).isEqualTo(42L);
        assertThat(BpmnFeelTypes.fromFeel(BigDecimal.valueOf(42), Short.class, "n")).isEqualTo((short) 42);
        assertThat(BpmnFeelTypes.fromFeel(BigDecimal.valueOf(42), BigInteger.class, "n")).isEqualTo(BigInteger.valueOf(42));
    }

    @Test
    void aFractionalResultIsRefusedRatherThanQuietlyTruncated() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> BpmnFeelTypes.fromFeel(new BigDecimal("20.5"), Integer.class, "half"))
                .withMessageContaining("20.5")
                .withMessageContaining("half")
                .withMessageContaining("Integer");
    }

    @Test
    void aValueTooLargeForTheDeclaredTypeIsRefusedToo() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> BpmnFeelTypes.fromFeel(new BigDecimal("99999999999"), Integer.class, "big"));
    }

    @Test
    void floatingPointTargetsAreConvertedSinceApproximationIsWhatTheyAreFor() {
        assertThat(BpmnFeelTypes.fromFeel(new BigDecimal("20.5"), Double.class, "n")).isEqualTo(20.5d);
        assertThat(BpmnFeelTypes.fromFeel(new BigDecimal("20.5"), Float.class, "n")).isEqualTo(20.5f);
    }

    @Test
    void aFeelDateAndTimeIsWrittenBackAsTheDeclaredDate() {
        ZonedDateTime moment = ZonedDateTime.of(2026, 3, 10, 12, 0, 0, 0, ZoneId.systemDefault());
        assertThat(BpmnFeelTypes.fromFeel(moment, Date.class, "when")).isEqualTo(Date.from(moment.toInstant()));
        assertThat(BpmnFeelTypes.fromFeel(LocalDate.of(2026, 3, 10), Date.class, "when"))
                .isEqualTo(Date.from(LocalDate.of(2026, 3, 10).atStartOfDay(ZoneId.systemDefault()).toInstant()));
    }

    @Test
    void aValueThatAlreadyFitsIsLeftAlone() {
        assertThat(BpmnFeelTypes.fromFeel("hello", String.class, "s")).isEqualTo("hello");
        assertThat(BpmnFeelTypes.fromFeel(Boolean.TRUE, Boolean.class, "b")).isEqualTo(Boolean.TRUE);
        assertThat(BpmnFeelTypes.fromFeel(null, Integer.class, "n")).isNull();
    }

    @Test
    void anUndeclaredOrObjectTypedVariableTakesWhateverFeelProduced() {
        assertThat(BpmnFeelTypes.fromFeel(BigDecimal.valueOf(42), null, "n")).isEqualTo(BigDecimal.valueOf(42));
        assertThat(BpmnFeelTypes.fromFeel(BigDecimal.valueOf(42), Object.class, "n")).isEqualTo(BigDecimal.valueOf(42));
    }
}
