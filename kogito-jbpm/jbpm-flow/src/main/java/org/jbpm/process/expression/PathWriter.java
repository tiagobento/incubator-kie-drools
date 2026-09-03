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
package org.jbpm.process.expression;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Writes a value into the place a dotted path names, for a language that has no assignment of its own.
 *
 * <p>
 * <code>person.address.city</code> reads <code>person</code> from the scope, walks <code>address</code> through a
 * getter, a public field or a map key, and writes <code>city</code> through a setter, a public field or a map key.
 * A path of one segment names a variable: there is nothing to navigate, so the value is returned for the caller
 * to store under that name, which is what every language does for a bare variable target.
 */
public final class PathWriter {

    private PathWriter() {
    }

    public static Object write(String path, Object value, ExpressionScope scope) {
        String[] segments = path.trim().split("\\s*\\.\\s*");
        if (segments.length == 1) {
            return value;
        }
        if (!scope.has(segments[0])) {
            throw new IllegalArgumentException(String.format("'%s' cannot be written: '%s' is not a variable in scope.", path, segments[0]));
        }
        Object current = scope.get(segments[0]);
        if (current == null) {
            throw new IllegalArgumentException(String.format("'%s' cannot be written: '%s' is null.", path, segments[0]));
        }
        for (int i = 1; i < segments.length - 1; i++) {
            Object next = read(current, segments[i], path);
            if (next == null) {
                throw new IllegalArgumentException(String.format("'%s' cannot be written: '%s' is null.", path, String.join(".", java.util.Arrays.copyOf(segments, i + 1))));
            }
            current = next;
        }
        set(current, segments[segments.length - 1], value, path);
        return value;
    }

    private static Object read(Object target, String name, String path) {
        if (target instanceof Map) {
            return ((Map<?, ?>) target).get(name);
        }
        String capitalized = capitalize(name);
        for (String prefix : new String[] { "get", "is" }) {
            try {
                Method getter = target.getClass().getMethod(prefix + capitalized);
                return getter.invoke(target);
            } catch (NoSuchMethodException e) {
                // try the next form
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException(String.format("'%s' cannot be read while writing '%s'.", name, path), e);
            }
        }
        try {
            Field field = target.getClass().getField(name);
            return field.get(target);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(String.format("'%s' cannot be written: %s has no property '%s'.", path, target.getClass().getName(), name));
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(String.format("'%s' cannot be read while writing '%s'.", name, path), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void set(Object target, String name, Object value, String path) {
        if (target instanceof Map) {
            ((Map<String, Object>) target).put(name, value);
            return;
        }
        String setterName = "set" + capitalize(name);
        Method fallback = null;
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(setterName) || method.getParameterCount() != 1) {
                continue;
            }
            if (value == null || wrap(method.getParameterTypes()[0]).isInstance(value)) {
                invoke(method, target, value, path);
                return;
            }
            fallback = method;
        }
        if (fallback != null) {
            invoke(fallback, target, value, path);
            return;
        }
        try {
            target.getClass().getField(name).set(target, value);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(String.format("'%s' cannot be written: %s has no writable property '%s'.", path, target.getClass().getName(), name));
        } catch (IllegalAccessException | IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format("'%s' cannot be written to '%s'.", value, path), e);
        }
    }

    private static void invoke(Method setter, Object target, Object value, String path) {
        try {
            setter.invoke(target, value);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format("'%s' cannot be written to '%s'.", value, path), e);
        }
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        return Character.class;
    }

    private static String capitalize(String name) {
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
