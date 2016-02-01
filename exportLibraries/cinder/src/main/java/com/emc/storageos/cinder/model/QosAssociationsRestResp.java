/*
 * Copyright 2015 EMC Corporation
 * Copyright 2016 Intel Corporation
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

package com.emc.storageos.cinder.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
// REST response for the List volume associations query
@XmlRootElement(name = "qos_associations")
public class QosAssociationsRestResp {

	private List<CinderQosAssociation> associations;

	@XmlElementRef(name="qos_associations")
	public List<CinderQosAssociation> getAssociation(){
		if (associations == null){
			associations = new ArrayList<CinderQosAssociation>();
		}
		return associations;
	}
	
}
