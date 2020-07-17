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
		capability "Refresh"
		capability "Thermostat"
		capability "ThermostatOperatingState"
	}
    attribute "equipState", "string"
    attribute "ventState", "string"
    attribute "fanState", "string"
    attribute "cooling_time", "number"
    attribute "heating_time", "number"
    attribute "cooling2_time", "number"
    attribute "heating2_time", "number"
    attribute "fan_time", "number"
    attribute "idle_time", "number"
    command "second_stage_on"
    command "second_stage_off"
    command "update", ["bool", "bool", "bool"]
    command "setequipState", ["string"]
    command "setventState", ["string"]
    command "setfanState", ["string"]
    command "reset_runtime"
    command "setTemperature", ["decimal"]
}

def installed() {
    log.debug("In installed()")
    initialize()
}

def updated() {
    log.debug("In updated()")
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    log.debug("In initialize()")
    state.cooling_time = 0
    state.heating_time = 0
    state.cooling2_time = 0
    state.heating2_time = 0
    state.fan_time = 0
    state.idle_time = 0
    state.fan_state = "auto"
    state.currentState = "idle"
    state.second_stage = false
}

def second_stage_on() {
    add_interval()
    state.second_stage = true
}

def second_stage_off() {
    add_interval()
    state.second_stage = false
    unschedule()
}

def refresh() {
    log.debug("In refresh()")
    add_interval()
}

def reset_runtime() {
    log.debug("In reset_runtime()")
    state.cooling_time = 0
    state.heating_time = 0
    state.cooling2_time = 0
    state.heating2_time = 0
    state.fan_time = 0
    state.idle_time = 0
    sendEvent(name:"cooling_time", value:state.cooling_time)
    sendEvent(name:"heating_time", value:state.heating_time)
    sendEvent(name:"cooling2_time", value:state.cooling2_time)
    sendEvent(name:"heating2_time", value:state.heating2_time)
    sendEvent(name:"fan_time", value:state.fan_time)
    sendEvent(name:"idle_time", value:state.idle_time)
    state.last_change = now()
}

def add_interval() {
    log.debug("In add_interval()")
    Long interval = (now() - state.last_change) / 1000
    state.last_change = now()
    switch ("$state.currentState") {
        case "idle":
            state.idle_time += interval
            sendEvent(name:"idle_time", value:state.idle_time)
            break;
        case "fan only":
            state.fan_time += interval
            sendEvent(name:"fan_time", value:state.fan_time)
            break;
        case "heating":
            state.heating_time += interval
            if (state.second_stage) {
                state.heating2_time += interval
                sendEvent(name:"heating2_time", value:state.heating2_time)
            }
            sendEvent(name:"heating_time", value:state.heating_time)
            break;
        case "cooling":
            state.cooling_time += interval
            if (state.second_stage) {
                state.cooling2_time += interval
                sendEvent(name:"cooling2_time", value:state.cooling2_time)
            }
            sendEvent(name:"cooling_time", value:state.cooling_time)
            break;
        default:
            log.debug("State $state.currentState not recognized")
    }
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
}

String time_string(Long time) {
    Long seconds = time / 1000 - 4*60*60
    Long minutes = seconds / 60
    Long hours = minutes / 60
    Long days = hours / 12
    seconds = seconds - (minutes * 60)
    minutes = minutes - (hours * 60)
    hours = hours - (days * 12)
    if (hours == 0) {
        hours = 12
    }
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

def update(heat, cool, fan) {
	log.debug("In update $heat $cool $fan")
    add_interval()
    if ("$heat" == "true") {
    	sendEvent(name:"thermostatOperatingState", value:"heating")
        state.currentState = "heating"
    } else if ("$cool" == "true") {
    	sendEvent(name:"thermostatOperatingState", value:"cooling")
        state.currentState = "cooling"
    } else if ("$fan" == "true") {
    	sendEvent(name:"thermostatOperatingState", value:"fan only")
        state.currentState = "fan only"
        state.second_stage = false
    } else {
    	sendEvent(name:"thermostatOperatingState", value:"idle")
        state.currentState = "idle"
        state.second_stage = false
    }
}

def setequipState(new_equip_state) {
    log.debug("In setequipState($new_equip_state)")
    add_interval()
    switch ("$new_equip_state") {
        case "Idle":
        case "IdleH":
        case "PauseH":
        case "IdleC":
        case "PauseC":
            switch ("$state.fan_state") {
                case "on":
                    log.debug("fan state is on")
    	            sendEvent(name:"thermostatOperatingState", value:"fan only")
                    state.currentState = "fan only"
                    break
                case "auto":
                    log.debug("fan state is auto")
    	            sendEvent(name:"thermostatOperatingState", value:"idle")
                    state.currentState = "idle"
                    break
                default:
                    log.debug("fan state is not recognized")
            }
            state.second_stage = false
            break
        case "Heating":
        case "HeatingL":
    	    sendEvent(name:"thermostatOperatingState", value:"heating")
            state.currentState = "heating"
            break
        case "Cooling":
        case "CoolingL":
    	    sendEvent(name:"thermostatOperatingState", value:"cooling")
            state.currentState = "cooling"
            break
    }
    String time = time_string(now())
    sendEvent(name:"equipState", value:"$new_equip_state since $time")
}

def setventState(String new_state) {
    log.debug("In setventState($new_state)")
    add_interval()
    switch ("$new_state") {
        case "Waiting":
    	    sendEvent(name:"thermostatOperatingState", value:"idle")
            state.currentState = "idle"
            break
    }
    String time = time_string(now())
    sendEvent(name:"ventState", value:"$new_state since $time")
}

def setfanState(String new_state) {
    log.debug("In setfanState($new_state)")
    add_interval()
    switch ("$new_state") {
        case "On_for_vent":
        case "On_by_request":
            fanOn()
            switch ("$state.currentState") {
                case "idle":
    	            sendEvent(name:"thermostatOperatingState", value:"fan_only")
                    state.currentState = "fan only"
                    break
            }
            break
        default:
            fanAuto()
    }
    sendEvent(name:"fanState", value:"$new_state")
}

def auto() {
    sendEvent(name:"thermostatMode", value:"auto")
}

def cool() {
    sendEvent(name:"thermostatMode", value:"cool")
}

def emergencyHeat() {
    sendEvent(name:"thermostatMode", value:"emergency heat")
}

def fanAuto() {
    sendEvent(name:"thermostatFanMode", value:"auto")
    state.fan_state = "auto"
}

def fanCirculate() {
    sendEvent(name:"thermostatFanMode", value:"circulate")
}

def fanOn() {
    sendEvent(name:"thermostatFanMode", value:"on")
    state.fan_state = "on"
}

def heat() {
    sendEvent(name:"thermostatMode", value:"heat")
}

def off() {
    sendEvent(name:"thermostatMode", value:"off")
}

def setTemperature(temperature) {
    sendEvent(name:"temperature", value:temperature)
}

def setCoolingSetpoint(temperature) {
    // temperature required (NUMBER) - Cooling setpoint in degrees
    sendEvent(name:"coolingSetpoint", value:temperature)
}

def setHeatingSetpoint(temperature) {
    // temperature required (NUMBER) - Heating setpoint in degrees
    sendEvent(name:"heatingSetpoint", value:temperature)
}

def setSchedule(JSON_OBJECT) {
    // JSON_OBJECT (JSON_OBJECT) - JSON_OBJECT
}

def setThermostatFanMode(fanmode) {
    // fanmode required (ENUM) - Fan mode to set
}

def setThermostatMode(thermostatmode) {
    // thermostatmode required (ENUM) - Thermostat mode to set
}
