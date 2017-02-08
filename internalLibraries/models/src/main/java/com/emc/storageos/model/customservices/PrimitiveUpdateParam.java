/*
 * Copyright 2017 Dell Inc. or its subsidiaries.
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
package com.emc.storageos.model.customservices;

import java.net.URI;
import java.util.Map;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "primitive_update_param")
public class PrimitiveUpdateParam {

    private String name;
    private String friendlyName;
    private String description;
    private InputUpdateParam input;
    private OutputUpdateParam output;
    private Map<String,String> attributes;
    private URI resource;

    @XmlElement(name = "name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElement(name = "friendly_name")
    public String getFriendlyName() {
        return friendlyName;
    }

    public void setFriendlyName(String friendlyName) {
        this.friendlyName = friendlyName;
    }

    @XmlElement(name = "description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @XmlElement(name= "attributes") 
    public Map<String, String> getAttributes() {
        return attributes;
    }
    
    public void setAttributes(final Map<String,String> attributes) {
        this.attributes = attributes;
    }
    
    @XmlElement(name = "input")
    public InputUpdateParam getInput() {
        return input;
    }

    public void setInput(InputUpdateParam input) {
        this.input = input;
    }

    @XmlElement(name = "output")
    public OutputUpdateParam getOutput() {
        return output;
    }

    public void setOutput(final OutputUpdateParam output) {
        this.output = output;
    }
}
