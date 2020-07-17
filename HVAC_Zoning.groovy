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
            input "wired_tstat", "capability.thermostatOperatingState", required: false, title: "Thermostat wired to Equipment (not required)"
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
                case "Single stage":
                    input "heat_min_runtime", "number", required: true, title: "Minimum Heating Runtime (minutes)", range: "1 . . 30", default: "5"
                    input "heat_min_idletime", "number", required: true, title: "Minimum Heating Idle Time (minutes)", range: "1 . . 30", default: "5"
                    break
            }
            switch ("$cool_type") {
                case "Two stage":
                    input "cool_stage2_delay", "number", required: true, title: "Time in stage 1 cooling to trigger stage 2 (minutes)", range: "0 . . 300", default: "30"
                    input "cool_stage2_deltat", "number", required: true, title: "Temperature difference from setpoint to trigger stage 2 cooling", range: "1 . . 100", default: "2"
                case "Single stage":
                    input "cool_min_runtime", "number", required: true, title: "Minimum Cooling Runtime (minutes)", range: "1 . . 30", default: "5"
                    input "cool_min_idletime", "number", required: true, title: "Minimum Cooling Idle Time (minutes)", range: "1 . . 30", default: "5"
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
    state.equip_state = "Idle"
    state.vent_state = "Off"
    state.fan_state = "Off"
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
    // state.equip_state = "Idle"
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
            V.off()
            if (vent_force) {
                subscribe(vent_force, "switch.on", vent_force_activated)
                subscribe(vent_force, "switch.off", vent_force_deactivated)
                currentvalue = vent_force.currentValue("switch")
                switch ("$currentvalue") {
                    case "on":
                        state.vent_state = "Forced"
                        V.on()
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
    runEvery30Minutes(check_for_lockup)
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

// Equipment Control Routines
// The variable state.equip_state indicates the current state of heating and cooling equipment.  Possible values are:
// "Idle": which means that both heating and cooling equipment has been off for long enough that either heating or cooling calls could be served if they occur
// "IdleH": which means that both heating and cooling equipment is off and the heating equipment turned off long enough ago to serve a heat call (but not a cooling call)
// "PauseH": which means that both heating and cooling equipment is off, but the heating equipment turned off recently enough that new calls should not be served yet
// "Heating": which means that heating equipment is running and has been running long enough to turn if off when heating calls end
// "HeatingL": which means that heating equipment is running but has turn on recently enough that it needs to remain on even if a heating call ends
// "IdleC": which means that both heating and cooling equipment is off and the cooling equipment turned off long enough ago to serve a cooling call (but not a heat call)
// "PauseC": which means that both heating and cooling equipment is off, but the cooling equipment turned off recently enough that new calls should not be served yet
// "Cooling": which means that coolinging equipment is running and has been running long enough to turn if off when cooling calls end
// "CoolingL": which means that cooling equipment is running but has turn on recently enough that it needs to remain on even if a cooling call ends

// The variable state.fan_state indicates the current state of the blower.  Possible values are:
// "Off": blower is off
// "On_for_cooling": blower is commanded on because cooling is being commanded (user may also be requesting and ventilation may also be underway)
// "On_for_vent": blower is commanded on because ventilation is being commanded (user may also be requesting)
// "On_by_request": blower is commanded on due to fan call from a zone (ventilation and cooling off)

// The variable state.vent_state indicates the current state of the ventilation.  Possible values are:
// "Off": which means that the user has turned ventilation off
// "Forced": which means that the user has forced ventilation to run, regardless of equipment state of how much it has already run recently
// "Waiting": which means ventilation has not yet run enough during the present interval, but the app is waiting for equipment to run so
//   that ventilation can be done during a cooling or heating call.  In this state, a call to vent_deadline_reached() is scheduled for when
//   ventilation should start even if there is no heating or cooling call.
// "Complete": which means ventilation has already run enough during the present interval
// "Running": which means ventilation is running during a heating or cooling call.  In this state, a call to vent_runtime_reached() is scheduled
//   for when ventilation will have run enough in this interval and should be stopped even if the heating or cooling call continues.
// "End_Phase": which means that ventilation needs to run for the remainder of the interval in order to run enough during the present interval

def child_updated() {
    log.debug("In child_updated()")
    // off_capacity is the airflow capacity of the system if all of the zones are unselectedg
    state.off_capacity = 0
    def zones = getChildApps()
    zones.each { z ->
        state.off_capacity += z.get_off_capacity();
    }
}

def wired_tstatHandler(evt=NULL) {
    /* TO DO: I need to re-think the concept of a wired thermostat after I get the stat-machine equipment handling working
    // this routine is called if a zone which is hardwired to the equipment gets updated.
    // the purpose of the routine is to ensure that the app does not issue equipment calls inconsistent with the hardwired thermostat
    // log.debug("In wired_tstatHandler")
    def opstate = wired_tstat.currentValue("thermostatOperatingState")
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
    */
}

// This routine handles a change in status in a zone
// It also gets called by equipment_state_timeout() after time-based equipment state changes

def zone_call_changed() {
    log.debug("In zone_call_changed()")
    // heating, cooling, and fan demands come from thermostats in zones (or some times subzones) initiating a call
    // the value represents the cubic feet per minute of airflow desired
    state.cool_demand = 0
    state.heat_demand = 0
    state.fan_demand = 0
    // heat_accept and cool_accept represent the cubic feet per minute of airflow the zone volunteers to accept (as a dump zone)
    state.cool_accept = 0
    state.heat_accept = 0
    // vent demand is the cubic feet per minute that zones would accept if the system goes into ventilation mode with no heating or cooling equipment running
    state.vent_demand = 0
    def zones = getChildApps()
    zones.each { z ->
        state.cool_demand += z.get_cool_demand()
        state.heat_demand += z.get_heat_demand()
        state.fan_demand += z.get_fan_demand()
        state.vent_demand += z.get_vent_demand()
        state.cool_accept += z.get_cool_accept()
        state.heat_accept += z.get_heat_accept()
    }
    log.debug("cool_demand = $state.cool_demand, heat_demand = $state.heat_demand, cool_accept = $state.cool_accept, heat_accept = $state.heat_accept, fan_demand = $state.fan_demand, vent_demand = $state.vent_demand")
    log.debug("In state $state.equip_state")  
    // this section updates the state and, in heating and cooling modes, selects the zones and equipment
    switch ("$state.equip_state") {
        case "Idle":
            if (servable_heat_call()) {
                start_heat_run()
                return
            } else if (servable_cool_call()) {
                start_cool_run()
                return
            } else {
                update_fan_state()
                update_fan_zones()
            }
            break
        case "IdleH":
            if (servable_heat_call()) {
                start_heat_run()
                return
            } else {
                update_fan_state()
                update_fan_zones()
            }
            break
        case "PauseH":
            update_fan_state()
            update_fan_zones()
            break
        case "Heating":
            if (servable_heat_call()) {
                update_heat_run()
                return
            } else {
                end_heat_run()
                // fan state and zones will be updated after ventilation is adjusted
            }
            break
        case "HeatingL":
            update_heat_run()
            return
        case "IdleC":
            if (servable_cool_call()) {
                start_cool_run()
                return
            } else {
                update_fan_state()
                update_fan_zones()
            }
            break
        case "PauseC":
            update_fan_state()
            update_fan_zones()
            break
        case "Cooling":
            if (servable_cool_call()) {
                update_cool_run()
                return
            } else {
                end_cool_run()
                // fan state and zones will be updated after ventilation is adjusted
            }
            break
        case "CoolingL":
            update_cool_run()
            return
    }
}

def equipment_state_timeout() {
    log.debug("In equipment_state_timeout()")
    switch ("$state.equip_state") {
        case "Idle":
            log.debug("timeout in Idle mode shouldn't happen")
            break
        case "IdleH":
        case "IdleC":
            state.equip_state = "Idle"
            if (status) {
                status.setequipState("$state.equip_state")
            }
            zone_call_changed()
            break
        case "PauseH":
            if (mode_change_delay > heat_min_idletime) {
                state.equip_state = "IdleH"
                runIn((mode_change_delay-heat_min_idletime)*60, equipment_state_timeout) // this will cause a transition to Idle state at the right time 
                if (status) {
                    status.setequipState("$state.equip_state")
                }
            } else {
                state.equip_state = "Idle"
                if (status) {
                    status.setequipState("$state.equip_state")
                }
            }
            zone_call_changed()
            break
        case "PauseC":
            if (mode_change_delay > cool_min_idletime) {
                state.equip_state = "IdleC"
                runIn((mode_change_delay-cool_min_idletime)*60, equipment_state_timeout) // this will cause a transition to Idle state at the right time 
                if (status) {
                    status.setequipState("$state.equip_state")
                }
            } else {
                state.equip_state = "Idle"
                if (status) {
                    status.setequipState("$state.equip_state")
                }
            }
            zone_call_changed()
            break
        case "Heating":
            log.debug("timeout in Heating mode shouldn't happen")
            break
        case "HeatingL":
            state.equip_state = "Heating"
            if (status) {
                status.setequipState("$state.equip_state")
            }
            zone_call_changed()
            break
        case "Cooling":
            log.debug("timeout in Cooling mode shouldn't happen")
            break
        case "CoolingL":
            state.equip_state = "Cooling"
            if (status) {
                status.setequipState("$state.equip_state")
            }
            zone_call_changed()
            break
    }
}

def check_for_lockup() {
    // This function runs periodically and frees the system up if a call to equipment_state_timeout somehow got missed leaving the system stuck in a state
    log.debug("In check_for_lockup()")
    switch ("$state.equip_state") {
        case "PauseH":
            if ((now() - state.last_heating_stop) > heat_min_idletime*60*1000) {
                log.debug("Appears to be stuck in state $state.equip_state")
                runIn(5, "equipment_state_timeout")
            }
            break
        case "IdleH":
            if ((now() - state.last_heating_stop) > mode_change_delay*60*1000) {
                log.debug("Appears to be stuck in state $state.equip_state")
                runIn(5, "equipment_state_timeout")
            }
            break
        case "HeatingL":
            if ((now() - state.last_heating_start) > heat_min_runtime*60*1000) {
                log.debug("Appears to be stuck in state $state.equip_state")
                runIn(5, "equipment_state_timeout")
            }
            break
        case "PauseC":
            if ((now() - state.last_cooling_stop) > cool_min_idletime*60*1000) {
                log.debug("Appears to be stuck in state $state.equip_state")
                runIn(5, "equipment_state_timeout")
            }
            break
        case "IdleC":
            if ((now() - state.last_cooling_stop) > mode_change_delay*60*1000) {
                log.debug("Appears to be stuck in state $state.equip_state")
                runIn(5, "equipment_state_timeout")
            }
            break
        case "CoolingL":
            if ((now() - state.last_cooling_start) > cool_min_runtime*60*1000) {
                log.debug("Appears to be stuck in state $state.equip_state")
                runIn(5, "equipment_state_timeout")
            }
            break
    }   
}

// Routines for handling heating

Boolean servable_heat_call() {
    // check for servable heat call
    switch ("$heat_type") {
        case "Single stage":
        case "Two stage":
            if (state.heat_demand > 0) {
                 if (state.heat_demand+state.off_capacity >= cfmW1) {
                     return true;
                 }
            }
    }
    return false;
}

def start_heat_run() {
    // log.debug("In start_heat_run()")
    // turn on zones with heat call and turn off others
    def zones = getChildApps()
    zones.each { z ->
        if (z.get_heat_demand() > 0) {
            z.turn_on("heating")
        } else {
            z.turn_off()
        }
    }
    state.last_heating_start = now()
    W1.on()
    state.equip_state = "HeatingL"
    if (status) {
        status.setequipState("$state.equip_state")
    }
    runIn(1, equipment_on_adjust_vent) // this updates the ventilation state if necessary
    runIn(heat_min_runtime*60, equipment_state_timeout) // this will cause a transition to Heating state at the right time 
    switch ("$heat_type") {
        case "Two stage":
            runIn(heat_stage2_delay*60, stage2_heat) 
            stage2_heat()
    }
}

def stage2_heat() {
    // log.debug("In stage2_heat()")
    switch ("$state.equip_state") {
        case "Heating":
        case "HeatingL":
            switch ("$heat_type") {
                case "Two stage":
                    if (state.heat_demand + state.off_capacity < cfmW2) {
                        W2.off()
                        if (status) {
                            status.second_stage_off()
                        }
                    } else if (now() - state.last_heating_start > heat_stage2_delay * 60 * 1000) {
                        W2.on()
                        if (status) {
                            status.second_stage_on()
                        }
                    } else {
                        def zones = getChildApps()
                        zones.each { z ->
                            if ((z.get_heat_demand() > 0) && (z.full_thermostat())) {
                                if (z.get_heat_setpoint() - z.get_temp() >= heat_stage2_deltat) {
                                    W2.on()
                                    if (status) {
                                        status.second_stage_on()
                                    }
                                    return
                                }
                            }
                        }
                        W2.off()
                        if (status) {
                            status.second_stage_off()
                        }
                    }
            }
            break
    }
}

def update_heat_run() {
    // log.debug("In update_heat_run()")
    // TO DO: in HeatingL Mode, turn on zones with heat call and enough others to exceed the low stage heating airflow
    // In Heating Mode, turn on zones with heat call and turn off others
    def zones = getChildApps()
    zones.each { z ->
        if (z.get_heat_demand() > 0) {
            z.turn_on("heating")
        } else {
            z.turn_off()
        }
    }
    stage2_heat()
}

def end_heat_run() {
    // log.debug("In end_heat_run()")
    state.last_heating_stop = now()
    switch ("$heat_type") {
        case "Two stage":
            W2.off()
        case "Single stage":
            W1.off()
    }
    state.equip_state = "PauseH"
    if (status) {
        status.setequipState("$state.equip_state")
    }
    runIn(1, equipment_off_adjust_vent) // this updates the ventilation state if necessary
    runIn(heat_min_idletime*60, equipment_state_timeout) // this will cause a transition to IdleH state at the right time 
    switch ("$heat_type") {
        case "Two stage":
            stage2_heat()
    }
}

// Routines for handling cooling

Boolean servable_cool_call() {
    // check for servable cooling call
    switch ("$cool_type") {
        case "Single stage":
        case "Two stage":
            if (state.cool_demand > 0) {
                 if (state.cool_demand+state.off_capacity >= cfmY1) {
                     return true;
                 }
            }
    }
    return false;
}

def start_cool_run() {
    log.debug("In start_cool_run()")
    // turn on zones with cooling call and turn off others
    def zones = getChildApps()
    zones.each { z ->
        if (z.get_cool_demand() > 0) {
            z.turn_on("cooling")
        } else {
            z.turn_off()
        }
    }
    state.last_cooling_start = now()
    Y1.on()
    switch_fan_on()
    state.equip_state = "CoolingL"
    if (status) {
        status.setequipState("$state.equip_state")
    }
    runIn(1, equipment_on_adjust_vent) // this updates the ventilation state if necessary
    runIn(cool_min_runtime*60, equipment_state_timeout) // this will cause a transition to Cooling state at the right time 
    switch ("$cool_type") {
        case "Two stage":
            runIn(cool_stage2_delay*60, stage2_cool) 
            stage2_cool()
    }
}

def stage2_cool() {
    log.debug("In stage2_cool()")
    switch ("$state.equip_state") {
        case "Cooling":
        case "CoolingL":
            switch ("$cool_type") {
                case "Two stage":
                    if (state.cool_demand + state.off_capacity < cfmY2) {
                        Y2.off()
                        if (status) {
                            status.second_stage_off()
                        }
                    } else if (now() - state.last_cooling_start > cool_stage2_delay * 60 * 1000) {
                        Y2.on()
                        if (status) {
                            status.second_stage_on()
                        }
                    } else {
                        def zones = getChildApps()
                        zones.each { z ->
                            if ((z.get_cool_demand() > 0) && (z.full_thermostat())) {
                                if (z.get_temp() - z.get_cool_setpoint() >= cool_stage2_deltat) {
                                    Y2.on()
                                    if (status) {
                                        status.second_stage_on()
                                    }
                                    return
                                }
                            }
                        }
                        Y2.off()
                        if (status) {
                            status.second_stage_off()
                        }
                    }
            }
            break
    }
}

def update_cool_run() {
    log.debug("In update_cool_run()")
    // TO DO: in CoolingL Mode, turn on zones with cooling call and enough others to exceed the low stage cooling airflow
    // In Cooling Mode, turn on zones with cool call and turn off others
    def zones = getChildApps()
    zones.each { z ->
        if (z.get_cool_demand() > 0) {
            z.turn_on("cooling")
        } else {
            z.turn_off()
        }
    }
    switch ("$cool_type") {
        case "Two stage":
            stage2_cool()
    }
}

def end_cool_run() {
    log.debug("In end_cool_run()")
    state.last_cooling_stop = now()
    switch ("$cool_type") {
        case "Two stage":
            Y2.off()
        case "Single stage":
            Y1.off()
    }
    state.equip_state = "PauseC"
    if (status) {
        status.setequipState("$state.equip_state")
    }
    runIn(5, equipment_off_adjust_vent) // this updates the ventilation state if necessary (extra time because thermostat sometimes stays in fan_only for a few seconds between cooling and idle)
    runIn(cool_min_idletime*60, equipment_state_timeout) // this will cause a transition to IdleC state at the right time 
}

// Routines for handling fan only commands

def update_fan_state() {
    log.debug("In update_fan_state()")
    switch ("$state.equip_state") {
        case "Idle":
        case "IdleH":
        case "PauseH":
        case "IdleC":
        case "PauseC":
            if ("$vent_type" == "Requires Blower") {
                switch ("$state.vent_state") {
                    case "Forced":
                    case "End_Phase":
                    case "Running":
                    state.fan_state = "On_for_vent"
                    if (status) {
                        status.setfanState("$state.fan_state")
                    }
                    return
                }
            }
            if (state.fan_demand > 0) {
                 if (state.fan_demand+state.off_capacity >= cfmG) {
                     state.fan_state = "On_by_request"
                     if (status) {
                         status.setfanState("$state.fan_state")
                     }
                     return
                 }
            }
            state.fan_state = "Off"
            if (status) {
                status.setfanState("$state.fan_state")
            }
            return
        case "Heating":
        case "HeatingL":
            // leave fan_state wherever it was when heating equipment is running
            return
        case "Cooling":
        case "CoolingL":
            state.fan_state = "On_for_cooling"
            if (status) {
                status.setfanState("$state.fan_state")
            }
            return
    }
}

def update_fan_zones() {
    log.debug("In update_fan_zones()")
    // select the correct zones and turn the fan on or off if needed
    switch ("$state.equip_state") {
        case "Heating":
        case "HeatingL":
            // don't update zones or fan command during heating call 
            return
    }
    def zones = getChildApps()
    switch ("$state.fan_state") {
        case "On_for_cooling":
            switch_fan_on()
            break
        case "On_for_vent": 
            if (state.vent_demand >= cdfG) {
                zones.each { z ->
                    if (z.get_vent_demand() > 0) {
                        z.turn_on("fan only")
                    } else {
                        z.turn_off()
                    }
                }
            } else {
                zones.each { z ->
                    z.turn_on("fan only")
                }
            }
            switch_fan_on()
            break
        case "On_by_request":
            zones.each { z ->
                if (z.get_fan_demand() > 0) {
                    z.turn_on("fan only")
                } else {
                    z.turn_off()
                }
            }
            switch_fan_on()
            break
        case "Off":
            zones.each { z ->
                z.turn_idle()
            }
            switch_fan_off()
            break
    }
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

// Ventilation Control Routines
// The ventilation functionality runs ventilation for a specified fraction of each hour.  The function start_vent_interval gets called at the beginning
// of each interval.  Ventilation can be turned off.  Ventilation can also be forced to run.
// The variable state.vent_runtime represents the number of minutes that is still needed in the current interval.  state.vent_runtime is
// updated during changes (not continuously).

def start_vent_interval() {
    log.debug("In start_vent_interval()")
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
    log.debug("runtime is $state.vent_runtime")
    if ("$state.vent_state" == "Forced") {
        // log.debug("Still in forced vent state")
        return;
    }
    switch ("$vent_type") {
        case "Doesn't Require Blower":
            // log.debug("vent on - Running")
            state.vent_state = "Running"
            V.on()
            runIn(state.vent_runtime, vent_runtime_reached)
            if (status) {
                status.setventState("$state.vent_state")
            }
            break;
        case "Requires Blower":
            switch ("$state.equip_state") {
                case "Idle":
                case "IdleH":
                case "PauseH":
                case "IdleC":
                case "PauseC":
                    log.debug("vent off - Waiting")
                    state.vent_state = "Waiting"
                    V.off()
                    update_fan_state()
                    update_fan_zones()
                    runIn(60 * 60 - state.vent_runtime, vent_deadline_reached)
                    if (status) {
                        status.setventState("$state.vent_state")
                    }
                    break
                case "Heating":
                case "HeatingL":
                case "Cooling":
                case "CoolingL":
                    state.vent_started = now()
                    log.debug("vent on - Running")
                    state.vent_state = "Running"
                    V.on()
                    runIn(state.vent_runtime, vent_runtime_reached)
                    if (status) {
                        status.setventState("$state.vent_state")
                    }
                    break
            }
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
    V.on()
    switch ("$vent_type") {
        case "Requires Blower":
            update_fan_state()
            update_fan_zones()
            break
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
            V.off()
            switch ("$vent_type") {
                case "Requires Blower":
                    update_fan_state()
                    update_fan_zones()
            }
        } else {
            switch ("$vent_type") {
                case "Doesn't Require Blower":
                    V.on()
                    // log.debug("vent on - Running")
                    state.vent_state = "Running"
                    runIn(state.vent_runtime, vent_runtime_reached)
                    break;
                case "Requires Blower":
                    switch ("$state.equip_state") {
                        case "Idle":
                        case "IdleH":
                        case "PauseH":
                        case "IdleC":
                        case "PauseC":
                            log.debug("vent off - Waiting")
                            state.vent_state = "Waiting"
                            V.off()
                            update_fan_state()
                            update_fan_zones()
                            Integer deadline = (state.vent_interval_end - now()) / 1000 - state.vent_runtime
                            runIn(deadline, vent_deadline_reached)
                            if (status) {
                                status.setventState("$state.vent_state")
                            }
                            break
                        case "Heating":
                        case "HeatingL":
                        case "Cooling":
                        case "CoolingL":
                            log.debug("vent on - Running")
                            state.vent_started = now()
                            state.vent_state = "Running"
                            V.on()
                            runIn(state.vent_runtime, vent_runtime_reached)
                            if (status) {
                                status.setventState("$state.vent_state")
                            }
                    }
                    break;
            }
        }
    } else {
        // log.debug("vent off - Off")
        state.vent_state = "Off"
        V.off()
        switch ("$vent_type") {
            case "Requires Blower":
                update_fan_state()
                update_fan_zones()
        }
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
        V.off()
        switch ("$vent_type") {
            case "Requires Blower":
                update_fan_state()
                update_fan_zones()
        }
    }
    if (status) {
        status.setventState("$state.vent_state")
    }
}

def vent_runtime_reached() {
    log.debug("In vent_runtime_reached()")
    state.vent_state = "Complete"
    V.off()
    switch ("$vent_type") {
        case "Requires Blower":
            update_fan_state()
            update_fan_zones()
    }
    if (status) {
        status.setventState("$state.vent_state")
    }
}

def vent_deadline_reached() {
    log.debug("In vent_deadline_reached()")
    state.vent_state = "End_Phase"
    V.on()
    switch ("$vent_type") {
        case "Requires Blower":
            update_fan_state()
            update_fan_zones()
    }
    if (status) {
        status.setventState("$state.vent_state")
    }
}

def equipment_off_adjust_vent() {
    // this is called any time that the heating and cooling equipment transitions from on to off
    // it may also be called while equipment is off, such as extraneous call from mode_change_delay
    // it should not be called with equipment on, although the blower may be on serving a fan only call
    log.debug("In equipment_off_adjust_vent()")
    switch ("$state.equip_state") {
        case "Heating":
        case "HeatingL":
        case "Cooling":
        case "CoolingL":
            log.debug("*** equipment_off_adjust_vent() should not be called with equipment running")
            return
    }
    switch ("$vent_type") {
        case "Doesn't Require Blower":
        case "None":
            break
        case "Requires Blower":
            switch ("$state.vent_state") {
                case "Complete":
                case "Waiting":
                case "Off":
                case "End_Phase":
                case "Forced":
                    break;
                case "Running":
                    log.debug("vent off - Waiting")
                    state.vent_state = "Waiting"
                    unschedule(vent_runtime_reached)
                    V.off()
                    Integer runtime = state.vent_runtime - (now() - state.vent_started) / 1000
                    state.vent_runtime = runtime
                    // log.debug("runtime is $state.vent_runtime")
                    Integer deadline = (state.vent_interval_end - now()) / 1000 - runtime
                    runIn(deadline, vent_deadline_reached)
                    if (status) {
                        status.setventState("$state.vent_state")
                    }
                    break;
            }
    }
    update_fan_state()
    update_fan_zones()
}

def equipment_on_adjust_vent() {
    // this is called any time that the heating and cooling equipment transitions from off to on
    log.debug("In equipment_on_adjust_vent()")
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
                    V.on()
                    update_fan_state()
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
    switch ("$state.equip_state") {
        case "Heating":
        case "HeatingL":
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
            break
        case "Cooling":
        case "CoolingL":
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
            break
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
    switch ("$state.equip_state") {
        case "Idle":
        case "IdleH":
        case "PauseH":
        case "IdleC":
        case "PauseC":
            switch ("$state.fan_state") {
                case "On_for_cooling":
                case "On_for_vent": 
                case "On_by_request":
                    return "fan only"
                case "Off":
                    return "idle"
            }
        case "Heating":
        case "HeatingL":
            return "heating"
        case "Cooling":
        case "CoolingL":
            return "cooling"
    }
}
