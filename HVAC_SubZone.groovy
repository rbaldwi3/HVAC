/**
 *  HVAC SubZone
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
 * version 0.1 - Initial Release
 * version 0.2 - Logic changed to better support Indirect thermostats (introduced concept of dump zones so subzone logic no longer requires zone temperature and setpoint)
 *             - Misc. robustness improvements
 *             - Added separate control of dump zone and ventilation selection based on occupancy
 */

definition(
    name: "HVAC SubZone",
    namespace: "rbaldwi3",
    author: "Reid Baldwin",
    description: "This app controls HVAC zone dampers and HVAC equipment in response to multiple thermostats.  This is the child app for each subzone",
    category: "General",
    parent: "rbaldwi3:HVAC Zone",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "pageOne", nextPage: "pageTwo", uninstall: true) {
        section ("Zone Data") {
            label required: true, multiple: false
            input "cfm", "number", required: true, title: "Maximum airflow for Zone", range: "100 . . 1000"
            input "closed_pos", "number", required: true, title: "Percent Open when in Off position", default: 0, range: "0 . . 100"
            input "zone", "capability.switch", required: true, title: "Selection of Zone" // future feature - percentage control as opposed to on/off
            input "normally_open", "bool", required: true, title: "Normally Open (i.e. Switch On = Zone Inactive, Switch Off = Zone Selected)", default: false
        }
    }
    page(name: "pageTwo", title: "Control Rules", install: true, uninstall: true)
}

def pageTwo() {
    dynamicPage(name: "pageTwo") {
        if (stat) {
            section ("Temperature and Set point - Specify either a thermostat or a temperatures sensor and offsets from the parent zone setpoints") {
                input "stat", "capability.thermostat", required: true, title: "Thermostat", submitOnChange: true
                input "temp_sensor", "capability.temperatureMeasurement", required: false, title: "Temperature Sensor", submitOnChange: true
                input "heat_offset", "number", required: false, title: "Heating Setpoint Offset from Parent Zone", default: 0, range: "-10 . . 10", submitOnChange: true
                input "cool_offset", "number", required: false, title: "Cooling Setpoint Offset from Parent Zone", default: 0, range: "-10 . . 10", submitOnChange: true
            }
        } else {
            section ("Temperature and Set point - Specify either a thermostat or a temperatures sensor and offsets from the parent zone setpoints") {
                input "stat", "capability.thermostat", required: false, title: "Thermostat", submitOnChange: true
                input "temp_sensor", "capability.temperatureMeasurement", required: true, title: "Temperature Sensor", submitOnChange: true
                input "heat_offset", "number", required: true, title: "Heating Setpoint Offset from Parent Zone", default: 0, range: "-10 . . 10", submitOnChange: true
                input "cool_offset", "number", required: true, title: "Cooling Setpoint Offset from Parent Zone", default: 0, range: "-10 . . 10", submitOnChange: true
            }
        }
        section ("Control Rules") {
            if (stat) {
                input (name:"heat_join", type: "enum", required: true, title: "Select during parent zone heating call", options: ["Only during subzone heating call",
                                                                                                                            "When subzone temp is less than setpoint",
                                                                                                                            "When subzone temp is no more than 1 degree above setpoint"])
                input (name:"cool_join", type: "enum", required: true, title: "Select during parent zone cooling call", options: ["Only during subzone cooling call",
                                                                                                                            "When subzone temp is greater than setpoint",
                                                                                                                            "When subzone temp is no more than 1 degree below setpoint"])
            } else {
                input (name:"heat_join", type: "enum", required: true, title: "Select during parent zone heating call", options: ["When subzone temp is less than setpoint",
                                                                                                                            "When subzone temp is no more than 1 degree above setpoint"])
                input (name:"cool_join", type: "enum", required: true, title: "Select during parent zone cooling call", options: ["When subzone temp is greater than setpoint",
                                                                                                                            "When subzone temp is no more than 1 degree below setpoint"])
            }
            input "fan_switch", "capability.switch", required: false,
                title: "Optional switch to select whether subzone is selected during parent fan only call (including ventilation) - not selected by default"
            // time interval for polling in case any signals missed
            input(name:"input_refresh_interval", type:"enum", required: true, title: "Input refresh", options: ["None","Every 5 minutes","Every 10 minutes",
                                                                                                                 "Every 15 minutes","Every 30 minutes"]) 
        }
    }
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    initialize()
}

def initialize() {
    log.debug("In SubZone Initialize()")
    // Preprocessing of airflow per zone
    atomicState.off_capacity = settings.cfm * settings.closed_pos / 100
    atomicState.on_capacity = settings.cfm - atomicState.off_capacity
    // Subscribe to state changes
    if (stat) {
        subscribe(stat, "thermostatOperatingState.idle", idleHandler)
        subscribe(stat, "thermostatOperatingState.heating", heatHandler)
        subscribe(stat, "thermostatOperatingState.cooling", coolHandler)
	    subscribe(stat, "temperature", tempHandler)
        subscribe(stat, "heatingSetpoint", heat_setHandler)
        subscribe(stat, "coolingSetpoint", cool_setHandler)
        def levelstate = stat.currentState("temperature")
        atomicState.temperature = levelstate.value as BigDecimal
        levelstate = stat.currentState("heatingSetpoint")
        atomicState.heat_setpoint = levelstate.value as BigDecimal
        levelstate = stat.currentState("coolingSetpoint")
        atomicState.cool_setpoint = levelstate.value as BigDecimal
    } else if (temp_sensor && parent.full_thermostat()) {
	    subscribe(temp_sensor, "temperature", tempHandler)
        def levelstate = temp_sensor.currentState("temperature")
        atomicState.temperature = levelstate.value as BigDecimal
        atomicState.heat_setpoint = parent.get_heat_setpoint() + settings.heat_offset
        atomicState.cool_setpoint = parent.get_cool_setpoint() + settings.cool_offset
    } else {
        log.error("Neither Thermostat nor Temperature Sensor specified")
        atomicState.heat_setpoint = 70.0
        atomicState.cool_setpoint = 80.0
        atomicState.temperature = 75.0
    }
    if (fan_switch) {
        def currentvalue = fan_switch.currentValue("switch")
        switch ("$currentvalue") {
            case "on":
                atomicState.fan_switch = true
                break;
            case "off":
                atomicState.fan_switch = false
                break;
        }
	    subscribe(fan_switch, "switch", fan_switchHandler)
    } else {
        atomicState.fan_switch = false
    }
    switch ("$input_refresh_interval") {
        case "None":
            break
        case "Every 5 minutes":
            runEvery5Minutes(refresh_inputs)
            break
        case "Every 10 minutes":
            runEvery10Minutes(refresh_inputs)
            break
        case "Every 15 minutes":
            runEvery15Minutes(refresh_inputs)
            break
        case "Every 30 minutes":
            runEvery30Minutes(refresh_inputs)
            break
    }
    parent.child_updated()
}

def refresh_inputs() {
    log.debug("In refresh_inputs()")
    if (!stat) {
        return
    }
    if (stat.hasCapability("Refresh")) {
        stat.refresh()
    }
    def value = stat.currentValue("thermostatOperatingState")
    switch ("$value") {
        case "heating":
            if (atomicState.heat_demand == 0) {
                stateHandler()
            }
            break
        case "cooling":
            if (atomicState.cool_demand == 0) {
                stateHandler()
            }
            break
        case "fan only":
            if (atomicState.fan_demand == 0) {
                stateHandler()
            }
            break
        default:
            if ((atomicState.heat_demand > 0) || (atomicState.cool_demand > 0)) {
                stateHandler()
            }
            state = stat.currentValue("thermostatFanMode")
            switch ("$state.value") {
                case "on":
                case " on":
                if (atomicState.fan_demand == 0) {
                    stateHandler()
                }
	    }
    }
    def levelstate = stat.currentState("heatingSetpoint")
    new_setpoint = levelstate.value as BigDecimal
    if (new_setpoint != atomicState.heat_setpoint) {
        heat_setHandler()
    }
    levelstate = stat.currentState("coolingSetpoint")
    new_setpoint = levelstate.value as BigDecimal
    if (new_setpoint != atomicState.cool_setpoint) {
        cool_setHandler()
    }
}

def get_heat_demand(parent_mode) {
    log.debug("In SubZone get_heat_demand($parent_mode)")
    switch (parent_mode) {
        case "heating":
            switch ("$heat_join") {
                case "Only during subzone heating call":
                    def state = stat.currentValue("thermostatOperatingState")
                    switch ("$state") {
                        case "heating":
                            return atomicState.on_capacity
                        default:
                            return 0
                    }
                case "When subzone temp is less than setpoint":
                    if (atomicState.temperature <= atomicState.heat_setpoint) {
                        return atomicState.on_capacity
                    } else {
                       return 0
                    }
                case "When subzone temp is no more than 1 degree above setpoint":
                    if (atomicState.temperature - 1 <= atomicState.heat_setpoint) {
                        return atomicState.on_capacity
                    } else {
                       return 0
                    }
            }
            break;
        case "accepting_heat":
            if (stat) {
                def state = stat.currentValue("thermostatOperatingState")
                switch ("$state") {
                    case "heating":
                        return atomicState.on_capacity
                    default:
                        return 0
                }
            }
            break;
    }
    return 0;
}

def get_cool_demand(parent_mode) {
    log.debug("In SubZone get_cool_demand($parent_mode)")
    switch (parent_mode) {
        case "cooling":
            switch ("$cool_join") {
                case "Only during subzone cooling call":
                    def state = stat.currentValue("thermostatOperatingState")
                    switch ("$state") {
                        case "cooling":
                            return atomicState.on_capacity
                        default:
                            return 0
                    }
                case "When subzone temp is greater than setpoint":
                    if (atomicState.temperature >= atomicState.cool_setpoint) {
                        return atomicState.on_capacity
                    } else {
                       return 0
                    }
                case "When subzone temp is no more than 1 degree below setpoint":
                    if (atomicState.temperature + 1 >= atomicState.cool_setpoint) {
                        return atomicState.on_capacity
                    } else {
                       return 0
                    }
            }
            break;
        case "accepting_cool":
            if (stat) {
                def state = stat.currentValue("thermostatOperatingState")
                switch ("$state") {
                    case "cooling":
                        return atomicState.on_capacity
                    default:
                        return 0
                }
            }
            break;
    }
    return 0;
}

def get_vent_demand() {
    log.debug("In SubZone get_vent_demand()")
    if (atomicState.fan_switch) {
        return atomicState.on_capacity
    } else {
        return 0;
    }
}

def idleHandler(evt) {
    log.debug("In SubZone idleHandler()")
    parent.update_demand()
}

def heatHandler(evt) {
    log.debug("In SubZone heatHandler()")
    parent.update_demand()
}

def coolHandler(evt) {
    log.debug("In SubZone coolHandler()")
    parent.update_demand()
}

def tempHandler(evt) {
    log.debug("In SubZone tempHandler()")
    if (stat) {
        def levelstate = stat.currentState("temperature")
        new_temp = levelstate.value as BigDecimal
    } else {
        def levelstate = temp_sensor.currentState("temperature")
        new_temp = levelstate.value as BigDecimal
    }
    changed = false;
    switch ("$heat_join") {
        case "Only during subzone heating call":
            break
        case "When subzone temp is less than setpoint":
            if ((atomicState.temperature <= atomicState.heat_setpoint) != (new_temp <= atomicState.heat_setpoint)) {
                changed = true
            }
            break
        case "When subzone temp is no more than 1 degree above setpoint":
            if ((atomicState.temperature -1 <= atomicState.heat_setpoint) != (new_temp -1 <= atomicState.heat_setpoint)) {
                changed = true
            }
            break
    }
    switch ("$cool_join") {
        case "Only during subzone cooling call":
            break
        case "When subzone temp is greater than setpoint":
            if ((atomicState.temperature >= atomicState.cool_setpoint) != (new_temp >= atomicState.cool_setpoint)) {
                changed = true
            }
            break
        case "When subzone temp is no more than 1 degree below setpoint":
            if ((atomicState.temperature + 1 >= atomicState.cool_setpoint) != (new_temp + 1 >= atomicState.cool_setpoint)) {
                changed = true
            }
            break
    }
    atomicState.temperature = new_temp
    if (changed) {
        parent.update_demand()
    }
}

def heat_setpoint_updated(new_setpoint) {
    log.debug("In SubZone heat_setpoint_updated($new_setpoint)")
    changed = false;
    switch ("$heat_join") {
        case "Only during subzone heating call":
            break
        case "When subzone temp is less than setpoint":
            if ((atomicState.temperature <= new_setpoint) != (atomicState.temperature <= atomicState.heat_setpoint)) {
                changed = true
            }
            break
        case "When subzone temp is no more than 1 degree above setpoint":
            if ((atomicState.temperature -1 <= new_setpoint) != (atomicState.temperature -1 <= atomicState.heat_setpoint)) {
                changed = true
            }
            break
    }
    atomicState.heat_setpoint = new_setpoint
    if (changed) {
        parent.update_demand()
    }
}

def parent_heat_setpoint_updated(parent_setpoint) {
    log.debug("In SubZone parent_heat_setpoint_updated($parent_setpoint)")
    if (!stat) {
        new_setpoint = parent_setpoint as BigDecimal
        new_setpoint += settings.heat_offset
        heat_setpoint_updated(new_setpoint)
    }
}

def fan_switchHandler(evt) {
    log.debug("In SubZone fan_switchHandler()")
    def currentvalue = fan_switch.currentValue("switch")
    switch ("$currentvalue") {
        case "on":
            atomicState.fan_switch = true
            break;
        case "off":
            atomicState.fan_switch = false
            break;
    }
    parent.update_demand()
}

def heat_setHandler(evt) {
    log.debug("In SubZone heat_setHandler()")
    def levelstate = stat.currentState("heatingSetpoint")
    new_setpoint = levelstate.value as BigDecimal
    heat_setpoint_updated(new_setpoint)
}

def cool_setpoint_updated(new_setpoint) {
    log.debug("In SubZone cool_setpoint_updated($new_setpoint)")
    changed = false;
    switch ("$cool_join") {
        case "Only during subzone cooling call":
            break
        case "When subzone temp is greater than setpoint":
            if ((atomicState.temperature >= new_setpoint) != (atomicState.temperature >= atomicState.cool_setpoint)) {
                changed = true
            }
            break
        case "When subzone temp is no more than 1 degree below setpoint":
            if ((atomicState.temperature + 1 >= new_setpoint) != (atomicState.temperature + 1 <= atomicState.cool_setpoint)) {
                changed = true
            }
            break
    }
    atomicState.cool_setpoint = new_setpoint
    if (changed) {
        parent.update_demand()
    }
}

def parent_cool_setpoint_updated(parent_setpoint) {
    log.debug("In SubZone parent_cool_setpoint_updated($parent_setpoint)")
    if (!stat) {
        new_setpoint = parent_setpoint as BigDecimal
        new_setpoint += settings.cool_offset
        cool_setpoint_updated(new_setpoint)
    }
}

def cool_setHandler(evt) {
    log.debug("In SubZone cool_setHandler()")
    def levelstate = stat.currentState("coolingSetpoint")
    new_setpoint = levelstate.value as BigDecimal
    cool_setpoint_updated(new_setpoint)
}

def get_off_capacity() {
    return atomicState.off_capacity
}

def get_on_capacity() {
    return atomicState.on_capacity
}

def get_fan_switch() {
    return atomicState.fan_switch
}

def turn_on() {
    log.debug("In SubZone turn_on()")
    if (normally_open) {
        zone.off()
    } else {
        zone.on()
    }
}

def turn_off() {
    log.debug("In SubZone turn_off()")
    if (normally_open) {
        zone.on()
    } else {
        zone.off()
    }
}

def handle_overpressure() {
    // log.debug("In SubZone handle_overpressure()")
    def currentvalue = zone.currentValue("switch")
    switch ("$currentvalue") {
        case "on":
            atomicState.on_capacity *= 0.9
            break;
        case "off":
            atomicState.off_capacity *= 0.9
            break;
    }
}
