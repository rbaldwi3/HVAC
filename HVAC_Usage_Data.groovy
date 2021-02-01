/**
 *  HVAC Usage Data
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
 *

 * Version 1.0 - Initial Release
 */

metadata {
	definition (name: "HVAC Usage Data", namespace: "rbaldwi3", author: "Reid Baldwin", cstHandler: true) {
		capability "Actuator" // this makes it selectable for custom actions in rule machine
        capability "Refresh" // refresh() ensures that the cumulative time attributes are up to date. Otherwise, the attributes are only updated on changes.
	}
    attribute "cooling", "number" // current cooling rate (kbtu/hr)
    command "set_cooling", ["number"]
    attribute "heating", "number" // current heating rate (kbtu/hr)
    command "set_heating", ["number"]
    attribute "cum_cooling", "number" // cooling since last reset (btu)
    attribute "cum_heating", "number" // heating since last reset (btu)
    attribute "prev_cooling", "number" // previous period cooling (kbtu)
    attribute "prev_heating", "number" // previous period heating (kbtu)
    attribute "prev_date", "date" // date and time of last reset
    command "set_reset_time", ["integer", "integer"] // time to reset each day, arguments are hours and minutes
}

def installed() {
    log.debug("In installed()")
    state.reset_hours = 23
    state.reset_minutes = 30
    schedule("0 30 23 * * ?","reset")
    initialize()
}

def updated() {
    log.debug("In updated()")
    initialize()
}

def initialize() {
    log.debug("In initialize()")
    state.cooling_time = 0
    state.heating_time = 0
    state.cum_cooling = 0
    state.cum_heating = 0
}

def refresh() {
    if (state.cooling_time) { update_cooling(state.cooling) }
    if (state.heating_time) { update_heating(state.heating) }
}

def reset() {
    refresh()
    Integer prev = state.cum_cooling / 10 + 0.5
    sendEvent(name:"prev_cooling", value:(prev / 100))
    state.cum_cooling = 0
    sendEvent(name:"cum_cooling", value:0)
    prev = state.cum_heating / 10 + 0.5
    sendEvent(name:"prev_heating", value:(prev / 100))
    state.cum_heating = 0
    sendEvent(name:"cum_heating", value:0)
    Date reset_date = timeToday("$state.reset_hours:$state.reset_minutes", location.timezone)
    sendEvent(name:"prev_date", value:reset_date)
}

def update_cooling(Number new_value) {
	log.debug("In update_cooling($new_value)")
    duration = now() - state.cooling_time
    state.cooling_time = now()
    state.cum_cooling += state.cooling * duration / 60 / 60
    Integer rounded = state.cum_cooling + 0.5
    sendEvent(name:"cum_cooling", value:rounded)
    state.cooling = new_value
    sendEvent(name:"cooling", value:new_value)
}

def set_cooling(Number new_value) {
	log.debug("In set_cooling($new_value)")
    if (state.cooling_time) {
        update_cooling(new_value)
    } else {
        state.cooling_time = now()
        state.cooling = new_value
        sendEvent(name:"cooling", value:new_value)
    }
}

def update_heating(Number new_value) {
	log.debug("In update_heating($new_value)")
    duration = now() - state.heating_time
    state.heating_time = now()
    state.cum_heating += state.heating * duration / 60 / 60
    Integer rounded = state.cum_heating + 0.5
    sendEvent(name:"cum_heating", value:rounded)
    state.heating = new_value
    sendEvent(name:"heating", value:new_value)
}

def set_heating(Number new_value) {
	log.debug("In set_heating($new_value)")
    if (state.heating_time) {
        update_heating(new_value)
    } else {
        state.heating_time = now()
        state.heating = new_value
        sendEvent(name:"heating", value:new_value)
    }
}

def set_reset_time(Integer hours, Integer minutes) {
	log.debug("In set_reset_time($hours:$minutes)")
    if (hours < 0) { return }
    if (hours > 23) { return }
    if (minutes < 0) { return }
    if (minutes > 59) { return }
    unschedule()
    schedule("0 $minutes $hours * * ?","reset")
    state.reset_hours = hours
    state.reset_minutes = minutes
}
