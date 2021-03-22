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
            input "sensors", "capability.temperatureMeasurement", multiple: true, required: true, title: "Outdoor condition sensors (temperature, dewpoint, illuminance, windSpeed)"
            input "min_interval", "number", required:false, title: "Minimum interval duration"
            input "reset_time", "time", required:false, title: "Time of day to start daily data"
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
                            Map wind_data = from_dc.get_conditions("${s.label}", "WD")
                            if (wind_data) {
                                log.debug "copying wind days for sensor ${s.label}"
                                to_dc.new_condition(wind_data, "${s.label}", "WD", month_keys, day_keys, points)
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
        log.debug("checking $s, DNI = $new_DNI, sensor_device = $sensor_device")
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
        subscribe(z, "load_state", load_state_change)
        if (set_reset_time) {
             z.set_reset_time(hours, minutes)
        }
    }
    sensors.each { s ->
        subscribe(s, "temperature", update_temperature)
        if (s.hasAttribute("dewpoint")) { subscribe(s, "dewpoint", update_dewpoint) }
        if (s.hasAttribute("illuminance")) { subscribe(s, "illuminance", update_illuminance) }
        if (s.hasAttribute("cloud")) { subscribe(s, "cloud", update_cloud) }
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
        minutes += 5
        if (minutes > 59) {
            minutes -= 60
            hours += 1
            if (hours > 23) {
                hours -= 24
            }
        }
        schedule("0 $minutes $hours * * ?","update_data")
        minutes += 5
        if (minutes > 59) {
            minutes -= 60
            hours += 1
            if (hours > 23) {
                hours -= 24
            }
        }
        schedule("0 $minutes $hours * * ?","grab_estimators")
    }
    // update_data()
    // grab_estimators()
    // update_estimates()
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
            Number prev_AID = 0
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
            levelstate = sensor_device.currentState("prev_AID")
            if (levelstate) { prev_AID = levelstate.getNumberValue() }
            levelstate = sensor_device.currentState("prev_WD")
            if (levelstate) { prev_WD = levelstate.getNumberValue() }
            log.debug("$s.label, date=$prev_date, HDD=$prev_HDD, CDD=$prev_CDD, DPD=$prev_DPD, ID=$prev_ID, AID=$prev_AID, WD=$prev_WD")
            def collectors = getChildApps()
            collectors.each { c ->
                c.new_sensor_data(s.label, prev_date, prev_HDD, prev_CDD, prev_DPD, prev_ID, prev_AID, prev_WD)
            }
        } else {
            log.debug("sensor device not found")
        }
    }
    state.intervals = null
}

// state.intervals is [<device> : <device data>]
// <device data> is [<load_type> : beginning_cum_load, "start_time" : <start_time>, <sensor> : <sensor data>, . . .]
// <sensor data> is [<attribute> : beginning_cum_value, . . . ]

Number process_sensor_attribute(String attr, Map sensor_data, String sensor_DNI, Number factor) {
    Number result = null
    sensor_device = getChildDevice(sensor_DNI)
    if (sensor_device) {
        levelstate = sensor_device.currentState("$attr")
        if (levelstate) { 
            Number end_value = levelstate.getNumberValue()
            Number begin_value = sensor_data["$attr"]
            if (begin_value != null) {
                Integer rounded = 1000 * (end_value - begin_value) * factor / 60 / 24 + 0.5
                result = rounded / 1000
                log.debug("ending $attr = $end_value, equivalent daily value = $result")
            }
            sensor_data["$attr"] = end_value
        }
    }
    return result
}

def process_load_state(Map params) {
    log.debug("In process_load_state($params)")
    String device = params["device"]
    log.debug("device = $device")
    Map device_data = null
    if (state.intervals) {
        device_data = state.intervals["$device"]
    }
    if (device_data) {
        log.debug("data for previous interval is: $device_data")
        prev_time = device_data["start_time"]
        Number duration = (now() - prev_time) / 1000 / 60 // interval duration in minutes
        if (min_interval) {
            if (duration < min_interval) {
                log.debug("interval is too short - $duration")
                return
            }
        }
        Number factor = 60 * 24 / duration
        device_data["start_time"] = now()
        List saved_by = []
        zones.each { z ->
            if ("$z.label" == "$device") {
                Number begin_heating = device_data["heat_load"]
                Number end_heating = z.currentState("cum_heating").getNumberValue()
                device_data["heat_load"] = end_heating
                Integer rounded = (end_heating - begin_heating) * factor + 0.5
                Number eq_heat_load = rounded / 1000 // equivalent daily heat load in kbtu
                Number begin_cooling = device_data["cool_load"]
                Number end_cooling = z.currentState("cum_cooling").getNumberValue()
                device_data["cool_load"] = end_cooling
                rounded = (end_cooling - begin_cooling) * factor + 0.5
                Number eq_cool_load = rounded / 1000 // equivalent daily cooling load in kbtu
                def collectors = getChildApps()
                collectors.each { c ->
                    if (c.new_zone_data(z.label, timeToday("12:00", location.timezone), eq_heat_load, eq_cool_load, true)) {
                        log.debug("saved by collector $c.label")
                        saved_by << "$c.label"
                    }
                }
                log.debug("duration = $duration minutes, factor = $factor, daily heat load = $eq_heat_load kbtu, daily cooling load = $eq_cool_load kbtu")
                z.debug("duration = $duration minutes, daily heat load = $eq_heat_load kbtu")
            }
        }
        sensors.each { s ->
            DNI = "${s.label}_S${app.id}"
            Map sensor_data = device_data["$s.label"]
            if (!sensor_data) {
                sensor_data = ["dummy" : 0]
                device_data["$s.label"] = sensor_data
            }
            heating = process_sensor_attribute("HDM", sensor_data, DNI, factor)
            cooling = process_sensor_attribute("CDM", sensor_data, DNI, factor)
            dewpoint = process_sensor_attribute("DPM", sensor_data, DNI, factor)
            illuminance = process_sensor_attribute("IM", sensor_data, DNI, factor)
            adj_illuminance = process_sensor_attribute("AIM", sensor_data, DNI, factor)
            wind = process_sensor_attribute("WM", sensor_data, DNI, factor)
            def collectors = getChildApps()
            collectors.each { c ->
                saved_by.each { sb ->
                    if ("$c.label" == "$sb") {
                        c.new_sensor_data(s.label, timeToday("12:00", location.timezone), heating, cooling, dewpoint, illuminance, adj_illuminance, wind, true)
                    }
                }
            }
        }
    } else {
        log.debug("starting a new interval")
        device_data = ["start_time" : now()]
        zones.each { z ->
            if ("$z.label" == "$device") {
                Number begin_heating = z.currentState("cum_heating").getNumberValue()
                device_data["heat_load"] = begin_heating
                Number begin_cooling = z.currentState("cum_cooling").getNumberValue()
                device_data["cool_load"] = begin_cooling
                log.debug("begin_heating = $begin_heating, begin_cooling = $begin_cooling for $device")
            }
        }
        sensors.each { s ->
            DNI = "${s.label}_S${app.id}"
            Map sensor_data = ["dummy" : 0]
            device_data["$s.label"] = sensor_data
            process_sensor_attribute("HDM", sensor_data, DNI, 1.0)
            process_sensor_attribute("CDM", sensor_data, DNI, 1.0)
            process_sensor_attribute("DPM", sensor_data, DNI, 1.0)
            process_sensor_attribute("IM", sensor_data, DNI, 1.0)
            process_sensor_attribute("AIM", sensor_data, DNI, 1.0)
            process_sensor_attribute("WM", sensor_data, DNI, 1.0)
        }
        if (state.intervals) {
            state.intervals["$device"] = device_data
        } else {
            state.intervals = ["$device" : device_data]
        }
    }
}

def load_state_change(evt=NULL) {
    log.debug("In load_state_change()")
    device = evt.getDevice()
    String new_state = evt.getValue()
    log.debug("device = $device, new_state = $new_state")
    switch (new_state) {
        case "Exception":
            log.debug("discarding current interval")
            if (state.intervals) {
                state.intervals["$device"] = null // not sure why remove() doesn't seem to work
            }
            break
        case "Cooling":
        case "Heating":
            zones.each { z ->
                if ("$z.label" == "$device") {
                    z.refresh()
                }
            }
            sensors.each { s ->
                DNI = "${s.label}_S${app.id}"
                sensor_device = getChildDevice(DNI)
                if (sensor_device) {
                    sensor_device.refresh()
                }
            }
            runIn(10, process_load_state, [data : ["device" : "$device"]])
            break
    }
}

def update_temperature(evt=NULL) {
    // log.debug("In update_temperature()")
    Number new_value = evt.getNumericValue()
    device = evt.getDevice()
    if (device) { 
        DNI = "${device.label}_S${app.id}"
        sensor_device = getChildDevice(DNI)
        if (sensor_device) { 
            // log.debug("setting temperature to $new_value for $device.label") 
            sensor_device.set_temperature(new_value)
            runIn(30, "update_estimates")
        }
    }
}

def update_dewpoint(evt=NULL) {
    // log.debug("In update_dewpoint()")
    Number new_value = evt.getNumericValue()
    device = evt.getDevice()
    if (device) { 
        DNI = "${device.label}_S${app.id}"
        sensor_device = getChildDevice(DNI)
        if (sensor_device) { 
            // log.debug("setting dewpoint to $new_value for $device.label") 
            sensor_device.set_dewpoint(new_value)
            runIn(30, "update_estimates")
        }
    }
}

def update_illuminance(evt=NULL) {
    // log.debug("In update_illuminance()")
    Number new_value = evt.getNumericValue()
    device = evt.getDevice()
    if (device) { 
        DNI = "${device.label}_S${app.id}"
        sensor_device = getChildDevice(DNI)
        if (sensor_device) { 
            // log.debug("setting illuminance to $new_value for $device.label") 
            sensor_device.set_illuminance(new_value)
            runIn(30, "update_estimates")
        }
    }
}

def update_cloud(evt=NULL) {
    // log.debug("In update_cloud()")
    Number new_value = evt.getNumericValue()
    device = evt.getDevice()
    if (device) { 
        DNI = "${device.label}_S${app.id}"
        sensor_device = getChildDevice(DNI)
        if (sensor_device) { 
            // log.debug("setting cloud to $new_value for $device.label") 
            sensor_device.set_cloud(new_value)
        }
    }
}

def update_wind(evt=NULL) {
    // log.debug("In update_wind()")
    Number new_value = evt.getNumericValue()
    device = evt.getDevice()
    if (device) { 
        DNI = "${device.label}_S${app.id}"
        sensor_device = getChildDevice(DNI)
        if (sensor_device) { 
            // log.debug("setting wind to $new_value for $device.label") 
            sensor_device.set_wind(new_value)
            runIn(30, "update_estimates")
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
    adj_illuminance_list = []
    wind_list = []
    sensors.each { s ->
        metrics_list = []
        if (s.hasAttribute("temperature")) { temperature_list << s.label }
        if (s.hasAttribute("dewpoint")) { dewpoint_list << s.label }
        if (s.hasAttribute("illuminance")) {
            illuminance_list << s.label
            if (s.hasAttribute("cloud")) {
                adj_illuminance_list << s.label
            }
        }
        if (s.hasAttribute("windSpeed")) { wind_list << s.label }
    }
    log.debug("finished get_sensors()")
    return [temperature: temperature_list, dewpoint: dewpoint_list, illuminance: illuminance_list, adj_illuminance: adj_illuminance_list, wind: wind_list]
}

// each entry in state.estimators is of the form:
//        ["dc":<dc>] where <dc> is the label of a data collector
//        ["source":<source>] where <source> is the label of a zone for the estimated load
//        ["type":<type>] where <type> is either "heat_load" or "cool_load"
//        ["vars":<list of sensors>] where each entry in <list of sensors> is of the form:
//                ["label":<sensor label>]
//                ["type":<sensor type>] where <sensor type> is either "HDD", "CDD", "DPD", ID", or "WD" 
//        ["coefs":<list of coefficients>] where each entry in <list of coefficients> is a Number
//        ["values":<list of values>] where each entry in <list of values> is a Number
//        ["constant":<constant term>] where <constant term> is a Number
//        ["estimate":<current estimate>] where <current estimate> is a Number

def grab_estimators() {
    state.remove("estimators")
    state.estimators = []
    def collectors = getChildApps()
    collectors.each { c ->
        c.curve_fit()
        Map estim = c.get_estimator()
        if (estim) {
            estim["dc"] = c.label
            state.estimators << estim
        }
    }
}

def update_estimates() {
    state.estimators.each { est ->
        // log.debug("estimator: $est")
        Number updated_estimate = est["constant"]
        List est_vars = est["vars"]
        for (i=0 ; i<est_vars.size() ; i++) {
            label = est_vars[i]["label"]
            typ = est_vars[i]["type"]
            DNI = "${label}_S${app.id}"
            // log.debug "label = $label, typ = $typ, DNI = $DNI"
            sensor_device = getChildDevice(DNI)
            if (sensor_device) {
                // log.debug "device found for $label"
                Number value
                switch ("$typ") {
                    case "HDD":
                        value = sensor_device.currentValue("heating_degrees")
                        break
                    case "CDD":
                        value = sensor_device.currentValue("cooling_degrees")
                        break
                    case "DPD":
                        value = sensor_device.currentValue("dewpoint_degrees")
                        break
                    case "ID":
                        value = sensor_device.currentValue("illuminance")
                        break
                    case "AID":
                        value = sensor_device.currentValue("adj_illuminance")
                        break
                    case "WD":
                        value = sensor_device.currentValue("wind")
                        break
                    default:
                        value = 0.0
                }
                // log.debug "$typ = $value"
                est["values"][i] = value
                updated_estimate += value * est["coefs"][i]
                // log.debug "estimate = $updated_estimate"
            }
        }
        old_est = est["estimate"]
        est["estimate"] = updated_estimate
        zone = est["source"]
        load = est["type"]
        // log.debug "updating estimate of $zone - $load from $old_est to $updated_estimate"
        zones.each { z ->
            if (z.label == zone) {
                Integer rounded = 100 * updated_estimate / 24 + 0.5
                switch ("$load") {
                    case "heat_load":
                        z.set_est_heat_load(rounded / 100)
                        break
                    case "cool_load":
                        z.set_est_cool_load(rounded / 100)
                        break
                }
            }
        }
    }
}
