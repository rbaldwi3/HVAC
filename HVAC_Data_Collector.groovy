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
        section ("Loads to be saved") {
            zones = parent.get_zones()
            input "heat_loads", "enum", required: false, multiple: true, title: "Daily Heating Loads", options: zones
            input "cool_loads", "enum", required: false, multiple: true, title: "Daily Cooling Loads", options: zones
            input "cycle_heat_loads", "enum", required: false, multiple: true, title: "Thermostat Cycle Heating Loads", options: zones
            input "cycle_cool_loads", "enum", required: false, multiple: true, title: "Thermostat Cycle Cooling Loads", options: zones
        }
        section ("Outdoor Condition Data to be saved") {
            sensors = parent.get_sensors()
            input "heating_sensors", "enum", required: false, multiple: true, title: "Heating Degree Day Sensors", options: sensors.temperature
            input "cooling_sensors", "enum", required: false, multiple: true, title: "Cooling Degree Day Sensors", options: sensors.temperature
            input "dewpoint_sensors", "enum", required: false, multiple: true, title: "Dewpoint Sensors", options: sensors.dewpoint
            input "illuminance_sensors", "enum", required: false, multiple: true, title: "Illuminance Sensors", options: sensors.illuminance
            input "adj_illuminance_sensors", "enum", required: false, multiple: true, title: "Adjusted Illuminance Sensors", options: sensors.adj_illuminance
            input "wind_sensors", "enum", required: false, multiple: true, title: "Wind Sensors", options: sensors.wind
        }
        section ("Retention Criteria") {
            input "days_to_save", "enum", required: true, multiple: true, title: "Days of Week to save",
                options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
            input "max_data_points", "number", required: false, title: "Maximum number of data points to save"
            input "exclude_zero_loads", "bool", required: true, title: "Only save data points with non-zero loads"
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
                // log.debug "days = $days"
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
                        List day_options = ["daily"]
                        if (state.cycle_data) {
                            Map month_cycle_data = state.cycle_data["$state.month_key"]
                            if (month_cycle_data) {
                                List day_cycle_data = month_cycle_data["$state.day_key"]
                                if (day_cycle_data) {
                                    day_options += day_cycle_data
                                }
                            }
                        }
                        input "day_option", "enum", required: false, multiple: false, title: "Selection", options: day_options, submitOnChange: true
                        if (days) {
                            String day_data = days["$data_point"]
                            if (day_data != null) { paragraph "$day_data" }
                        }
                        String day_key2 = "$state.day_key"
                        if (day_option) {
                            if (day_option != "daily") {
                                day_key2 += ".$day_option"
                            }
                        }
                        heat_loads.each { hl ->
                            Number value = get_load("$hl", "heat_load", "$state.month_key", "$day_key2")
                            if (value != null) { paragraph "Heat Load: $hl : $value" }
                        }
                        cool_loads.each { cl ->
                            Number value = get_load("$cl", "cool_load", "$state.month_key", "$day_key2")
                            if (value != null) { paragraph "Cooling Load: $cl : $value" }
                        }
                        heating_sensors.each { hs ->
                            Number value = get_condition("$hs", "HDD", "$state.month_key", "$day_key2")
                            if (value != null) { paragraph "Heating Degree Days: $hs : $value" }
                        }
                        cooling_sensors.each { cs ->
                            Number value = get_condition("$cs", "CDD", "$state.month_key", "$day_key2")
                            if (value != null) { paragraph "Cooling Degree Days: $cs : $value" }
                        }
                        dewpoint_sensors.each { ds ->
                            Number value = get_condition("$ds", "DPD", "$state.month_key", "$day_key2")
                            if (value != null) { paragraph "Dewpoint Degree Days: $ds : $value" }
                        }
                        illuminance_sensors.each { ills ->
                            Number value = get_condition("$ills", "ID", "$state.month_key", "$day_key2")
                            if (value != null) { paragraph "Illuminance Days: $ills : $value" }
                        }
                        adj_illuminance_sensors.each { ais ->
                             Number value = get_condition("$ais", "AID", "$state.month_key", "$day_key2")
                            if (value != null) { paragraph "Adjusted Illuminance Days: $ais : $value" }
                        }
                        wind_sensors.each { ws ->
                            Number value = get_condition("$ws", "WD", "$state.month_key", "$day_key2")
                            if (value != null) { paragraph "Wind Days: $ws : $value" }
                        }
		             	input "delete_button", "button", title: "Delete", width: 6
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
        adj_illuminance_sensors.each { ais ->
            signals << "Adjusted Illuminance - $ais"
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
        section ("Regression") {
            input "dependent", "enum", required: false, multiple: false, title: "Load to Estimate", options: loads
            input "subtract_loads", "enum", required: false, multiple: true, title: "Loads to Subtract from the Load to Estimate", options: loads
            input "independent", "enum", required: false, multiple: true, title: "Sensor Signals", options: signals
            input "interval_types", "enum", required: true, multiple: false, title: "Types of Data Points",
                options: ["daily data only", "thermostat cycles only", "both"]
        }
    }
}

def appButtonHandler(btn) {
	switch (btn) {
		case "delete_button":
            log.debug("deleting data for $state.month_key - $state.day_key")
            // delete_zone_data("$state.month_key", "$state.day_key")
            // delete_sensor_data("$state.month_key", "$state.day_key")
            delete_load_data(heat_loads, "$state.month_key", "$state.day_key")
            delete_load_data(cool_loads, "$state.month_key", "$state.day_key")
            delete_sensor_data(heating_sensors, "$state.month_key", "$state.day_key")
            delete_sensor_data(cooling_sensors, "$state.month_key", "$state.day_key")
            delete_sensor_data(dewpoint_sensors, "$state.month_key", "$state.day_key")
            delete_sensor_data(illuminance_sensors, "$state.month_key", "$state.day_key")
            delete_sensor_data(adj_illuminance_sensors, "$state.month_key", "$state.day_key")
            delete_sensor_data(wind_sensors, "$state.month_key", "$state.day_key")
            break
	}
}

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
    curve_fit()
    // apply_filters()
}

// each entry of state.loads is of the form [<source>:<data for source>] where <source> is the label of a zone
// each entry in <data for source> is of the form [<type>:<data for type>] where <type> is either "heat_load" or "cool_load"
// each entry is <data for type> is of the form [<month>:<data for month>] where <month> is the number of months since Jan, 1900
// each entry in <data for month> is of the form [<day>:<data>] where <day> is the day of the month and <data> is the heat load for that day

// each entry of state.sensors is of the form [<sensor>:<data for sensor>] where <sensor> is the label of an outdoor condition data device
// each entry in <data for sensor> is of the form [<type>:<data for type>] where <type> is either "HDD", "CDD", "DPD", ID", or "WD"
// each entry is <data for type> is of the form [<month>:<data for month>] where <month> is the number of months since Jan, 1900
// each entry in <data for month> is of the form [<day>:<data>] where <day> is the day of the month and <data> is the value for that day

// for cycle-based entries <day> is <day of the month>.<index_number>, and <data> is adjusted to correspond to a full day

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
    // log.debug("In set_value($value, $source_data, $typ, $month_key, $day_key)")
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
    // log.debug("In set_condition($value, $sensor, $typ, $month_key, $day_key)")
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
    // log.debug("In get_condition($source, $typ, $month_key, $day_key)")
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

def curve_fit() {
    log.debug("In curve_fit()")
    state.coefs = null
    if (!dependent) { return }
    if (!independent) { return }
    Map data = available_data()
    if (data) { // [list : list_of_days, days : days_of_week, month_keys : month_keys, day_keys : day_keys]
        pts = []
        data.list.each { li ->
            month_key = data.month_keys["$li"]
            day_key = data.day_keys["$li"]
            List day_key2 = []
            switch (interval_types) {
                case "daily data only":
                    day_key2 << "$day_key"
                    break
                case "both":
                    day_key2 << "$day_key"
                case "thermostat cycles only":
                    if (state.cycle_data) {
                        Map month_cycle_data = state.cycle_data["$month_key"]
                        if (month_cycle_data) {
                            List day_cycle_data = month_cycle_data["$day_key"]
                            if (day_cycle_data) {
                                day_cycle_data.each { dcd ->
                                    day_key2 << "$day_key.$dcd"
                                }
                            }
                         }
                    }
            }
            // log.debug("$day_key2")
            day_key2.each { dk2 ->
                pt = []
                independent.each { iv ->
                    if ("$iv" == "Constant") {
                        pt << 1.0
                    } else {
                        heating_sensors.each { hs ->
                            if ("$iv" == "Heating degree days - $hs") {
                                Number value = get_condition("$hs", "HDD", "$month_key", "$dk2")
                                if (value != null) { pt << value }
                            }
                        }
                        cooling_sensors.each { cs ->
                            if ("$iv" == "Cooling degree days - $cs") {
                                Number value = get_condition("$cs", "CDD", "$month_key", "$dk2")
                                if (value != null) { pt << value }
                            }
                        }
                        dewpoint_sensors.each { ds ->
                            if ("$iv" == "Dewpoint - $ds") {
                                Number value = get_condition("$ds", "DPD", "$month_key", "$dk2")
                                if (value != null) { pt << value }
                            }
                        }
                        illuminance_sensors.each { ills ->
                            if ("$iv" == "Illuminance - $ills") {
                                Number value = get_condition("$ills", "ID", "$month_key", "$dk2")
                                if (value != null) { pt << value }
                            }
                        }
                        adj_illuminance_sensors.each { ais ->
                            if ("$iv" == "Adjusted Illuminance - $ais") {
                                Number value = get_condition("$ais", "AID", "$month_key", "$dk2")
                                if (value != null) { pt << value }
                            }
                        }
                        wind_sensors.each { ws ->
                            if ("$iv" == "Wind Speed - $ws") {
                                Number value = get_condition("$ws", "WD", "$month_key", "$dk2")
                                if (value != null) { pt << value }
                            }
                        }
                    }
                }
                Number load = 0
                heat_loads.each { hl ->
                    if ("$dependent" == "Heating load - $hl") {
                        Number value = get_load("$hl", "heat_load", "$month_key", "$dk2")
                        if (value) { load = value }
                    }
                }
                cool_loads.each { cl ->
                    if ("$dependent" == "Cooling load - $cl") {
                        Number value = get_load("$cl", "cool_load", "$month_key", "$dk2")
                        if (value) { load = value }
                    }
                }
                if (subtract_loads) {
                    subtract_loads.each { sl ->
                        heat_loads.each { hl1 ->
                            if ("$sl" == "Heating load - $hl1") {
                                Number value = get_load("$hl1", "heat_load", "$month_key", "$dk2")
                                if (value) { load -= value }
                            }
                        }
                        cool_loads.each { cl1 ->
                            if ("$sl" == "Cooling load - $cl1") {
                                Number value = get_load("$cl1", "cool_load", "$month_key", "$dk2")
                                if (value) { load -= value }
                            }
                        }
                    }
                }
                if (load > 1) {
                    pt << load
                }
                // log.debug "($month_key - $dk2 - $pt)"
                if (pt.size() == independent.size() + 1) { pts << pt }
            }
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
        Map days_of_week = ["end" : "end"]
        Map month_keys = ["end" : 0]
        Map day_keys = ["end" : 0]
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
                        days_of_week["$month-$i-$year"] = days_spelled[day_data]
                        month_keys["$month-$i-$year"] = month_key
                        day_keys["$month-$i-$year"] = i
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

Boolean new_zone_data(String source, Date date, Number heat_load, Number cool_load, Boolean cyclic=false) {
    // log.debug("In new_zone_data($source, $date, $heat_load, $cool_load, $cyclic)")
    if ((state.cycle_index == null) || (!cyclic)) {
        state.cycle_index = 1
    } else {
        state.cycle_index++
    }
    int month = date.getMonth() + date.getYear() * 12
    int day = date.getDate()
    int day_of_week = date.getDay()
    Boolean result = false
    if (cyclic) {
        cycle_heat_loads.each { hl ->
            if ("$hl" == "$source") {
                set_load(heat_load, source, "heat_load", "$month", "$day.$state.cycle_index")
                result = true
            }
        }
        cycle_cool_loads.each { cl ->
            if ("$cl" == "$source") {
                set_load(cool_load, source, "cool_load", "$month", "$day.$state.cycle_index")
                result = true
            }
        }
    } else {
        heat_loads.each { hl ->
            if ("$hl" == "$source") {
                set_load(heat_load, source, "heat_load", "$month", "$day")
                result = true
            }
        }
        cool_loads.each { cl ->
            if ("$cl" == "$source") {
                set_load(cool_load, source, "cool_load", "$month", "$day")
                result = true
            }
        }
    }
    if (result) {
        if (cyclic) {
            if (state.cycle_data) {
                Map month_data = state.cycle_data["$month"]
                if (month_data) {
                    List day_data = month_data["$day"]
                    if (day_data) {
                        day_data << state.cycle_index
                    } else {
                        month_data << ["$day" : [state.cycle_index]]
                    }
                } else {
                    state.cycle_data << ["$month" : ["$day" : [state.cycle_index]]]
                }     
            } else {
                state.cycle_data = ["$month" : ["$day" : [state.cycle_index]]]
            }
            log.debug("month = $month, day = $day, cycle_index = $state.cycle_index")
        } else {
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
        }
    }
    return result
}

def new_sensor_data(String source, Date date, Number heating, Number cooling, Number dewpoint, Number illuminance, Number adj_illuminance, Number wind, Boolean cyclic=false) {
    log.debug("In new_sensor_data($source, $date, $heating, $cooling, $dewpoint, $illuminance, $adj_illuminance, $wind, $cyclic)")
    int month = date.getMonth() + date.getYear() * 12
    int day = date.getDate()
    String day_key
    if (cyclic) {
        day_key = "$day.$state.cycle_index"
    } else {
        day_key = "$day"
    }
    heating_sensors.each { hs ->
        if ("$hs" == "$source") {
            set_condition(heating, source, "HDD", "$month", day_key)
        }
    }
    cooling_sensors.each { cs ->
        if ("$cs" == "$source") {
            set_condition(cooling, source, "CDD", "$month", day_key)
        }
    }
    dewpoint_sensors.each { ds ->
        if ("$ds" == "$source") {
            set_condition(dewpoint, source, "DPD", "$month", day_key)
       }
    }
    illuminance_sensors.each { ills ->
        if ("$ills" == "$source") {
            set_condition(illuminance, source, "ID", "$month", day_key)
        }
    }
    adj_illuminance_sensors.each { ais ->
        if ("$ais" == "$source") {
            set_condition(adj_illuminance, source, "AID", "$month", day_key)
        }
    }
    wind_sensors.each { ws ->
        if ("$ws" == "$source") {
            set_condition(wind, source, "WD", "$month", day_key)
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
    // log.debug "In get_conditions $sensor $typ"
    if (state.sensors) {
        typ_conds = state.sensors["$sensor"]
        if (typ_conds) {
            return typ_conds["$typ"]
        } else { return null }
    } else { return null }
}

def merge(Map source_data, typ, Map data, Map month_keys, Map day_keys, List points) {
    // log.debug "In merge1"
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
    // log.debug "In merge_conditions($sensor, $typ)"
    // log.debug "data = $data"
    if (state.sensors) {
        Map sensor_data = state.sensors["$sensor"]
        if (sensor_data) {
            // log.debug "merging data for sensor"
            merge(sensor_data, typ, data, month_keys, day_keys, points)
            // log.debug "state.sensors = ${state.sensors}"
        } else {
            // log.debug "adding data for sensor"
            state.sensors["$sensor"] = ["$typ" : data]
            // log.debug "state.sensors = ${state.sensors}"
        }
    } else {
        // log.debug "creating state.sensors"
        state.sensors = ["$sensor" : ["$typ" : data]]
        // log.debug "state.sensors = ${state.sensors}"
    }
}

def new_condition(Map data, String sensor, String typ, Map month_keys, Map day_keys, List points) {
    // log.debug "In new_condition($sensor, $typ)"
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
        case "AID":
            adj_illuminance_sensors.each { ais ->
                if ("$ais" == "$sensor") {
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

Map get_estimator() {
    if (state.coefs) {
        if (state.coefs.size() != independent.size()) { return null }
        Map result = ["constant" : 0.0, "estimate" : 0.0, "values" : [], "vars" : [], "coefs" : []]
        for (i=0 ; i<independent.size() ; i++) {
            iv = independent[i]
            if ("$iv" == "Constant") {
                result["constant"] = state.coefs[i]
            } else {
                heating_sensors.each { hs ->
                    if ("$iv" == "Heating degree days - $hs") {
                        Map sensor_entry = ["label" : "$hs", "type" : "HDD"]
                        result["vars"] << sensor_entry
                        result["coefs"] << state.coefs[i]
                    }
                }
                cooling_sensors.each { cs ->
                    if ("$iv" == "Cooling degree days - $cs") {
                        Map sensor_entry = ["label" : "$cs", "type" : "CDD"]
                        result["vars"] << sensor_entry
                        result["coefs"] << state.coefs[i]
                    }
                }
                dewpoint_sensors.each { ds ->
                    if ("$iv" == "Dewpoint - $ds") {
                        Map sensor_entry = ["label" : "$ds", "type" : "DPD"]
                        result["vars"] << sensor_entry
                        result["coefs"] << state.coefs[i]
                    }
                }
                illuminance_sensors.each { ills ->
                    if ("$iv" == "Illuminance - $ills") {
                        Map sensor_entry = ["label" : "$ills", "type" : "ID"]
                        result["vars"] << sensor_entry
                        result["coefs"] << state.coefs[i]
                    }
                }
                adj_illuminance_sensors.each { ais ->
                    if ("$iv" == "Adjusted Illuminance - $ais") {
                        Map sensor_entry = ["label" : "$ais", "type" : "ID"]
                        result["vars"] << sensor_entry
                        result["coefs"] << state.coefs[i]
                    }
                }
                wind_sensors.each { ws ->
                    if ("$iv" == "Wind Speed - $ws") {
                        Map sensor_entry = ["label" : "$ws", "type" : "WD"]
                        result["vars"] << sensor_entry
                        result["coefs"] << state.coefs[i]
                    }
                }
            }
        }
        heat_loads.each { hl ->
            if ("$dependent" == "Heating load - $hl") {
                result["source"] = "$hl"
                result["type"] = "heat_load"
            }
        }
        cool_loads.each { cl ->
            if ("$dependent" == "Cooling load - $cl") {
                result["source"] = "$cl"
                result["type"] = "cool_load"
            }
        }
        return result
    } else { return null }
}

/*
        section ("Retention Criteria") {
            input "days_to_save", "enum", required: true, multiple: true, title: "Days of Week to save",
                options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
            input "max_data_points", "number", required: false, title: "Maximum number of data points to save"
            input "exclude_zero_loads", "bool", required: true, title: "Only save data points with non-zero loads"
        }
*/

def apply_filters() {
    log.debug("In apply_filters()")
    Map data = available_data()
    if (data) { // [list : list_of_days, days : days_of_week, month_keys : month_keys, day_keys : day_keys]
        data.list.each { li ->
            Boolean keep_it = !exclude_zero_loads
            month_key = data.month_keys["$li"]
            day_key = data.day_keys["$li"]
            if (!keep_it) {
                heat_loads.each { hl ->
                    if ("$dependent" == "Heating load - $hl") {
                        Number value = get_load("$hl", "heat_load", "$month_key", "$day_key")
                        if (value) { keep_it = true }
                    }
                }
            }
            if (!keep_it) {
                cool_loads.each { cl ->
                    if ("$dependent" == "Cooling load - $cl") {
                        Number value = get_load("$cl", "cool_load", "$month_key", "$day_key")
                        if (value) { keep_it = true }
                    }
                }
            }
            if (keep_it) {
                day_of_week = data.days["$li"]
                if (days_to_save.findAll { it == day_of_week }) {
                    // log.debug("$li has a non-zero load, day of week = $day_of_week - retain")
                } else {
                    // log.debug("$li has a non-zero load, day of week = $day_of_week - discard")
                }
            } else {
                // log.debug("$li should be deleted because all loads are zero")
            }
        }  
    }
    // deal with maximum number of data points criteria
}
