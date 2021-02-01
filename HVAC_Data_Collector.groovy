/**
 *  HVAC Data Collector
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
    name: "HVAC Data Collector",
    namespace: "rbaldwi3",
    author: "Reid Baldwin",
    description: "This app gathers a data set of heating and cooling loads and also outdoor condition data. The app then allows curve fitting load data to outdoor conditions.",
    category: "General",
    parent: "rbaldwi3:HVAC Usage Tracker",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "pageOne", install: true, uninstall: true)
}

def pageOne() {
    dynamicPage(name: "pageOne") {
        section {
            label required: true, multiple: false
        }
        section ("Data to be saved") {
            zones = parent.get_zones()
            input "heat_loads", "enum", required: false, multiple: true, title: "Heating Loads", options: zones
            input "cool_loads", "enum", required: false, multiple: true, title: "Cooling Loads", options: zones
            sensors = parent.get_sensors()
            input "heating_sensors", "enum", required: false, multiple: true, title: "Heating Degree Day Sensors", options: sensors.temperature
            input "cooling_sensors", "enum", required: false, multiple: true, title: "Cooling Degree Day Sensors", options: sensors.temperature
            input "dewpoint_sensors", "enum", required: false, multiple: true, title: "Dewpoint Sensors", options: sensors.dewpoint
            input "illuminance_sensors", "enum", required: false, multiple: true, title: "Illuminance Sensors", options: sensors.temperature
            input "ultravioletIndex_sensors", "enum", required: false, multiple: true, title: "UltravioletIndex Sensors", options: sensors.dewpoint
        }
    }
}

// each entry state.heat_load is of the form [<source>:<data for source>] where <source> is the label of a zone
// each entry in <data for source> is of the form [<month>:<data for month>] where <month> is the number of months since Jan, 1900
// each entry in <data for month> is of the form [<data>:<data>] where <day is the day of the month> and <data is the heat load for that day

def installed() {
    log.debug("In installed()")
    initialize()
}

def uninstalled() {
    log.debug("In uninstalled()")
}

def updated() {
    log.debug("In updated()")
    unsubscribe()
    initialize()
}

def initialize() {
    log.debug("In initialize()")
}

def new_zone_data(String source, Date date, Number heat_load, Number cool_load) {
    log.debug("In new_zone_data($source, $date, $heat_load, $cool_load)")
    int month = date.getMonth() + date.getYear() * 12
    int day = date.getDate()
    int day_of_week = date.getDay()
    if (state.day_of_week) {
        Map month_data = state.day_of_week["$month"]
        if (month_data) {
            month_data << ["$day" : day_of_week]
        } else {
            state.day_of_week << ["$month" : ["$day" : day_of_week]]
        }     
    } else {
        state.day_of_week = ["$month" : ["$day" : day_of_week]]
    }
    // log.debug("month = $month, day = $day, day_of_week = $day_of_week")
    heat_loads.each { hl ->
        if ("$hl" == "$source") {
            if (state.heat_load) {
                Map source_data = state.heat_load["$source"]
                if (source_data) {
                    Map month_data = source_data["$month"]
                    if (month_data) {
                        month_data << ["$day" : heat_load]
                    } else {
                        source_data << ["$month" : ["$day" : heat_load]]
                    }
                } else {
                    state.heat_load << ["$source" : ["$month" : ["$day" : heat_load]]]
                }
            } else {
                state.heat_load = ["$source" : ["$month" : ["$day" : heat_load]]]
            }
        }
    }
    cool_loads.each { cl ->
        if ("$cl" == "$source") {
            if (state.cool_load) {
                Map source_data = state.cool_load["$source"]
                if (source_data) {
                    Map month_data = source_data["$month"]
                    if (month_data) {
                        month_data << ["$day" : cool_load]
                    } else {
                        source_data << ["$month" : ["$day" : cool_load]]
                    }
                } else {
                    state.cool_load << ["$source" : ["$month" : ["$day" : cool_load]]]
                }
            } else {
                state.cool_load = ["$source" : ["$month" : ["$day" : cool_load]]]
            }
        }
    }
}

def new_sensor_data(String source, Date date, Number heating, Number cooling, Number dewpoint, Number illuminance, Number ultravioletIndex) {
    log.debug("In new_sensor_data($source, $date, $heating, $cooling, $dewpoint, $illuminance, $ultravioletIndex)")
    int month = date.getMonth() + date.getYear() * 12
    int day = date.getDate()
    heating_sensors.each { hs ->
        if ("$hs" == "$source") {
            if (state.heating) {
                Map source_data = state.heating["$source"]
                if (source_data) {
                    Map month_data = source_data["$month"]
                    if (month_data) {
                        month_data << ["$day" : heating]
                    } else {
                        source_data << ["$month" : ["$day" : heating]]
                    }
                } else {
                    state.heating << ["$source" : ["$month" : ["$day" : heating]]]
                }
            } else {
                state.heating = ["$source" : ["$month" : ["$day" : heating]]]
            }
        }
    }
    cooling_sensors.each { cs ->
        if ("$cs" == "$source") {
            if (state.cooling) {
                Map source_data = state.cooling["$source"]
                if (source_data) {
                    Map month_data = source_data["$month"]
                    if (month_data) {
                        month_data << ["$day" : cooling]
                    } else {
                        source_data << ["$month" : ["$day" : cooling]]
                    }
                } else {
                    state.cooling << ["$source" : ["$month" : ["$day" : cooling]]]
                }
            } else {
                state.cooling = ["$source" : ["$month" : ["$day" : cooling]]]
            }
        }
    }
    dewpoint_sensors.each { ds ->
        if ("$ds" == "$source") {
            if (state.dewpoint) {
                Map source_data = state.dewpoint["$source"]
                if (source_data) {
                    Map month_data = source_data["$month"]
                    if (month_data) {
                        month_data << ["$day" : dewpoint]
                    } else {
                        source_data << ["$month" : ["$day" : dewpoint]]
                    }
                } else {
                    state.dewpoint << ["$source" : ["$month" : ["$day" : dewpoint]]]
                }
            } else {
                state.dewpoint = ["$source" : ["$month" : ["$day" : dewpoint]]]
            }
        }
    }
    illuminance_sensors.each { ills ->
        if ("$ills" == "$source") {
            if (state.illuminance) {
                Map source_data = state.illuminance["$source"]
                if (source_data) {
                    Map month_data = source_data["$month"]
                    if (month_data) {
                        month_data << ["$day" : illuminance]
                    } else {
                        source_data << ["$month" : ["$day" : illuminance]]
                    }
                } else {
                    state.illuminance << ["$source" : ["$month" : ["$day" : illuminance]]]
                }
            } else {
                state.illuminance = ["$source" : ["$month" : ["$day" : illuminance]]]
            }
        }
    }
    ultravioletIndex_sensors.each { uvs ->
        if ("$uvs" == "$source") {
            if (state.ultravioletIndex) {
                Map source_data = state.ultravioletIndex["$source"]
                if (source_data) {
                    Map month_data = source_data["$month"]
                    if (month_data) {
                        month_data << ["$day" : ultravioletIndex]
                    } else {
                        source_data << ["$month" : ["$day" : ultravioletIndex]]
                    }
                } else {
                    state.ultravioletIndex << ["$source" : ["$month" : ["$day" : ultravioletIndex]]]
                }
            } else {
                state.ultravioletIndex = ["$source" : ["$month" : ["$day" : ultravioletIndex]]]
            }
        }
    }
}
