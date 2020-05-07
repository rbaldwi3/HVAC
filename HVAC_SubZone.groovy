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
    section ("Temperature and Set point - Specify either a thermostat ot a temperatures sensor and offsets from the parent zone setpoints") {
        input "stat", "capability.thermostat", required: false, title: "Thermostat"
        input "temp_sensor", "capability.temperatureMeasurement", required: false, title: "Temperature Sensor"
        input "heat_offset", "number", required: false, title: "Heating Setpoint Offset from Parent Zone", default: 0, range: "-10 . . 10"
        input "cool_offset", "number", required: false, title: "Cooling Setpoint Offset from Parent Zone", default: 0, range: "-10 . . 10"
    }
    section ("Zone Data") {
        input "cfm", "number", required: true, title: "Maximum airflow for Zone", range: "200 . . 3000"
        input "closed_pos", "number", required: true, title: "Percent Open when in Off position", default: 0, range: "0 . . 100"
        input "zone", "capability.switch", required: false, title: "Selection of Zone" // future feature - percentage control as opposed to on/off
        input "normally_open", "bool", required: true, title: "Normally Open (i.e. Switch On = Zone Inactive, Switch Off = Zone Selected)", default: false
    }
    section ("Control Rules") {
        input (name:"heat_join", type: "enum", required: true, title: "Select during parent zone heating call", options: ["Only during subzone heating call",
                                                                                                                    "When subzone temp is less than setpoint",
                                                                                                                    "When subzone temp is no more than 1 degree above setpoint"])
        input (name: "heat_trigger", type: "enum", required: true, title: "Subzone triggers parent zone heating call", options: ["Never", "Always",
                                                                                              "When parent zone temp is less than setpoint",
                                                                            "When parent zone temp is no more than 1 degree above setpoint"])
        input (name:"cool_join", type: "enum", required: true, title: "Select during parent zone cooling call", options: ["Only during subzone cooling call",
                                                                                                                    "When subzone temp is greater than setpoint",
                                                                                                                    "When subzone temp is no more than 1 degree below setpoint"])
        input (name:"cool_trigger", type: "enum", required: true, title: "Subzone triggers parent zone heating call", options: ["Never", "Always",
                                                                                           "When parent zone temp is greater than setpoint",
                                                                               "When parent zone temp no more than 1 degree below setpoint"])
        input "fan_switch", "capability.switch", required: false, title: "Switch to select whether subzone is selected during parent fan only call (including ventilation)"
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
    // Sanity check the settings
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
    } else if (temp_sensor) {
        def levelstate = temp_sensor.currentState("temperature")
        atomicState.temperature = levelstate.value as BigDecimal
        atomicState.heat_setpoint = parent.get_heat_setpoint() + settings.heat_offset
        atomicState.cool_setpoint = parent.get_cool_setpoint() + settings.cool_offset
    } else {
        log.error("Neither Thermostat nor Temperature Sensor specified")
        atomicState.heat_setpoint = 68.5
        atomicState.cool_setpoint = 78.5
        atomicState.temperature = 74.0
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
    } else {
        atomicState.fan_switch = false
    }
    parent.child_updated()
}

def get_heat_demand(parent_mode, parent_setpoint, parent_temp ) {
    log.debug("In SubZone get_heat_demand($parent_mode, $parent_setpoint, $parent_temp)")
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
        case "idle":
        case "fan only":
            def state = stat.currentValue("thermostatOperatingState")
            switch ("$state") {
                case "heating":
                    break
                default:
                    return 0
            }
            BigDecimal setpoint = parent_setpoint as BigDecimal
            BigDecimal temperature = parent_temp as BigDecimal
            switch ("$heat_trigger") {
                case "Never":
                    return 0
                case "Always":
                    return atomicState.on_capacity
                case "When parent zone temp is less than setpoint":
                    if (temperature <= setpoint) {
                        return atomicState.on_capacity
                    } else {
                       return 0
                    }
                case "When parent zone temp is no more than 1 degree above setpoint":
                    if (temperature - 1 <= setpoint) {
                        return atomicState.on_capacity
                    } else {
                       return 0
                    }
            }
            break;
    }
    return 0;
}

def get_cool_demand(parent_mode, parent_setpoint, parent_temp ) {
    log.debug("In SubZone get_cool_demand($parent_mode, $parent_setpoint, $parent_temp)")
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
        case "idle":
        case "fan only":
            def state = stat.currentValue("thermostatOperatingState")
            switch ("$state") {
                case "cooling":
                    break
                default:
                    return 0
            }
            BigDecimal setpoint = parent_setpoint as BigDecimal
            BigDecimal temperature = parent_temp as BigDecimal
            switch ("$cool_trigger") {
                case "Never":
                    return 0
                case "Always":
                    return atomicState.on_capacity
                case "When parent zone temp is greater than setpoint":
                    if (temperature >= setpoint) {
                        return atomicState.on_capacity
                    } else {
                       return 0
                    }
                case "When parent zone temp no more than 1 degree below setpoint":
                    if (temperature + 1 >= setpoint) {
                        return atomicState.on_capacity
                    } else {
                       return 0
                    }
            }
            break;
    }
    return 0;
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
    def levelstate = stat.currentState("temperature")
    new_temp = levelstate.value as BigDecimal
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
    log.debug("In SubZone handle_overpressure()")
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
