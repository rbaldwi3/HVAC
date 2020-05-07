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
        section {
            app(name: "zones", appName: "HVAC Zone", namespace: "rbaldwi3", title: "Create New Zone", multiple: true, submitOnChange: true)
        }
        section ("Equipment Types") {
            input(name: "heat_type", type: "enum", required: true, title: "Heating Equipment Type", options: ["None","Single stage","Two stage"])
            input(name: "cool_type", type: "enum", required: true, title: "Cooling Equipment Type", options: ["None","Single stage","Two stage"])
            input(name: "vent_type", type: "enum", required: true, title: "Ventilation Equipment Type", options: ["None", "Requires Blower", "Doesn't Require Blower"])
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
            // Would like a control to feed in runtime of ventilation per hour
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
            input "G", "capability.switch", required:true, title: "Command Fan"
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
            switch ("$vent_type") {
                case "Requires Blower":
                case "Doesn't Require Blower":
                    input "vent_control", "capability.switchLevel", required:true, title: "Ventilation Control - Use dimmer to set percent runtime and on/off"
                    input "vent_force", "capability.switch", required:false, title: "Spot Ventilation Control - turn on to temporarily force ventilation on (e.g. bathroom vent)"
            }
            // time interval for polling in case any signals missed
            // time interval for repeating signals to avoid auto-shutoff of ZEN16
        }
    }
}

def installed() {
    log.debug("In installed()")
    state.heating_mode = true  // false implies cooling mode
    state.heating_equipment_running = false
    state.cooling_equipment_running = false
    state.fan_running_by_request = false
    state.last_cooling_start = now() - 1000*60*30
    state.last_cooling_stop = now() - 1000*60*30
    state.last_heating_start = now() - 1000*60*30
    state.last_heating_stop = now() - 1000*60*30
    state.vent_interval_start = now() - 1000*60*30
    state.vent_interval_end = now() - 1000*60*20
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
    child_updated()
    // Sanity check the settings
    // Subscribe to state changes
    switch ("$vent_type") {
        case "Requires Blower":
        case "Doesn't Require Blower":
            runEvery1Hour(start_vent_interval)
            subscribe(vent_control, "switch.on", vent_control_activated)
            subscribe(vent_control, "switch.off", vent_control_deactivated)
            if (vent_force) {
                subscribe(vent_force, "switch.on", vent_force_activated)
                subscribe(vent_force, "switch.off", vent_force_deactivated)
            }
    }
    if (over_pressure) {
        subscribe(over_pressure, "switch.on", over_pressure_stage1)
        state.over_pressure_time = now() - 5*60*1000
    }
    state.wired_mode = "none"
}

def child_updated() {
    log.debug("In child_updated()")
    // Preprocessing of airflow per zone
    state.off_capacity = 0
    def zones = getChildApps()
    zones.each { z ->
        state.off_capacity += z.get_off_capacity();
    }
}

def update_wired_mode(new_mode) {
    log.debug("In update_wired_mode($new_mode)")
    state.wired_mode = new_mode
    switch("$new_mode") {
        case "heating":
            turn_off_cool()
            state.heating_mode = true
            break
        case "cooling":
            turn_off_heat()
            state.heating_mode = false
            break
    }
}

// This routing handles a change in status in a zone
// It also gets called back mode_change_delay minutes after heating or cooling is turned off to re-check whether opposite mode should be started

def zone_call_changed() {
    log.debug("In zone_call_changed()")
    state.cool_demand = 0
    state.heat_demand = 0
    state.fan_demand = 0
    def zones = getChildApps()
    zones.each { z ->
        state.cool_demand += z.get_cool_demand()
        state.heat_demand += z.get_heat_demand()
        state.fan_demand += z.get_fan_demand()
    }
    log.debug("cool_demand = $state.cool_demand, heat_demand = $state.heat_demand")
    if (state.heating_mode) {
       // check for servable heat call
       switch ("$heat_type") {
            case "Single stage":
            case "Two stage":
                if (state.heat_demand > 0) {
                    if (state.heat_demand+state.off_capacity >= cfmW1) {
                        serve_heat_call(state.heat_demand)
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
                            serve_cool_call(state.cool_demand)
                            return
                        }
                    }
                }
                break
        }
    } else {
        // check for servable cooling call
        switch ("$cool_type") {
            case "Single stage":
            case "Two stage":
                if (state.cool_demand > 0) {
                    if (state.cool_demand+state.off_capacity >= cfmY1) {
                        serve_cool_call(state.cool_demand)
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
                            serve_heat_call(state.heat_demand)
                            return
                        }
                    }
                }
                break
        }
    }
    // check for servable fan call
    if (state.fan_demand+state.off_capacity >= cfmG) {
        // turn on zones with fan call and turn off others
        zones.each { z ->
            if (z.get_fan_demand() > 0) {
                z.turn_on("fan only")
            } else {
                z.turn_off()
            }
        }
        // turn on fan
        G.on()
        state.fan_running_by_request = true;
        runIn(1, equipment_on_adjust_vent)
        return
    } else {
        state.fan_running_by_request = false;
        runIn(1, equipment_off_adjust_vent) // re-calling zone_call_changed after mode_change_delay is causing extraneous call to equipment_off_adjust_vent
    }
    // idle turn all zones on
    zones.each { z ->
        z.turn_on("idle")
    }
}

// Routines for handling heating

def serve_heat_call(heat_demand) {
    log.debug("In serve_heat_call($heat_demand)")
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
    turn_on_heat(heat_demand)
}

def turn_on_heat(heat_demand) {
    log.debug("In turn_on_heat($heat_demand)")
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
        runIn(1, equipment_on_adjust_vent)
    }
    // check whether second stage should be used
    // implement logic for delay
    // implement logic for temperature delta from setpoint
    switch ("$heat_type") {
        case "Two stage":
            if (heat_demand + state.off_capacity > cfmW2) {
                W2.on()
            } else {
                W2.off()
            }
    }
}

def turn_off_heat() {
    log.debug("In turn_off_heat()")
    if (state.heating_equipment_running) {
        state.last_heating_stop = now()
        switch ("$heat_type") {
            case "Two stage":
                W2.off()
            case "Single stage":
                W1.off()
        }
        state.heating_equipment_running = false
        runIn(60 * mode_change_delay, zone_call_changed)
    }
}

// Routines for handline cooling

def serve_cool_call(cool_demand) {
    log.debug("In serve_cool_call($cool_demand)")
    state.heating_mode = false
    // turn on zones with heat call and turn off others
    def zones = getChildApps()
    zones.each { z ->
        if (z.get_cool_demand() > 0) {
            z.turn_on("cooling")
        } else {
            z.turn_off()
        }
    }
    turn_on_cool(cool_demand)
}

def turn_on_cool(cool_demand) {
    log.debug("In turn_on_cool($cool_demand)")
    // check consistency with hardwired connection
    switch("$state.wired_mode") {
        case "heating":
            return
    }
    // turn on cooling equipment if not already on
    if (!state.cooling_equipment_running) {
        state.last_cooling_start = now()
        Y1.on()
        state.cooling_equipment_running = true
        runIn(1, equipment_on_adjust_vent)
    }
    // check whether second stage should be used
    // implement logic for delay
    // implement logic for temperature delta from setpoint
    switch ("$cool_type") {
        case "Two stage":
            if (cool_demand + state.off_capacity > cfmY2) {
                Y2.on()
            } else {
                Y2.off()
            }
    }
}

def turn_off_cool() {
    log.debug("In turn_off_cool()")
    if (state.cooling_equipment_running) {
        state.last_cooling_stop = now()
        switch ("$cool_type") {
            case "Two stage":
                Y2.off()
            case "Single stage":
                Y1.off()
        }
        state.cooling_equipment_running = false
        runIn(60 * mode_change_delay, zone_call_changed)
    }
}

// Ventilation Control Routines

// adjust to select the zones as indicated by buttons

// adjust so fan mode on thermostat does not trigger ventilation

def start_vent_interval() {
    log.debug("In start_vent_interval()")
    unschedule(vent_runtime_reached)
    unschedule(vent_deadline_reached)
    state.vent_interval_start = now()
    state.vent_interval_end = now() + 60*60*1000
    if ("$state.vent_state" == "Forced") {
        log.debug("Still in forced vent state")
        return;
    }
    def switchstate = vent_control.currentState("switch")
    if (switchstate.value == "on") {
        def levelstate = vent_control.currentState("level")
        Integer percent = levelstate.value as Integer
        Integer runtime = 60 * 60 * percent / 100
        state.vent_runtime = runtime
        log.debug("runtime is $state.vent_runtime")
        switch ("$vent_type") {
            case "Doesn't Require Blower":
                V.on()
                log.debug("vent on - Running")
                state.vent_state = "Running"
                runIn(state.vent_runtime, vent_runtime_reached)
                break;
            case "Requires Blower":
                if(state.heating_equipment_running || state.cooling_equipment_running) {
                    V.on()
                    log.debug("vent on - Running")
                    state.vent_started = now()
                    state.vent_state = "Running"
                    def zones = getChildApps()
                    zones.each { z ->
                        if (z.get_on_for_vent()  || z.get_fan_demand()) {
                            z.turn_on("vent")
                        } else {
                            z.turn_off()
                        }
                    }
                    runIn(state.vent_runtime, vent_runtime_reached)
                } else {
                    V.off()
                    log.debug("vent off - Waiting")
                    if (!state.fan_running_by_request) {
                        G.off()
                        log.debug("fan off")
                    }
                    state.vent_state = "Waiting"
                    runIn(60 * 60 - state.vent_runtime, vent_deadline_reached)
                }
                break;
        }
    }
}

def vent_force_activated(evt) {
    log.debug("In vent_force_activated()")
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
    switch ("$vent_type") {
        case "Requires Blower":
            G.on()
            def zones = getChildApps()
            zones.each { z ->
               if (z.get_on_for_vent()  || z.get_fan_demand()) {
                   z.turn_on("vent")
               } else {
                   z.turn_off()
               }
            }
            log.debug("fan on")
        case "Doesn't Require Blower":
            state.vent_started = now()
            V.on()
            log.debug("vent on - Forced")
            state.vent_state = "Forced"
    }
}

def vent_force_deactivated(evt) {
    log.debug("In vent_force_deactivated()")
    def switchstate = vent_control.currentState("switch")
    if (switchstate.value == "on") {
        if (state.vent_started > state.interval_start) {
            // still in same interval as when force ventilation started
            log.debug("state.vent_runtime is $state.vent_runtime")
            Integer runtime = state.vent_runtime
            log.debug("runtime is $runtime")
            log.debug("state.vent_started is $state.vent_started")
            runtime -= (now() - state.vent_started) / 1000
        } else {
            // we have been in forced ventilation for the entire interval up to this point
            def levelstate = vent_control.currentState("level")
            Integer percent = levelstate.value as Integer
            Integer runtime = 60 * 60 * percent / 100
            runtime -= (now() - state.interval_start) / 1000
        }
        state.vent_runtime = runtime
        log.debug("runtime is $state.vent_runtime")
        if (runtime <= 0) {
            V.off()
            log.debug("vent off - Complete")
            if (!state.fan_running_by_request) {
                 G.off()
                 log.debug("fan off")
            }
            state.vent_state = "Complete"
        } else {
            switch ("$vent_type") {
                case "Doesn't Require Blower":
                    V.on()
                    log.debug("vent on - Running")
                    state.vent_state = "Running"
                    runIn(state.vent_runtime, vent_runtime_reached)
                    break;
                case "Requires Blower":
                    if(state.heating_equipment_running || state.cooling_equipment_running || state.fan_running_by_request) {
                        V.on()
                        log.debug("vent on - Running")
                        state.vent_started = now()
                        state.vent_state = "Running"
                        runIn(state.vent_runtime, vent_runtime_reached)
                    } else {
                        V.off()
                        log.debug("vent off - Waiting")
                        if (!state.fan_running_by_request) {
                            G.off()
                            log.debug("fan off")
                        }
                        state.vent_state = "Waiting"
                        Integer deadline = (state.vent_interval_end - now()) / 1000 - state.vent_runtime
                        runIn(deadline, vent_deadline_reached)
                    }
                    break;
            }
        }
    } else {
        V.off()
        log.debug("vent off - Off")
        if (!state.fan_running_by_request) {
             G.off()
             log.debug("fan off")
        }
        state.vent_state = "Off"
    }
}

def vent_control_activated(evt) {
    log.debug("In vent_control_activated()")
    if ("$state.vent_state" == "Forced") {
        log.debug("Still in forced vent state")
        return;
    }
    def levelstate = vent_control.currentState("level")
    Integer percent = levelstate.value as Integer
    Integer runtime = (state.vent_interval_end - now()) /1000 * percent / 100
    state.vent_runtime = runtime
    log.debug("runtime is $state.vent_runtime")
    switch ("$vent_type") {
        case "Doesn't Require Blower":
            V.on()
            log.debug("vent on - Running")
            state.vent_state = "Running"
            runIn(state.vent_runtime, vent_runtime_reached)
            break;
        case "Requires Blower":
            if(state.heating_equipment_running || state.cooling_equipment_running) {
                V.on()
                log.debug("vent on - Running")
                state.vent_started = now()
                state.vent_state = "Running"
                runIn(state.vent_runtime, vent_runtime_reached)
            } else {
                V.off()
                log.debug("vent off - Waiting")
                if (!state.fan_running_by_request) {
                    G.off()
                    log.debug("fan off")
                }
                state.vent_state = "Waiting"
                Integer deadline = (state.vent_interval_end - now()) / 1000 - state.vent_runtime
                runIn(deadline, vent_deadline_reached)
            }
            break;
    }
}

def vent_control_deactivated(evt) {
    log.debug("In vent_control_deactivated()")
    if ("$state.vent_state" == "Forced") {
        log.debug("Still in forced vent state")
        return;
    }
    V.off()
    log.debug("vent off - Waiting (switched off by user)")
    if (!state.fan_running_by_request) {
        G.off()
        log.debug("fan off")
    }
    state.vent_state = "Off"
    unschedule(vent_runtime_reached)
    unschedule(vent_deadline_reached)
}

def vent_runtime_reached() {
    log.debug("In vent_runtime_reached()")
    V.off()
    log.debug("vent off - Complete")
    state.vent_state = "Complete"
}

def vent_deadline_reached() {
    log.debug("In vent_deadline_reached()")
    V.on()
    G.on()
    def zones = getChildApps()
    zones.each { z ->
        if (z.get_on_for_vent()  || z.get_fan_demand()) {
            z.turn_on("vent")
        } else {
            z.turn_off()
        }
    }
    log.debug("vent and fan on - End Phase")
    state.vent_state = "End_Phase"
}

def equipment_off_adjust_vent() {
    log.debug("In equipment_off_adjust_vent()")
    switch ("$vent_type") {
        case "Doesn't Require Blower":
        case "None":
            G.off()
            break;
        case "Requires Blower":
            switch ("$state.vent_state") {
                case "Complete":
                case "Waiting":
                case "Off":
                    G.off()
                case "End_Phase":
                case "Forced":
                    // Could get here due to extraneous call from mode_change_delay
                    break;
                case "Running":
                    V.off()
                    log.debug("vent off - Waiting")
                    state.vent_state = "Waiting"
                    unschedule(vent_runtime_reached)
                    Integer runtime = state.vent_runtime - (now() - state.vent_started) / 1000
                    state.vent_runtime = runtime
                    log.debug("runtime is $state.vent_runtime")
                    Integer deadline = (state.vent_interval_end - now()) / 1000 - runtime
                    runIn(deadline, vent_deadline_reached)
                    break;
            }
    }
}

def equipment_on_adjust_vent() {
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
                    V.on()
                    log.debug("vent on - Running")
                    state.vent_state = "Running"
                    state.vent_started = now()
                    unschedule(vent_deadline_reached)
                    runIn(state.vent_runtime, vent_runtime_reached)
                    break;
            }
    }
}

def over_pressure_stage1(evt) {
    log.debug("In over_pressure_stage1()")
    // Handle over-pressure signal
    if (now() - state.over_pressure_time < 2*60*1000) { // don't respond to more than one over pressure per two minutes
        return
    }
    state.over_pressure_time = now()
    /*
    def zones = getChildApps()
    zones.each { z ->
        z.handle_overpressure()
    }
    child_updated()
    */
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
    log.debug("In over_pressure_stage2()")
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
