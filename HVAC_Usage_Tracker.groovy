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
            input "sensors", "capability.temperatureMeasurement", required: true, title: "Outdoor condition sensors (temperature, dewpoint, illuminance, ultravioletIndex)"
            input "reset_time", "time", required:false, title: "Time of day to start new interval"
        }
    }
    page(name: "pageTwo", title: "Data Collectors", install: true, uninstall: true)
}

def pageTwo() {
    dynamicPage(name: "pageTwo") {
        section ("Data Collectors") {
            app(name: "collectors", appName: "HVAC Data Collector", namespace: "rbaldwi3", title: "Create New Data Collector", multiple: true, submitOnChange: true)
        }
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
        subscribe(z, "heat_output", update_heat_output)
        subscribe(z, "cool_output", update_cool_output)
        DNI = "${z.label}_${app.id}"
        zone_device = getChildDevice(DNI)
        if (zone_device) {
            if (set_reset_time) {
                zone_device.set_reset_time(hours, minutes)
            }
            Date prev_date = zone_device.currentState("prev_date").getDate()
            Number prev_heating = zone_device.currentState("prev_heating").getNumberValue()
            Number prev_cooling = zone_device.currentState("prev_cooling").getNumberValue()
            log.debug("$z.label, date=$prev_date, heating=$prev_heating, cooling=$prev_cooling")
            def collectors = getChildApps()
            collectors.each { c ->
                c.new_zone_data(z.label, prev_date, prev_heating, prev_cooling)
            }
            subscribe(zone_device, "prev_date", new_load_set)
        }
    }
    sensors.each { s ->
        subscribe(s, "temperature", update_temperature)
        if (s.hasAttribute("dewpoint")) { subscribe(s, "dewpoint", update_dewpoint) }
        if (s.hasAttribute("illuminance")) { subscribe(s, "illuminance", update_illuminance) }
        if (s.hasAttribute("ultravioletIndex")) { subscribe(s, "ultravioletIndex", update_uvi) }
        DNI = "${s.label}_S${app.id}"
        sensor_device = getChildDevice(new_DNI)
        if (sensor_device) {
            if (set_reset_time) {
                sensor_device.set_reset_time(hours, minutes)
            }
            Date prev_date = sensor_device.currentState("prev_date").getDate()
            Number prev_HDD = sensor_device.currentState("prev_HDD").getNumberValue()
            Number prev_CDD = sensor_device.currentState("prev_CDD").getNumberValue()
            Number prev_DPD = sensor_device.currentState("prev_DPD").getNumberValue()
            Number prev_ID = sensor_device.currentState("prev_ID").getNumberValue()
            Number prev_UVIH = sensor_device.currentState("prev_UVIH").getNumberValue()
            log.debug("$s.label, date=$prev_date, HDD=$prev_HDD, CDD=$prev_CDD, DPD=$prev_DPD, ID=$prev_ID, UVIH=$prev_UVIH")
            def collectors = getChildApps()
            collectors.each { c ->
                c.new_sensor_data(s.label, prev_date, prev_HDD, prev_CDD, prev_DPD, prev_ID, prev_UVIH)
            }
            subscribe(sensor_device, "prev_date", new_condition_set)
        }
    }
}

def new_load_set(evt) {
    log.debug("In new_load_set()")
    device = evt.getDevice()
    if (device) { 
        Date prev_date = device.currentState("prev_date").getDate()
        Number prev_heating = device.currentState("prev_heating").getNumberValue()
        Number prev_cooling = device.currentState("prev_cooling").getNumberValue()
        label = device.getDeviceNetworkId() - "_${app.id}"
        log.debug("$label, date=$prev_date, heating=$prev_heating, cooling=$prev_cooling")
        def collectors = getChildApps()
        collectors.each { c ->
            c.new_zone_data(label, prev_date, prev_heating, prev_cooling)
        }
    }
}

def new_condition_set(evt) {
    log.debug("In new_condition_set()")
    device = evt.getDevice()
    if (device) { 
        Date prev_date = device.currentState("prev_date").getDate()
        Number prev_HDD = device.currentState("prev_HDD").getNumberValue()
        Number prev_CDD = device.currentState("prev_CDD").getNumberValue()
        Number prev_DPD = device.currentState("prev_DPD").getNumberValue()
        Number prev_ID = device.currentState("prev_ID").getNumberValue()
        Number prev_UVIH = device.currentState("prev_UVIH").getNumberValue()
        label = device.label - " Tracker"
        log.debug("$label, date=$prev_date, HDD=$prev_HDD, CDD=$prev_CDD, DPD=$prev_DPD, ID=$prev_ID, UVIH=$prev_UVIH")
        def collectors = getChildApps()
        collectors.each { c ->
            c.new_sensor_data(label, prev_date, prev_HDD, prev_CDD, prev_DPD, prev_ID, prev_UVIH)
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

def update_uvi(evt=NULL) {
    log.debug("In update_uvi()")
    Number new_value = evt.getNumericValue()
    device = evt.getDevice()
    if (device) { 
        DNI = "${device.label}_S${app.id}"
        zone_device = getChildDevice(DNI)
        if (zone_device) { 
            log.debug("setting UVI to $new_value for $device.label") 
            zone_device.set_UVI(new_value)
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
    ultravioletIndex_list = []
    sensors.each { s ->
        metrics_list = []
        if (s.hasAttribute("temperature")) { temperature_list << s.label }
        if (s.hasAttribute("dewpoint")) { dewpoint_list << s.label }
        if (s.hasAttribute("illuminance")) { illuminance_list << s.label }
        if (s.hasAttribute("ultravioletIndex")) { ultravioletIndex_list << s.label }
    }
    return [temperature: temperature_list, dewpoint: dewpoint_list, illuminance: illuminance_list, ultravioletIndex: ultravioletIndex_list]
}
