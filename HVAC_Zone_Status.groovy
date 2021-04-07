/**
 *  HVAC Zone Status
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

 * Version 0.3 - Initial Release
 */

metadata {
	definition (name: "HVAC Zone Status", namespace: "rbaldwi3", author: "Reid Baldwin", cstHandler: true) {
		capability "Actuator" // this makes it selectable for custom actions in rule machine
        capability "Refresh" // refresh() ensures that the cumulative time attributes are up to date. Otherwise, the attributes are only updated on state changes.
	}
    attribute "current_state", "string" // One of Cooling, Heating, Fan, Vent, Dehum, Humid, Off, and Idle - Off means stop flow, Idle means blower not running
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
    attribute "humid_accept", "number"
    command "set_humid_accept", ["number"]
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
    attribute "humid_time", "number" // cumulative humidification time, in seconds, excludes time that overlaps with ventilation
    attribute "runtime_msg", "string"
    command "reset_runtime" // resets all cumulative time attributes to zero
    attribute "recent_heat", "string"
    command "set_recent_heat", ["boolean"]
    attribute "recent_cool", "string"
    command "set_recent_cool", ["boolean"]
    attribute "temp_increasing", "string"
    command "set_temp_increasing", ["boolean"]
    attribute "temp_decreasing", "string"
    command "set_temp_decreasing", ["boolean"]
    attribute "offline", "string"
    command "set_offline", ["boolean"]
    attribute "heat_output", "number" // (kbtu / hr)
    command "set_heat_output", ["number"]
    attribute "cool_output", "number" // (kbtu / hr)
    command "set_cool_output", ["number"]
    attribute "vent_output", "number" // (percent)
    command "set_vent_output", ["number"]
    attribute "vent_in_last_hour", "number" // (percent)
    command "set_vent_in_last_hour", ["number"]
    attribute "cum_cooling", "number" // cooling since last reset (btu)
    attribute "cum_heating", "number" // heating since last reset (btu)
    attribute "cum_vent", "number" // ventilation since last reset (percent hr)
    attribute "prev_cooling", "number" // previous period cooling (kbtu)
    attribute "prev_heating", "number" // previous period heating (kbtu)
    attribute "prev_vent", "number" // previous period ventilation (percent hr)
    attribute "prev_date", "date" // date and time of last reset
    command "set_reset_time", ["integer", "integer"] // time to reset each day, arguments are hours and minutes
    attribute "est_heat_load", "number" // (kbtu / hr)
    command "set_est_heat_load", ["number"]
    attribute "est_cool_load", "number" // (kbtu / hr)
    command "set_est_cool_load", ["number"]
    // attribute "net_cool", "number" // (kbtu)
    // attribute "net_heat", "number" // (kbtu)
    // command "reset_net"
    attribute "load_state", "string" // one of "Heating", "Cooling", "Idle", or "Exception"
    command "set_load_state", ["string"]
}

def installed() {
    log.debug("In installed()")
    state.tz_offset = location.timeZone.rawOffset/1000/60/60
    state.reset_hours = 23
    state.reset_minutes = 30
    schedule("0 30 23 * * ?","reset_runtime")
    schedule("0 0 0 * * ?", set_tz_offset)
    initialize()
}

def updated() {
    // log.debug("In updated()")
    unsubscribe()
    // unschedule()
    initialize()
}

def initialize() {
    log.debug("In initialize()")
    state.last_change = now()
    state.heat_demand = 0
    state.cool_demand = 0
    state.fan_demand = 0
    state.heat_accept = 0
    state.cool_accept = 0
    state.fan_accept = 0
    state.dehum_accept = 0
    state.humid_accept = 0
    set_heat_demand(0)
    set_cool_demand(0)
    set_fan_demand(0)
    set_heat_accept(0)
    set_cool_accept(0)
    set_fan_accept(0)
    set_dehum_accept(0)
    set_humid_accept(0)
    state.off_capacity = 0
    set_off_capacity(0)
    debug("initialized")
    state.cool_time = 0 
    state.heat_time = 0
    state.vent_time = 0 
    state.cum_cooling = 0 
    state.cum_heating = 0 
    state.cum_vent = 0 
    state.current_state = "Idle"
    reset_runtime()
    setState("Idle", 0)
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
    log.debug("In refresh()")
    add_interval()
    if (state.cool_time) { update_cooling(state.cool_output) }
    if (state.heat_time) { update_heating(state.heat_output) }
    if (state.vent_time) { update_vent(state.vent_output) }
    // update_net()
    log.debug("finished refresh()")
}

def reset_runtime() {
    log.debug("In reset_runtime()")
    state.cooling_time = 0
    state.heating_time = 0
    state.venting_time = 0
    state.fan_time = 0
    state.idle_time = 0
    state.dehum_time = 0
    state.humid_time = 0
    sendEvent(name:"cooling_time", value:state.cooling_time)
    sendEvent(name:"heating_time", value:state.heating_time)
    sendEvent(name:"vent_time", value:state.vent_time)
    sendEvent(name:"fan_time", value:state.fan_time)
    sendEvent(name:"idle_time", value:state.idle_time)
    sendEvent(name:"dehum_time", value:state.dehum_time)
    sendEvent(name:"humid_time", value:state.dehum_time)
    state.last_change = now()
    refresh()
    Integer prev = state.cum_cooling / 10 + 0.5
    sendEvent(name:"prev_cooling", value:(prev / 100))
    state.cum_cooling = 0
    sendEvent(name:"cum_cooling", value:0)
    prev = state.cum_heating / 10 + 0.5
    sendEvent(name:"prev_heating", value:(prev / 100))
    state.cum_heating = 0
    sendEvent(name:"cum_heating", value:0)
    prev = state.cum_vent * 100 + 0.5
    sendEvent(name:"prev_vent", value:(prev / 100))
    state.cum_vent = 0
    sendEvent(name:"cum_vent", value:0)
    Date reset_date = timeToday("$state.reset_hours:$state.reset_minutes", location.timezone)
    sendEvent(name:"prev_date", value:reset_date)
}

def add_interval() {
    log.debug("In add_interval()")
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
            state.venting_time += interval
            sendEvent(name:"vent_time", value:state.venting_time)
            break;
        case "Dehum":
            state.dehum_time += interval
            sendEvent(name:"dehum_time", value:state.dehum_time)
            break;
        case "Humid":
            state.humid_time += interval
            sendEvent(name:"humid_time", value:state.humid_time)
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
    if (state.venting_time) {
        result += "vent=" + duration_string(state.venting_time) + " "
    }
    if (state.dehum_time) {
        result += "dehum=" + duration_string(state.dehum_time) + " "
    }
    if (state.humid_time) {
        result += "humid=" + duration_string(state.humid_time) + " "
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
	log.debug("In setState($new_state, $new_flow)")
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

def set_humid_accept(new_value) {
	// log.debug("In set_humid_accept($new_value)")
    state.humid_accept = new_value
    sendEvent(name:"humid_accept", value:new_value)
}

def set_off_capacity(new_value) {
	// log.debug("In set_off_capacity($new_value)")
    state.off_capacity = new_value
    sendEvent(name:"off_capacity", value:new_value)
}

def set_recent_heat(new_value) {
	// log.debug("In set_recent_heat($new_value)")
    if (new_value) {
        sendEvent(name:"recent_heat", value:"true")
    } else {
        sendEvent(name:"recent_heat", value:"false")
    }
}

def set_recent_cool(new_value) {
	// log.debug("In set_recent_cool($new_value)")
    if (new_value) {
        sendEvent(name:"recent_cool", value:"true")
    } else {
        sendEvent(name:"recent_cool", value:"false")
    }
}

def set_temp_increasing(new_value) {
	// log.debug("In set_temp_increasing($new_value)")
    if (new_value) {
        sendEvent(name:"temp_increasing", value:"true")
    } else {
        sendEvent(name:"temp_increasing", value:"false")
    }
}

def set_temp_decreasing(new_value) {
	// log.debug("In set_temp_decreasing($new_value)")
    if (new_value) {
        sendEvent(name:"temp_decreasing", value:"true")
    } else {
        sendEvent(name:"temp_decreasing", value:"false")
    }
}

def set_offline(new_value) {
	// log.debug("In set_offline($new_value)")
    if (new_value) {
        sendEvent(name:"offline", value:"true")
    } else {
        sendEvent(name:"offline", value:"false")
    }
}

def update_heating(Number new_value) {
	log.debug("In update_heating($new_value)")
    duration = now() - state.heat_time
    state.heat_time = now()
    state.cum_heating += state.heat_output * duration / 60 / 60
    Integer rounded = state.cum_heating + 0.5
    sendEvent(name:"cum_heating", value:rounded)
    if (state.heat_output != new_value) {
        // update_net()
        state.heat_output = new_value
        rounded = new_value * 100 + 0.5
        sendEvent(name:"heat_output", value:(rounded / 100))
    }
}

def set_heat_output(new_value) {
	log.debug("In set_heat_output($new_value)")
    if (state.heat_time) {
        update_heating(new_value)
    } else {
        state.heat_time = now()
        state.heat_output = new_value
        Integer rounded = new_value * 100 + 0.5
        sendEvent(name:"heat_output", value:(rounded / 100))
    }
}

def update_cooling(Number new_value) {
	log.debug("In update_cooling($new_value)")
    duration = now() - state.cool_time
    state.cool_time = now()
    state.cum_cooling += state.cool_output * duration / 60 / 60
    Integer rounded = state.cum_cooling + 0.5
    sendEvent(name:"cum_cooling", value:rounded)
    if (state.cool_output != new_value) {
        // update_net()
        state.cool_output = new_value
        rounded = new_value * 100 + 0.5
        sendEvent(name:"cool_output", value:(rounded / 100))
    }
}

def set_cool_output(new_value) {
	log.debug("In set_cool_output($new_value)")
    if (state.cool_time) {
        update_cooling(new_value)
    } else {
        state.cool_time = now()
        state.cool_output = new_value
        Integer rounded = new_value * 100 + 0.5
        sendEvent(name:"cool_output", value:(rounded / 100))
    }
}

def update_vent(Number new_value) {
	log.debug("In update_vent($new_value)")
    duration = now() - state.vent_time
    state.vent_time = now()
    state.cum_vent += state.vent_output * duration / 1000 / 60 / 60
    Integer rounded = state.cum_vent + 0.5
    sendEvent(name:"cum_vent", value:rounded)
    state.vent_output = new_value
    rounded = new_value * 100 + 0.5
    sendEvent(name:"vent_output", value:(rounded / 100))
}

def set_vent_output(new_value) {
	log.debug("In set_vent_output($new_value)")
    if (state.vent_time) {
        update_vent(new_value)
    } else {
        state.vent_time = now()
        state.vent_output = new_value
        Integer rounded = new_value * 100 + 0.5
        sendEvent(name:"vent_output", value:(rounded / 100))
    }
}

def set_vent_in_last_hour(new_value) {
	log.debug("In set_vent_in_last_hour($new_value)")
    sendEvent(name:"vent_in_last_hour", value:new_value)
}

def set_reset_time(Integer hours, Integer minutes) {
	log.debug("In set_reset_time($hours:$minutes)")
    if (hours < 0) { return }
    if (hours > 23) { return }
    if (minutes < 0) { return }
    if (minutes > 59) { return }
    unschedule()
    schedule("0 $minutes $hours * * ?","reset_runtime")
    state.reset_hours = hours
    state.reset_minutes = minutes
}

def set_est_heat_load(new_value) {
	log.debug("In set_est_heat_load($new_value)")
    // update_net()
    state.est_heat_load = new_value
    sendEvent(name:"est_heat_load", value:new_value)
}

def set_est_cool_load(new_value) {
	log.debug("In set_est_cool_load($new_value)")
    // update_net()
    state.est_cool_load = new_value
    sendEvent(name:"est_cool_load", value:new_value)
}

/*
def update_net() {
    if (state.load_time) {
        Number duration = (now() - state.load_time) / 1000 / 60 // minutes
        log.debug "duration = $duration"
        if ((state.est_cool_load != null) && (state.cool_output != null)) {
            state.net_cool += (state.cool_output - state.est_cool_load) * duration
            Integer rounded = 100 * state.net_cool / 60 + 0.5
            sendEvent(name:"net_cool", value:rounded / 100)
        }
        if ((state.est_heat_load != null) && (state.heat_output != null)) {
            state.net_heat += (state.heat_output - state.est_heat_load) * duration
            Integer rounded = 100 * state.net_heat / 60 + 0.5
            sendEvent(name:"net_heat", value:rounded / 100)
        }
        state.load_time = now()
    }
}

def reset_net() {
    state.load_time = now()
    state.net_cool = 0.0
    sendEvent(name:"net_cool", value:0.0)
    state.net_heat = 0.0
    sendEvent(name:"net_heat", value:0.0)
}
*/

def set_load_state(String new_state) {
    state.load_state = new_state
    sendEvent(name:"load_state", value:new_state)
}
