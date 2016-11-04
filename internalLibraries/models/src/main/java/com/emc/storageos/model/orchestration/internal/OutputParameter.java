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
package com.emc.storageos.model.orchestration.internal;

/**
 * Base class that represents an output parameter
 */
public abstract class OutputParameter implements Parameter {
    private final String name;
    
    public OutputParameter(final String name) {
        this.name = name;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    public abstract boolean isBasicOutputParameter();
    public abstract BasicOutputParameter asBasicOutputParameter();
    
    public abstract boolean isTableOutputParameter();
    public abstract TableOutputParameter asTableOutputParameter();
}
