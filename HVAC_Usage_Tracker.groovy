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
    page(name: "pageOne", nextPage: "pageTwo", uninstall: true) {
        section ("Label") {
            label required: true, multiple: false
        }
        section {
            input "zones", "device.HVACZoneStatus", multiple: true, required: true, title: "Heating and cooling loads to track"
            input "sensors", "capability.temperatureMeasurement", required: true, title: "Outdoor condition sensors (temperature, dewpoint, illuminance, windSpeed)"
            input "reset_time", "time", required:false, title: "Time of day to start new interval"
        }
    }
    page(name: "pageTwo", title: "Data Collectors", install: true, uninstall: true)
    page(name: "DCcopy", nextPage: "pageTwo")
}

def pageTwo() {
    dynamicPage(name: "pageTwo") {
        section ("Data Collectors") {
            app(name: "collectors", appName: "HVAC Data Collector", namespace: "rbaldwi3", title: "Create New Data Collector", multiple: true, submitOnChange: true)
        }
        def collectors = getChildApps()
        if (collectors.size > 1) { 
            section ("Copy Data") {
			    href(name: "DCcopy", title: "Copy Data", required: false, page: "DCcopy", description: "Copy data saved in one data collector to another data collector.")
            }
        }
    }
}
  
def DCcopy() {
    dynamicPage(name: "DCcopy") {
        section ("Data Collectors") {
            paragraph "this is where I want to select the source and destination data collectors"
            def collectors = getChildApps()
            List label_list = []
            collectors.each { c ->
                label_list << "$c.label"
            }
            input(name: "from_collector", type: "enum", required: false, multiple: false, title: "Data Collector to copy from", options: label_list, submitOnChange: true)
            input(name: "to_collector", type: "enum", required: false, multiple: false, title: "Data Collector to copy to", options: label_list, submitOnChange: true)
			input "copy_button", "button", title: "Copy", width: 3
        }
    }
}
  
def appButtonHandler(btn) {
	switch (btn) {
		case "copy_button":
            if (from_collector) { log.debug("copy from $from_collector") }
            if (to_collector) { log.debug("copy to $to_collector") }
            def from_dc
            def to_dc
            if (from_collector && to_collector && (from_collector != to_collector)) {
                def collectors = getChildApps()
                collectors.each { c ->
                    if (c.label == from_collector) { from_dc = c }
                    if (c.label == to_collector) { to_dc = c }
                }
            }
            if (from_dc && to_dc) {
                log.debug "both collectors found"
                Map data = from_dc.available_data()
                if (data) {
                    log.debug "data successfully retrieved"
                    Map month_keys = data["month_keys"]
                    Map day_keys = data["day_keys"]
                    // Map days = data["days"]
                    List points = data["list"]
                    if (month_keys && day_keys && points) {
                        log.debug "sub-maps retrieved"
                        Map day_data = from_dc.get_day_data()
                        if (day_data) {
                            log.debug "copying day_data"
                            to_dc.new_day_data(day_data, month_keys, day_keys, points)
                        }
                        zones.each { z ->
                            Map heat_data = from_dc.get_loads("${z.label}", "heat_load")
                            if (heat_data) {
                                log.debug "copying heat_data for zone ${z.label}"
                                to_dc.new_load(heat_data, "${z.label}", "heat_load", month_keys, day_keys, points)
                            }
                            Map cool_data = from_dc.get_loads("${z.label}", "cool_load")
                            if (cool_data) {
                                log.debug "copying cool_data for zone ${z.label}"
                                to_dc.new_load(cool_data, "${z.label}", "cool_load", month_keys, day_keys, points)
                            }
                            heat_data = from_dc.get_heat_load("${z.label}")
                            if (heat_data) {
                                log.debug "copying heat_data for zone ${z.label}"
                                to_dc.new_heat_load(heat_data, "${z.label}", month_keys, day_keys, points)
                            }
                            cool_data = from_dc.get_cool_load("${z.label}")
                            if (cool_data) {
                                log.debug "copying cool_data for zone ${z.label}"
                                to_dc.new_cool_load(cool_data, "${z.label}", month_keys, day_keys, points)
                            }
                        }
                        sensors.each { s ->
                            Map heat_data = from_dc.get_conditions("${s.label}", "HDD")
                            if (heat_data) {
                                log.debug "copying heating days for sensor ${s.label}"
                                to_dc.new_condition(heat_data, "${s.label}", "HDD", month_keys, day_keys, points)
                            }
                            Map cool_data = from_dc.get_conditions("${s.label}", "CDD")
                            if (cool_data) {
                                log.debug "copying cooling days for sensor ${s.label}"
                                to_dc.new_conditions(cool_data, "${s.label}", "CDD", month_keys, day_keys, points)
                            }
                            Map dewpoint_data = from_dc.get_conditions("${s.label}", "DPD")
                            if (dewpoint_data) {
                                log.debug "copying dewpoint days for sensor ${s.label}"
                                to_dc.new_condition(dewpoint_data, "${s.label}", "DPD", month_keys, day_keys, points)
                            }
                            Map illuminance_data = from_dc.get_conditions("${s.label}", "ID")
                            if (illuminance_data) {
                                log.debug "copying illuminance days for sensor ${s.label}"
                                to_dc.new_condition(illuminance_data, "${s.label}", "ID", month_keys, day_keys, points)
                            }
                            heat_data = from_dc.get_heating_days("${s.label}")
                            if (heat_data) {
                                log.debug "copying heating days for sensor ${s.label}"
                                to_dc.new_heating_days(heat_data, "${s.label}", month_keys, day_keys, points)
                            }
                            cool_data = from_dc.get_cooling_days("${s.label}")
                            if (cool_data) {
                                log.debug "copying cooling days for sensor ${s.label}"
                                to_dc.new_cooling_days(cool_data, "${s.label}", month_keys, day_keys, points)
                            }
                            dewpoint_data = from_dc.get_dewpoint_days("${s.label}")
                            if (dewpoint_data) {
                                log.debug "copying dewpoint days for sensor ${s.label}"
                                to_dc.new_dewpoint_days(dewpoint_data, "${s.label}", month_keys, day_keys, points)
                            }
                            illuminance_data = from_dc.get_illuminance_days("${s.label}")
                            if (illuminance_data) {
                                log.debug "copying illuminance days for sensor ${s.label}"
                                to_dc.new_illuminance_days(illuminance_data, "${s.label}", month_keys, day_keys, points)
                            }
                            wind_data = from_dc.get_wind_days("${s.label}")
                            if (wind_data) {
                                log.debug "copying wind days for sensor ${s.label}"
                                to_dc.new_wind_days(wind_data, "${s.label}", month_keys, day_keys, points)
                            }
                        }
                    }
                }
            }
			break
	}
}

def installed() {
    log.debug("In installed()")
    zones.each { z ->
        new_DNI = "${z.label}_${app.id}"
        new_label = "${z.label} HVAC Usage"
        log.debug("DNI = $new_DNI, label = $new_label")
        addChildDevice("rbaldwi3", "HVAC Usage Data", new_DNI, [isComponent: true, label: new_label])
    }
    sensors.each { s ->
        new_DNI = "${s.label}_S${app.id}"
        new_label = "${s.label} Tracker"
        log.debug("DNI = $new_DNI, label = $new_label")
        addChildDevice("rbaldwi3", "Outdoor Condition Data", new_DNI, [isComponent: true, label: new_label])
    }
    initialize()
}

def uninstalled() {
    log.debug("In uninstalled()")
}

def updated() {
    log.debug("In updated()")
    unsubscribe()
    zones.each { z ->
        new_DNI = "${z.label}_${app.id}"
        zone_device = getChildDevice(new_DNI)
        if (!zone_device) {
            new_label = "${z.label} HVAC Usage"
            log.debug("adding DNI = $new_DNI, label = $new_label")
            addChildDevice("rbaldwi3", "HVAC Usage Data", new_DNI, [isComponent: true, label: new_label])
        }
    }
    sensors.each { s ->
        new_DNI = "${s.label}_S${app.id}"
        sensor_device = getChildDevice(new_DNI)
        if (!sensor_device) {
            new_label = "${s.label} Tracker"
            log.debug("adding DNI = $new_DNI, label = $new_label")
            addChildDevice("rbaldwi3", "Outdoor Condition Data", new_DNI, [isComponent: true, label: new_label])
        }
    }
    initialize()
}

def initialize() {
    log.debug("In initialize()")
    Integer hours
    Integer minutes
    Boolean set_reset_time = false
    if (reset_time) {
        reset_asdate = timeToday(reset_time, location.timeZone)
        if (reset_asdate) {
            hours = reset_asdate.getHours()
            minutes = reset_asdate.getMinutes()
            set_reset_time = true
        }
    }
    zones.each { z ->
        // subscribe(z, "heat_output", update_heat_output)
        // subscribe(z, "cool_output", update_cool_output)
        if (set_reset_time) {
             z.set_reset_time(hours, minutes)
        }
    }
    sensors.each { s ->
        subscribe(s, "temperature", update_temperature)
        if (s.hasAttribute("dewpoint")) { subscribe(s, "dewpoint", update_dewpoint) }
        if (s.hasAttribute("illuminance")) { subscribe(s, "illuminance", update_illuminance) }
        if (s.hasAttribute("windSpeed")) { subscribe(s, "windSpeed", update_wind) }
        DNI = "${s.label}_S${app.id}"
        sensor_device = getChildDevice(DNI)
        if (sensor_device) {
            if (set_reset_time) {
                sensor_device.set_reset_time(hours, minutes)
            }
        }
    }
    if (set_reset_time) {
        // minutes += 5
        // if (minutes > 59) {
            // minutes -= 60
            hours += 10
            if (hours > 23) {
                hours -= 24
            }
        // }
        schedule("0 $minutes $hours * * ?","update_data")
    }
    update_data()
}

def update_data() {
    log.debug("In update_data()")
    zones.each { z ->
        Date prev_date = z.currentState("prev_date").getDate()
        Number prev_heating = z.currentState("prev_heating").getNumberValue()
        Number prev_cooling = z.currentState("prev_cooling").getNumberValue()
        log.debug("$z.label, date=$prev_date, heating=$prev_heating, cooling=$prev_cooling")
        def collectors = getChildApps()
        collectors.each { c ->
            c.new_zone_data(z.label, prev_date, prev_heating, prev_cooling)
        }
    }
    sensors.each { s ->
        DNI = "${s.label}_S${app.id}"
        log.debug("DNI = $DNI")
        sensor_device = getChildDevice(DNI)
        if (sensor_device) {
            Number prev_HDD = 0
            Number prev_CDD = 0
            Number prev_DPD = 0
            Number prev_ID = 0
            Number prev_WD = 0
            Date prev_date = sensor_device.currentState("prev_date").getDate()
            def levelstate = sensor_device.currentState("prev_HDD")
            if (levelstate) { prev_HDD = levelstate.getNumberValue() }
            levelstate = sensor_device.currentState("prev_CDD")
            if (levelstate) { prev_CDD = levelstate.getNumberValue() }
            levelstate = sensor_device.currentState("prev_DPD")
            if (levelstate) { prev_DPD = levelstate.getNumberValue() }
            levelstate = sensor_device.currentState("prev_ID")
            if (levelstate) { prev_ID = levelstate.getNumberValue() }
            levelstate = sensor_device.currentState("prev_WD")
            if (levelstate) { prev_WD = levelstate.getNumberValue() }
            log.debug("$s.label, date=$prev_date, HDD=$prev_HDD, CDD=$prev_CDD, DPD=$prev_DPD, ID=$prev_ID, WD=$prev_WD")
            def collectors = getChildApps()
            collectors.each { c ->
                c.new_sensor_data(s.label, prev_date, prev_HDD, prev_CDD, prev_DPD, prev_ID, prev_WD)
            }
        } else {
            log.debug("sensor device not found")
        }
    }
}

def update_heat_output(evt) {
    log.debug("In update_heat_output()")
    Number new_value = evt.getNumericValue()
    device = evt.getDevice()
    if (device) { 
        DNI = "${device.label}_${app.id}"
        zone_device = getChildDevice(DNI)
        if (zone_device) { 
            log.debug("setting heating to $new_value for $device.label") 
            zone_device.set_heating(new_value)
        }
    }
}

def update_cool_output(evt=NULL) {
    log.debug("In update_cool_output()")
    Number new_value = evt.getNumericValue()
    device = evt.getDevice()
    if (device) { 
        DNI = "${device.label}_${app.id}"
        zone_device = getChildDevice(DNI)
        if (zone_device) { 
            log.debug("setting cooling to $new_value for $device.label") 
            zone_device.set_cooling(new_value)
        }
    }
}

def update_temperature(evt=NULL) {
    log.debug("In update_temperature()")
    Number new_value = evt.getNumericValue()
    device = evt.getDevice()
    if (device) { 
        DNI = "${device.label}_S${app.id}"
        zone_device = getChildDevice(DNI)
        if (zone_device) { 
            log.debug("setting temperature to $new_value for $device.label") 
            zone_device.set_temperature(new_value)
        }
    }
}

def update_dewpoint(evt=NULL) {
    log.debug("In update_dewpoint()")
    Number new_value = evt.getNumericValue()
    device = evt.getDevice()
    if (device) { 
        DNI = "${device.label}_S${app.id}"
        zone_device = getChildDevice(DNI)
        if (zone_device) { 
            log.debug("setting dewpoint to $new_value for $device.label") 
            zone_device.set_dewpoint(new_value)
        }
    }
}

def update_illuminance(evt=NULL) {
    log.debug("In update_illuminance()")
    Number new_value = evt.getNumericValue()
    device = evt.getDevice()
    if (device) { 
        DNI = "${device.label}_S${app.id}"
        zone_device = getChildDevice(DNI)
        if (zone_device) { 
            log.debug("setting illuminance to $new_value for $device.label") 
            zone_device.set_illuminance(new_value)
        }
    }
}

def update_wind(evt=NULL) {
    log.debug("In update_wind()")
    Number new_value = evt.getNumericValue()
    device = evt.getDevice()
    if (device) { 
        DNI = "${device.label}_S${app.id}"
        zone_device = getChildDevice(DNI)
        if (zone_device) { 
            log.debug("setting wind to $new_value for $device.label") 
            zone_device.set_wind(new_value)
        }
    }
}

List get_zones() {
    log.debug("In get_zones()")
    result = []
    zones.each { z ->
        result << "${z.label}"
    }
    return result
}

Map get_sensors() {
    log.debug("In get_sensors()")
    temperature_list = []
    dewpoint_list = []
    illuminance_list = []
    wind_list = []
    sensors.each { s ->
        metrics_list = []
        if (s.hasAttribute("temperature")) { temperature_list << s.label }
        if (s.hasAttribute("dewpoint")) { dewpoint_list << s.label }
        if (s.hasAttribute("illuminance")) { illuminance_list << s.label }
        if (s.hasAttribute("windSpeed")) { wind_list << s.label }
    }
    return [temperature: temperature_list, dewpoint: dewpoint_list, illuminance: illuminance_list, wind: wind_list]
}
