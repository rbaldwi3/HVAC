/**
 *  Indirect Thermostat Filler
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
 * This app can be used to fill a HVAC Zoning Status device to be used as a Thermostat input to an HVAC Zone when ThermostatOperatingState is being physically sensed.
 * version 0.2 - Initial Release
 */

definition(
    name: "Indirect Thermostat Filler",
    namespace: "rbaldwi3",
    author: "Reid Baldwin",
    description: "This app transfers the state of thermostat sensors to a device that accepts raw heating and cooling calls",
    category: "General",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "pageOne", nextPage: "pageTwo", uninstall: true) {
        section ("Label") {
            label required: true, multiple: false
        }
        section ("Output") {
            input "stat", "device.HVACZoningStatus", required: true, title: "Indirect Thermostat"
        }
        section ("Input Types") {
            input(name: "input_type", type: "enum", required: true, title: "Input Type", options: ["Contact Sensor","Switch"])
        }
    }
    page(name: "pageTwo", title: "Inputs", install: true, uninstall: true)
}

def pageTwo() {
    dynamicPage(name: "pageTwo") {
        section ("Inputs") {
            switch ("$input_type") {
                case "Contact Sensor":
                    input "heat_contact", "capability.contactSensor", required: false, title: "Heating Call"
                    input "cool_contact", "capability.contactSensor", required: false, title: "Cooling Call"
                    input "fan_contact", "capability.contactSensor", required: false, title: "Fan Call"
                    input "normally_closed", "bool", required: true, title: "True -> Open when call in progress", default: true
                    break
                case "Switch":
                    input "heat_switch", "capability.switch", required: false, title: "Heating Call"
                    input "cool_switch", "capability.switch", required: false, title: "Cooling Call"
                    input "fan_switch", "capability.switch", required: false, title: "Fan Call"
                    input "normally_off", "bool", required: true, title: "True -> On when call in progress", default: true
                    break
            }
            input "temperature", "capability.temperatureMeasurement", required: false, title: "Temperature Source"
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
    log.debug("In Initialize()")
    if (heat_contact) {
        subscribe(heat_contact, "contact", contact_callHandler)
    }
    if (cool_contact) {
        subscribe(cool_contact, "contact", contact_callHandler)
    }
    if (fan_contact) {
        subscribe(fan_contact, "contact", contact_callHandler)
    }
    if (heat_switch) {
        subscribe(heat_switch, "switch", switch_callHandler)
    }
    if (cool_switch) {
        subscribe(cool_switch, "switch", switch_callHandler)
    }
    if (fan_switch) {
        subscribe(fan_switch, "switch", switch_callHandler)
    }
    if (temperature) {
        tempHandler()
        subscribe(temperature, "temperature", tempHandler)
        if (temperature.hasAttribute("heatingSetpoint")) {
            heatsetHandler()
            subscribe(temperature, " heatingSetpoint ", heatsetHandler)
        }
        if (temperature.hasAttribute("coolingSetpoint")) {
            coolsetHandler()
            subscribe(temperature, " coolingSetpoint ", coolsetHandler)
        }
    }
}

def tempHandler(evt=NULL) {
    log.debug("In tempHandler()")
    def state = temperature.currentState("temperature")
    new_temp = state.value as BigDecimal
    stat.setTemperature(new_temp)
}

def heatsetHandler(evt=NULL) {
    log.debug("In heatsetHandler()")
    def state = temperature.currentState("heatingSetpoint ")
    new_temp = state.value as BigDecimal
    stat. setHeatingSetpoint (new_temp)
}

def coolsetHandler(evt=NULL) {
    log.debug("In coolsetHandler()")
    def state = temperature.currentState("coolingSetpoint ")
    new_temp = state.value as BigDecimal
    stat. setCoolingSetpoint (new_temp)
}

def contact_callHandler(evt=NULL) {
    log.debug("In contact_callHandler()")
    Boolean heat_call = false
    if (heat_contact) {
        def state = heat_contact.currentValue("contact")
        log.debug("heat = $state.value")
        if (normally_closed) {
            if ("$state.value" == "open") {
               heat_call = true
            }
        } else {
            if ("$state.value" == "closed") {
               heat_call = true
            }
        }
    }
    Boolean cool_call = false
    if (cool_contact) {
        def state = cool_contact.currentValue("contact")
        log.debug("cool = $state.value")
        if (normally_closed) {
            if ("$state.value" == "open") {
               cool_call = true
            }
        } else {
            if ("$state.value" == "closed") {
               cool_call = true
            }
        }
    }
    Boolean fan_call = false
    if (fan_contact) {
        def state = fan_contact.currentValue("contact")
        log.debug("fan = $state.value")
        if (normally_closed) {
            if ("$state.value" == "open") {
               fan_call = true
            }
        } else {
            if ("$state.value" == "closed") {
               fan_call = true
            }
        }
    }
    stat.update(heat_call, cool_call, fan_call)
}

def switch_callHandler(evt=NULL) {
    log.debug("In switch_callHandler()")
    Boolean heat_call = false
    if (heat_switch) {
        def state = heat_switch.currentValue("switch")
        log.debug("heat = $state.value")
        if (normally_off) {
            if ("$state.value" == "on") {
               heat_call = true
            }
        } else {
            if ("$state.value" == "off") {
               heat_call = true
            }
        }
    }
    Boolean cool_call = false
    if (cool_switch) {
        def state = cool_switch.currentValue("switch")
        log.debug("cool = $state.value")
        if (normally_off) {
            if ("$state.value" == "on") {
               cool_call = true
            }
        } else {
            if ("$state.value" == "off") {
               cool_call = true
            }
        }
    }
    Boolean fan_call = false
    if (fan_switch) {
        def state = fan_switch.currentValue("switch")
        log.debug("fan = $state.value")
        if (normally_off) {
            if ("$state.value" == "on") {
               fan_call = true
            }
        } else {
            if ("$state.value" == "off") {
               fan_call = true
            }
        }
    }
    stat.update(heat_call, cool_call, fan_call)
}
