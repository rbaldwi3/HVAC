/**
 *  HVAC Zone
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
 * version 0.2 -Logic changed to better support Indirect thermostats (introduced concept of dump zones so subzone logic no longer requires zone temperature and setpoint)
 *             - Misc. robustness improvements
 *             - Added separate control of dump zone and ventilation selection based on occupancy
 *             - Added ventilation selection control based on modes
 */

definition(
    name: "HVAC Zone",
    namespace: "rbaldwi3",
    author: "Reid Baldwin",
    description: "This app controls HVAC zone dampers and HVAC equipment in response to multiple thermostats.  This is the child app for each zone",
    category: "General",
    parent: "rbaldwi3:HVAC Zoning",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)


preferences {
    page(name: "pageOne", nextPage: "pageTwo", uninstall: true) {
        section ("Zone Data") {
            label required: true, multiple: false
            input "stat", "capability.thermostat", required: true, title: "Thermostat"
            input "cfm", "number", required: true, title: "Maximum airflow for Zone", range: "200 . . 3000"
            input "closed_pos", "number", required: true, title: "Percent Open when in Off position", default: 0, range: "0 . . 100"
            input "zone", "capability.switch", required: true, title: "Switch for selection of Zone"
            input "normally_open", "bool", required: true, title: "Normally Open (i.e. Switch On = Zone Inactive, Switch Off = Zone Selected)", default: true
            input "occupied", "capability.switch", required: false, title: "Optional switch to indicate zone is occupied (On) or unoccupied (Off) - to be set externally"
        }
    }
    page(name: "pageTwo", title: "Control Rules", install: true, uninstall: true)
}

def pageTwo() {
    dynamicPage(name: "pageTwo") {
        section {
            app(name: "subzone", appName: "HVAC SubZone", namespace: "rbaldwi3", title: "Create New Sub-Zone", multiple: true, submitOnChange: true)
        }
        if (occupied) {
            section ("Control Rules when Zone is Occupied") {
                if (full_thermostat()) {
                    input (name:"heat_dump_occupied", type: "enum", required: true, title: "Under what conditions should this zone accept heating without a heat call when occupied?",
                           options: ["Never", "Anytime", "If a heat call happened within the last hour",
                                     "When temp is less than setpoint", "When temp is no more than 1 degree above setpoint"])
                    input (name:"cool_dump_occupied", type: "enum", required: true, title: "Under what conditions should this zone accept cooling without a cool call when occupied?",
                           options: ["Never", "Anytime", "If a cooling call happened within the last hour",
                                     "When temp is greater than setpoint", "When temp is no more than 1 degree below setpoint"])
                } else {
                    input (name:"heat_dump_occupied", type: "enum", required: true, title: "Under what conditions should this zone accept heating without a heat call when occupied?",
                           options: ["Never", "Anytime", "If a heat call happened within the last hour"])
                    input (name:"cool_dump_occupied", type: "enum", required: true, title: "Under what conditions should this zone accept cooling without a cool call when occupied?",
                           options: ["Never", "Anytime", "If a cooling call happened within the last hour"])
                }
                input (name:"vent_modes_occupied", type: "mode", required: false, multiple: true, title: "In what modes should zone receive ventilation when occupied?")
            }
            section ("Control Rules when Zone is Not Occupied") {
                if (full_thermostat()) {
                    input (name:"heat_dump_vacant", type: "enum", required: true, title: "Under what conditions should this zone accept heating without a heat call when not occupied?",
                           options: ["Never", "Anytime", "If a heat call happened within the last hour",
                                     "When temp is less than setpoint", "When temp is no more than 1 degree above setpoint"])
                    input (name:"cool_dump_vacant", type: "enum", required: true, title: "Under what conditions should this zone accept cooling without a cool call when not occupied?",
                           options: ["Never", "Anytime", "If a cooling call happened within the last hour",
                                     "When temp is greater than setpoint", "When temp is no more than 1 degree below setpoint"])
                } else {
                    input (name:"heat_dump_vacant", type: "enum", required: true, title: "Under what conditions should this zone accept heating without a heat call when not occupied?",
                           options: ["Never", "Anytime", "If a heat call happened within the last hour"])
                    input (name:"cool_dump_vacant", type: "enum", required: true, title: "Under what conditions should this zone accept cooling without a cool call when not occupied?",
                           options: ["Never", "Anytime", "If a cooling call happened within the last hour"])
                }
                input (name:"vent_modes_vacant", type: "mode", required: false, multiple: true, title: "In what modes should zone receive ventilation when not occupied?")
            }
        } else {
            // when there is no occupied sensor, use the rules for occupied all of the time
            section ("Control Rules") {
                if (full_thermostat()) {
                    input (name:"heat_dump_occupied", type: "enum", required: true, title: "Under what conditions should this zone accept heating without a heat call?",
                           options: ["Never", "Anytime", "If a heat call happened within the last hour",
                                     "When temp is less than setpoint", "When temp is no more than 1 degree above setpoint"])
                    input (name:"cool_dump_occupied", type: "enum", required: true, title: "Under what conditions should this zone accept cooling without a cool call?",
                           options: ["Never", "Anytime", "If a cooling call happened within the last hour",
                                     "When temp is greater than setpoint", "When temp is no more than 1 degree below setpoint"])
                } else {
                    input (name:"heat_dump_occupied", type: "enum", required: true, title: "Under what conditions should this zone accept heating without a heat call?",
                           options: ["Never", "Anytime", "If a heat call happened within the last hour"])
                    input (name:"cool_dump_occupied", type: "enum", required: true, title: "Under what conditions should this zone accept cooling without a cool call?",
                           options: ["Never", "Anytime", "If a cooling call happened within the last hour"])
                }
                input (name:"vent_modes_occupied", type: "mode", required: false, multiple: true, title: "In what modes should zone receive ventilation?")
            }
        }
        section ("Refresh") {
            // time interval for polling in case any signals missed
            input(name:"output_refresh_interval", type:"enum", required: true, title: "Output refresh", options: ["None","Every 5 minutes","Every 10 minutes",
                                                                                                                 "Every 15 minutes","Every 30 minutes"]) 
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
    // Preprocessing of airflow per zone
    child_updated()
    atomicState.prev_state = "idle"
    atomicState.cool_in_last_hour = false
    atomicState.heat_in_last_hour = false
    update_state()
    subscribe(stat, "thermostatOperatingState", stateHandler)
    atomicState.heat_setpoint = 70
    atomicState.temperature = 75
    atomicState.cool_setpoint = 80
    if (full_thermostat()) {
        update_temp()
    	subscribe(stat, "temperature", tempHandler)
        update_heat_setpoint()
        subscribe(stat, "heatingSetpoint", heat_setHandler)
        update_cool_setpoint()
        subscribe(stat, "coolingSetpoint", cool_setHandler)
    }
    update_occupied()
    if (occupied) {
        subscribe(occupied, "switch", occupiedHandler)
    }
    def value = zone.currentValue("switch")
    switch ("$value") {
        case "on":
            if (normally_open) {
                atomicState.current_mode = "unselected"
            } else {
                atomicState.current_mode = parent.get_equipment_status()
            }
            break;
        case "off":
            if (normally_open) {
                atomicState.current_mode = parent.get_equipment_status()
            } else {
                atomicState.current_mode = "unselected"
            }
            break;
    }
    atomicState.cool_demand = 0
    atomicState.heat_demand = 0
    atomicState.cool_accept = 0
    atomicState.heat_accept = 0
    atomicState.fan_demand = 0
    atomicState.vent_demand = 0
    update_demand()
    update_vent_demand()
    subscribe(location, "mode", modeHandler)
    switch ("$output_refresh_interval") {
        case "None":
            break
        case "Every 5 minutes":
            runEvery5Minutes(refresh_outputs)
            break
        case "Every 10 minutes":
            runEvery10Minutes(refresh_outputs)
            break
        case "Every 15 minutes":
            runEvery15Minutes(refresh_outputs)
            break
        case "Every 30 minutes":
            runEvery30Minutes(refresh_outputs)
            break
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
}

def refresh_outputs() {
    log.debug("In refresh_outputs(" + $atomicState.current_mode + ")")
    switch ("$atomicState.current_mode") {
        case "unselected":
            turn_off()
            break
        case "idle":
            turn_idle()
            break
        default:
            turn_on("$atomicState.current_mode")
    }
}

def refresh_inputs() {
    log.debug("In refresh_inputs()")
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
        case "idle":
            if ((atomicState.heat_demand > 0) || (atomicState.cool_demand > 0)) {
                stateHandler()
            }
            if (stat.hasAttribute("thermostatFanMode")) {
                fan_state = stat.currentValue("thermostatFanMode")
                switch ("$fan_state.value") {
                    case "on":
                    case " on":
                    if (atomicState.fan_demand == 0) {
                        stateHandler()
                    }
                }
            }
            break
    }
    if (stat.hasAttribute("heatingSetpoint")) {
        def levelstate = stat.currentState("heatingSetpoint")
        new_setpoint = levelstate.value as BigDecimal
        if (new_setpoint != atomicState.heat_setpoint) {
            heat_setHandler()
        }
    }
    if (stat.hasAttribute("coolingSetpoint")) {
        levelstate = stat.currentState("coolingSetpoint")
        new_setpoint = levelstate.value as BigDecimal
        if (new_setpoint != atomicState.cool_setpoint) {
            cool_setHandler()
        }
    }
}

def child_updated() {
    // log.debug("In Zone child_updated()")
    // Preprocessing of airflow per zone
    atomicState.off_capacity = settings.cfm * settings.closed_pos / 100
    atomicState.on_capacity = settings.cfm - atomicState.off_capacity
    def subzones = getChildApps()
    subzones.each { sz ->
        atomicState.off_capacity += sz.get_off_capacity()
    }
    parent.child_updated()
}

// This function updates atomicState.cool_demand, atomicState.heat_demand, atomicState.fan_demand, 
// atomicstate.cool_accept, and atomicstate.heat_accept.
// If any of these are changed, the main Zoning app is asked to re-evaluate its state

def update_demand() {
    log.debug("In Zone update_demand()")
    heat_demand = 0
    cool_demand = 0
    heat_accept = 0
    cool_accept = 0
    fan_demand = 0
    def subzones = getChildApps()
    def state = stat.currentValue("thermostatOperatingState")
    switch ("$state.value") {
        case "heating":
            // the heating demand is the capacity of this main zone plus the capacities of any subzones that would join this heating call
            heat_demand = atomicState.on_capacity
            subzones.each { sz ->
                heat_demand += sz.get_heat_demand("heating")
            }
            heat_accept = heat_demand
            break
        case "cooling":
            // the cooling demand is the capacity of this main zone plus the capacities of any subzones that would join this cooling call
            cool_demand = atomicState.on_capacity
            subzones.each { sz ->
                cool_demand += sz.get_cool_demand("heating")
            }
            cool_accept = cool_demand
            break
        case "fan only":
            // when idle or fan only command, determine if any subzones would trigger heating or cooling calls for this main zone
            fan_demand = atomicState.on_capacity
        case "idle":
            if (full_thermostat()) {
                state = stat.currentValue("thermostatFanMode")
                switch ("$state.value") {
                    case "on":
                    case " on":
                        // some thermostats do not change thermostatOperatingState to "fan only" when they should
                        fan_demand = atomicState.on_capacity
                        break
                }
            }
            if (fan_demand > 0) {
                subzones.each { sz ->
                    fan_demand += sz.get_vent_demand()
                }
            }
            // Decide whether this zone is a dump zone for heating or cooling
            String cool_rule
            String heat_rule
            if (atomicState.occupied_now) {
                cool_rule = "$cool_dump_occupied"
                heat_rule = "$heat_dump_occupied"
            } else {
                cool_rule = "$cool_dump_vacant"
                heat_rule = "$heat_dump_vacant"
            }
            switch ("$heat_rule") {
                case "Never":
                    break
                case "Anytime":
                    heat_accept = atomicState.on_capacity
                    break
                case "If a heat call happened within the last hour":
                    // if (now() - atomicState.last_heat_stop > 60*60*1000) {
                    if (atomicState.heat_in_last_hour) {
                        heat_accept = atomicState.on_capacity
                    }
                    break
                case "When temp is less than setpoint":
                    if (atomicState.temperature < atomicState.heat_setpoint) {
                        heat_accept = atomicState.on_capacity
                    }
                    break
                case "When temp is no more than 1 degree above setpoint":
                    if (atomicState.temperature < atomicState.heat_setpoint + 1) {
                        heat_accept = atomicState.on_capacity
                    }
                    break
                default:
                    log.debug("Unrecognized heat rule $heat_rule")
            }
            if (heat_accept > 0) {
                subzones.each { sz ->
                    heat_demand += sz.get_heat_demand("accepting_heat")
                }
                if (heat_demand > 0) {
                    heat_demand = atomicState.on_capacity
                    subzones.each { sz ->
                        heat_demand += sz.get_heat_demand("heating")
                    }
                    heat_accept = heat_demand
                }
            }
            switch ("$cool_rule") {
                case "Never":
                    break
                case "Anytime":
                    cool_accept = atomicState.on_capacity
                    break
                case "If a cooling call happened within the last hour":
                    // if (now() - atomicState.last_cool_stop > 60*60*1000) {
                    if (atomicState.cool_in_last_hour) {
                        cool_accept = atomicState.on_capacity
                    }
                    break
                case "When temp is greater than setpoint":
                    if (atomicState.temperature > atomicState.cool_setpoint) {
                        cool_accept = atomicState.on_capacity
                    }
                    break
                case "When temp is no more than 1 degree below setpoint":
                    if (atomicState.temperature > atomicState.heat_setpoint - 1) {
                        cool_accept = atomicState.on_capacity
                    }
                    break
                default:
                    log.debug("Unrecognized heat rule $heat_rule")
            }
            if (cool_accept > 0) {
                subzones.each { sz ->
                    cool_demand += sz.get_cool_demand("accepting_cool")
                }
                if (cool_demand > 0) {
                    cool_demand = atomicState.on_capacity
                    subzones.each { sz ->
                        cool_demand += sz.get_cool_demand("cooling")
                    }
                    cool_accept = cool_demand
                }
            }
            break
    }
    log.debug("heat_demand = $heat_demand, heat_accept = $heat_accept, cool_demand = $cool_demand, cool_accept = $cool_accept, fan_demand = $fan_demand")
    if ((atomicState.heat_demand != heat_demand) || (atomicState.cool_demand != cool_demand) || (atomicState.fan_demand != fan_demand) ||
        (atomicState.heat_accept != heat_accept) || (atomicState.cool_accept != cool_accept)) {
        atomicState.heat_demand = heat_demand
        atomicState.cool_demand = cool_demand
        atomicState.heat_accept = heat_accept
        atomicState.cool_accept = cool_accept
        atomicState.fan_demand = fan_demand
        parent.zone_call_changed()
    }
}

def update_vent_demand() {
    log.debug("In Zone update_vent_demand()")
    // vent_demand is the capacity that would be accepted if the zone control app decides to run ventilation.  It is not a call to the zone control.
    def currMode = location.mode
    vent_demand = 0
    if (atomicState.occupied_now) {
        if (vent_modes_occupied) {
            vent_modes_occupied.each { md ->
                if ("$currMode" == "$md") {
                    vent_demand = atomicState.on_capacity
                }
            }
        }
    } else {
        if (vent_modes_vacant) {
            vent_modes_vacant.each { md ->
                if ("$currMode" == "$md") {
                    vent_demand = atomicState.on_capacity
                }
            }
        }
    }
    if (vent_demand > 0) {
        def subzones = getChildApps()
        subzones.each { sz ->
            vent_demand += sz.get_vent_demand()
        }
    }
    if (atomicState.vent_demand != vent_demand) {
        atomicState.vent_demand = vent_demand
        parent.zone_call_changed()
    }
}
    
def no_cool_for_an_hour() {
    log.debug("In no_cool_for_an_hour()")
    atomicState.cool_in_last_hour = false
    update_demand()
}

def no_heat_for_an_hour() {
    log.debug("In no_heat_for_an_hour()")
    atomicState.heat_in_last_hour = false
    update_demand()
}

def update_state() {
    log.debug("In update_state()")
    def new_state = stat.currentValue("thermostatOperatingState")
    switch ("$atomicState.prev_state") {
        case "heating":
            atomicState.heat_in_last_hour = true;
            switch ("$new_state") {
                case "cooling":
                    atomicState.cool_in_last_hour = true;
                    unschedule("no_cool_for_an_hour")
                case "fan only":
                case "idle":
                    runIn(3600, "no_heat_for_an_hour", [misfire: "ignore"])
                    break
            }
            break
        case "cooling":
            atomicState.cool_in_last_hour = true;
            switch ("$new_state") {
                case "heating":
                    atomicState.heat_in_last_hour = true;
                    unschedule("no_heat_for_an_hour")
                case "fan only":
                case "idle":
                    runIn(3600, "no_cool_for_an_hour", [misfire: "ignore"])
                    break
            }
            break
        case "fan only":
        case "idle":
            switch ("$new_state") {
                case "cooling":
                    atomicState.cool_in_last_hour = true;
                    unschedule("no_cool_for_an_hour")
                    break
                case "heating":
                    atomicState.heat_in_last_hour = true;
                    unschedule("no_heat_for_an_hour")
                    break
            }
            break
    }
    atomicState.prev_state = new_state
}

def stateHandler(evt=NULL) {
    log.debug("In Zone stateHandler()")
    update_state()
    runIn(5, "update_demand", [misfire: "ignore"]) // this does the work of calculating what should be demanded of the zone control app
    // delaying ensures rapid changes only create one call and also ensures that wired_tstatHandler() gets called before zone_call_changed() in parent.
}

Boolean update_temp() {
    // log.debug("In update_temp()")
    if (stat.hasAttribute("temperature")) {
        def levelstate = stat.currentState("temperature")
        new_temp = levelstate.value as BigDecimal
        // Note - update demand is called at half-degree offsets from the actual thresholds to avoid rapid cycling due to small temperature variations
        changed = false;
        if ((new_temp + 0.5 <= atomicState.heat_setpoint) != (atomicState.temperature + 0.5 <= atomicState.heat_setpoint)) {
            changed = true
        }
        if ((new_temp - 0.5 <= atomicState.heat_setpoint) != (atomicState.temperature - 0.5 <= atomicState.heat_setpoint)) {
            changed = true
        }
        if ((new_temp - 1.5 <= atomicState.heat_setpoint) != (atomicState.temperature - 1.5 <= atomicState.heat_setpoint)) {
            changed = true
        }
        if ((new_temp - 0.5 >= atomicState.cool_setpoint) != (atomicState.temperature - 0.5 >= atomicState.cool_setpoint)) {
            changed = true
        }
        if ((new_temp + 0.5 >= atomicState.cool_setpoint) != (atomicState.temperature + 0.5 >= atomicState.cool_setpoint)) {
            changed = true
        }
        if ((new_temp + 1.5 >= atomicState.cool_setpoint) != (atomicState.temperature + 1.5 >= atomicState.cool_setpoint)) {
            changed = true
        }
        atomicState.temperature = new_temp
        return changed
    } else {
        return false
    }
}

def tempHandler(evt=NULL) {
    // changes in temperature from the thermostat do not directly result in heating or cooling calls - those result from operating state changes
    // changes in temperature may result in a zone becoming a dump zone or not, which can start or ending cooling call or heating call from subzones
    // log.debug("In Zone tempHandler()")
    if (update_temp()) {
        update_demand()
    }
}

Boolean update_heat_setpoint() {
    // log.debug("In update_heat_setpoint()")
    if (stat.hasAttribute("heatingSetpoint")) {
        def levelstate = stat.currentState("heatingSetpoint")
        atomicState.heat_setpoint = new_setpoint
        new_setpoint = levelstate.value as BigDecimal
        def subzones = getChildApps()
        subzones.each { sz ->
            sz.parent_heat_setpoint_updated(new_setpoint)
        }
        changed = false;
        if ((atomicState.temperature <= new_setpoint) != (atomicState.temperature <= atomicState.heat_setpoint)) {
            changed = true
        }
        if ((atomicState.temperature -1 <= new_setpoint) != (atomicState.temperature -1 <= atomicState.heat_setpoint)) {
            changed = true
        }
        atomicState.heat_setpoint = new_setpoint
        if (new_setpoint - atomicState.temperature >= 1) {
            parent.stage2_heat()
        }
        return changed
    } else {
        return false
    }
}

def heat_setHandler(evt=NULL) {
    // changes in thermostat set point do not directly result in heating calls - those result from operating state changes
    // changes in setpoint may result in a subzone starting or ending a heating call
    // log.debug("In Zone heat_setHandler()")
    if (update_heat_setpoint()) {
        update_demand()
    }
}

Boolean update_cool_setpoint() {
    // log.debug("In update_cool_setpoint()")
    if (stat.hasAttribute("coolingSetpoint")) {
        def levelstate = stat.currentState("coolingSetpoint")
        new_setpoint = levelstate.value as BigDecimal
        atomicState.cool_setpoint = new_setpoint
        def subzones = getChildApps()
        subzones.each { sz ->
            sz.parent_cool_setpoint_updated(new_setpoint)
        }
        changed = false;
        if ((atomicState.temperature >= new_setpoint) != (atomicState.temperature >= atomicState.cool_setpoint)) {
            changed = true
        }
        if ((atomicState.temperature + 1 >= new_setpoint) != (atomicState.temperature + 1 >= atomicState.cool_setpoint)) {
            changed = true
        }
        if (atomicState.temperature - new_setpoint >= 1) {
            parent.stage2_cool()
        }
        return changed
    } else {
        return false
    }
}

def cool_setHandler(evt=NULL) {
    // changes in thermostat set point do not directly result in cooling calls - those result from operating state changes
    // changes in setpoint may result in a subzone starting or ending a cooling call
    // log.debug("In SubZone cool_setHandler()")
    if (update_cool_setpoint()) {
        update_demand()
    }
}

def update_occupied() {
    // log.debug("In update_occupied()")
    if (occupied) {
        def currentvalue = occupied.currentValue("switch")
        switch ("$currentvalue") {
            case "on":
                atomicState.occupied_now = true
                break;
            case "off":
                atomicState.occupied_now = false
                break;
        }
    } else {
        atomicState.occupied_now = true
    }
}

def modeHandler(evt=NULL) {
    // log.debug("In Zone modeHandler")
    update_vent_demand()
}

def occupiedHandler(evt=NULL) {
    // log.debug("In Zone occupiedHandler")
    update_occupied()
    update_demand()
    update_vent_demand()
}

Boolean full_thermostat() {
    log.debug("In full_thermostat()")
    def temp_state = stat.currentState("temperature")
    def heat_state = stat.currentState("heatingSetpoint")
    def cool_state = stat.currentState("coolingSetpoint")
    return (temp_state) && (heat_state) && (cool_state)
}

// These functions let the zone control app and subzones access this zones state

def get_off_capacity() {
    return atomicState.off_capacity
}

def get_on_capacity() {
    return atomicState.on_capacity
}

def get_heat_demand() {
    return atomicState.heat_demand
}

def get_cool_demand() {
    return atomicState.cool_demand
}

def get_heat_accept() {
    return atomicState.heat_accept
}

def get_cool_accept() {
    return atomicState.cool_accept
}

def get_fan_demand() {
    return atomicState.fan_demand
}

def get_vent_demand() {
    return atomicState.vent_demand
}

def get_heat_setpoint() {
    return atomicState.heat_setpoint
}

def get_cool_setpoint() {
    return atomicState.cool_setpoint
}

def get_temp() {
    return atomicState.temperature
}

def get_on_for_vent() {
    return atomicState.on_for_vent
}

// This function is called by the zoning app to select this zone.  In turn, it selects appropriate subzones.

def turn_on(mode) {
    log.debug("In Zone turn_on($mode)")
    atomicState.current_mode = mode
    def subzones = getChildApps()
    switch ("$mode") {
        case "heating":
            subzones.each { sz ->
                if (sz.get_heat_demand("heating") > 0) {
                    sz.turn_on()
                } else {
                    sz.turn_off()
                }
            }
            break
        case "cooling":
            subzones.each { sz ->
                if (sz.get_cool_demand("cooling") > 0) {
                    sz.turn_on()
                } else {
                    sz.turn_off()
                }
            }
            break
        case "vent":
            subzones.each { sz ->
                if (sz.get_fan_switch()) {
                    sz.turn_on()
                } else {
                    sz.turn_off()
                }
            }
            break
    }
    if (normally_open) {
        zone.off()
    } else {
        zone.on()
    }
}

def turn_off() {
    log.debug("In Zone turn_off()")
    atomicState.current_mode = "unselected"
    if (normally_open) {
        zone.on()
    } else {
        zone.off()
    }
}

def turn_idle() {
    // when the equipment is idle, including the blower, the zone switch is turned off, regardless of whether it is normally on or normally off
    log.debug("In Zone turn_idle()")
    atomicState.current_mode = "idle"
    zone.off()
}

def handle_overpressure() {
    // log.debug("In Zone handle_overpressure()")
    def currentvalue = zone.currentValue("switch")
    switch ("$currentvalue") {
        case "on":
            atomicState.on_capacity *= 0.9
            break;
        case "off":
            atomicState.off_capacity *= 0.9
            break;
    }
    def subzones = getChildApps()
    subzones.each { sz ->
        sz.handle_overpressure()
    }
}
