/**
 *  HVAC Zoning Status
 *
 *  Copyright 2020 Reid Baldwin
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * Version 0.2 - Initial Release
 */

metadata {
	definition (name: "HVAC Zoning Status", namespace: "rbaldwi3", author: "Reid Baldwin", cstHandler: true) {
		capability "ThermostatOperatingState"
	}
    attribute "ventState", "string"
    command "update", ["bool", "bool", "bool"]
    command "setventState", ["string"]
}
    
// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
}

def update(heat, cool, fan) {
	log.debug("In update $heat $cool $fan")
    if ("$heat" == "true") {
    	sendEvent(name:"thermostatOperatingState", value:"heating")
    } else if ("$cool" == "true") {
    	sendEvent(name:"thermostatOperatingState", value:"cooling")
    } else if ("$fan" == "true") {
    	sendEvent(name:"thermostatOperatingState", value:"fan only")
    } else {
    	sendEvent(name:"thermostatOperatingState", value:"idle")
    }
}

def setventState(new_state) {
    sendEvent(name:"ventState", value:"$new_state")
}
