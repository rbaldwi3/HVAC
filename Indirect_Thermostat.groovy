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
    section ("Devices") {
        input "stat", "device.HVACZoningStatus", required: true, title: "Indirect Thermostat"
        input "heat", "capability.contactSensor", required: false, title: "Heating Call"
        input "cool", "capability.contactSensor", required: false, title: "Cooling Call"
        input "fan", "capability.contactSensor", required: false, title: "Fan Call"
        input "temperature", "capability.temperatureMeasurement", required: false, title: "Temperature Source"
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
    if (heat) {
        subscribe(heat, "contact", callHandler)
    }
    if (cool) {
        subscribe(cool, "contact", callHandler)
    }
    if (fan) {
        subscribe(fan, "contact", callHandler)
    }
    if (temperature) {
        subscribe(temperature, "temperature", tempHandler)
    }
}

def tempHandler(evt=NULL) {
    log.debug("In tempHandler()")
    def state = temperature.currentState("temperature")
    new_temp = state.value as BigDecimal
    stat.setTemperature(new_temp)
}

def callHandler(evt=NULL) {
    log.debug("In callHandler()")
    Boolean heat_call = false
    if (heat) {
        def state = heat.currentValue("contact")
        log.debug("heat = $state.value")
        if ("$state.value" == "open") {
           heat_call = true
        }
    }
    Boolean cool_call = false
    if (cool) {
        def state = cool.currentValue("contact")
        log.debug("cool = $state.value")
        if ("$state.value" == "open") {
           cool_call = true
        }
    }
    Boolean fan_call = false
    if (fan) {
        def state = fan.currentValue("contact")
        log.debug("fan = $state.value")
        if ("$state.value" == "open") {
           fan_call = true
        }
    }
    stat.update(heat_call, cool_call, fan_call)
}
