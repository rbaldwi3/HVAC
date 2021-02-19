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
    page(name: "pageOne", nextPage: "pageTwo", uninstall: true)
    page(name: "pageTwo", title: "Edit", nextPage: "pageThree", uninstall: true)
    page(name: "pageThree", title: "Curve Fit", install: true, uninstall: true)
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
            input "illuminance_sensors", "enum", required: false, multiple: true, title: "Illuminance Sensors", options: sensors.illuminance
            input "wind_sensors", "enum", required: false, multiple: true, title: "Wind Sensors", options: sensors.wind
        }
    }
}

def pageTwo() {
    dynamicPage(name: "pageTwo") {
        section () {
            Map data = available_data()
            if (data) {
                Map month_keys = data["month_keys"]
                // log.debug "month_keys = $month_keys"
                Map day_keys = data["day_keys"]
                // log.debug "day_keys = $day_keys"
                Map days = data["days"]
                log.debug "days = $days"
                if (data) {
                    // paragraph "$data"
                    // log.debug "$data"
                    input "data_point", "enum", required: false, multiple: false, title: "Data Points", options: data.list, submitOnChange: true
                    if (data_point) {
                        // log.debug "$data_point"
                        if (month_keys) {
                             state.month_key = month_keys["$data_point"]
                             // log.debug "month_key = $month_key"
                        }
                        if (day_keys) {
                             state.day_key = day_keys["$data_point"]
                             // log.debug "day_key = $day_key"
                        }
                        if (state.month_key && state.day_key) {
                            if (days) {
                                String day_data = days["$data_point"]
                                if (day_data != null) {
                                    log.debug "$day_data"
                                    paragraph "$day_data"
                                }
                            }
                            heating_sensors.each { hs ->
                                Number value = get_condition("$hs", "HDD", "$state.month_key", "$state.day_key")
                                if (value != null) {
                                    paragraph "Heating Degree Days: $hs : $value"
                                }
                                /*
                                if (state.heating) {
                                    Map sensor_data = state.heating["$hs"]
                                    // log.debug "$sensor_data"
                                    if (sensor_data) {
                                        Map month_data = sensor_data["$state.month_key"]
                                        // log.debug "$month_data"
                                        if (month_data) {
                                            Number day_data = month_data["$state.day_key"]
                                            if (day_data != null) {
                                                // log.debug "$day_data"
                                                paragraph "Heating Degree Days: $hs : $day_data"
                                            }
                                        }
                                    }
                                }
                                */
                            }
                            cooling_sensors.each { cs ->
                                Number value = get_condition("$cs", "CDD", "$state.month_key", "$state.day_key")
                                if (value != null) {
                                    paragraph "Cooling Degree Days: $cs : $value"
                                }
                                /*
                                if (state.cooling) {
                                    Map sensor_data = state.cooling["$cs"]
                                    if (sensor_data) {
                                        Map month_data = sensor_data["$state.month_key"]
                                        if (month_data) {
                                            Number day_data = month_data["$state.day_key"]
                                            if (day_data != null) {
                                                paragraph "Cooling Degree Days: $cs : $day_data"
                                            }
                                        }
                                    }
                                }
                                */
                            }
                            dewpoint_sensors.each { ds ->
                                Number value = get_condition("$ds", "DPD", "$state.month_key", "$state.day_key")
                                if (value != null) {
                                    paragraph "Dewpoint Degree Days: $ds : $value"
                                }
                                /*
                                if (state.dewpoint) {
                                    Map sensor_data = state.dewpoint["$ds"]
                                    if (sensor_data) {
                                        Map month_data = sensor_data["$state.month_key"]
                                        if (month_data) {
                                            Number day_data = month_data["$state.day_key"]
                                            if (day_data != null) {
                                                paragraph "Dewpoint Degree Days: $ds : $day_data"
                                            }
                                        }
                                    }
                                }
                                */
                            }
                            illuminance_sensors.each { ills ->
                                Number value = get_condition("$ills", "ID", "$state.month_key", "$state.day_key")
                                if (value != null) {
                                    paragraph "Illuminance Days: $ills : $value"
                                }
                                /*
                                if (state.illuminance) {
                                    Map sensor_data = state.illuminance["$ills"]
                                    if (sensor_data) {
                                        Map month_data = sensor_data["$state.month_key"]
                                        if (month_data) {
                                            Number day_data = month_data["$state.day_key"]
                                            if (day_data != null) {
                                                paragraph "Illuminance Days: $ills : $day_data"
                                            }
                                        }
                                    }
                                }
                                */
                            }
                            wind_sensors.each { ws ->
                                Number value = get_condition("$ws", "WD", "$state.month_key", "$state.day_key")
                                if (value != null) {
                                    paragraph "Wind Days: $ws : $value"
                                }
                            }
                            heat_loads.each { hl ->
                                Number value = get_load("$hl", "heat_load", "$state.month_key", "$state.day_key")
                                if (value != null) {
                                    paragraph "Heat Load: $hl : $value"
                                }
                                /*
                                if (state.heat_load) {
                                    Map source_data = state.heat_load["$hl"]
                                    // log.debug "$source_data"
                                    if (source_data) {
                                        Map month_data = source_data["$state.month_key"]
                                        // log.debug "$month_data"
                                        if (month_data) {
                                            Number day_data = month_data["$state.day_key"]
                                            if (day_data != null) {
                                                // log.debug "$day_data"
                                                paragraph "Heat Load: $hl : $day_data"
                                            }
                                        }
                                    }
                                }   
                                */
                            }
                            cool_loads.each { cl ->
                                Number value = get_load("$cl", "cool_load", "$state.month_key", "$state.day_key")
                                if (value != null) {
                                    paragraph "Cooling Load: $cl : $value"
                                }
                                /*
                                if (state.cool_load) {
                                    Map source_data = state.cool_load["$cl"]
                                    if (source_data) {
                                        Map month_data = source_data["$state.month_key"]
                                        if (month_data) {
                                            Number day_data = month_data["$state.day_key"]
                                            if (day_data != null) {
                                                paragraph "Cooling Load: $hl : $day_data"
                                            }
                                        }
                                    }
                                }   
                                */
                            }
		                	input "delete_button", "button", title: "Delete", width: 6
                        }
                    }
                }
            }
        }
    }
}

def pageThree() {
    dynamicPage(name: "pageThree") {
        List loads
        List signals = ["Constant"]
        heating_sensors.each { hs ->
            signals << "Heating degree days - $hs"
        }
        cooling_sensors.each { cs ->
            signals << "Cooling degree days - $cs"
        }
        dewpoint_sensors.each { ds ->
            signals << "Dewpoint - $ds"
        }
        illuminance_sensors.each { ills ->
            signals << "Illuminance - $ills"
        }
        wind_sensors.each { ws ->
            signals << "Wind Speed - $ws"
        }
        heat_loads.each { hl ->
            if (loads) {
                loads << "Heating load - $hl"
            } else {
                loads = ["Heating load - $hl"]
            }
        }
        cool_loads.each { cl ->
            if (loads) {
                loads << "Cooling load - $cl"
            } else {
                loads = ["Cooling load - $cl"]
            }
        }
        section ("Curve Fitting") {
            input "dependent", "enum", required: false, multiple: false, title: "Load to Estimate", options: loads
            input "independent", "enum", required: false, multiple: true, title: "Sensor Signals", options: signals
        }
    }
}

def appButtonHandler(btn) {
	switch (btn) {
		case "delete_button":
            log.debug("deleting data for $state.month_key - $state.day_key")
            delete_zone_data("$state.month_key", "$state.day_key")
            delete_sensor_data("$state.month_key", "$state.day_key")
            delete_load_data(heat_loads, "$state.month_key", "$state.day_key")
            delete_load_data(cool_loads, "$state.month_key", "$state.day_key")
            delete_sensor_data(heating_sensors, "$state.month_key", "$state.day_key")
            delete_sensor_data(cooling_sensors, "$state.month_key", "$state.day_key")
            delete_sensor_data(dewpoint_sensors, "$state.month_key", "$state.day_key")
            delete_sensor_data(illuminance_sensors, "$state.month_key", "$state.day_key")
            delete_sensor_data(wind_sensors, "$state.month_key", "$state.day_key")
            break
	}
}

// each entry state.heat_load is of the form [<source>:<data for source>] where <source> is the label of a zone
// each entry in <data for source> is of the form [<month>:<data for month>] where <month> is the number of months since Jan, 1900
// each entry in <data for month> is of the form [<data>:<data>] where <day is the day of the month> and <data is the heat load for that day

def installed() {
    log.debug("In installed()")
    initialize()
    state.first_month = 1452
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
    // copy_loads()
    // copy_sensors()
    curve_fit()
}

// each entry of state.loads is of the form [<source>:<data for source>] where <source> is the label of a zone
// each entry in <data for source> is of the form [<type>:<data for type>] where <type> is either "heat_load" or "cool_load"
// each entry is <data for type> is of the form [<month>:<data for month>] where <month> is the number of months since Jan, 1900
// each entry in <data for month> is of the form [<data>:<data>] where <day is the day of the month> and <data is the heat load for that day

// each entry of state.sensors is of the form [<sensor>:<data for sensor>] where <sensor> is the label of an outdoor condition data device
// each entry in <data for sensor> is of the form [<type>:<data for type>] where <type> is either "HDD", "CDD", "DPD", or "ID"
// each entry is <data for type> is of the form [<month>:<data for month>] where <month> is the number of months since Jan, 1900
// each entry in <data for month> is of the form [<data>:<data>] where <day is the day of the month> and <data is the heat load for that day

Number get_value(Map data, String month_key, String day_key) {
    if (data) {
        Map month_data = data["$month_key"]
        if (month_data) {
            return month_data["$day_key"]
        } else {return null }
    } else { return null }
}

Number get_value(Map data, String typ, String month_key, String day_key) {
    if (data) {
        Map typ_data = data["$typ"]
        if (typ_data) {
            return get_value(typ_data, month_key, day_key)
        } else {return null }
    } else { return null }
}

def set_value(Number value, Map source_data, String typ, String month_key, String day_key) {
    log.debug("In set_value($value, $source_data, $typ, $month_key, $day_key)")
    Map typ_data = source_data["$typ"]
    if (typ_data) {
        Map month_data = typ_data["$month_key"]
        if (month_data) {
            Number day_data = month_data["$day_key"]
            if (day_data) {
                month_data["$day_key"] = value
            } else {
                month_data << ["$day_key" : value]
            }
        } else {
            typ_data << ["$month_key" : ["$day_key" : value]]
        }
    } else {
        source_data << ["$typ" : ["$month_key" : ["$day_key" : value]]]
    }
}

def set_condition(Number value, String sensor, String typ, String month_key, String day_key) {
    log.debug("In set_condition($value, $sensor, $typ, $month_key, $day_key)")
    if (state.sensors) {
        Map sensor_data = state.sensors["$sensor"]
        if (sensor_data) {
            set_value(value, sensor_data, typ, month_key, day_key)
        } else {
            state.sensors << ["$sensor" : ["$typ" : ["$month_key" : ["$day_key" : value]]]]
        }
    } else {
        state.sensors = ["$sensor" : ["$typ" : ["$month_key" : ["$day_key" : value]]]]
    }
}

Number get_condition(String sensor, String typ, String month_key, String day_key) {
    log.debug("In get_condition($source, $typ, $month_key, $day_key)")
    if (state.sensors) {
        Map sensor_data = state.sensors["$sensor"]
        if (sensor_data) {
            return get_value(sensor_data, typ, month_key, day_key)
        } else {return null }
    } else { return null }
}

def set_load(Number value, String source, String typ, String month_key, String day_key) {
    // log.debug("In set_load($source, $typ, $month_key, $day_key)")
    if (state.loads) {
        Map source_data = state.loads["$source"]
        if (source_data) {
            set_value(value, source_data, typ, month_key, day_key)
        } else {
            state.loads << ["$source" : ["$typ" : ["$month_key" : ["$day_key" : value]]]]
        }
    } else {
        state.loads = ["$source" : ["$typ" : ["$month_key" : ["$day_key" : value]]]]
    }
}

Number get_load(String source, String typ, String month_key, String day_key) {
    // log.debug("In get_load($source, $typ, $month_key, $day_key)")
    if (state.loads) {
        Map source_data = state.loads[source]
        if (source_data) {
            return get_value(source_data, typ, month_key, day_key)
        } else {return null }
    } else { return null }
}

def delete_data(Map typ_data, String month_key, String day_key) {
    if (typ_data) {
        Map month_data = typ_data["$month_key"]
        if (month_data) {
            month_data.remove(day_key)
        }
    }
}

def delete_load_data(List sources, String month_key, String day_key) {
    if (state.loads) {
        sources.each { src ->
            Map source_data = state.loads["$src"]
            if (source_data) {
                delete_data(source_data["heat_load"], month_key, day_key)
                delete_data(source_data["cool_load"], month_key, day_key)
            }
        }
    }
}

def delete_sensor_data(List sensors, String month_key, String day_key) {
    if (state.sensors) {
        sensors.each { sen ->
            Map sensor_data = state.sensors["$sen"]
            if (sensor_data) {
                delete_data(sensor_data["HDD"], month_key, day_key)
                delete_data(sensor_data["CDD"], month_key, day_key)
                delete_data(sensor_data["DPD"], month_key, day_key)
                delete_data(sensor_data["ID"], month_key, day_key)
                delete_data(sensor_data["WD"], month_key, day_key)
            }
        }
    }
}

// This is a temporary routine to copy data to new format
def copy_loads() {
    if (state.heat_load) {
        heat_loads.each { hl ->
            Map old_source_data = state.heat_load["$hl"]
            if (old_source_data) {
                if (state.loads) {
                    Map new_source_data = state.loads["$hl"]
                    if (new_source_data) {
                        new_source_data["heat_load"] = old_source_data
                    } else {
                        state.loads << ["$hl" : ["heat_load" : old_source_data]]
                    }
                } else {
                    state.loads = ["$hl" : ["heat_load" : old_source_data]]
                }
            }
        }
    }
}

// This is a temporary routine to copy data to new format
def copy_sensors() {
    if (state.heating) {
        heating_sensors.each { hs ->
            Map old_sensor_data = state.heating["$hs"]
            if (old_sensor_data) {
                if (state.sensors) {
                    Map new_sensor_data = state.sensors["$hs"]
                    if (new_sensor_data) {
                        new_sensor_data["HDD"] = old_sensor_data
                    } else {
                        state.sensors << ["$hs" : ["HDD" : old_sensor_data]]
                    }
                } else {
                    state.sensors = ["$hs" : ["HDD" : old_sensor_data]]
                }
            }
        }
    }
    if (state.dewpoint) {
        dewpoint_sensors.each { ds ->
            Map old_sensor_data = state.dewpoint["$ds"]
            if (old_sensor_data) {
                if (state.sensors) {
                    Map new_sensor_data = state.sensors["$ds"]
                    if (new_sensor_data) {
                        new_sensor_data["DPD"] = old_sensor_data
                    } else {
                        state.sensors << ["$ds" : ["DPD" : old_sensor_data]]
                    }
                } else {
                    state.sensors = ["$ds" : ["DPD" : old_sensor_data]]
                }
            }
        }
    }
    if (state.illuminance) {
        illuminance_sensors.each { ills ->
            Map old_sensor_data = state.illuminance["$ills"]
            if (old_sensor_data) {
                if (state.sensors) {
                    Map new_sensor_data = state.sensors["$ills"]
                    if (new_sensor_data) {
                        new_sensor_data["ID"] = old_sensor_data
                    } else {
                        state.sensors << ["$ills" : ["ID" : old_sensor_data]]
                    }
                } else {
                    state.sensors = ["$ills" : ["ID" : old_sensor_data]]
                }
            }
        }
    }
}

def curve_fit() {
    log.debug("In curve_fit()")
    if (!dependent) { return }
    if (!independent) { return }
    Map data = available_data()
    if (data) { // [list : list_of_days, days : days_of_week, month_keys : month_keys, day_keys : day_keys]
        pts = []
        data.list.each { li ->
            pt = []
            month_key = data.month_keys["$li"]
            day_key = data.day_keys["$li"]
            independent.each { iv ->
                if ("$iv" == "Constant") {
                    pt << 1.0
                } else {
                    heating_sensors.each { hs ->
                        if ("$iv" == "Heating degree days - $hs") {
                            Number value = get_condition("$hs", "HDD", "$month_key", "$day_key")
                            if (value != null) { pt << value }
                        }
                    }
                    cooling_sensors.each { cs ->
                        if ("$iv" == "Cooling degree days - $cs") {
                            Number value = get_condition("$cs", "CDD", "$month_key", "$day_key")
                            if (value != null) { pt << value }
                        }
                    }
                    dewpoint_sensors.each { ds ->
                        if ("$iv" == "Dewpoint - $ds") {
                            Number value = get_condition("$ds", "DPD", "$month_key", "$day_key")
                            if (value != null) { pt << value }
                        }
                    }
                    illuminance_sensors.each { ills ->
                        if ("$iv" == "Illuminance - $ills") {
                            Number value = get_condition("$ills", "ID", "$month_key", "$day_key")
                            if (value != null) { pt << value }
                        }
                    }
                    wind_sensors.each { ws ->
                        if ("$iv" == "Wind Speed - $ws") {
                            Number value = get_condition("$ws", "WD", "$month_key", "$day_key")
                            if (value != null) { pt << value }
                        }
                    }
                }
            }
            heat_loads.each { hl ->
                if ("$dependent" == "Heating load - $hl") {
                    Number value = get_load("$hl", "heat_load", "$month_key", "$day_key")
                    if (value != null) { pt << value }
                }
            }
            cool_loads.each { cl ->
                if ("$dependent" == "Cooling load - $cl") {
                    Number value = get_load("$cl", "cool_load", "$month_key", "$day_key")
                    if (value != null) { pt << value }
                }
            }
            // log.debug "($month_key - $day_key - $pt)"
            if (pt.size() == independent.size() + 1) { pts << pt }
        }
        // log.debug "$pts"
        if (pts.size() < independent.size() + 2) { return }
        List lhs = []
        List rhs = []
        List sums = []
        int sz=independent.size()
        Number sum = 0.0
        for (i=0 ; i<sz ; i++) {
            List row = []
            for (j=0 ; j<sz ; j++) {
                row << 0.0
            }
            lhs << row
            rhs << 0.0
            sums << 0.0
        }
        pts.each { point ->
            // log.debug "$point"
            for (i=0 ; i<sz ; i++) {
                for (j=0 ; j<sz ; j++) {
                    lhs[i][j] += point[i] * point[j]
                }
                rhs[i] += point[i] * point[sz]
                sums[i] += point[i]
            }
            sum += point[sz]
        }
        // log.debug "lhs = $lhs, rhs = $rhs, mean = $mean"
        for (i=0 ; i<sz-1 ; i++) {
            // zero out the ith column in every row greater than i
            for (j=i+1 ; j<sz ; j++) {
                // zero out the ith column in the jth row
                Number factor = lhs[i][i] / lhs[j][i]
                for (k=0 ; k<sz ; k++) {
                    lhs[j][k] = lhs[i][k] - lhs[j][k] * factor
                }
                rhs[j] = rhs[i] - rhs[j] * factor
            }
            // log.debug "lhs = $lhs, rhs = $rhs"
        }
        for (i=sz-1 ; i>0 ; i--) {
            // zero out the ith column in every row less than i
            for (j=i-1 ; j>=0 ; j--) {
                // zero out the ith column in the jth row
                Number factor = lhs[i][i] / lhs[j][i]
                for (k=0 ; k<sz ; k++) {
                    lhs[j][k] = lhs[i][k] - lhs[j][k] * factor
                }
                rhs[j] = rhs[i] - rhs[j] * factor
            }
            // log.debug "lhs = $lhs, rhs = $rhs"
        }
        Number mean = sum / pts.size()
        List means = []
        List squared_vars = []
        result = []
        for (i=0 ; i<sz ; i++) {
            squared_vars << 0.0
            means << sums[i] / pts.size()
            result << rhs[i] / lhs[i][i]
        }
        // log.debug "$result"
        Number squared_var = 0
        Number squared_res = 0
        pts.each { point ->
            Number est = 0.0
            for (int i=0 ; i<sz ; i++) {
                est += result[i] * point[i]
                squared_vars[i] += (point[i] - means[i]) ** 2
            }
            Integer val = est * 100 + 0.5
            Number rounded = val / 100
            log.debug "$point - estimate = $rounded"
            squared_var += (point[sz] - mean) ** 2
            squared_res += (point[sz] - est) ** 2
        }
        // log.debug "squared_var = $squared_var, squared_res = $squared_res"
        state.coefs = []
        for (int i=0 ; i<sz ; i++) {
            Integer val = means[i] * 100 + 0.5
            Number rounded_mean = val / 100
            val = (squared_vars[i] / pts.size()) ** (0.5) * 100 + 0.5
            Number std_dev = val / 100
            val = result[i] * 10000 + 0.5
            Number rounded_result = val / 10000
            state.coefs << rounded_result
            label = independent[i]
            log.debug "$label - mean = $rounded_mean, std dev = $std_dev, coefficient = $rounded_result"
        }
        Integer val = mean * 100 + 0.5
        state.mean = val / 100
        val = (squared_var / pts.size()) ** (0.5) * 100 + 0.5
        state.std_dev = val / 100
        val = (squared_res / pts.size()) ** (0.5) * 100 + 0.5
        state.std_err = val / 100
        Number r_squared = 1 - (squared_res / squared_var) * (pts.size() - 1) / (pts.size() - sz - 1)
        val = r_squared * 1000 + 0.5
        state.r_squared = val / 1000
        log.debug "$dependent - mean = $state.mean, std dev = $state.std_dev, std residual = $state.std_err, r_squared = $state.r_squared"
    }
}

Map available_data() {
    if (state.day_of_week) {
        Map days_spelled = [0 : "Sunday", 1 : "Monday", 2 : "Tuesday", 3 : "Wednesday", 4 : "Thursday", 5 : "Friday", 6 : "Saturday"]
        List list_of_days = []
        Map days_of_week
        Map month_keys
        Map day_keys
        Date current_date = timeToday("12:00", location.timezone)
        int month_key = current_date.getMonth() + current_date.getYear() * 12
        while (month_key >= state.first_month) {
            Map month_data = state.day_of_week["$month_key"]
            if (month_data) {
                int year = month_key / 12
                int month = month_key - year * 12 + 1
                year += 1900
                for (int i = 31; i > 0; i--) {
                    day_data = month_data["$i"]
                    if (day_data != null) {
                        list_of_days << "$month-$i-$year"
                        if (days_of_week) {
                            days_of_week["$month-$i-$year"] = days_spelled[day_data]
                            month_keys["$month-$i-$year"] = month_key
                            day_keys["$month-$i-$year"] = i
                        } else {
                            days_of_week = ["$month-$i-$year" : days_spelled[day_data]]
                            month_keys = ["$month-$i-$year" : month_key]
                            day_keys = ["$month-$i-$year" : i]
                        }
                    }
                }
            }
            month_key--
        } 
        return [list : list_of_days, days : days_of_week, month_keys : month_keys, day_keys : day_keys]
    } else {
        return null
    }
}

def new_zone_data(String source, Date date, Number heat_load, Number cool_load) {
    // log.debug("In new_zone_data($source, $date, $heat_load, $cool_load)")
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
            set_load(heat_load, source, "heat_load", "$month", "$day")
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
            set_load(cool_load, source, "cool_load", "$month", "$day")
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

def new_sensor_data(String source, Date date, Number heating, Number cooling, Number dewpoint, Number illuminance, Number wind) {
    log.debug("In new_sensor_data($source, $date, $heating, $cooling, $dewpoint, $illuminance, $wind)")
    int month = date.getMonth() + date.getYear() * 12
    int day = date.getDate()
    heating_sensors.each { hs ->
        if ("$hs" == "$source") {
            set_condition(heating, source, "HDD", "$month", "$day")
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
            set_condition(cooling, source, "CDD", "$month", "$day")
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
            set_condition(dewpoint, source, "DPD", "$month", "$day")
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
            set_condition(illuminance, source, "ID", "$month", "$day")
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
    wind_sensors.each { ws ->
        if ("$ws" == "$source") {
            set_condition(wind, source, "WD", "$month", "$day")
            if (state.wind) {
                Map source_data = state.wind["$source"]
                if (source_data) {
                    Map month_data = source_data["$month"]
                    if (month_data) {
                        month_data << ["$day" : wind]
                    } else {
                        source_data << ["$month" : ["$day" : wind]]
                    }
                } else {
                    state.wind << ["$source" : ["$month" : ["$day" : wind]]]
                }
            } else {
                state.wind = ["$source" : ["$month" : ["$day" : wind]]]
            }
        }
    }
}

def delete_zone_data(String month_key, String day_key) {
    // log.debug("In delete_zone_data($month_key, $day_key)")
    if (state.day_of_week) {
        Map month_data = state.day_of_week["$month_key"]
        if (month_data) {
            // log.debug "attempting to delete [$day_key:*]"
            month_data.remove(day_key)
        }     
    }
    heat_loads.each { hl ->
        if (state.heat_load) {
            Map source_data = state.heat_load["$hl"]
            if (source_data) {
                Map month_data = source_data["$month_key"]
                if (month_data) {
                    // log.debug "attempting to delete [$day_key:*]"
                    month_data.remove(day_key)
                }
            }
        }
    }
    cool_loads.each { cl ->
        if (state.cool_load) {
            Map source_data = state.cool_load["$cl"]
            if (source_data) {
                Map month_data = source_data["$month_key"]
                if (month_data) {
                    // log.debug "attempting to delete [$day_key:*]"
                    month_data.remove(day_key)
                }
            }
        }
    }
}

def delete_sensor_data(String month_key, String day_key) {
    // log.debug("In delete_sensor_data($month_key, $day_key)")
    heating_sensors.each { hs ->
        if (state.heating) {
            Map sensor_data = state.heating["$hs"]
            if (sensor_data) {
                Map month_data = sensor_data["$month_key"]
                if (month_data) {
                    // log.debug "attempting to delete [$day_key:*]"
                    month_data.remove(day_key)
                }
            }
        }
    }
    cooling_sensors.each { cs ->
        if (state.cooling) {
            Map sensor_data = state.cooling["$cs"]
            if (sensor_data) {
                Map month_data = sensor_data["$month_key"]
                if (month_data) {
                    // log.debug "attempting to delete [$day_key:*]"
                    month_data.remove(day_key)
                }
            }
        }
    }
    dewpoint_sensors.each { ds ->
        if (state.dewpoint) {
            Map sensor_data = state.dewpoint["$ds"]
            if (sensor_data) {
                Map month_data = sensor_data["$month_key"]
                if (month_data) {
                    // log.debug "attempting to delete [$day_key:*]"
                    month_data.remove(day_key)
                }
            }
        }
    }
    illuminance_sensors.each { ills ->
        if (state.illuminance) {
            Map sensor_data = state.illuminance["$ills"]
            if (sensor_data) {
                Map month_data = sensor_data["$month_key"]
                if (month_data) {
                    // log.debug "attempting to delete [$day_key:*]"
                    month_data.remove(day_key)
                }
            }
        }
    }
    wind_sensors.each { ws ->
        if (state.wind) {
            Map sensor_data = state.wind["$ws"]
            if (sensor_data) {
                Map month_data = sensor_data["$month_key"]
                if (month_data) {
                    // log.debug "attempting to delete [$day_key:*]"
                    month_data.remove(day_key)
                }
            }
        }
    }
}

Map get_loads(String source, String typ) {
    // log.debug "In get_loads $sensor $typ"
    if (state.loads) {
        typ_loads = state.loads["$source"]
        if (typ_loads) {
            return typ_loads["$typ"]
        } else { return null }
    } else { return null }
}

Map get_conditions(String sensor, String typ) {
    log.debug "In get_conditions $sensor $typ"
    if (state.sensors) {
        typ_conds = state.sensors["$sensor"]
        if (typ_conds) {
            return typ_conds["$typ"]
        } else { return null }
    } else { return null }
}

def merge(Map source_data, typ, Map data, Map month_keys, Map day_keys, List points) {
    log.debug "In merge1"
    // log.debug "source_data = $source_data"
    // log.debug "data = $data"
    // log.debug "month_keys = $month_keys"
    // log.debug "day_keys = $day_keys"
    // log.debug "points = $points"
    Map typ_data = source_data["$typ"]
    if (typ_data) {
        points.each { pts ->
            // log.debug "pts = $pts"
            m_key = month_keys["$pts"]
            d_key = day_keys["$pts"]
            if (m_key && d_key) {
                Number data_point = data["$m_key"]["$d_key"]
                // log.debug "($m_key, $d_key, $data_point)"
                if (data_point != null) {
                    Map month_data = typ_data["$m_key"]
                    if (month_data) {
                        month_data["$d_key"] = data_point
                    } else {
                        typ_data << ["$m_key" : ["$d_key" : data_point]]
                    }
                }
            }
        }
    } else {
        source_data << ["$typ" : data]
    }
}

def merge_conditions(Map data, String sensor, String typ, Map month_keys, Map day_keys, List points) {
    log.debug "In merge_conditions($sensor, $typ)"
    log.debug "data = $data"
    if (state.sensors) {
        Map sensor_data = state.sensors["$sensor"]
        if (sensor_data) {
            log.debug "merging data for sensor"
            merge(sensor_data, typ, data, month_keys, day_keys, points)
            log.debug "state.sensors = ${state.sensors}"
        } else {
            log.debug "adding data for sensor"
            state.sensors["$sensor"] = ["$typ" : data]
            log.debug "state.sensors = ${state.sensors}"
        }
    } else {
        log.debug "creating state.sensors"
        state.sensors = ["$sensor" : ["$typ" : data]]
        log.debug "state.sensors = ${state.sensors}"
    }
}

def new_condition(Map data, String sensor, String typ, Map month_keys, Map day_keys, List points) {
    log.debug "In new_condition($sensor, $typ)"
    switch (typ) {
        case "HDD":
            heating_sensors.each { hs ->
                if ("$hs" == "$sensor") {
                    merge_conditions(data, sensor, typ, month_keys, day_keys, points)
                }
            }
            break
        case "CDD":
            cooling_sensors.each { cs ->
                if ("$cs" == "$sensor") {
                    merge_conditions(data, sensor, typ, month_keys, day_keys, points)
                }
            }
            break
        case "DPD":
            dewpoint_sensors.each { ds ->
                if ("$ds" == "$sensor") {
                    merge_conditions(data, sensor, typ, month_keys, day_keys, points)
                }
            }
            break
        case "ID":
            illuminance_sensors.each { ills ->
                if ("$ills" == "$sensor") {
                    merge_conditions(data, sensor, typ, month_keys, day_keys, points)
                }
            }
            break
        case "WD":
            wind_sensors.each { ws ->
                if ("$ws" == "$sensor") {
                    merge_conditions(data, sensor, typ, month_keys, day_keys, points)
                }
            }
            break
    }
}

def merge_load(Map data, String source, String typ, Map month_keys, Map day_keys, List points) {
    // log.debug "In merge_load $source $typ"
    if (state.loads) {
        Map source_data = state.loads["$source"]
        if (source_data) {
            merge(source_data, typ, data, month_keys, day_keys, points)
        } else {
            state.loads["$source"] = ["$typ" : data]
        }
    } else {
        state.loads = ["$source" : ["$typ" : data]]
    }
}

def new_load(Map data, String source, String typ, Map month_keys, Map day_keys, List points) {
    // log.debug "In new_load $source $typ"
    switch (typ) {
        case "heat_load":
            heat_loads.each { hl ->
                if ("$hl" == "$source") {
                    merge_load(data, source, typ, month_keys, day_keys, points)
                }
            }
            break
        case "cool_load":
            cool_loads.each { cl ->
                if ("$cl" == "$source") {
                    merge_load(data, source, typ, month_keys, day_keys, points)
                }
            }
            break
    }
}

def merge(Map source_data, Map data, Map month_keys, Map day_keys, List points) {
    log.debug "In merge2"
    // log.debug "source_data = $source_data"
    // log.debug "data = $data"
    // log.debug "month_keys = $month_keys"
    // log.debug "day_keys = $day_keys"
    // log.debug "points = $points"
    points.each { pts ->
        // log.debug "pts = $pts"
        m_key = month_keys["$pts"]
        d_key = day_keys["$pts"]
        if (m_key && d_key) {
            Number data_point = data["$m_key"]["$d_key"]
            // log.debug "($m_key, $d_key, $data_point)"
            if (data_point != null) {
                Map month_data = source_data["$m_key"]
                if (month_data) {
                    month_data["$d_key"] = data_point
                } else {
                    source_data << ["$m_key" : ["$d_key" : data_point]]
                }
            }
        }
    }
}

Map get_heat_load(String source) {
    // log.debug "In get_heat_load $sensor"
    if (state.heat_load) {
        return state.heat_load["$source"]
    } else {
        return null
    }
}

def new_heat_load(Map data, String source, Map month_keys, Map day_keys, List points) {
    // log.debug "In new_heat_load $source $month_keys $day_keys $points"
    heat_loads.each { hl ->
        if ("$hl" == "$source") {
            if (state.heat_load) {
                Map source_data = state.heat_load["$source"]
                if (source_data) {
                    merge(source_data, data, month_keys, day_keys, points)
                } else {
                    state.heat_load["$source"] = data
                }
            } else {
                state.heat_load = ["$source" : data]
            }
        }
    }
}

Map get_cool_load(String source) {
    // log.debug "In get_cool_load $sensor"
    if (state.cool_load) {
        return state.cool_load["$source"]
    } else {
        return null
    }
}

def new_cool_load(Map data, String source, Map month_keys, Map day_keys, List points) {
    // log.debug "In new_cool_load $source $month_keys $day_keys, $points"
    cool_loads.each { cl ->
        if ("$cl" == "$source") {
            if (state.cool_load) {
                Map source_data = state.cool_load["$source"]
                if (source_data) {
                    merge(source_data, data, month_keys, day_keys, points)
                } else {
                    state.cool_load["$source"] = data
                }
            } else {
                state.cool_load = ["$source" : data]
            }
        }
    }
}

Map get_heating_days(String sensor) {
    // log.debug "In get_heating_days $sensor"
    if (state.heating) {
        return state.heating["$sensor"]
    } else {
        return null
    }
}

def new_heating_days(Map data, String sensor, Map month_keys, Map day_keys, List points) {
    // log.debug "In new_heating_days $sensor"
    heating_sensors.each { hs ->
        if ("$hs" == "$sensor") {
            if (state.heating) {
                Map sensor_data = state.heating["$sensor"]
                if (sensor_data) {
                    merge(sensor_data, data, month_keys, day_keys, points)
                } else {
                    state.heating["$sensor"] = data
                }
            } else {
                state.heating = ["$sensor" : data]
            }
        }
    }
}

Map get_cooling_days(String sensor) {
    // log.debug "In get_cooling_days $sensor"
    if (state.cooling) {
        return state.cooling["$sensor"]
    } else {
        return null
    }
}

def new_cooling_days(Map data, String sensor, Map month_keys, Map day_keys, List points) {
    // log.debug "In new_cooling_days $sensor"
    cooling_sensors.each { hs ->
        if ("$hs" == "$sensor") {
            if (state.cooling) {
                Map sensor_data = state.cooling["$sensor"]
                if (sensor_data) {
                    merge(sensor_data, data, month_keys, day_keys, points)
                } else {
                    state.cooling["$sensor"] = data
                }
            } else {
                state.cooling = ["$sensor" : data]
            }
        }
    }
}

Map get_dewpoint_days(String sensor) {
    // log.debug "In get_dewpoint_days $sensor"
    if (state.dewpoint) {
        return state.dewpoint["$sensor"]
    } else {
        return null
    }
}

def new_dewpoint_days(Map data, String sensor, Map month_keys, Map day_keys, List points) {
    // log.debug "In new_dewpoint_days $sensor"
    dewpoint_sensors.each { hs ->
        if ("$hs" == "$sensor") {
            if (state.dewpoint) {
                Map sensor_data = state.dewpoint["$sensor"]
                if (sensor_data) {
                    merge(sensor_data, data, month_keys, day_keys, points)
                } else {
                    state.dewpoint["$sensor"] = data
                }
            } else {
                state.dewpoint = ["$sensor" : data]
            }
        }
    }
}

Map get_illuminance_days(String sensor) {
    // log.debug "In get_illuminance_days $sensor"
    if (state.illuminance) {
        return state.illuminance["$sensor"]
    } else {
        return null
    }
}

def new_illuminance_days(Map data, String sensor, Map month_keys, Map day_keys, List points) {
    // log.debug "In new_illuminance_days $sensor"
    illuminance_sensors.each { hs ->
        if ("$hs" == "$sensor") {
            if (state.illuminance) {
                Map sensor_data = state.illuminance["$sensor"]
                if (sensor_data) {
                    merge(sensor_data, data, month_keys, day_keys, points)
                } else {
                    state.illuminance["$sensor"] = data
                }
            } else {
                state.illuminance = ["$sensor" : data]
            }
        }
    }
}

Map get_wind_days(String sensor) {
    // log.debug "In get_wind_days($sensor)"
    if (state.wind) {
        return state.wind["$sensor"]
    } else {
        return null
    }
}

def new_wind_days(Map data, String sensor, Map month_keys, Map day_keys, List points) {
    // log.debug "In new_wind_days $sensor"
    wind_sensors.each { hs ->
        if ("$hs" == "$sensor") {
            if (state.wind) {
                Map sensor_data = state.wind["$sensor"]
                if (sensor_data) {
                    merge(sensor_data, data, month_keys, day_keys, points)
                } else {
                    state.wind["$sensor"] = data
                }
            } else {
                state.wind = ["$sensor" : data]
            }
        }
    }
}

def new_day_data(Map data, Map month_keys, Map day_keys, List points) {
    // log.debug "In new_day_data"
    if (state.day_of_week) {
        merge(state.day_of_week, data, month_keys, day_keys, points)
    } else {
        state.day_of_week = data
    }
}

Map get_day_data() {
    return state.day_of_week
}
