/**
 *  HVAC Usage Tracker
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
    name: "HVAC Usage Tracker",
    namespace: "rbaldwi3",
    author: "Reid Baldwin",
    description: "This app tracks HVAC usage",
    category: "General",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    section ("Zones") {
        input "zones", "device.HVACZoneStatus", required: true, title: "Zone or System"
    }
}

def installed() {
    log.debug("In installed()")
    addChildDevice("rbaldwi3", "HVAC Usage Data", "Tracker_${app.id}", [isComponent: true, label: app.label])
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
    subscribe(zones, "heat_output", update_heat_output)
    subscribe(zones, "cool_output", update_cool_output)
}

def update_heat_output(evt=NULL) {
    log.debug("In update_heat_output()")
    Number new_value
    if (evt) {
        log.debug("evt is $evt")
        new_value = evt.getNumericValue()
        log.debug("new_value is $new_value")
        device = evt.getDevice()
        if (device) { log.debug("device is $device.label") }
    } else {
        def levelstate = zones.currentState("output")
        log.debug("levelstate is $levelstate")
        new_value = levelstate.getNumberValue()
        log.debug("new_value is $new_value")
    }
    status_device = getChildDevice("Tracker_${app.id}")
    status_device.set_heating(new_value)
}

def update_cool_output(evt=NULL) {
    log.debug("In update_cool_output()")
    Number new_value
    if (evt) {
        log.debug("evt is $evt")
        new_value = evt.getNumericValue()
        log.debug("new_value is $new_value")
        device = evt.getDevice()
        if (device) { log.debug("device is $device.label") }
    } else {
        def levelstate = zones.currentState("output")
        log.debug("levelstate is $levelstate")
        new_value = levelstate.getNumberValue()
        log.debug("new_value is $new_value")
    }
    status_device = getChildDevice("Tracker_${app.id}")
    status_device.set_cooling(new_value)
}
