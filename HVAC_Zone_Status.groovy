/**
 *  HVAC Zone Status
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
 *

 * Version 0.3 - Initial Release
 */

metadata {
	definition (name: "HVAC Zone Status", namespace: "rbaldwi3", author: "Reid Baldwin", cstHandler: true) {
		capability "Actuator" // this makes it selectable for custom actions in rule machine
        capability "Refresh" // refresh() ensures that the cumulative time attributes are up to date. Otherwise, the attributes are only updated on state changes.
	}
    attribute "current_state", "string" // One of Cooling, Heating, Fan, Vent, Dehum, Off, and Idle - Off means stop flow, Idle means blower not running
    attribute "flow", "number" // cfm desired in addition to off_capacity
    command "setState", ["string", "number"] // called by equipment control or parent zone
    // these attributes are set by the zone itself based on thermostat and child zone inputs
    attribute "heat_demand", "number"
    command "set_heat_demand", ["number"]
    attribute "cool_demand", "number"
    command "set_cool_demand", ["number"]
    attribute "fan_demand", "number"
    command "set_fan_demand", ["number"]
    attribute "heat_accept", "number"
    command "set_heat_accept", ["number"]
    attribute "cool_accept", "number"
    command "set_cool_accept", ["number"]
    attribute "fan_accept", "number"
    command "set_fan_accept", ["number"]
    attribute "dehum_accept", "number"
    command "set_dehum_accept", ["number"]
    attribute "off_capacity", "number"
    command "set_off_capacity", ["number"]
    // debugging features
    attribute "debug_msg", "string"
    command "debug", ["string"]
    attribute "status_msg", "string"
    command "set_status", ["string"]
    // performance tracking features
    attribute "cooling_time", "number" // cumulative cooling time, in seconds
    attribute "heating_time", "number" // cumulative heating time, in seconds
    attribute "vent_time", "number" // cumulative vent time, in seconds, excludes time that overlaps with heating or cooling
    attribute "fan_time", "number" // cumulative fan only time, in seconds
    attribute "idle_time", "number" // cumulative idle time, in seconds
    attribute "dehum_time", "number" // cumulative dehumidification time, in seconds, excludes time that overlaps with ventilation
    attribute "runtime_msg", "string"
    command "reset_runtime" // resets all cumulative time attributes to zero
    attribute "recent_heat", "boolean"
    command "set_recent_heat", ["boolean"]
    attribute "recent_cool", "boolean"
    command "set_recent_cool", ["boolean"]
    attribute "temp_increasing", "boolean"
    command "set_temp_increasing", ["boolean"]
    attribute "temp_decreasing", "boolean"
    command "set_temp_decreasing", ["boolean"]
    attribute "offline", "boolean"
    command "set_offline", ["boolean"]
}

def installed() {
    // log.debug("In installed()")
    state.tz_offset = location.timeZone.rawOffset/1000/60/60
    initialize()
}

def updated() {
    // log.debug("In updated()")
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    // log.debug("In initialize()")
    reset_runtime()
    state.last_change = now()
    state.current_state = "Idle"
    setState("Idle", 0)
    state.heat_demand = 0
    state.cool_demand = 0
    state.fan_demand = 0
    state.heat_accept = 0
    state.cool_accept = 0
    state.fan_accept = 0
    state.dehum_accept = 0
    set_heat_demand(0)
    set_cool_demand(0)
    set_fan_demand(0)
    set_heat_accept(0)
    set_cool_accept(0)
    set_fan_accept(0)
    set_dehum_accept(0)
    state.off_capacity = 0
    set_off_capacity(0)
    debug("initialized")
    schedule("0 0 0 * * ?", set_tz_offset)
}

def set_tz_offset() {
    // log.debug("In set_tz_offset()")
    Long seconds = now() / 1000
    Long minutes = seconds / 60
    Long hours = minutes / 60
    Long days = hours / 24
    hours = hours - (days * 24)
    state.tz_offset = hours
}

def refresh() {
    // log.debug("In refresh()")
    add_interval()
}

def reset_runtime() {
    // log.debug("In reset_runtime()")
    state.cooling_time = 0
    state.heating_time = 0
    state.vent_time = 0
    state.fan_time = 0
    state.idle_time = 0
    state.dehum_time = 0
    sendEvent(name:"cooling_time", value:state.cooling_time)
    sendEvent(name:"heating_time", value:state.heating_time)
    sendEvent(name:"vent_time", value:state.vent_time)
    sendEvent(name:"fan_time", value:state.fan_time)
    sendEvent(name:"idle_time", value:state.idle_time)
    sendEvent(name:"dehum_time", value:state.dehum_time)
    state.last_change = now()
}

def add_interval() {
    // log.debug("In add_interval()")
    Long interval = (now() - state.last_change) / 1000
    state.last_change = now()
    switch ("$state.current_state") {
        case "Cooling":
            state.cooling_time += interval
            sendEvent(name:"cooling_time", value:state.cooling_time)
            break;
        case "Heating":
            state.heating_time += interval
            sendEvent(name:"heating_time", value:state.heating_time)
            break;
        case "Fan":
            state.fan_time += interval
            sendEvent(name:"fan_time", value:state.fan_time)
            break;
        case "Vent":
            state.vent_time += interval
            sendEvent(name:"vent_time", value:state.vent_time)
            break;
        case "Dehum":
            state.dehum_time += interval
            sendEvent(name:"dehum_time", value:state.vent_time)
            break;
        case "Idle":
        case "Off":
            state.idle_time += interval
            sendEvent(name:"idle_time", value:state.idle_time)
            break;
        default:
            log.debug("State $state.currentState not recognized")
    }
    String result = ""
    if (state.heating_time) {
        result += "heating=" + duration_string(state.heating_time)
        result += " "
    }
    if (state.cooling_time) {
        result += "cooling=" + duration_string(state.cooling_time)
        result += " "
    }
    if (state.fan_time) {
        result += "fan=" + duration_string(state.fan_time) + " "
    }
    if (state.vent_time) {
        result += "vent=" + duration_string(state.vent_time) + " "
    }
    if (state.dehum_time) {
        result += "dehum=" + duration_string(state.dehum_time) + " "
    }
    if (state.idle_time) {
        result += "idle=" + duration_string(state.idle_time)
    }
    sendEvent(name:"runtime_msg", value:"$result")
    state.runtime_msg = result
}

String print_time(Long hours, Long minutes, Long seconds) {
    if (minutes < 10) {
        if (seconds < 10) {
            return "$hours:0$minutes:0$seconds"
        } else {
            return "$hours:0$minutes:$seconds"
        }
    } else {
        if (seconds < 10) {
            return "$hours:$minutes:0$seconds"
        } else {
            return "$hours:$minutes:$seconds"
        }
    }
}

String time_string(Long time) {
    Long seconds = time / 1000 - state.tz_offset*60*60
    Long minutes = seconds / 60
    Long hours = minutes / 60
    Long days = hours / 12
    seconds = seconds - (minutes * 60)
    minutes = minutes - (hours * 60)
    hours = hours - (days * 12)
    if (hours == 0) {
        hours = 12
    }
    return print_time(hours, minutes, seconds)
}

String duration_string(Long seconds) {
    Long minutes = seconds / 60
    Long hours = minutes / 60
    seconds = seconds - (minutes * 60)
    minutes = minutes - (hours * 60)
    return print_time(hours, minutes, seconds)
}

def setState(String new_state, new_flow) {
	// log.debug("In setState($new_state, $new_flow)")
    add_interval()
    state.current_state = new_state
    sendEvent(name:"current_state", value:"$new_state")
    state.flow = new_flow
    sendEvent(name:"flow", value:new_flow)
}

def debug(String msg) {
    // log.debug("In debug($msg)")
    String time = time_string(now())
    sendEvent(name:"debug_msg", value:"$msg at $time")
}

def set_status_msg(String msg) {
    // log.debug("In set_status_msg($msg)")
    String time = time_string(now())
    sendEvent(name:"status_msg", value:"$msg at $time")
}

def set_heat_demand(new_value) {
	// log.debug("In set_heat_demand($new_value)")
    state.heat_demand = new_value
    sendEvent(name:"heat_demand", value:new_value)
}

def set_heat_accept(new_value) {
	// log.debug("In set_heat_accept($new_value)")
    state.heat_accept = new_value
    sendEvent(name:"heat_accept", value:new_value)
}

def set_cool_demand(new_value) {
	// log.debug("In set_cool_demand($new_value)")
    state.cool_demand = new_value
    sendEvent(name:"cool_demand", value:new_value)
}

def set_cool_accept(new_value) {
	// log.debug("In set_cool_accept($new_value)")
    state.cool_accept = new_value
    sendEvent(name:"cool_accept", value:new_value)
}

def set_fan_demand(new_value) {
	// log.debug("In set_fan_demand($new_value)")
    state.fan_demand = new_value
    sendEvent(name:"fan_demand", value:new_value)
}

def set_fan_accept(new_value) {
	// log.debug("In set_fan_accept($new_value)")
    state.fan_accept = new_value
    sendEvent(name:"fan_accept", value:new_value)
}

def set_dehum_accept(new_value) {
	// log.debug("In set_dehum_accept($new_value)")
    state.dehum_accept = new_value
    sendEvent(name:"dehum_accept", value:new_value)
}

def set_off_capacity(new_value) {
	// log.debug("In set_off_capacity($new_value)")
    state.off_capacity = new_value
    sendEvent(name:"off_capacity", value:new_value)
}

def set_recent_heat(new_value) {
	// log.debug("In set_recent_heat($new_value)")
    sendEvent(name:"recent_heat", value:new_value)
}

def set_recent_cool(new_value) {
	// log.debug("In set_recent_cool($new_value)")
    sendEvent(name:"recent_cool", value:new_value)
}

def set_temp_increasing(new_value) {
	// log.debug("In set_temp_increasing($new_value)")
    sendEvent(name:"temp_increasing", value:new_value)
}

def set_temp_decreasing(new_value) {
	// log.debug("In set_temp_decreasing($new_value)")
    sendEvent(name:"temp_decreasing", value:new_value)
}

def set_offline(new_value) {
	// log.debug("In set_offline($new_value)")
    sendEvent(name:"offline", value:new_value)
}
