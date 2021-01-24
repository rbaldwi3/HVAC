/**
 *  Outdoor Condition Tracker
 *
 *  Copyright 2021 Reid Baldwin
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
 * version 1.0 - Initial Release
 */

definition(
    name: "Outdoor Condition Tracker",
    namespace: "rbaldwi3",
    author: "Reid Baldwin",
    description: "This app tracks outdoor temperature and illuminance and calculates heating degree days",
    category: "General",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    section ("Sensors") {
        input "temperature_sensor", "capability.temperatureMeasurement", required: false, title: "Outdoor temperature sensor"
        input "illuminance_sensor", "capability.illuminanceMeasurement", required: false, title: "Outdoor illuminance sensor"
    }
}

def installed() {
    log.debug("In installed()")
    addChildDevice("rbaldwi3", "Outdoor Condition Data", "Tracker_${app.id}", [isComponent: true, label: app.label])
    initialize()
}

def uninstalled() {
    log.debug("In uninstalled()")
}

def updated() {
    log.debug("In updated()")
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    log.debug("In initialize()")
    if (temperature_sensor) {
        subscribe(temperature_sensor, "temperature", update_temperature)
        update_temperature()
    }
    if (illuminance_sensor) {
        subscribe(illuminance_sensor, "illuminance", update_illuminance)
        update_illuminance()
    }
}

def update_temperature(evt=NULL) {
    log.debug("In update_temperature()")
    Number new_value
    if (evt) {
        // log.debug("evt is $evt")
        new_value = evt.getNumericValue()
        // log.debug("new_value is $new_value")
    } else {
        def levelstate = temperature_sensor.currentState("temperature")
        // log.debug("levelstate is $levelstate")
        new_value = levelstate.getNumberValue()
        // log.debug("new_value is $new_value")
    }
    status_device = getChildDevice("Tracker_${app.id}")
    status_device.set_temperature(new_value)
}

def update_illuminance(evt=NULL) {
    log.debug("In update_illuminance()")
    Number new_value
    if (evt) {
        // log.debug("evt is $evt")
        new_value = evt.getNumericValue()
        // log.debug("new_value is $new_value")
    } else {
        def levelstate = illuminance_sensor.currentState("illuminance")
        // log.debug("levelstate is $levelstate")
        new_value = levelstate.getNumberValue()
        // log.debug("new_value is $new_value")
    }
    status_device = getChildDevice("Tracker_${app.id}")
    status_device.set_illuminance(new_value)
}
