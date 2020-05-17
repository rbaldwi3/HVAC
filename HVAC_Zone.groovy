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
    section ("Zone Data") {
        input "stat", "capability.thermostat", required: true, title: "Thermostat"
        input "cfm", "number", required: true, title: "Maximum airflow for Zone", range: "200 . . 3000"
        input "closed_pos", "number", required: true, title: "Percent Open when in Off position", default: 0, range: "0 . . 100"
        input "zone", "capability.switch", required: true, title: "Switch for selection of Zone" // future feature - percentage control as opposed to on/off
        input "normally_open", "bool", required: true, title: "Normally Open (i.e. Switch On = Zone Inactive, Switch Off = Zone Selected)", default: true
        input "on_for_vent", "capability.switch", required: false, title: "Select during Ventilation Only - yes if no switch specified or switch is on"
        // time interval for polling in case any signals missed
        input(name:"output_refresh_interval", type:"enum", required: true, title: "Output refresh", options: ["None","Every 5 minutes","Every 10 minutes",
                                                                                                             "Every 15 minutes","Every 30 minutes"]) 
        input(name:"input_refresh_interval", type:"enum", required: true, title: "Input refresh", options: ["None","Every 5 minutes","Every 10 minutes",
                                                                                                             "Every 15 minutes","Every 30 minutes"]) 
    }
    section {
        app(name: "subzone", appName: "HVAC SubZone", namespace: "rbaldwi3", title: "Create New Sub-Zone", multiple: true, submitOnChange: true)
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
    atomicState.cool_demand = 0
    atomicState.heat_demand = 0
    atomicState.fan_demand = 0
    atomicState.vent_demand = 0
    atomicState.on_for_vent = true
    // Subscribe to state changes
    subscribe(stat, "thermostatOperatingState", stateHandler)
    subscribe(stat, "thermostatFanMode", stateHandler)
	subscribe(stat, "temperature", tempHandler)
    subscribe(stat, "heatingSetpoint", heat_setHandler)
    subscribe(stat, "coolingSetpoint", cool_setHandler)
    def levelstate = stat.currentState("temperature")
    atomicState.temperature = levelstate.value as BigDecimal
    levelstate = stat.currentState("heatingSetpoint")
    atomicState.heat_setpoint = levelstate.value as BigDecimal
    levelstate = stat.currentState("coolingSetpoint")
    atomicState.cool_setpoint = levelstate.value as BigDecimal
    def value = zone.currentValue("switch")
    switch ("$value") {
        case "on":
            atomicState.current_mode = parent.get_equipment_status()
            break;
        case "off":
            atomicState.current_mode = "unselected"
            break;
    }
    if (on_for_vent) {
        value = on_for_vent.currentValue("switch")
        switch ("$value") {
            case "on":
                atomicState.on_for_vent = true
                atomicState.vent_demand = atomicState.on_capacity
                break;
            case "off":
                atomicState.on_for_vent = false
                break;
        }
        subscribe(on_for_vent, "switch", on_for_ventHandler)
    }
    value = stat.currentValue("thermostatOperatingState")
    switch ("$value") {
        case "heating":
            atomicState.heat_demand = atomicState.on_capacity
            break
        case "cooling":
            atomicState.cool_demand = atomicState.on_capacity
            break
        case "fan only":
            atomicState.fan_demand = atomicState.on_capacity
            break
    }
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
    // log.debug("In refresh_inputs()")
    if (stat.hasCapability("refresh")) {
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
            state = stat.currentValue("thermostatFanMode")
            switch ("$state.value") {
                case "on":
                case " on":
                if (atomicState.fan_demand == 0) {
                    stateHandler()
                }
            }
            break
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

// This function updates atomicState.cool_demand, atomicState.heat_demand, atomicState.fan_demand, and atomicState.vent_demand
// If any of the former three are changed, the main Zoning app is asked to re-evaluate its state

def update_demand() {
    // log.debug("In Zone update_demand()")
    temperature = stat.currentState("temperature")
    heat_setpoint = stat.currentState("heatingSetpoint")
    cool_setpoint = stat.currentState("coolingSetpoint")
    def state = stat.currentValue("thermostatOperatingState")
    fan_demand = 0
    // pre-process which zones would join for ventilation or fan calls
    subzone_fan_demand = 0
    def subzones = getChildApps()
    subzones.each { sz ->
        subzone_fan_demand += sz.get_vent_demand()
    }    
    switch ("$state.value") {
        case "heating":
            // the heating demand is the capacity of this main zone plus the capacities of any subzones that would join this heating call
            heat_demand = atomicState.on_capacity
            cool_demand = 0
            subzones.each { sz ->
                heat_demand += sz.get_heat_demand("heating", heat_setpoint.value, temperature.value)
            }
            break
        case "cooling":
            // the cooling demand is the capacity of this main zone plus the capacities of any subzones that would join this cooling call
            cool_demand = atomicState.on_capacity
            heat_demand = 0
            subzones.each { sz ->
                cool_demand += sz.get_cool_demand("cooling", cool_setpoint.value, temperature.value)
            }    
            break
        case "fan only":
            // when idle or fan only command, determine if any subzones would trigger heating or cooling calls for this main zone
            fan_demand = atomicState.on_capacity + subzone_fan_demand
        case "idle":
            state = stat.currentValue("thermostatFanMode")
            switch ("$state.value") {
                case "on":
                case " on":
                    // some thermostats do not change thermostatOperatingState to "fan only" when they should
                    fan_demand = atomicState.on_capacity + subzone_fan_demand
            }
            heat_demand = 0
            cool_demand = 0
            subzones.each { sz ->
                heat_demand += sz.get_heat_demand("idle", heat_setpoint.value, temperature.value)
                cool_demand += sz.get_cool_demand("idle", cool_setpoint.value, temperature.value)
            }    
            // if any subzones trigger a heating or cooling call, add this main zones capacity to the capacities of those subzones
            if (heat_demand > 0) {
                heat_demand += atomicState.on_capacity
            }
            if (cool_demand > 0) {
                cool_demand += atomicState.on_capacity
            }
            break
    }
    // vent_demand is the capacity that would be accepted if the zone control app decides to run ventilation.  It is not a call to the zone control.
    if (atomicState.on_for_vent) {
        atomicState.vent_demand = subzone_fan_demand + atomicState.on_capacity
    } else {
        atomicState.vent_demand = 0
    }
    if ((atomicState.heat_demand != heat_demand) || (atomicState.cool_demand != cool_demand)|| (atomicState.fan_demand != fan_demand)) {
        atomicState.heat_demand = heat_demand
        atomicState.cool_demand = cool_demand
        atomicState.fan_demand = fan_demand
        parent.zone_call_changed()
    }
}

def stateHandler(evt=NULL) {
    // log.debug("In Zone stateHandler()")
/*
    if (wired) {
        def state = stat.currentValue("thermostatOperatingState")
        parent.update_wired_mode("$state.value")
    }
*/
    update_demand() // this does the work of calculating what should be demanded of the zone control app
}

def tempHandler(evt=NULL) {
    // changes in temperature from the thermostat do not directly result in heating or cooling calls - those result from operating state changes
    // changes in temperature may result in a subzone starting or ending cooling call or heating call
    // log.debug("In Zone tempHandler()")
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
    if (changed) {
        parent.zone_call_changed()
    }
}

def heat_setHandler(evt=NULL) {
    // changes in thermostat set point do not directly result in heating calls - those result from operating state changes
    // changes in setpoint may result in a subzone starting or ending a heating call
    // log.debug("In Zone heat_setHandler()")
    def levelstate = stat.currentState("heatingSetpoint")
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
    if (changed) {
        update_demand()
    }
    if (new_setpoint - atomicState.temperature > 1) {
        parent.stage2_heat()
    }
}

def cool_setHandler(evt=NULL) {
    // changes in thermostat set point do not directly result in cooling calls - those result from operating state changes
    // changes in setpoint may result in a subzone starting or ending a cooling call
    // log.debug("In SubZone cool_setHandler()")
    def levelstate = stat.currentState("coolingSetpoint")
    new_setpoint = levelstate.value as BigDecimal
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
    atomicState.cool_setpoint = new_setpoint
    if (changed) {
        update_demand()
    }
    if (atomicState.temperature - new_setpoint > 1) {
        parent.stage2_cool()
    }
}

def on_for_ventHandler(evt=NULL) {
    // log.debug("In SubZone on_for_ventHandler()")
    def currentvalue = on_for_vent.currentValue("switch")
    switch ("$currentvalue") {
        case "on":
            atomicState.on_for_vent = true
            break;
        case "off":
            atomicState.on_for_vent = false
            break;
    }
    update_demand()
}

// These functions let the zone control app and and subzones access this zones state

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
    // log.debug("In Zone turn_on($mode)")
    atomicState.current_mode = mode
    temperature = stat.currentState("temperature")
    def subzones = getChildApps()
    switch ("$mode") {
        case "heating":
            heat_setpoint = stat.currentState("heatingSetpoint")
            subzones.each { sz ->
                if (sz.get_heat_demand("heating", heat_setpoint.value, temperature.value) > 0) {
                    sz.turn_on()
                } else {
                    sz.turn_off()
                }
            }
            break
        case "cooling":
            cool_setpoint = stat.currentState("coolingSetpoint")
            subzones.each { sz ->
                if (sz.get_cool_demand("cooling", cool_setpoint.value, temperature.value) > 0) {
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
    atomicState.current_mode = "unselected"
    if (normally_open) {
        zone.on()
    } else {
        zone.off()
    }
}

def turn_idle() {
    // when the equipment is idle, including the blower, the zone switch is turned off, regardless of whether it is normally on or normally off
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
