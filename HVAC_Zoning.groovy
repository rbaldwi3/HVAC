/**
 *  HVAC Zoning
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
 * version 0.2 - 
 */

definition(
    name: "HVAC Zoning",
    namespace: "rbaldwi3",
    author: "Reid Baldwin",
    description: "This app controls HVAC zone dampers and HVAC equipment in response to multiple thermostats",
    category: "General",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)


preferences {
    page(name: "pageOne", nextPage: "pageTwo", uninstall: true) {
        section ("Zones") {
            app(name: "zones", appName: "HVAC Zone", namespace: "rbaldwi3", title: "Create New Zone", multiple: true, submitOnChange: true)
        }
        section ("Equipment Types") {
            input(name: "heat_type", type: "enum", required: true, title: "Heating Equipment Type", options: ["None","Single stage","Two stage"])
            input(name: "cool_type", type: "enum", required: true, title: "Cooling Equipment Type", options: ["None","Single stage","Two stage"])
            input(name: "vent_type", type: "enum", required: true, title: "Ventilation Equipment Type", options: ["None", "Requires Blower", "Doesn't Require Blower"])
        }
        section ("Wired Thermostat") {
            input "wired_tstat", "capability.thermostat", required: false, title: "Thermostat wired to Equipment (not required)"
        }
    }
    page(name: "pageTwo", title: "Equipment Data", install: true, uninstall: true)
}

def pageTwo() {
    dynamicPage(name: "pageTwo") {
        section ("Equipment Data") {
            switch ("$heat_type") {
                case "Single stage":
                    input "cfmW1", "number", required: true, title: "Airflow for heating (cfm)", range: "200 . . 3000"
                    break
                case "Two stage":
                    input "cfmW1", "number", required: true, title: "Airflow for heating stage 1 (cfm)", range: "200 . . 3000"
                    input "cfmW2", "number", required: true, title: "Airflow for heating stage 2 (cfm)", range: "200 . . 3000"
                    break
            }
            switch ("$cool_type") {
                case "Single stage":
                    input "cfmY1", "number", required: true, title: "Airflow for cooling (cfm)", range: "200 . . 3000"
                    break
                case "Two stage":
                    input "cfmY1", "number", required: true, title: "Airflow for cooling stage 1 (cfm)", range: "200 . . 3000"
                    input "cfmY2", "number", required: true, title: "Airflow for cooling stage 2 (cfm)", range: "200 . . 3000"
                    break
            }
            input "cfmG", "number", required: true, title: "Airflow for fan only (cfm)", range: "200 . . 3000"
        }
        section ("Equipment Control Switches") {
            switch ("$heat_type") {
                case "Single stage":
                    input "W1", "capability.switch", required: true, title: "Command Heat"
                    break
                case "Two stage":
                    input "W1", "capability.switch", required: true, title: "Command Heat stage 1"
                    input "W2", "capability.switch", required: true, title: "Command Heat stage 2"
                    break
            }
            switch ("$cool_type") {
                case "Single stage":
                    input "Y1", "capability.switch", required: true, title: "Command Cooling"
                    break
                case "Two stage":
                    input "Y1", "capability.switch", required: true, title: "Command Cooling stage 1"
                    input "Y2", "capability.switch", required: true, title: "Command Cooling stage 2"
                    break
            }
            if (wired_tstat) {
                input "fan_by_wired_tstat", "bool", required: true, title: "Wired Thermostat Controls Fan", default: false, submitOnChange: true
                if (!fan_by_wired_tstat) {
                    input "G", "capability.switch", required:true, title: "Command Fan"
                }
            } else {
                input "G", "capability.switch", required:true, title: "Command Fan"
            }
            switch ("$vent_type") {
                case "Requires Blower":
                case "Doesn't Require Blower":
                    input "V", "capability.switch", required:true, title: "Command Ventilation Equipment"
            }
            input "over_pressure", "capability.switch", required:false, title: "Excessive Pressure Indicator (optional)"
        }
        section ("Control Parameters") {
            mode_change_delay = 10
            switch ("$heat_type") {
                case "Single stage":
                case "Two stage":
                switch ("$cool_type") {
                    case "Single stage":
                    case "Two stage":
                        input "mode_change_delay", "number", required: true, title: "Minimum time between heating and cooling (minutes)", range: "1 . . 300"
                }
            }
            switch ("$heat_type") {
                case "Two stage":
                    input "heat_stage2_delay", "number", required: true, title: "Time in stage 1 heating to trigger stage 2 (minutes)", range: "0 . . 300", default: "30"
                    input "heat_stage2_deltat", "number", required: true, title: "Temperature difference from setpoint to trigger stage 2 heating", range: "1 . . 100", default: "2"
                    break
            }
            switch ("$cool_type") {
                case "Two stage":
                    input "cool_stage2_delay", "number", required: true, title: "Time in stage 1 cooling to trigger stage 2 (minutes)", range: "0 . . 300", default: "30"
                    input "cool_stage2_deltat", "number", required: true, title: "Temperature difference from setpoint to trigger stage 2 cooling", range: "1 . . 100", default: "2"
                    break
            }
            switch ("$vent_type") {
                case "Requires Blower":
                case "Doesn't Require Blower":
                    input "vent_control", "capability.switchLevel", required:true, title: "Ventilation Control - Use dimmer to set percent runtime and on/off"
                    input "vent_force", "capability.switch", required:false, title: "Spot Ventilation Control - turn on to temporarily force ventilation on (e.g. bathroom vent)"
            }
            // time interval for polling in case any signals missed
            input(name:"output_refresh_interval", type:"enum", required: true, title: "Output refresh", options: ["None","Every 5 minutes","Every 10 minutes",
                                                                                                                 "Every 15 minutes","Every 30 minutes"]) 
            input(name:"input_refresh_interval", type:"enum", required: true, title: "Input refresh", options: ["None","Every 5 minutes","Every 10 minutes",
                                                                                                                 "Every 15 minutes","Every 30 minutes"]) 
            input "status", "device.HVACZoningStatus", required: false, title: "Status Reporting Device (Optional)"
        }
    }
}

def installed() {
    // log.debug("In installed()")
    state.heating_mode = true  // false implies cooling mode, this indicates which equipment ran most recently but doesn't imply either is currently running
    // these three variables indicate if a request is currently being served.  At most one should be true at a time.
    state.heating_equipment_running = false
    state.cooling_equipment_running = false
    state.fan_running_by_request = false
    // These variables indicate the timing of the last cooling and heating equipment runs.
    // When equipment is running start time > stop time. Otherwise, stop time > start time.
    state.last_cooling_start = now() - 1000*60*30
    state.last_cooling_stop = now() - 1000*60*30
    state.last_heating_start = now() - 1000*60*30
    state.last_heating_stop = now() - 1000*60*30
    // These two variables indicate the beginning and ending of the current ventilation interval.
    // When ventilation is on, start time is in the past and end time is in the future.
    state.vent_interval_start = now() - 1000*60*30
    state.vent_interval_end = now() - 1000*60*20
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
    child_updated()
    // Subscribe to state changes for inputs and set state variables to reflect their current values
    switch ("$vent_type") {
        case "Requires Blower":
        case "Doesn't Require Blower":
            subscribe(vent_control, "switch.on", vent_control_activated)
            subscribe(vent_control, "switch.off", vent_control_deactivated)
            def currentvalue = vent_control.currentValue("switch")
            switch ("$currentvalue") {
                case "on":
                    state.vent_state = "Complete"
                    runEvery1Hour(start_vent_interval)
                    break;
                case "off":
                    state.vent_state = "Off"
                    break;
            }
            turn_off_vent()
            if (vent_force) {
                subscribe(vent_force, "switch.on", vent_force_activated)
                subscribe(vent_force, "switch.off", vent_force_deactivated)
                currentvalue = vent_force.currentValue("switch")
                switch ("$currentvalue") {
                    case "on":
                        state.vent_state = "Forced"
                        turn_on_vent()
                        break;
                }
            }
            if (status) {
                status.setventState("$state.vent_state")
            }
    }
    if (over_pressure) {
        subscribe(over_pressure, "switch.on", over_pressure_stage1)
        state.over_pressure_time = now() - 5*60*1000
    }
    if (wired_tstat) {
        subscribe(stat, "thermostatOperatingState", wired_tstatHandler)
        wired_tstatHandler() // sets wired mode and calls zone_call_changed()
    } else {
        fan_by_wired_tstat = false
        state.wired_mode = "none"
        // call zone_call_changed() to put outputs in a state consistent with the current inputs
        zone_call_changed()
    }
    // schedule any periodic refreshes
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
    // log.debug("In refresh_outputs()")
    if (W1) {
        if (state.heating_equipment_running) {
            W1.on()
            if (W2) {
                stage2_heat()
            }
        } else {
            W1.off()
        }
    }
    if (Y1) {
        if (state.cooling_equipment_running) {
            Y1.on()
            if (Y2) {
                stage2_cool()
            }
        } else {
            Y1.off()
        }
    }
    if (V) {
        switch ("$state.vent_state") {
            case "End_Phase":
            case "Running":
            case "Forced":
                V.on()
                if (state.fan_running_by_request || ("$vent_type" == "Requires Blower")) {
                    switch_fan_on()
                } else {
                    switch_fan_off()
                }
                break
            case "Complete":
            case "Off":
            case "Waiting":
                V.off()
                if (state.fan_running_by_request || state.cooling_equipment_running) {
                    switch_fan_on()
                } else {
                    switch_fan_off()
                }
                break
        }
    }
}

def refresh_inputs() {
    // log.debug("In refresh_inputs()")
    if (vent_control.hasCapability("refresh")) {
        vent_control.refresh()
    }
    if (vent_force) {
        if (vent_force.hasCapability("refresh")) {
            vent_force.refresh()
        }
    }
    switch ("$vent_type") {
        case "Requires Blower":
        case "Doesn't Require Blower":
            def vent_value = vent_control.currentValue("switch")
            def force_value = "off"
            if (vent_force) {
                force_value = vent_force.currentValue("switch")
            }
            log.debug("vent_state is $state.vent_state, vent_value is $vent_value, force_value is $force_value")
            switch ("$state.vent_state") {
                case "Forced":
                    if ("$force_value" != "on") {
                        vent_force_deactivated()
                    }
                    break
                case "Off":
                    if ("$force_value" == "on") {
                        vent_force_activated()
                        break
                    }
                    if ("$vent_value" != "off") {
                        vent_control_activated()
                    }
                    break
                case "Complete":
                case "Waiting":
                case "End_Phase":
                case "Running":
                    if ("$force_value" == "on") {
                        vent_force_activated()
                        break
                    }
                    if ("$vent_value" != "on") {
                        vent_control_deactivated()
                    }
            }
    }
}

def child_updated() {
    // log.debug("In child_updated()")
    // off_capacity is the airflow capacity of the system if all of the zones are unselected
    state.off_capacity = 0
    def zones = getChildApps()
    zones.each { z ->
        state.off_capacity += z.get_off_capacity();
    }
}

def wired_tstatHandler(evt=NULL) {
    // this routine is called if a zone which is hardwired to the equipment gets updated.
    // the purpose of the routine is to ensure that the app does not issue equipment calls inconsistent with the hardwired thermostat
    // log.debug("In wired_tstatHandler")
    def opstate = wired_tstat.currentValue("thermostatOperatingState")
    state.wired_mode = "$opstate.value"  // this variable is used in other routines to make sure a new equipment command is not inconsistent
    switch ("$state.value") {
        case "heating":
            turn_off_cool()
            state.heating_mode = true
            break
        case "cooling":
            turn_off_heat()
            state.heating_mode = false
            break
        case "fan only":
        case "idle":
            break
    }
    zone_call_changed() // zone_call_changed will be called twice, but I need to ensure that the last one is after this function
}

// This routine handles a change in status in a zone
// It also gets called back mode_change_delay minutes after heating or cooling is turned off to re-check whether opposite mode should be started

def zone_call_changed() {
    // log.debug("In zone_call_changed()")
    // heating, cooling, and fan demands come from thermostats in zones (or some times subzones) initiating a call
    // the value represents the cubic feet per minute of airflow desired
    state.cool_demand = 0
    state.heat_demand = 0
    state.fan_demand = 0
    // vent demand is the cubic feet per minute that zones would accept if the system goes into ventilation mode with no heating or cooling equipment running
    state.vent_demand = 0
    def zones = getChildApps()
    zones.each { z ->
        state.cool_demand += z.get_cool_demand()
        state.heat_demand += z.get_heat_demand()
        state.fan_demand += z.get_fan_demand()
        state.vent_demand += z.get_vent_demand()
    }
    log.debug("cool_demand = $state.cool_demand, heat_demand = $state.heat_demand, fan_demand = $state.fan_demand, vent_demand = $state.vent_demand")
    // if heating equipment has been used more recently than cooling equipment, then state.heating_mode is true
    // if cooling equipment has been used more recently than heating equipment, then state.heating_mode is false
    if (state.heating_mode) { // in heating mode, heating gets priority over cooling
       // check for servable heat call
       switch ("$heat_type") {
            case "Single stage":
            case "Two stage":
                if (state.heat_demand > 0) {
                    if (state.heat_demand+state.off_capacity >= cfmW1) {
                        serve_heat_call()
                        return
                    }
                }
                break
        }
        turn_off_heat()
        // check for servable cooling call
        switch ("$cool_type") {
            case "Single stage":
            case "Two stage":
                if (state.cool_demand > 0) {
                    if (state.cool_demand+state.off_capacity >= cfmY1) {
                        if(now()-state.last_heating_stop >= mode_change_delay * 60 * 1000) {
                            serve_cool_call()
                            return
                        }
                    }
                }
                break
        }
    } else { // not in heating mode - cooling gets priority over heating
        // check for servable cooling call
        switch ("$cool_type") {
            case "Single stage":
            case "Two stage":
                if (state.cool_demand > 0) {
                    if (state.cool_demand+state.off_capacity >= cfmY1) {
                        serve_cool_call()
                        return
                    }
                }
                break
        }
        turn_off_cool()
       // check for servable heat call
       switch ("$heat_type") {
            case "Single stage":
            case "Two stage":
                if (state.heat_demand > 0) {
                    if (state.heat_demand+state.off_capacity >= cfmW1) {
                        if(now()-state.last_cooling_stop >= mode_change_delay * 60 * 1000) {
                            serve_heat_call()
                            return
                        }
                    }
                }
                break
        }
    }
    // if this point is reached, then there is no servable heating or cooling call, heating and cooling equipment are off
    runIn(1, equipment_off_adjust_vent)
    // re-calling zone_call_changed after mode_change_delay is causing extraneous call to equipment_off_adjust_vent but not causing a problem
    // check for servable fan call
    if (state.fan_demand+state.off_capacity >= cfmG) {
        serve_fan_call()
        return
    } else {
        turn_off_fan() // this function will not turn off the fan if it is needed for ventilation
    }
}

// Routines for handling heating

def serve_heat_call() {
    // log.debug("In serve_heat_call()")
    state.heating_mode = true
    // turn on zones with heat call and turn off others
    def zones = getChildApps()
    zones.each { z ->
        if (z.get_heat_demand() > 0) {
            z.turn_on("heating")
        } else {
            z.turn_off()
        }
    }
    turn_on_heat()
}

def turn_on_heat() {
    // log.debug("In turn_on_heat()")
    // check consistency with hardwired connection
    switch("$state.wired_mode") {
        case "cooling":
            return
    }
    // turn on heating equipment if not already on
    if (!state.heating_equipment_running) {
        state.last_heating_start = now()
        W1.on()
        state.heating_equipment_running = true
        runIn(1, equipment_on_adjust_vent) // this updates the ventilation state if necessary
    }
    stage2_heat()
}

def stage2_heat() {
    // log.debug("In stage2_heat()")
    if (!state.heating_equipment_running) {
        return
    }
    switch ("$heat_type") {
        case "Two stage":
            if (state.heat_demand + state.off_capacity < cfmW2) {
                W2.off()
            } else if (now() - state.last_heating_start > heat_stage2_delay * 60 * 1000) {
                W2.on()
            } else {
                W2.off()
                Long time = (heat_stage2_delay * 60 - (now() - state.last_heating_start) / 1000)
                runIn(time, stage2_heat) // re-check when it has been long enough
                def zones = getChildApps()
                zones.each { z ->
                    if (z.get_heat_demand() > 0) {
                        if (z.get_heat_setpoint() - z.get_temp() >= heat_stage2_deltat) {
                            W2.on()
                            return
                        }
                    }
                }
            }
    }
}

def turn_off_heat() {
    // log.debug("In turn_off_heat()")
    if (state.heating_equipment_running) {
        state.last_heating_stop = now()
        switch ("$heat_type") {
            case "Two stage":
                W2.off()
            case "Single stage":
                W1.off()
        }
        state.heating_equipment_running = false
        runIn(60 * mode_change_delay, zone_call_changed) // this makes sure that a cooling call that may be waiting gets served
    }
}

// Routines for handling cooling

def serve_cool_call() {
    // log.debug("In serve_cool_call()")
    state.heating_mode = false
    // turn on zones with cooling call and turn off others
    def zones = getChildApps()
    zones.each { z ->
        if (z.get_cool_demand() > 0) {
            z.turn_on("cooling")
        } else {
            z.turn_off()
        }
    }
    turn_on_cool()
}

def turn_on_cool() {
    // log.debug("In turn_on_cool()")
    // check consistency with hardwired connection
    switch("$state.wired_mode") {
        case "heating":
            return
    }
    // turn on cooling equipment if not already on
    if (!state.cooling_equipment_running) {
        state.last_cooling_start = now()
        switch_fan_on()
        Y1.on()
        state.cooling_equipment_running = true
        runIn(1, equipment_on_adjust_vent)
    }
    stage2_cool()
}

def stage2_cool() {
    // log.debug("In stage2_cool()")
    if (!state.cooling_equipment_running) {
        return
    }
    switch ("$cool_type") {
        case "Two stage":
            if (state.cool_demand + state.off_capacity < cfmY2) {
                Y2.off()
            } else if (now() - state.last_cooling_start > cool_stage2_delay * 60 * 1000) {
                Y2.on()
            } else {
                Y2.off()
                Long time = (cool_stage2_delay * 60 - (now() - state.last_cooling_start) / 1000)
                runIn(time, stage2_cool) // re-check when it has been long enough
                def zones = getChildApps()
                zones.each { z ->
                    if (z.get_cool_demand() > 0) {
                        if (z.get_temp() - z.get_cool_setpoint() >= cool_stage2_deltat) {
                            Y2.on()
                            return
                        }
                    }
                }
            }
    }
}

def turn_off_cool() {
    // log.debug("In turn_off_cool()")
    if (state.cooling_equipment_running) {
        state.last_cooling_stop = now()
        switch ("$cool_type") {
            case "Two stage":
                Y2.off()
            case "Single stage":
                Y1.off()
        }
        if (!state.fan_running_by_request) {
            turn_off_fan()
        }
        state.cooling_equipment_running = false
        runIn(60 * mode_change_delay, zone_call_changed)
    }
}

// Routines for handling fan only commands

def serve_fan_call() {
    // log.debug("In serve_fan_call()")
    // turn on proper zones and turn off others
    def zones = getChildApps()
    if ("$vent_type" == "Requires Blower") {
        switch ("$state.vent_state") {
            case "Forced":
            case "End_Phase":
            case "Running":
                // blower is already on serving ventilation, open zones with either fan call or vent call
                zones.each { z ->
                    if ((z.get_fan_demand() > 0) || (z.get_vent_demand() > 0)) {
                        z.turn_on("fan only")
                    } else {
                        z.turn_off()
                    }
                }
                return
        }
    }
    // blower is not already serving ventilation, open only zones with fan call and then turn on blower
    zones.each { z ->
        if (z.get_fan_demand() > 0) {
            z.turn_on("fan only")
        } else {
            z.turn_off()
        }
    }
    turn_on_fan()
}

// switch_fan_on() and switch_fan_off change either the fan switch or the wired thermostat

def switch_fan_on() {
    // log.debug("In switch_fan_on()")
    if (fan_by_wired_tstat) {
        wired_tstat.fanOn()
    } else {
        G.on()
    }
}

def switch_fan_off() {
    // log.debug("In switch_fan_off()")
    if (fan_by_wired_tstat) {
        wired_tstat.fanAuto()
    } else {
        G.off()
    }
}

// turn_on_fan() and turn_off_fan() are called when fan is requested from a manual change to a thermostat
// turn_fan_off() is also called to turn off the fan, if appropriate, at the end of a cooling call
// they should not be called to turn on the fan for ventilation

def turn_on_fan() {
    // log.debug("In turn_on_fan()")
    state.fan_running_by_request = true;
    switch_fan_on()
}

def turn_off_fan() {
    // log.debug("In turn_off_fan()")
    state.fan_running_by_request = false;
    if ("$vent_type" != "Requires Blower") {
        switch_fan_off()
        return
    }
    switch ("$state.vent_state") {
        case "Complete":
        case "Off":
        case "Waiting":
            switch_fan_off()
            break
        case "Forced":
        case "End_Phase":
        case "Running":
            // in these states, leave fan on because it is needed for ventilation
            break
    }
}

// Ventilation Control Routines
// The ventilation functionality runs ventilation for a specified fraction of each hour.  The function start_vent_interval gets called at the beginning
// of each interval.  Ventilation can be turned off.  Ventilation can also be forced to run.
// The variable state.vent_runtime represents the number of minutes that is still needed in the current interval.  stat.vent_runtime is
// updated during changes (not continuously).
// The variable state.vent_state indicates the current state.  Possible values are:
// "Off": which means that the user has turned ventilation off
// "Forced": which means that the user has forced ventilation to run, regardless of equipment state of how much it has already run recently
// "Waiting": which means ventilation has not yet run enough during the present interval, but the app is waiting for equipment to run so
// that ventilation can be done during a cooling or heating call.  In this state, a call to vent_deadline_reached() is scheduled for when
// ventilation should start even if there is no heating or cooling call.
// "Complete": which means ventilation has already run enough during the present interval
// "Running": which means ventilation is running during a heating or cooling call.  In this state, a call to vent_runtime_reached() is scheduled
// for when ventilation will have run enough in this interval and should be stopped even if the heating or cooling call continues.
// "End_Phase": which means that ventilation needs to run for the remainder of the interval in order to run enough during the present interval


def turn_on_vent() {  
    // log.debug("In turn_on_vent()")
    // this function is only called when ventilation requires the blower
    switch ("$vent_type") {
        case "Doesn't Require Blower":
        case "None":
            log.debug("*** turn_on_vent() should only be called if ventilation requires the blower")
    }
    switch ("$state.vent_state") {
        case "Complete":
        case "Off":
        case "Waiting":
            log.debug("turn_on_vent() should not have been called in $state.vent_state state")
            return
        case "Running":
            break // do not adjust zone selection
        case "Forced":
            if (state.heating_equipment_running || state.cooling_equipment_running) {
                break // do not adjust zone selection
            }
        case "End_Phase":
            // turn on zones
            def zones = getChildApps()
            if (state.vent_demand+state.off_capacity >= cfmG) {
                // demand is high enough with only zones calling for vent and fan
                zones.each { z ->
                    if (z.get_on_for_vent()  || z.get_fan_demand()) {
                         z.turn_on("vent")
                    } else {
                         z.turn_off()
                    }
                }
            } else {
                // not enough capacity in zones calling for vent, so turn on all zones
                zones.each { z ->
                    z.turn_on("vent")
                }
            }
    }
    V.on()
    switch_fan_on()
}

def turn_off_vent() {
    // log.debug("In turn_off_vent")
    V.off()
    if (state.heating_equipment_running || state.cooling_equipment_running) {
        return
    }
    if (state.fan_running_by_request) {
        serve_fan_call()
    } else {
        switch_fan_off()
        def zones = getChildApps()
        zones.each { z ->
            z.turn_idle()
        }
    }
}

def start_vent_interval() {
    // log.debug("In start_vent_interval()")
    unschedule(vent_runtime_reached)
    unschedule(vent_deadline_reached)
    state.vent_interval_start = now()
    state.vent_interval_end = now() + 60*60*1000
    // determine how many minutes ventilation should run per interval, based on dimmer setting
    // any change to the dimmer during the interval has no effect until the next interval
    def levelstate = vent_control.currentState("level")
    Integer percent = levelstate.value as Integer
    Integer runtime = 60 * 60 * percent / 100
    state.vent_runtime = runtime
    // log.debug("runtime is $state.vent_runtime")
    if ("$state.vent_state" == "Forced") {
        // log.debug("Still in forced vent state")
        return;
    }
    switch ("$vent_type") {
        case "Doesn't Require Blower":
            V.on()
            // log.debug("vent on - Running")
            state.vent_state = "Running"
            runIn(state.vent_runtime, vent_runtime_reached)
            break;
        case "Requires Blower":
            if(state.heating_equipment_running || state.cooling_equipment_running) {
                state.vent_started = now()
                // log.debug("vent on - Running")
                state.vent_state = "Running"
                turn_on_vent()
                runIn(state.vent_runtime, vent_runtime_reached)
            } else {
                // log.debug("vent off - Waiting")
                state.vent_state = "Waiting"
                turn_off_vent()
                runIn(60 * 60 - state.vent_runtime, vent_deadline_reached)
            }
            break;
    }
    if (status) {
        status.setventState("$state.vent_state")
    }
}

def vent_force_activated(evt=NULL) {
    // log.debug("In vent_force_activated()")
    unschedule(vent_runtime_reached)
    unschedule(vent_deadline_reached)
    // update runtime so far for use when forced ventilation stops
    Integer runtime
    switch ("$state.vent_state") {
        case "End_Phase":
        case "Running":
            runtime = state.vent_runtime - (now() - state.vent_started) / 1000
            break
        case "Complete":
            runtime = 60*60 // actually less but this works
            break
        case "Off":
            runtime = 0
            break
        case "Forced":
        case "Waiting":
            runtime = state.vent_runtime
            break
    }
    state.vent_runtime = runtime
    state.vent_state = "Forced"
    state.vent_started = now()
    switch ("$vent_type") {
        case "Requires Blower":
            turn_on_vent()
            break
        case "Doesn't Require Blower":
            V.on()
    }
    if (status) {
        status.setventState("$state.vent_state")
    }
}

def vent_force_deactivated(evt=NULL) {
    // log.debug("In vent_force_deactivated()")
    def switchstate = vent_control.currentState("switch")
    if (switchstate.value == "on") {
        if (state.vent_started > state.vent_interval_start) {
            // still in same interval as when force ventilation started
            Integer runtime = state.vent_runtime
            // log.debug("runtime is $runtime")
            runtime -= (now() - state.vent_started) / 1000
            state.vent_runtime = runtime
        } else {
            // we have been in forced ventilation for the entire interval up to this point
            def levelstate = vent_control.currentState("level")
            Integer percent = levelstate.value as Integer
            Integer runtime = 60 * 60 * percent / 100
            runtime -= (now() - state.vent_interval_start) / 1000
            state.vent_runtime = runtime
        }
        if (state.vent_runtime <= 0) {
            // log.debug("vent off - Complete")
            state.vent_state = "Complete"
            turn_off_vent()
        } else {
            switch ("$vent_type") {
                case "Doesn't Require Blower":
                    V.on()
                    // log.debug("vent on - Running")
                    state.vent_state = "Running"
                    runIn(state.vent_runtime, vent_runtime_reached)
                    break;
                case "Requires Blower":
                    if(state.heating_equipment_running || state.cooling_equipment_running) {
                        // log.debug("vent on - Running")
                        state.vent_started = now()
                        state.vent_state = "Running"
                        turn_on_vent()
                        runIn(state.vent_runtime, vent_runtime_reached)
                    } else {
                        // log.debug("vent off - Waiting")
                        state.vent_state = "Waiting"
                        turn_off_vent()
                        Integer deadline = (state.vent_interval_end - now()) / 1000 - state.vent_runtime
                        runIn(deadline, vent_deadline_reached)
                    }
                    break;
            }
        }
    } else {
        // log.debug("vent off - Off")
        state.vent_state = "Off"
        turn_off_vent()
    }
    if (status) {
        status.setventState("$state.vent_state")
    }
}

def vent_control_activated(evt=NULL) {
    // log.debug("In vent_control_activated()")
    // schedule vent intervals every hour with the first one starting 5 seconds from now
    Long time = now() + 5000
    Long seconds = time / 1000
    Long minutes = seconds / 60
    Long hours = minutes / 60
    seconds = seconds - (minutes * 60)
    minutes = minutes - (hours * 60)
    // log.debug("$minutes minutes, $seconds seconds")
    schedule_str = "$seconds $minutes * ? * *"
    schedule(schedule_str, start_vent_interval)
    // In 5 seconds, start_vent_interval() will do the work of putting everything into the correct state
}

def vent_control_deactivated(evt=NULL) {
    // log.debug("In vent_control_deactivated()")
    unschedule(vent_runtime_reached)
    unschedule(vent_deadline_reached)
    unschedule(start_vent_interval)
    if ("$state.vent_state" == "Forced") {
        // log.debug("Still in forced vent state")
    } else {
        state.vent_state = "Off"
        turn_off_vent()
    }
    if (status) {
        status.setventState("$state.vent_state")
    }
}

def vent_runtime_reached() {
    // log.debug("In vent_runtime_reached()")
    state.vent_state = "Complete"
    turn_off_vent()
    if (status) {
        status.setventState("$state.vent_state")
    }
}

def vent_deadline_reached() {
    // log.debug("In vent_deadline_reached()")
    state.vent_state = "End_Phase"
    turn_on_vent()
    if (status) {
        status.setventState("$state.vent_state")
    }
}

def equipment_off_adjust_vent() {
    // this is called any time that the heating and cooling equipment transitions from on to off
    // it may also be called while equipment is off, such as extraneous call from mode_change_delay
    // it should not be called with equipment on, although the blower may be on serving a fan only call
    // log.debug("In equipment_off_adjust_vent()")
    if(state.heating_equipment_running || state.cooling_equipment_running) {
        log.debug("*** equipment_off_adjust_vent() should not be called with equipment running")
    }
    switch ("$vent_type") {
        case "Doesn't Require Blower":
        case "None":
        case "Requires Blower":
            switch ("$state.vent_state") {
                case "Complete":
                case "Waiting":
                case "Off":
                    if (!state.fan_running_by_request) {
                       def zones = getChildApps()
                       zones.each { z ->
                            z.turn_idle()
                        }
                    }
                    break
                case "End_Phase":
                case "Forced":
                    turn_on_vent() // may already be on, but this adjusts the zone selections correctly
                    break;
                case "Running":
                    // log.debug("vent off - Waiting")
                    state.vent_state = "Waiting"
                    unschedule(vent_runtime_reached)
                    turn_off_vent()
                    Integer runtime = state.vent_runtime - (now() - state.vent_started) / 1000
                    state.vent_runtime = runtime
                    // log.debug("runtime is $state.vent_runtime")
                    Integer deadline = (state.vent_interval_end - now()) / 1000 - runtime
                    runIn(deadline, vent_deadline_reached)
                    break;
            }
    }
    if (status) {
        status.setventState("$state.vent_state")
    }
}

def equipment_on_adjust_vent() {
    // this is called any time that the heating and cooling equipment transitions from off to on
    // log.debug("In equipment_on_adjust_vent()")
    switch ("$vent_type") {
        case "Doesn't Require Blower":
        case "None":
            break;
        case "Requires Blower":
            switch ("$state.vent_state") {
                case "End_Phase":
                case "Complete":
                case "Running":
                case "Off":
                case "Forced":
                    break;
                case "Waiting":
                    // log.debug("vent on - Running")
                    state.vent_state = "Running"
                    state.vent_started = now()
                    unschedule(vent_deadline_reached)
                    turn_on_vent()
                    runIn(state.vent_runtime, vent_runtime_reached)
                    break;
            }
    }
    if (status) {
        status.setventState("$state.vent_state")
    }
}

// These routeins handle an over-pressure signal.  The zones are each instructed to adapt their capacity to avoid getting into over-pressure in the future
// If stage 2 is operating, it is turned off and over_pressure_stage2() is scheduled for 60 seconds later in case that is not sufficient. If stage 1
// only is operating, over_pressure_stage2() is called right away.  over_pressure_stage2() opens all of the zones, regardless of which ones are calling.

def over_pressure_stage1(evt=NULL) {
    // log.debug("In over_pressure_stage1()")
    // Handle over-pressure signal
    if (now() - state.over_pressure_time < 2*60*1000) { // don't respond to more than one over pressure per two minutes
        return
    }
    state.over_pressure_time = now()
    def zones = getChildApps()
    zones.each { z ->
        z.handle_overpressure()
    }
    child_updated()
    if (heat_mode) {
        switch ("$heat_type") {
            case "Two stage":
                def currentvalue = W2.currentValue("switch")
                switch ("$currentvalue") {
                    case "on":
                    W2.off()
                    runIn(60, over_pressure_stage2)
                    return
                }
        }
    } else {
        switch ("$cool_type") {
            case "Two stage":
                def currentvalue = Y2.currentValue("switch")
                switch ("$currentvalue") {
                    case "on":
                    Y2.off()
                    runIn(60, over_pressure_stage2)
                    return
                }
        }
    }
    over_pressure_stage2()
}

def over_pressure_stage2() {
    // log.debug("In over_pressure_stage2()")
    // if overpressure still persists, open all zones
    def currentvalue = over_pressure.currentValue("switch")
    switch ("$currentvalue") {
        case "on":
        def zones = getChildApps()
        zones.each { z ->
            z.turn_on()
        }
    }
}

def get_equipment_status() {
    if (heating_equipment_running) {
        return "heating"
    } else if (cooling_equipment_running) {
        return "cooling"
    } else if (fan_running_by_request) {
        return "fan only"
    } else {
        // need to adjust to handle vent_state
        return "idle"
    }
}
