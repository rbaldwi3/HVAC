/**
 *  Outdoor Condition Data
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
	definition (name: "Outdoor Condition Data", namespace: "rbaldwi3", author: "Reid Baldwin", cstHandler: true) {
		capability "Actuator" // this makes it selectable for custom actions in rule machine
        capability "Refresh" // refresh() ensures that the cumulative time attributes are up to date. Otherwise, the attributes are only updated on changes.
	}
    preferences {
        input "heating_base", "number", required: false, title: "Base temperature for calculating heating degree days (default = 60)"
        input "cooling_base", "number", required: false, title: "Base temperature for calculating cooling degree days (default = 70)"
        input "dewpoint_base", "number", required: false, title: "Base temperature for calculating dewpoint degree days (default = 60)"
    }
    attribute "temperature", "number"
    command "set_temperature", ["number"]
    attribute "HDM", "number"
    attribute "CDM", "number"
    attribute "heating_degrees", "number"
    attribute "cooling_degrees", "number"
    attribute "prev_HDD", "number"
    attribute "prev_CDD", "number"
    attribute "illuminance", "number"
    attribute "adj_illuminance", "number"
    command "set_illuminance", ["number"]
    command "set_cloud", ["number"]
    attribute "IM", "number"
    attribute "AIM", "number"
    attribute "prev_ID", "number"
    attribute "prev_AID", "number"
    attribute "dewpoint", "number"
    command "set_dewpoint", ["number"]
    attribute "DPM", "number"
    attribute "dewpoint_degrees", "number"
    attribute "prev_DPD", "number"
    attribute "wind", "number"
    command "set_wind", ["number"]
    attribute "WM", "number"
    attribute "prev_WD", "number"
    attribute "prev_date", "date" // date and time of last reset
    command "set_reset_time", ["integer", "integer"] // time to reset each day, arguments are hours and minutes
}

def installed() {
    log.debug("In installed()")
    state.illuminance_time = 0
    state.temperature_time = 0
    state.dewpoint_time = 0
    state.uvi_time = 0
    state.AIM = 0
    state.IM = 0
    state.DPM = 0
    state.HDM = 0
    state.CDM = 0
    state.WM = 0
    state.cloud = 0
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
    if (heating_base) {
        state.heat_base = heating_base
    } else {
        state.heat_base = 60
    }
    if (cooling_base) {
        state.cool_base = cooling_base
    } else {
        state.cool_base = 70
    }
    if (dewpoint_base) {
        state.dp_base = dewpoint_base
    } else {
        state.dp_base = 60
    }
}

def refresh() {
	log.debug("In refresh()")
    if (state.temperature_time) { update_temperature(state.temperature) }
    if (state.illuminance_time && (state.cloud != null)) { update_illuminance(state.illuminance, state.cloud) }
    if (state.dewpoint_time) { update_dewpoint(state.dewpoint) }
    if (state.wind_time) { update_wind(state.wind) }
	log.debug("finished refresh()")
}

def reset() {
    refresh()
    // illuminance
    if (state.illuminance_time) {
        Integer prev = state.IM * 10 / 6 / 24
        sendEvent(name:"prev_ID", value:(prev / 100))
        state.IM = 0
        sendEvent(name:"IM", value:0)
        // Adjusted illuminance
        prev = state.AIM * 10 / 6 / 24
        sendEvent(name:"prev_AID", value:(prev / 100))
        state.AIM = 0
        sendEvent(name:"AIM", value:0)
    }
    // ultraviolet index
    if (state.wind_time) {
        Integer prev = state.WM * 10 / 6 / 24
        sendEvent(name:"prev_WD", value:(prev / 100))
        state.WM = 0
        sendEvent(name:"WM", value:0)
    }
    // dewpoint
    if (state.dewpoint_time) {
        Integer prev = state.DPM * 10 / 6 / 24
        sendEvent(name:"prev_DPD", value:(prev / 100))
        state.DPM = 0
        sendEvent(name:"DPM", value:0)
    }
    if (state.temperature_time) {
        // heating
        Integer prev = state.HDM * 10 / 6 / 24
        sendEvent(name:"prev_HDD", value:(prev / 100))
        state.HDM = 0
        sendEvent(name:"HDM", value:0)
        // cooling
        prev = state.CDM * 10 / 6 / 24
        sendEvent(name:"prev_CDD", value:(prev / 100))
        state.CDM = 0
        sendEvent(name:"CDM", value:0)
    }
    Date reset_date = timeToday("$state.reset_hours:$state.reset_minutes", location.timezone)
    sendEvent(name:"prev_date", value:reset_date)
}

def update_temperature(Number new_value) {
	log.debug("In update_temperature($new_value)")
    avg = (state.temperature + new_value) / 2
    duration = now() - state.temperature_time
    state.temperature_time = now()
    if (avg > state.cool_base) {
        state.CDM += (avg - state.cool_base) * duration / 60 / 1000
        Integer rounded = state.CDM + 0.5
        sendEvent(name:"CDM", value:rounded)
        Number degrees = new_value - state.cool_base
        sendEvent(name:"cooling_degrees", value:degrees)
        sendEvent(name:"heating_degrees", value:0.0)
    }
    if (avg < state.heat_base) {
        state.HDM += (state.heat_base - avg) * duration / 60 / 1000
        Integer rounded = state.HDM + 0.5
        sendEvent(name:"HDM", value:rounded)
        log.debug("HDM updated to $rounded")
        Number degrees = state.heat_base - new_value
        sendEvent(name:"heating_degrees", value:degrees)
        sendEvent(name:"cooling_degrees", value:0.0)
    }
    state.temperature = new_value
    sendEvent(name:"temperature", value:new_value)
}

def set_temperature(Number new_value) {
	log.debug("In set_temperature($new_value)")
    if (state.temperature_time) {
        update_temperature(new_value)
    } else {
        state.temperature_time = now()
        state.temperature = new_value
        sendEvent(name:"temperature", value:new_value)
    }
}

def update_illuminance(Number new_illuminance, Number new_cloud) {
	log.debug("In update_illuminance($new_illuminance, $new_cloud)")
    duration = now() - state.illuminance_time
    state.illuminance_time = now()
    avg = (state.illuminance + new_illuminance) / 2
    state.IM += avg * duration / 60 / 1000
    Integer rounded = state.IM + 0.5
    sendEvent(name:"IM", value:rounded)
    log.debug("IM updated to $rounded")
    avg = (state.illuminance * (100 - state.cloud) + new_illuminance * (100 - new_cloud)) / 200
    state.AIM += avg * duration / 60 / 1000
    rounded = state.AIM + 0.5
    sendEvent(name:"AIM", value:rounded)
    log.debug("AIM updated to $rounded")
    state.illuminance = new_illuminance
    sendEvent(name:"illuminance", value:new_illuminance)
    state.cloud = new_cloud
    rounded = new_illuminance * (100 - new_cloud) / 100 + 0.5
    sendEvent(name:"adj_illuminance", value:rounded)
}

def set_cloud(Number new_value) {
	log.debug("In set_cloud($new_value)")
    if (state.illuminance_time && state.cloud) {
        update_illuminance(state.illuminance, new_value)
    } else {
        state.cloud = new_value
    }
}

def set_illuminance(Number new_value) {
	log.debug("In set_illuminance($new_value)")
    if (state.illuminance_time && state.cloud) {
        update_illuminance(new_value, state.cloud)
    } else {
        state.illuminance_time = now()
        state.illuminance = new_value
        sendEvent(name:"illuminance", value:new_value)
    }
}

def update_dewpoint(Number new_value) {
	log.debug("In update_dewpoint($new_value)")
    avg = (state.dewpoint + new_value) / 2
    duration = now() - state.dewpoint_time
    state.dewpoint_time = now()
    state.DPM += (avg - state.dp_base) * duration / 60 / 1000
    Integer rounded = state.DPM + 0.5
    sendEvent(name:"DPM", value:rounded)
    log.debug("DPM updated to $rounded")
    Number degrees = new_value - state.dp_base
    sendEvent(name:"dewpoint_degrees", value:degrees)
    state.dewpoint = new_value
    sendEvent(name:"dewpoint", value:new_value)
}

def set_dewpoint(Number new_value) {
	log.debug("In set_dewpoint($new_value)")
    if (state.dewpoint_time) {
        update_dewpoint(new_value)
    } else {
        state.dewpoint_time = now()
        state.dewpoint = new_value
        sendEvent(name:"dewpoint", value:new_value)
    }
}

def update_wind(Number new_value) {
	log.debug("In update_wind($new_value)")
    avg = (state.wind + new_value) / 2
    duration = now() - state.wind_time
    state.wind_time = now()
    state.WM += avg * duration / 60 / 1000
    Integer rounded = state.WM + 0.5
    sendEvent(name:"WM", value:rounded)
    log.debug("WM updated to $rounded")
    state.wind = new_value
    sendEvent(name:"wind", value:new_value)
}

def set_wind(Number new_value) {
	log.debug("In set_wind($new_value)")
    if (state.wind_time) {
        update_wind(new_value)
    } else {
        state.wind_time = now()
        state.wind = new_value
        sendEvent(name:"wind", value:new_value)
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
