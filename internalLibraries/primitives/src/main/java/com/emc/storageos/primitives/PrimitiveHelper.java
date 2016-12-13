/*
 * Copyright 2016 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.storageos.primitives;

import java.util.Set;

import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;

import com.emc.storageos.primitives.samples.LocalAnsible;
import com.emc.storageos.primitives.samples.RestPrimitive;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

/**
 * Helper class to load primitives
 */
public final class PrimitiveHelper {
    
    private static final ImmutableMap<String, Primitive> PRIMITIVES_MAP;

    static {
        Set<Class<? extends ViPRPrimitive>> primitives = 
                new Reflections("com.emc.storageos", new SubTypesScanner()).getSubTypesOf(ViPRPrimitive.class);
        final Builder<String, Primitive> builder = ImmutableMap.<String, Primitive>builder();
        for( final Class<? extends ViPRPrimitive> primitive : primitives) {
            final ViPRPrimitive instance;
            try {
                instance = primitive.newInstance();
            } catch (final IllegalAccessException | InstantiationException e) {
                throw new RuntimeException("Failed to create instance of primitive: "+primitive.getName(), e);
            }
        
            builder.put(instance.getName(), instance);
        }
        
        PRIMITIVES_MAP = builder
                .put(LocalAnsible.class.getName(), new LocalAnsible())
                .put(RestPrimitive.class.getName(), new RestPrimitive())
                .build();
    }
    
    
    private static final ImmutableList<Primitive> PRIMITIVES_LIST = ImmutableList.<Primitive>builder()
            .addAll((PRIMITIVES_MAP.values()))
            .build();
    
    public static ImmutableList<Primitive> list() {
        return PRIMITIVES_LIST;
    }
    
    public static Primitive get(final String name) {
        return PRIMITIVES_MAP.get(name);
    }
}
