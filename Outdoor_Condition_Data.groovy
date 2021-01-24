/**
 *  Outdoor Condition Data
 *
 *  Copyright 2021 Reid Baldwin
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
 *

 * Version 1.0 - Initial Release
 */

metadata {
	definition (name: "Outdoor Condition Data", namespace: "rbaldwi3", author: "Reid Baldwin", cstHandler: true) {
		capability "Actuator" // this makes it selectable for custom actions in rule machine
        capability "Refresh" // refresh() ensures that the cumulative time attributes are up to date. Otherwise, the attributes are only updated on changes.
	}
    attribute "temperature", "number"
    command "set_temperature", ["number"]
    attribute "illuminance", "number"
    command "set_illuminance", ["number"]
    attribute "IM", "number"
    attribute "HDM1", "number"
    attribute "CDM1", "number"
    attribute "base1", "number"
    command "set_base1", ["number"]
    attribute "HDM2", "number"
    attribute "CDM2", "number"
    attribute "base2", "number"
    command "set_base2", ["number"]
    attribute "HDM3", "number"
    attribute "CDM3", "number"
    attribute "base3", "number"
    command "set_base3", ["number"]
    attribute "prev_ID", "number"
    attribute "prev_HDD1", "number"
    attribute "prev_CDD1", "number"
    attribute "prev_HDD2", "number"
    attribute "prev_CDD2", "number"
    attribute "prev_HDD3", "number"
    attribute "prev_CDD3", "number"
    command "reset"
}

def installed() {
    log.debug("In installed()")
    initialize()
    set_base1(50)
    set_base2(60)
    set_base3(70)
}

def updated() {
    log.debug("In updated()")
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    log.debug("In initialize()")
    state.illuminance_time = 0
    state.temperature_time = 0
    state.IM = 0
    state.HDM1 = 0
    state.CDM1 = 0
    state.HDM2 = 0
    state.CDM2 = 0
    state.HDM3 = 0
    state.CDM3 = 0
}

def refresh() {
    update_temperature(state.temperature)
    update_illuminance(state.illuminance)
}

def reset() {
    refresh()
    prev = state.IM / 60 / 24
    sendEvent(name:"prev_ID", value:prev)
    state.IM = 0
    sendEvent(name:"IM", value:0)
    prev = state.HDM1 / 60 / 24
    sendEvent(name:"prev_HDD1", value:prev)
    state.HDM1 = 0
    sendEvent(name:"HDM1", value:0)
    prev = state.CDM1 / 60 / 24
    sendEvent(name:"prev_CDD1", value:prev)
    state.CDM1 = 0
    sendEvent(name:"CDM1", value:0)
    prev = state.HDM2 / 60 / 24
    sendEvent(name:"prev_HDD2", value:prev)
    state.HDM2 = 0
    sendEvent(name:"HDM2", value:0)
    prev = state.CDM2 / 60 / 24
    sendEvent(name:"prev_CDD2", value:prev)
    state.CDM2 = 0
    sendEvent(name:"CDM2", value:0)
    prev = state.HDM3 / 60 / 24
    sendEvent(name:"prev_HDD3", value:prev)
    state.HDM3 = 0
    sendEvent(name:"HDM3", value:0)
    prev = state.CDM3 / 60 / 24
    sendEvent(name:"prev_CDD3", value:prev)
    state.CDM3 = 0
    sendEvent(name:"CDM3", value:0)
}

def set_base1(Number new_value) {
	log.debug("In set_base1($new_value)")
    state.base1 = new_value
    sendEvent(name:"base1", value:new_value)
}

def set_base2(Number new_value) {
	log.debug("In set_base2($new_value)")
    state.base2 = new_value
    sendEvent(name:"base2", value:new_value)
}

def set_base3(Number new_value) {
	log.debug("In set_base3($new_value)")
    state.base3 = new_value
    sendEvent(name:"base3", value:new_value)
}

def update_temperature(Number new_value) {
	log.debug("In update_temperature($new_value)")
    avg = (state.temperature + new_value) / 2
    duration = now() - state.temperature_time
    state.temperature_time = now()
    if (avg > state.base1) {
        state.CDM1 += (avg - state.base1) * duration / 60 / 1000
        sendEvent(name:"CDM1", value:state.CDM1)
    } else {
        state.HDM1 += (state.base1 - avg) * duration / 60 / 1000
        sendEvent(name:"HDM1", value:state.HDM1)
    }
    if (avg > state.base2) {
        state.CDM2 += (avg - state.base2) * duration / 60 / 1000
        sendEvent(name:"CDM2", value:state.CDM2)
    } else {
        state.HDM2 += (state.base2 - avg) * duration / 60 / 1000
        sendEvent(name:"HDM2", value:state.HDM2)
    }
    if (avg > state.base3) {
        state.CDM3 += (avg - state.base3) * duration / 60 / 1000
        sendEvent(name:"CDM3", value:state.CDM3)
    } else {
        state.HDM3 += (state.base3 - avg) * duration / 60 / 1000
        sendEvent(name:"HDM3", value:state.HDM3)
    }
    state.temperature = new_value
    sendEvent(name:"temperature", value:new_value)
}

def set_temperature(Number new_value) {
	log.debug("In set_temperature($new_value)")
    if (state.temperature_time) {
        update_temperature(new_value)
    } else {
        state.temperature_time = now()
        state.temperature = new_value
        sendEvent(name:"temperature", value:new_value)
    }
}

def update_illuminance(Number new_value) {
	log.debug("In update_illuminance($new_value)")
    avg = (state.illuminance + new_value) / 2
    duration = now() - state.illuminance_time
    state.IM += avg * duration / 60 / 1000
    sendEvent(name:"IM", value:state.IM)
    state.illuminance_time = now()
    state.illuminance = new_value
    sendEvent(name:"illuminance", value:new_value)
}

def set_illuminance(Number new_value) {
	log.debug("In set_illuminance($new_value)")
    if (state.illuminance_time) {
        update_illuminance(new_value)
    } else {
        state.illuminance_time = now()
        state.illuminance = new_value
        sendEvent(name:"illuminance", value:new_value)
    }
}
