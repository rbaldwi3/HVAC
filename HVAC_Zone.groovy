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
 * version 0.3 - restructured so apps communicate via HVAC Zone Status virtual devices
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
            input (name:"select_type", type: "enum", required: true, title: "Type of zone selection",
                   options: ["Always selected", "Normally Open Damper", "Normally Closed Damper or Duct Fan", "Proportional Damper"])
        }
        section ("Sub Zones") {
            input "subzones", "device.HVACZoneStatus", multiple: true, title: "Sub Zones"
        }
    }
    page(name: "pageTwo", title: "Control Rules", install: true, uninstall: true)
}

def pageTwo() {
    dynamicPage(name: "pageTwo") {
        if (subzones) {
            subzones.each { sz ->
                if (!parent.subzone_ok(app.label, sz.label)) {
                    section () {
                        paragraph "WARNING * * * * * $sz.label is not an acceptable subzone * * * * *"
                    }
                }
            }
        }
        switch ("$select_type") {
            case "Always selected":
                break
            case "Normally Open Damper":
            case "Normally Closed Damper or Duct Fan":
                section ("Select Switch") {
                    input "closed_pos", "number", required: true, title: "Percent Open when in Off position", default: 0, range: "0 . . 100"
                    input "select_switch", "capability.switch", required: false, title: "Switch for selection of Zone"
                }
                break
            case "Proportional Damper":
                section ("Select Switch") {
                    input "closed_pos", "number", required: true, title: "Percent Open when in Off position", default: 0, range: "0 . . 100"
                    input "select_dimmer", "capability.switchLevel", required: false, title: "Switch for selection of Zone"
                }
                break
        }
        section ("Under what conditions should this zone accept heating without a heat call?") {
            if (full_thermostat()) {
                input (name:"heat_dump_criteria", type: "enum", required: false,
                       options: ["Based on temperature / setpoint difference","If a heat call happened within the last hour","If temperature is NOT increasing","If temperature is decreasing",
                                 "When a switch in on","In certain thermostat modes","In certain hub modes","When certain people are NOT present"],
                       submitOnChange: true, multiple: true)
            } else {
                input (name:"heat_dump_criteria", type: "enum", required: false,
                       options: ["If a heat call happened within the last hour","When a switch in on","In certain hub modes","When certain people are NOT present"],
                       submitOnChange: true, multiple: true)
            }
            if (heat_dump_criteria) {
                if (heat_dump_criteria.findAll { it == "Based on temperature / setpoint difference" }) {
                    input "heat_dump_delta", "number", required: true, title: "Maximum temperature above setpoint for accepting heat", default: 1, range: "0 . . 10"
                }
                if (heat_dump_criteria.findAll { it == "When a switch in on" }) {
                    input "heat_dump_switch", "capability.switch", required: true, title: "Switch for accepting heat"
                }
                if (heat_dump_criteria.findAll { it == "In certain thermostat modes" }) {
                    input "heat_dump_thermostat_modes", "enum", required: true, multiple: true, title: "In what thermostat modes should zone accept heat?",
                          options: ["auto", "off", "heat", "emergency heat", "cool"], default: ["auto", "heat", "emergency heat"]
                }
                if (heat_dump_criteria.findAll { it == "In certain hub modes" }) {
                    input (name:"heat_dump_modes", type: "mode", required: false, multiple: true, title: "In what hub modes should zone accept heat?")
                }
                if (heat_dump_criteria.findAll { it == "When certain people are NOT present" }) {
                    input "heat_dump_people", "capability.presenceSensor", required:true, multiple: true, title: "Whose presence?"
                }
                if (heat_dump_criteria.size() > 1) {
                    input "heat_dump_and", "bool", required: true, title: "Require ALL conditions?", default: false
                }
            }
        }
        section ("Under what conditions should this zone accept cooling without a cooling call?") {
            if (full_thermostat()) {
                input (name:"cool_dump_criteria", type: "enum", required: false,
                       options: ["Based on temperature / setpoint difference","If a cooling call happened within the last hour","If temperature is NOT decreasing","If temperature is increasing",
                                 "When a switch in on","In certain thermostat modes","In certain hub modes","When certain people are NOT present"],
                       submitOnChange: true, multiple: true)
            } else {
                input (name:"cool_dump_criteria", type: "enum", required: false,
                       options: ["If a cooling call happened within the last hour","When a switch in on","In certain hub modes","When certain people are NOT present"],
                       submitOnChange: true, multiple: true)
            }
            if (cool_dump_criteria) {
                if (cool_dump_criteria.findAll { it == "Based on temperature / setpoint difference" }) {
                    input "cool_dump_delta", "number", required: true, title: "Maximum temperature below setpoint for accepting cooling", default: 1, range: "0 . . 10"
                }
                if (cool_dump_criteria.findAll { it == "When a switch in on" }) {
                    input "cool_dump_switch", "capability.switch", required: true, title: "Switch for accepting cooling"
                }
                if (cool_dump_criteria.findAll { it == "In certain thermostat modes" }) {
                    input "cool_dump_thermostat_modes", "enum", required: true, multiple: true, title: "In what thermostat modes should zone accept cooling?",
                          options: ["auto", "off", "heat", "emergency heat", "cool"], default: ["auto", "cool"]
                }
                if (cool_dump_criteria.findAll { it == "In certain hub modes" }) {
                    input (name:"cool_dump_modes", type: "mode", required: false, multiple: true, title: "In what hub modes should zone accept cooling?")
                }
                if (cool_dump_criteria.findAll { it == "When certain people are NOT present" }) {
                    input "cool_dump_people", "capability.presenceSensor", required:true, multiple: true, title: "Whose presence?"
                }
                if (cool_dump_criteria.size() > 1) {
                    input "cool_dump_and", "bool", required: true, title: "Require ALL conditions?", default: false
                }
            }
        }
        section ("Under what conditions should this zone accept ventilation?") {
            input (name:"vent_criteria", type: "enum", required: false, options: ["When a switch in on","In certain hub modes","When certain people are present"],
                   submitOnChange: true, multiple: true)
            if (vent_criteria) {
                if (vent_criteria.findAll { it == "When a switch in on" }) {
                    input "vent_switch", "capability.switch", required: true, title: "Switch for accepting ventilation"
                }
                if (vent_criteria.findAll { it == "In certain hub modes" }) {
                    input (name:"vent_modes", type: "mode", required: false, multiple: true, title: "In what hub modes should zone accept ventilation?")
                }
                if (vent_criteria.findAll { it == "When certain people are present" }) {
                    input "vent_people", "capability.presenceSensor", required:true, multiple: true, title: "Whose presence?"
                }
                if (vent_criteria.size() > 1) {
                    input "vent_and", "bool", required: true, title: "Require ALL conditions?", default: false
                }
            }
        }
        section ("Under what conditions should this zone accept dehumidification?") {
            input (name:"dehum_criteria", type: "enum", required: false, options: ["Above a humidity threshold","When a switch in on","In certain hub modes"],
                   submitOnChange: true, multiple: true)
            if (dehum_criteria) {
                if (dehum_criteria.findAll { it == "Above a humidity threshold" }) {
                    input "dehum_humidity", "capability.relativeHumidityMeasurement", required: true, title: "Humidity sensor for zone"
                    input "dehum_threshold", "number", required: true, title: "Humidity threshold for zone", default: 50, range: "0 . . 100"
                }
                if (dehum_criteria.findAll { it == "When a switch in on" }) {
                    input "dehum_switch", "capability.switch", required: true, title: "Switch for accepting dehumidification"
                }
                if (dehum_criteria.findAll { it == "In certain hub modes" }) {
                    input (name:"dehum_modes", type: "mode", required: false, multiple: true, title: "In what hub modes should zone accept dehumidification?")
                }
                if (dehum_criteria.size() > 1) {
                    input "dehum_and", "bool", required: true, title: "Require ALL conditions?", default: false
                }
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

def update_parent() {
    parent.updated()
}

def installed() {
    // log.debug("In installed()")
    atomicState.status_DNI = "HVACZone_${app.id}"
    addChildDevice("rbaldwi3", "HVAC Zone Status", atomicState.status_DNI, [isComponent: true, label: app.label])
    initialize()
}

def uninstalled() {
    // log.debug("In uninstalled()")
    runIn(10, update_parent, [misfire: "ignore"])
}

def updated() {
    // log.debug("In updated()")
    unsubscribe()
    initialize()
}

def initialize() {
    // log.debug("In initialize()")
    child_updated() // updates off_capacity
    atomicState.prev_state = "idle"
    atomicState.cool_in_last_hour = false
    atomicState.heat_in_last_hour = false
    update_state()
    subscribe(stat, "thermostatOperatingState", op_stateHandler)
    atomicState.heat_setpoint = 70
    atomicState.temperature = 75
    atomicState.cool_setpoint = 80
    atomicState.thermostat_mode = "off"
    if (full_thermostat()) {
        update_temp()
    	subscribe(stat, "temperature", tempHandler)
        update_heat_setpoint()
        subscribe(stat, "heatingSetpoint", heat_setHandler)
        update_cool_setpoint()
        subscribe(stat, "coolingSetpoint", cool_setHandler)
        update_thermostat_mode()
        subscribe(stat, "thermostatMode", thermostat_modeHandler)
    }
    if (heat_dump_criteria) {
        if (heat_dump_criteria.findAll { it == "When a switch in on" }) {
            if (heat_dump_switch) {
                subscribe(heat_dump_switch, "switch", switchHandler)
            }
        }
        if (heat_dump_criteria.findAll { it == "When certain people are NOT present" }) {
            if (heat_dump_people) {
                heat_dump_people.each { ppl ->
                    subscribe(ppl, "presence", switchHandler)
                }
            }
        }
    }
    if (cool_dump_criteria) {
        if (cool_dump_criteria.findAll { it == "When a switch in on" }) {
            if (cool_dump_switch) {
                subscribe(cool_dump_switch, "switch", switchHandler)
            }
        }
        if (cool_dump_criteria.findAll { it == "When certain people are NOT present" }) {
            if (cool_dump_people) {
                cool_dump_people.each { ppl ->
                    subscribe(ppl, "presence", switchHandler)
                }
            }
        }
    }
    if (vent_criteria) {
        if (vent_criteria.findAll { it == "When a switch in on" }) {
            if (vent_switch) {
                subscribe(vent_switch, "switch", vent_demandHandler)
            }
        }
        if (vent_criteria.findAll { it == "When certain people are present" }) {
            if (vent_people) {
                vent_people.each { ppl ->
                    subscribe(ppl, "presence", vent_demandHandler)
                }
            }
        }
    }
    if (dehum_criteria) {
        if (dehum_criteria.findAll { it == "When a switch in on" }) {
            if (dehum_switch) {
                subscribe(dehum_switch, "switch", dehum_demandHandler)
            }
        }
        if (dehum_criteria.findAll { it == "Above a humidity threshold" }) {
            if (dehum_humidity) {
                subscribe(dehum_humidity, "humidity", dehum_demandHandler)
            }
        }
    }
    subscribe(location, "mode", modeHandler)
    atomicState.cool_demand = 0
    atomicState.heat_demand = 0
    atomicState.cool_accept = 0
    atomicState.heat_accept = 0
    atomicState.fan_demand = 0
    update_demand()
    atomicState.fan_accept = 0
    update_vent_demand()
    atomicState.dehum_accept = 0
    update_dehum_demand()
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
    status_device = getChildDevice(atomicState.status_DNI)
    status_device.set_offline(false)
    status_device.set_temp_increasing(false)
    status_device.set_temp_decreasing(false)
    status_device.set_recent_heat(false)
    status_device.set_recent_cool(false)
    subscribe(status_device, "current_state", current_stateHandler)
    subscribe(status_device, "flow", current_stateHandler)
    tmplist = []
    if (subzones) {
        subzones.each { sz ->
            if (parent.subzone_ok(app.label, sz.label)) {
                tmplist << sz.label
                subscribe(sz, "heat_demand", demandHandler)
                subscribe(sz, "heat_accept", demandHandler)
                subscribe(sz, "cool_demand", demandHandler)
                subscribe(sz, "cool_accept", demandHandler)
                subscribe(sz, "fan_demand", demandHandler)
                subscribe(sz, "fan_accept", vent_demandHandler)
                subscribe(sz, "dehum_accept", dehum_demandHandler)
            } else {
                log.debug("$sz.label is not an acceptable subzone")
            }
        }
    }
    atomicState.valid_subzones = tmplist
    runIn(10, update_parent, [misfire: "ignore"])
}

def refresh_outputs() {
    // log.debug("In refresh_outputs()")
    status_device = get_status_device()
    status_device.set_heat_demand(atomicState.heat_demand)
    status_device.set_heat_accept(atomicState.heat_accept)
    status_device.set_cool_demand(atomicState.cool_demand)
    status_device.set_cool_accept(atomicState.cool_accept)
    status_device.set_fan_demand(atomicState.fan_demand)
    status_device.set_fan_accept(atomicState.fan_accept)
    status_device.set_dehum_accept(atomicState.dehum_accept)
    update_zone_state()
}

def refresh_inputs() {
    // log.debug("In refresh_inputs()")
    if (stat.hasCapability("Refresh")) {
        stat.refresh()
    }
    def value = stat.currentValue("thermostatOperatingState")
    switch ("$value") {
        case "heating":
            if (atomicState.heat_demand == 0) {
                op_stateHandler()
            }
            break
        case "cooling":
            if (atomicState.cool_demand == 0) {
                op_stateHandler()
            }
            break
        case "fan only":
            if (atomicState.fan_demand == 0) {
                op_stateHandler()
            }
            break
        default:
            if ((atomicState.heat_demand > 0) || (atomicState.cool_demand > 0)) {
                op_stateHandler()
            }
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
    update_demand()
    update_vent_demand()
    update_dehum_demand()
}

def child_updated() {
    // log.debug("In Zone child_updated()")
    // Preprocessing of airflow per zone
    subzone_off_capacity = 0
    if (subzones) {
        subzones.each { sz ->
            if (atomicState.valid_subzones.findAll { it == sz.label }) {
                def subzone_value = sz.currentValue("off_capacity")
                subzone_off_capacity += subzone_value
            }
        }
    }
    switch ("$select_type") {
        case "Always selected":
            atomicState.on_capacity = 1
            atomicState.off_capacity = settings.cfm + subzone_off_capacity - 1
            break
        case "Normally Open Damper":
        case "Normally Closed Damper or Duct Fan":
        case "Proportional Damper":
            atomicState.off_capacity = (settings.cfm + subzone_off_capacity) * settings.closed_pos / 100
            atomicState.on_capacity = settings.cfm + subzone_off_capacity - atomicState.off_capacity
            break
    }
    status_device = get_status_device()
    status_device.set_off_capacity(atomicState.off_capacity)
}

def update_demand() {
// This function updates atomicState.cool_demand, atomicState.heat_demand, atomicState.fan_demand, 
// atomicstate.cool_accept, and atomicstate.heat_accept.
// If any of these are changed, the main Zoning app is asked to re-evaluate its state
    // log.debug("In Zone update_demand()")
    heat_demand = 0
    cool_demand = 0
    heat_accept = 0
    cool_accept = 0
    fan_demand = 0
    atomicState.child_heat_accept = 0
    atomicState.child_cool_accept = 0
    def state = stat.currentValue("thermostatOperatingState")
    if (atomicState.change_for_three_hours) {
        thermostat_state = "$state.value"
    } else {
        thermostat_state = "idle"
    }
    switch ("$thermostat_state") {
        case "heating":
            // the heating demand is the capacity of this main zone plus the capacities of any subzones that would join this heating call
            heat_demand = atomicState.on_capacity
            if (subzones) {
                subzones.each { sz ->
                    if (atomicState.valid_subzones.findAll { it == sz.label }) {
                        def subzone_value = sz.currentValue("heat_demand")
                        heat_demand += subzone_value
                    }
                }
            }
            break
        case "cooling":
            // the cooling demand is the capacity of this main zone plus the capacities of any subzones that would join this cooling call
            cool_demand = atomicState.on_capacity
            if (subzones) {
                subzones.each { sz ->
                    if (atomicState.valid_subzones.findAll { it == sz.label }) {
                        def subzone_value = sz.currentValue("cool_demand")
                        cool_demand += subzone_value
                    }
                }
            }
            break
        case "fan only":
            // when idle or fan only command, determine if any subzones would trigger heating or cooling calls for this main zone
            fan_demand = atomicState.on_capacity
        default: // treat all other states like idle
            if (fan_demand > 0) {
                if (subzones) {
                    subzones.each { sz ->
                        if (atomicState.valid_subzones.findAll { it == sz.label }) {
                            def subzone_value = sz.currentValue("fan_demand")
                            fan_demand += subzone_value
                        }
                    }
                }
            }
            // Decide whether this zone is a dump zone for heating
            if (heat_dump_criteria) {
                num_satisfied = 0
                heat_dump_criteria.each { crit ->
                    switch ("$crit") {
                        case "Based on temperature / setpoint difference":
                            if (atomicState.temperature <= atomicState.heat_setpoint + heat_dump_delta) { num_satisfied++ }
                            break
                        case "If a heat call happened within the last hour":
                            if (atomicState.heat_in_last_hour) { num_satisfied++ }
                            break
                        case "If temperature is NOT increasing":
                            if (!atomicState.temp_increasing) { num_satisfied++ }
                            break
                        case "If temperature is decreasing":
                            if (atomicState.temp_decreasing) { num_satisfied++ }
                            break
                        case "When a switch in on":
                            if (heat_dump_switch) {
                                switch_value = heat_dump_switch.currentValue("switch")
                                if (switch_value == "on") { num_satisfied++ }
                            }
                            break
                        case "In certain thermostat modes":
                            if (heat_dump_thermostat_modes) {
                                heat_dump_thermostat_modes.each { tmd ->
                                    if ("$atomicState.thermostat_mode" == "$tmd") { num_satisfied++ }
                                }
                            }
                            break
                        case "In certain hub modes":
                            if (heat_dump_modes) {
                                def currMode = location.mode
                                heat_dump_modes.each { md ->
                                    if ("$currMode" == "$md") { num_satisfied++ }
                                }
                            }
                            break
                        case "When certain people are NOT present":
                            if (heat_dump_people) {
                                all_gone = true
                                heat_dump_people.each { ppl ->
                                    presence_value = ppl.currentValue("presence")
                                    if (presence_value == "present") { all_gone = false }
                                }
                                if (all_gone) { num_satisfied++ }
                            }
                            break
                        default:
                            log.debug("Unknown heat_dump_criteria $crit")
                    }
                }
                if (heat_dump_criteria.size() == 0) {
                    heat_accept = 0
                } else {
                    if (heat_dump_and) {
                        if (num_satisfied == heat_dump_criteria.size()) {
                            heat_accept = atomicState.on_capacity
                        } else {
                            heat_accept = 0
                        }
                    } else {
                        if (num_satisfied > 0) {
                            heat_accept = atomicState.on_capacity
                        } else {
                            heat_accept = 0
                        }
                    }
                }
            } else {
                heat_accept = 0
            }
            if (heat_accept > 0) {
                if (subzones) {
                    subzones.each { sz ->
                        if (atomicState.valid_subzones.findAll { it == sz.label }) {
                            def subzone_value = sz.currentValue("heat_demand")
                            heat_demand += subzone_value
                            subzone_value = sz.currentValue("heat_accept")
                            heat_accept += subzone_value
                        }
                    }
                }
                if (heat_demand > 0) {
                    heat_demand += atomicState.on_capacity
                    atomicState.child_heat_accept = heat_accept
                    heat_accept = 0
                } else {
                    atomicState.child_heat_accept = heat_accept - atomicState.on_capacity
                }
            }
            // Decide whether this zone is a dump zone for cooling
            if (cool_dump_criteria) {
                num_satisfied = 0
                cool_dump_criteria.each { crit ->
                    switch ("$crit") {
                        case "Based on temperature / setpoint difference":
                            if (atomicState.temperature >= atomicState.cool_setpoint - heat_dump_delta) { num_satisfied++ }
                            break
                        case "If a cooling call happened within the last hour":
                            if (atomicState.cool_in_last_hour) { num_satisfied++ }
                            break
                        case "If temperature is NOT decreasing":
                            if (!atomicState.temp_decreasing) { num_satisfied++ }
                            break
                        case "If temperature is increasing":
                            if (atomicState.temp_increasing) { num_satisfied++ }
                            break
                        case "When a switch in on":
                            if (cool_dump_switch) {
                                switch_value = cool_dump_switch.currentValue("switch")
                                if (switch_value == "on") { num_satisfied++ }
                            }
                            break
                        case "In certain thermostat modes":
                            if (cool_dump_thermostat_modes) {
                                cool_dump_thermostat_modes.each { tmd ->
                                    if ("$atomicState.thermostat_mode" == "$tmd") { num_satisfied++ }
                                }
                            }
                            break
                        case "In certain hub modes":
                            if (cool_dump_modes) {
                                def currMode = location.mode
                                cool_dump_modes.each { md ->
                                    if ("$currMode" == "$md") { num_satisfied++ }
                                }
                            }
                            break
                        case "When certain people are NOT present":
                            if (cool_dump_people) {
                                all_gone = true
                                cool_dump_people.each { ppl ->
                                    presence_value = ppl.currentValue("presence")
                                    if (presence_value == "present") { all_gone = false }
                                }
                                if (all_gone) { num_satisfied++ }
                            }
                            break
                        default:
                            log.debug("Unknown cool_dump_criteria $crit")
                    }
                }
                if (cool_dump_criteria.size() == 0) {
                    cool_accept = 0
                } else {
                    if (cool_dump_and) {
                        if (num_satisfied == cool_dump_criteria.size()) {
                            cool_accept = atomicState.on_capacity
                        } else {
                            cool_accept = 0
                        }
                    } else {
                        if (num_satisfied > 0) {
                            cool_accept = atomicState.on_capacity
                        } else {
                            cool_accept = 0
                        }
                    }
                }
            } else {
                cool_accept = 0
            }
            if (cool_accept > 0) {
                if (subzones) {
                    subzones.each { sz ->
                        if (atomicState.valid_subzones.findAll { it == sz.label }) {
                            def subzone_value = sz.currentValue("cool_demand")
                            cool_demand += subzone_value
                            subzone_value = sz.currentValue("cool_accept")
                            cool_accept += subzone_value
                        }
                    }
                }
                if (cool_demand > 0) {
                    cool_demand += atomicState.on_capacity
                    atomicState.child_cool_accept = cool_accept
                    cool_accept = 0
                } else {
                    atomicState.child_cool_accept = cool_accept - atomicState.on_capacity
                }
            }
    }
    // log.debug("heat_demand = $heat_demand, heat_accept = $heat_accept, cool_demand = $cool_demand, cool_accept = $cool_accept, fan_demand = $fan_demand")
    status_device = get_status_device()
    if (atomicState.heat_demand != heat_demand) {
        atomicState.heat_demand = heat_demand
        status_device.set_heat_demand(heat_demand)
    }
    if (atomicState.heat_accept != heat_accept) {
        atomicState.heat_accept = heat_accept
        status_device.set_heat_accept(heat_accept)
    }
    if (atomicState.cool_demand != cool_demand) {
        atomicState.cool_demand = cool_demand
        status_device.set_cool_demand(cool_demand)
    }
    if (atomicState.cool_accept != cool_accept) {
        atomicState.cool_accept = cool_accept
        status_device.set_cool_accept(cool_accept)
    }
    if (atomicState.fan_demand != fan_demand) {
        atomicState.fan_demand = fan_demand
        status_device.set_fan_demand(fan_demand)
    }
}

def update_vent_demand() {
    // log.debug("In Zone update_vent_demand()")
    // vent_demand is the capacity that would be accepted if the zone control app decides to run ventilation.  It is not a call to the zone control.
    vent_accept = 0
    if (vent_criteria) {
        num_satisfied = 0
        vent_criteria.each { crit ->
            switch ("$crit") {
                case "When a switch in on":
                    if (vent_switch) {
                        switch_value = vent_switch.currentValue("switch")
                        if (switch_value == "on") { num_satisfied++ }
                    }
                    break
                case "In certain hub modes":
                    if (vent_modes) {
                        def currMode = location.mode
                        vent_modes.each { md ->
                            if ("$currMode" == "$md") { num_satisfied++ }
                        }
                    }
                    break
                case "When certain people are present":
                    if (vent_people) {
                        some_here = false
                        vent_people.each { ppl ->
                            presence_value = ppl.currentValue("presence")
                            if (presence_value == "present") { some_here = true }
                        }
                        if (some_here) { num_satisfied++ }
                    }
                    break
                default:
                    log.debug("Unknown vent_criteria $crit")
            }
        }
        if (vent_criteria.size() == 0) {
            vent_accept = 0
        } else {
            if (vent_and) {
                if (num_satisfied == vent_criteria.size()) {
                    vent_accept = atomicState.on_capacity
                } else {
                    vent_accept = 0
                }
            } else {
                if (num_satisfied > 0) {
                    vent_accept = atomicState.on_capacity
                } else {
                    vent_accept = 0
                }
            }
        }
    }
    if (vent_accept > 0) {
        if (subzones) {
            subzones.each { sz ->
                if (atomicState.valid_subzones.findAll { it == sz.label }) {
                    subzone_value = sz.currentValue("fan_demand")
                    vent_accept += subzone_value
                    subzone_value = sz.currentValue("fan_accept")
                    vent_accept += subzone_value
                }
            }
        }
    }
    // log.debug("vent_accept = $vent_accept")
    if (atomicState.fan_accept != vent_accept) {
        atomicState.fan_accept = vent_accept
        status_device = get_status_device()
        status_device.set_fan_accept(vent_accept)
    }
}
    
def update_dehum_demand() {
    // log.debug("In Zone update_dehum_demand()")
    // dehum_demand is the capacity that would be accepted if the zone control app decides to run dehumidification.  It is not a call to the zone control.
    dehum_accept = 0
    if (dehum_criteria) {
        num_satisfied = 0
        dehum_criteria.each { crit ->
            switch ("$crit") {
                case "When a switch in on":
                    if (dehum_switch) {
                        switch_value = dehum_switch.currentValue("switch")
                        if (switch_value == "on") { num_satisfied++ }
                    }
                    break
                case "In certain hub modes":
                    if (dehum_modes) {
                        def currMode = location.mode
                        dehum_modes.each { md ->
                            if ("$currMode" == "$md") { num_satisfied++ }
                        }
                    }
                    break
                case "Above a humidity threshold":
                    if (dehum_humidity) {
                        def levelstate = dehum_humidity.currentState("humidity")
                        current_humidity = levelstate.value as Integer
                        if (current_humidity > dehum_threshold) { num_satisfied++ }
                    }
                    break
                default:
                    log.debug("Unknown dehum_criteria $crit")
            }
        }
        if (dehum_criteria.size() == 0) {
            dehum_accept = 0
        } else {
            if (dehum_and) {
                if (num_satisfied == dehum_criteria.size()) {
                    dehum_accept = atomicState.on_capacity
                } else {
                    dehum_accept = 0
                }
            } else {
                if (num_satisfied > 0) {
                    dehum_accept = atomicState.on_capacity
                } else {
                    dehum_accept = 0
                }
            }
        }
    }
    if (dehum_accept > 0) {
        if (subzones) {
            subzones.each { sz ->
                if (atomicState.valid_subzones.findAll { it == sz.label }) {
                    subzone_value = sz.currentValue("dehum_accept")
                    dehum_accept += subzone_value
                }
            }
        }
    }
    // log.debug("dehum_accept = $dehum_accept")
    if (atomicState.dehum_accept != dehum_accept) {
        atomicState.dehum_accept = dehum_accept
        status_device = get_status_device()
        status_device.set_dehum_accept(dehum_accept)
    }
}
    
def no_cool_for_an_hour() {
    // log.debug("In no_cool_for_an_hour()")
    atomicState.cool_in_last_hour = false
    update_demand()
    status_device = get_status_device()
    status_device.set_recent_cool(false)
}

def no_heat_for_an_hour() {
    // log.debug("In no_heat_for_an_hour()")
    atomicState.heat_in_last_hour = false
    update_demand()
    status_device = get_status_device()
    status_device.set_recent_heat(false)
}

def no_change_for_three_hours() {
    // log.debug("In no_change_for_three_hours()")
    atomicState.change_for_three_hours = false
    update_demand()
    status_device = get_status_device()
    status_device.set_offline(true)
}

def update_state() {
    // log.debug("In update_state()")
    def new_state = stat.currentValue("thermostatOperatingState")
    status_device = get_status_device()
    switch ("$atomicState.prev_state") {
        case "heating":
            atomicState.heat_in_last_hour = true;
            status_device.set_recent_heat(true)
            switch ("$new_state") {
                case "cooling":
                    atomicState.cool_in_last_hour = true;
                    status_device.set_recent_cool(true)
                    unschedule("no_cool_for_an_hour")
                case "fan only":
                case "idle":
                    runIn(3600, "no_heat_for_an_hour", [misfire: "ignore"])
                    break
            }
            break
        case "cooling":
            atomicState.cool_in_last_hour = true;
            status_device.set_recent_cool(true)
            switch ("$new_state") {
                case "heating":
                    atomicState.heat_in_last_hour = true;
                    status_device.set_recent_heat(true)
                    unschedule("no_heat_for_an_hour")
                case "fan only":
                case "idle":
                    runIn(3600, "no_cool_for_an_hour", [misfire: "ignore"])
                    break
            }
            break
        default:
            switch ("$new_state") {
                case "cooling":
                    atomicState.cool_in_last_hour = true;
                    status_device.set_recent_cool(true)
                    unschedule("no_cool_for_an_hour")
                    break
                case "heating":
                    atomicState.heat_in_last_hour = true;
                    status_device.set_recent_heat(true)
                    unschedule("no_heat_for_an_hour")
                    break
            }
    }
    atomicState.prev_state = new_state
}

def op_stateHandler(evt=NULL) {
    // log.debug("In Zone op_stateHandler()")
    atomicState.change_for_three_hours = true
    status_device = get_status_device()
    status_device.set_offline(false)
    runIn(3600*3, "no_change_for_three_hours", [misfire: "ignore"])
    update_state()
    runIn(5, "update_demand", [misfire: "ignore"]) // this does the work of calculating what should be demanded of the zone control app
    // delaying ensures rapid changes only create one call and also ensures that wired_tstatHandler() gets called before zone_call_changed() in parent.
}

def demandHandler(evt=NULL) {
    // log.debug("In Zone demandHandler()")
    runIn(5, "update_demand", [misfire: "ignore"]) // this does the work of calculating what should be demanded of the zone control app
    // delaying ensures rapid changes only create one call.
}

def vent_demandHandler(evt=NULL) {
    // log.debug("In Zone vent_demandHandler()")
    update_vent_demand()
}

def dehum_demandHandler(evt=NULL) {
    // log.debug("In Zone dehum_demandHandler()")
    update_dehum_demand()
}

Boolean update_temp() {
    // log.debug("In update_temp()")
    if (stat.hasAttribute("temperature")) {
        def levelstate = stat.currentState("temperature")
        new_temp = levelstate.value as BigDecimal
        // Note - update demand is called at half-degree offsets from the actual thresholds to avoid rapid cycling due to small temperature variations
        changed = false;
        if (heat_dump_delta) {
            if ((new_temp + 0.5 <= atomicState.heat_setpoint + heat_dump_delta) != (atomicState.temperature + 0.5 <= atomicState.heat_setpoint + heat_dump_delta)) {
                changed = true
            }
            if ((new_temp - 0.5 <= atomicState.heat_setpoint + heat_dump_delta) != (atomicState.temperature - 0.5 <= atomicState.heat_setpoint + heat_dump_delta)) {
                changed = true
            }
        }
        if (cool_dump_delta) {
            if ((new_temp - 0.5 >= atomicState.cool_setpoint - cool_dump_delta) != (atomicState.temperature - 0.5 >= atomicState.cool_setpoint - cool_dump_delta)) {
                changed = true
            }
            if ((new_temp + 0.5 >= atomicState.cool_setpoint - cool_dump_delta) != (atomicState.temperature + 0.5 >= atomicState.cool_setpoint - cool_dump_delta)) {
                changed = true
            }
        }
        status_device = get_status_device()
        switch ("$atomicState.current_mode") {
            case "Vent":
            case "Fan":
            case "Off":
            case "Idle":
                if (new_temp > atomicState.temperature) {
                    if (!atomicState.temp_increasing) { changed = true }
                    atomicState.temp_increasing = true
                    status_device.set_temp_increasing(true)
                } else {
                    if (atomicState.temp_increasing) { changed = true }
                    atomicState.temp_increasing = false
                    status_device.set_temp_increasing(false)
                }
                if (new_temp < atomicState.temperature) {
                    if (!atomicState.temp_decreasing) { changed = true }
                    atomicState.temp_decreasing = true
                    status_device.set_temp_decreasing(true)
                } else {
                    if (atomicState.temp_decreasing) { changed = true }
                    atomicState.temp_decreasing = false
                    status_device.set_temp_decreasing(false)
                }
                break
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
    } else if (!atomicState.change_for_three_hours) {
        atomicState.change_for_three_hours = true
        status_device = get_status_device()
        status_device.set_offline(false)
        update_demand()
    }
    runIn(3600*3, "no_change_for_three_hours", [misfire: "ignore"])
}

Boolean update_heat_setpoint() {
    // log.debug("In update_heat_setpoint()")
    if (stat.hasAttribute("heatingSetpoint")) {
        def levelstate = stat.currentState("heatingSetpoint")
        new_setpoint = levelstate.value as BigDecimal
        changed = false;
        if (heat_dump_delta) {
            if ((atomicState.temperature + 0.5 <= new_setpoint + heat_dump_delta) != (atomicState.temperature + 0.5 <= atomicState.heat_setpoint + heat_dump_delta)) {
                changed = true
            }
            if ((atomicState.temperature - 0.5 <= new_setpoint + heat_dump_delta) != (atomicState.temperature - 0.5 <= atomicState.heat_setpoint + heat_dump_delta)) {
                changed = true
            }
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
    } else if (!atomicState.change_for_three_hours) {
        atomicState.change_for_three_hours = true
        status_device = get_status_device()
        status_device.set_offline(false)
        update_demand()
    }
    runIn(3600*3, "no_change_for_three_hours", [misfire: "ignore"])
}

Boolean update_cool_setpoint() {
    // log.debug("In update_cool_setpoint()")
    if (stat.hasAttribute("coolingSetpoint")) {
        def levelstate = stat.currentState("coolingSetpoint")
        new_setpoint = levelstate.value as BigDecimal
        changed = false;
        if (cool_dump_delta) {
            if ((atomicState.temperature - 0.5 >= new_setpoint - cool_dump_delta) != (atomicState.temperature - 0.5 >= atomicState.cool_setpoint - cool_dump_delta)) {
                changed = true
            }
            if ((atomicState.temperature + 0.5 >= new_setpoint - cool_dump_delta) != (atomicState.temperature + 0.5 >= atomicState.cool_setpoint - cool_dump_delta)) {
                changed = true
            }
        }
        atomicState.cool_setpoint = new_setpoint
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
    } else if (!atomicState.change_for_three_hours) {
        atomicState.change_for_three_hours = true
        status_device = get_status_device()
        status_device.set_offline(false)
        update_demand()
    }
    runIn(3600*3, "no_change_for_three_hours", [misfire: "ignore"])
}

Boolean update_thermostat_mode() {
    // log.debug("In update_thermostat_mode()")
    if (stat.hasAttribute("thermostatMode")) {
        def levelstate = stat.currentState("thermostatMode")
        if (atomicState.thermostat_mode != levelstate.stringValue) {
            atomicState.thermostat_mode = levelstate.stringValue
            return true
        } else {
            return false
        }
    } else {
        return false
    }
}

def thermostat_modeHandler(evt=NULL) {
    // log.debug("In Zone thermostat_modeHandler()")
    if (update_thermostat_mode()) {
        update_demand()
    } else if (!atomicState.change_for_three_hours) {
        atomicState.change_for_three_hours = true
        status_device = get_status_device()
        status_device.set_offline(false)
        update_demand()
    }
    runIn(3600*3, "no_change_for_three_hours", [misfire: "ignore"])
}

def modeHandler(evt=NULL) {
    // log.debug("In Zone modeHandler")
    update_demand()
    update_vent_demand()
    update_dehum_demand()
}

def switchHandler(evt=NULL) {
    // log.debug("In Zone switchHandler")
    update_demand()
}

Boolean full_thermostat() {
    // log.debug("In full_thermostat()")
    def temp_state = stat.currentState("temperature")
    def heat_state = stat.currentState("heatingSetpoint")
    def cool_state = stat.currentState("coolingSetpoint")
    return (temp_state) && (heat_state) && (cool_state)
}

def update_zone_state() {
    // log.debug("In update_zone_state()")
    status_device = getChildDevice(atomicState.status_DNI)
    def mode = status_device.currentState("current_state")
    atomicState.current_mode = mode.getStringValue()
    def flow = status_device.currentState("flow")
    atomicState.current_flow = flow.getNumberValue()
    def child_demand = 0
    def child_accept = 0
    def child_force = 0
    // log.debug("mode is $atomicState.current_mode, flow is $atomicState.current_flow")
    switch ("$atomicState.current_mode") {
        case "Heating":
            atomicState.temp_increasing = false
            status_device.set_temp_increasing(false)
            atomicState.temp_decreasing = false
            status_device.set_temp_decreasing(false)
            if (atomicState.heat_demand) { // This zone had requested heat
                turn_on()
                child_demand = atomicState.heat_demand - atomicState.on_capacity
                child_accept = atomicState.child_heat_accept
                if (atomicState.current_flow > atomicState.on_capacity + child_demand + child_accept) {
                    child_force = atomicState.current_flow - atomicState.on_capacity - child_demand - child_accept
                }
            } else if (atomicState.heat_accept) { // This zone is a heat dump zone
                if (atomicState.current_flow <= atomicState.heat_accept) { // Only being sent what zone volunteered to take
                    def percentage = 100 * atomicState.current_flow / atomicState.heat_accept as Integer
                    if (turn_on(percentage) == 100) { // this is not a proportional zone
                        if (atomicState.current_flow > atomicState.on_capacity) {
                            child_accept = atomicState.current_flow - atomicState.on_capacity
                        }
                    } else { // this is a proportional zone
                        child_accept = atomicState.child_heat_accept
                    }
                } else { // Zone is being forced to take more than it volunteered to take
                    turn_on()
                    child_accept = atomicState.child_heat_accept
                    if (atomicState.current_flow > atomicState.on_capacity - child_accept) {
                        child_force = atomicState.current_flow - atomicState.on_capacity - child_accept
                    }
                }
            } else { // This zone is being forced to accept heat
                turn_on()
                if (atomicState.current_flow > atomicState.on_capacity) {
                    child_force = atomicState.current_flow - atomicState.on_capacity
                }
            }
            break
        case "Cooling":
            atomicState.temp_increasing = false
            status_device.set_temp_increasing(false)
            atomicState.temp_decreasing = false
            status_device.set_temp_decreasing(false)
            if (atomicState.cool_demand) { // This zone had requested cooling
                turn_on()
                child_demand = atomicState.cool_demand - atomicState.on_capacity
                child_accept = atomicState.child_cool_accept
                if (atomicState.current_flow > atomicState.on_capacity + child_demand + child_accept) {
                    child_force = atomicState.current_flow - atomicState.on_capacity - child_demand - child_accept
                }
            } else if (atomicState.cool_accept) { // This zone is a cooling dump zone
                if (atomicState.current_flow <= atomicState.cool_accept) { // Only being sent what zone volunteered to take
                    def percentage = 100 * atomicState.current_flow / atomicState.cool_accept as Integer
                    if (turn_on(percentage) == 100) { // this is not a proportional zone
                        if (atomicState.current_flow > atomicState.on_capacity) {
                            child_accept = atomicState.current_flow - atomicState.on_capacity
                        }
                    } else { // this is a proportional zone
                        child_accept = atomicState.child_cool_accept
                    }
                } else { // Zone is being forced to take more than it volunteered to take
                    turn_on()
                    child_accept = atomicState.child_cool_accept
                    if (atomicState.current_flow > atomicState.on_capacity - child_accept) {
                        child_force = atomicState.current_flow - atomicState.on_capacity - child_accept
                    }
                }
            } else { // This zone is being forced to accept cooling
                turn_on()
                if (atomicState.current_flow > atomicState.on_capacity) {
                    child_force = atomicState.current_flow - atomicState.on_capacity
                }
            }
            break
        case "Vent":
            turn_on()
            if (atomicState.fan_accept) { // This zone had requested ventilation
                child_demand = atomicState.fan_accept - atomicState.on_capacity
                if (atomicState.current_flow > atomicState.on_capacity + child_demand) {
                    child_force = atomicState.current_flow - atomicState.on_capacity - child_demand
                }
            } else { // This zone is being forced to accept flow
                if (atomicState.current_flow > atomicState.on_capacity) {
                    child_force = atomicState.current_flow - atomicState.on_capacity
                }
            }
            break
        case "Dehum":
            turn_on()
            if (atomicState.dehum_accept) { // This zone had requested dehumidification
                child_demand = atomicState.dehum_accept - atomicState.on_capacity
                if (atomicState.current_flow > atomicState.on_capacity + child_demand) {
                    child_force = atomicState.current_flow - atomicState.on_capacity - child_demand
                }
            } else { // This zone is being forced to accept flow
                if (atomicState.current_flow > atomicState.on_capacity) {
                    child_force = atomicState.current_flow - atomicState.on_capacity
                }
            }
            break
        case "Fan":
            turn_on()
            if (atomicState.fan_demand) { // This zone had requested fan
                child_demand = atomicState.fan_demand - atomicState.on_capacity
                if (atomicState.current_flow > atomicState.on_capacity + child_demand) {
                    child_force = atomicState.current_flow - atomicState.on_capacity - child_demand
                }
            } else { // This zone is being forced to accept flow
                if (atomicState.current_flow > atomicState.on_capacity) {
                    child_force = atomicState.current_flow - atomicState.on_capacity
                }
            }
            break
        case "Off":
            turn_off()
            break
        case "Idle":
            turn_idle()
            break
    }
    status_device = getChildDevice(atomicState.status_DNI)
    // log.debug("child_demand = $child_demand, child_accept = $child_accept, child_force = $child_force")
    if (subzones) {
        subzones.each { sz ->
            if (atomicState.valid_subzones.findAll { it == sz.label }) {
                switch ("$atomicState.current_mode") {
                    case "Heating":
                        demand = sz.currentValue("heat_demand")
                        if (demand) {
                            sz.setState("Heating", demand + child_force)
                        } else {
                            accept = sz.currentValue("heat_accept")
                            if (child_force) {
                                sz.setState("Heating", accept + child_force)
                            } else {
                                if (accept) {
                                    Integer sub_flow = accept * child_accept / atomicState.child_heat_accept
                                    sz.setState("Heating", sub_flow)
                                } else {
                                    sz.setState("Off", 0)
                                }
                            }
                        }
                        break
                    case "Cooling":
                        demand = sz.currentValue("cool_demand")
                        if (demand) {
                            sz.setState("Cooling", demand + child_force)
                        } else {
                            accept = sz.currentValue("cool_accept")
                            if (child_force) {
                                sz.setState("Cooling", accept + child_force)
                            } else {
                                if (accept) {
                                    Integer sub_flow = accept * child_accept / atomicState.child_cool_accept
                                    sz.setState("Cooling", sub_flow)
                                } else {
                                    sz.setState("Off", 0)
                                }
                            }
                        }
                        break
                    case "Fan":
                        demand = sz.currentValue("fan_demand")
                        if (demand) {
                            sz.setState("Fan", demand + child_force)
                        } else {
                            if (child_force) {
                                sz.setState("Fan", child_force)
                            } else {
                                sz.setState("Off", 0)
                            }
                        }
                        break
                    case "Vent":
                        demand = sz.currentValue("fan_accept")
                        if (demand) {
                            sz.setState("Vent", demand + child_force)
                        } else {
                            if (child_force) {
                                sz.setState("Vent", child_force)
                            } else {
                                sz.setState("Off", 0)
                            }
                        }
                        break
                    case "Dehum":
                        demand = sz.currentValue("dehum_accept")
                        if (demand) {
                            sz.setState("Dehum", demand + child_force)
                        } else {
                            if (child_force) {
                                sz.setState("Dehum", child_force)
                            } else {
                                sz.setState("Off", 0)
                            }
                        }
                        break
                    case "Idle":
                        sz.setState("Idle", 0)
                        break
                    case "Off":
                        sz.setState("Off", 0)
                        break
                }
            }
        }
    }
}

def current_stateHandler(evt=NULL) {
    // this gets called when parent zone changes this zone's current_state or flow
    // log.debug("In Zone current_stateHandler()")
    runIn(2, "update_zone_state", [misfire: "ignore"])
}

Integer turn_on(Integer percentage=100) {
    // log.debug("In Zone turn_on($percentage)")
    switch ("$select_type") {
        case "Always selected":
            return 100
            break
        case "Normally Open Damper":
            select_switch.off()
            return 100
            break
        case "Normally Closed Damper or Duct Fan":
            select_switch.on()
            return 100
            break
        case "Proportional Damper":
            select_dimmer.setLevel(percentage)
            select_dimmer.on()
            return percentage
            break
    }
}

def turn_off() {
    // log.debug("In Zone turn_off()")
    switch ("$select_type") {
        case "Always selected":
            break
        case "Normally Open Damper":
            select_switch.on()
            break
        case "Normally Closed Damper or Duct Fan":
            select_switch.off()
            break
        case "Proportional Damper":
            select_dimmer.setLevel(0)
            select_dimmer.off()
            break
    }
}

def turn_idle() {
    // when the equipment is idle, including the blower, the zone switch is turned off, regardless of whether it is normally on or normally off
    // log.debug("In Zone turn_idle()")
    switch ("$select_type") {
        case "Always selected":
            break
        case "Proportional Damper":
            select_dimmer.off()
            break
        case "Normally Open Damper":
        case "Normally Closed Damper or Duct Fan":
            select_switch.off()
            break
    }
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
}

com.hubitat.app.ChildDeviceWrapper get_status_device() {
    com.hubitat.app.ChildDeviceWrapper status_device = getChildDevice(atomicState.status_DNI)
    return status_device
}

Boolean has_subzone(String child_zone) {
    // log.debug("In has_subzone($child_zone)")
    Boolean result = false
    if (subzones) {
        subzones.each { sz ->
            if (child_zone == sz.label) { result = true }
        }
    }
    return result
}

BigDecimal heat_delta_temp() {
    // log.debug("In heat_delta_temp()")
    def temp_state = stat.currentState("temperature")
    def heat_state = stat.currentState("heatingSetpoint")
    if ((temp_state) && (heat_state)) {
        temperature = temp_state.value as BigDecimal
        setpoint = heat_state.value as BigDecimal
        return setpoint - temperature
    } else {
        return 0
    }
}

BigDecimal cool_delta_temp() {
    // log.debug("In cool_delta_temp()")
    def temp_state = stat.currentState("temperature")
    def cool_state = stat.currentState("coolingSetpoint")
    if ((temp_state) && (cool_state)) {
        temperature = temp_state.value as BigDecimal
        setpoint = cool_state.value as BigDecimal
        return temperature - setpoint
    } else {
        return 0
    }
}
