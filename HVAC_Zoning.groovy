/**
 *  HVAC Zoning
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
 * version 0.1 - Initial Release
 * version 0.2 - Restructured based on state machine for equipment state
 *            - Logic changed to better support Indirect thermostats
 *            - Added reporting via HVAC Zone Status device
 *            - Misc. robustness improvements
 * version 0.3 - restructured so apps communicate via HVAC Zone Status virtual devices
 *            - Added humdifier and dehumidifier controls
 *            - Misc. robustness improvements
 * version 1.0 - Added load tracking features
 *            - Added support for heat pumps
 */

definition(
    name: "HVAC Zoning",
    namespace: "rbaldwi3",
    author: "Reid Baldwin",
    description: "This app controls HVAC zone dampers and HVAC equipment in response to multiple thermostats",
    category: "General",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "pageOne", title: "Equipment Types", nextPage: "pageTwo", uninstall: true)
    page(name: "pageTwo", title: "Equipment Data", nextPage: "pageThree", uninstall: true)
    page(name: "pageThree", title: "Control Rules", nextPage: "pageFour", uninstall: true)
    page(name: "pageFour", title: "Zones", install: true, uninstall: true)
}

def pageOne() {
    dynamicPage(name: "pageOne") {
        section ("Label") {
            label required: true, multiple: false
        }
        section ("Equipment Types") {
            input(name: "equip_type", type: "enum", required: true, multiple: false, title: "Heating and Cooling Equipment", submitOnChange: true,
                  options: ["Furnace only","Air Conditioning only","Furnace and Air Conditioning",
                      "Heat Pump only","Heat Pump with Electric Backup","Heat Pump with Backup Furnace"])
            if (equip_type) {
                switch (equip_type) {
                    case "Furnace only":
                        input(name: "heat_type", type: "enum", required: true, title: "Furnace Type", options: ["Single stage","Two stage"])
                        break
                    case "Furnace and Air Conditioning":
                        input(name: "heat_type", type: "enum", required: true, title: "Furnace Type", options: ["Single stage","Two stage"])
                    case "Air Conditioning only":
                        input(name: "cool_type", type: "enum", required: true, title: "Air Conditioner Type", options: ["Single stage","Two stage"])
                        input "cool_dehum_mode", "bool", required: true, title: "Air Conditioner has dehumidify mode", default: false
                        break
                    case "Heat Pump with Backup Furnace":
                        input(name: "heat_type", type: "enum", required: true, title: "Furnace Type", options: ["Single stage","Two stage"])
                    case "Heat Pump only":
                    case "Heat Pump with Electric Backup":
                        input(name: "cool_type", type: "enum", required: true, title: "Heat Pump Type", options: ["Single stage","Two stage"])
                        input "cool_dehum_mode", "bool", required: true, title: "Heat pump has dehumidify mode", default: false
                        input "rv_heat", "bool", required: true, title: "Reversing valve type, True = on for heat (typically labeled B), False = on for cooling (typically labeled O)", default: false
                        break
                }
            }
            input(name: "vent_type", type: "enum", required: true, title: "Ventilation Equipment Type",
                  options: ["None", "Requires Blower", "Doesn't Require Blower"])
            if (equip_type) {
                switch (equip_type) {
                    case "Air Conditioning only":
                        input(name: "humidifer_type", type: "enum", required: true, title: "Humidifier Type",
                              options: ["None", "Separate from HVAC ductwork", "Requires Fan"])
                        break
                    case "Furnace only":
                    case "Furnace and Air Conditioning":
                    case "Heat Pump with Backup Furnace":
                    case "Heat Pump only":
                    case "Heat Pump with Electric Backup":
                        input(name: "humidifer_type", type: "enum", required: true, title: "Humidifier Type",
                              options: ["None", "Separate from HVAC ductwork", "Requires Heat", "Requires Fan"])
                        break
                }
            }
            input(name: "dehumidifer_type", type: "enum", required: true, title: "Dehumidifier Type",
                  options: ["None", "Separate from HVAC ductwork", "Outputs to Supply Plenum", "Outputs to Return Plenum"])
        }
    }
}

def pageTwo() {
    dynamicPage(name: "pageTwo") {
        section ("Equipment Output") {
            switch ("$equip_type") {
                case "Heat Pump only":
                case "Heat Pump with Electric Backup":
                case "Heat Pump with Backup Furnace":
                    switch ("$cool_type") {
                        case "Single stage":
                            input "capY1", "number", required: false, title: "Heat pump output (kbtu/hr)", submitOnChange: true
                            break
                        case "Two stage":
                            input "capY1", "number", required: false, title: "Heat pump stage 1 output (kbtu/hr)", submitOnChange: true
                            input "capY2", "number", required: false, title: "Heat pump stage 2 output (kbtu/hr)", submitOnChange: true
                            break
                    }
            }
            switch ("$equip_type") {
                case "Furnace only":
                case "Furnace and Air Conditioning":
                case "Heat Pump with Backup Furnace":
                    switch ("$heat_type") {
                        case "Single stage":
                            input "capW1", "number", required: false, title: "Furnace output (kbtu/hr)", submitOnChange: true
                            break
                        case "Two stage":
                            input "capW1", "number", required: false, title: "Furnace stage 1 output (kbtu/hr)", submitOnChange: true
                            input "capW2", "number", required: false, title: "Furnace stage 2 output (kbtu/hr)", submitOnChange: true
                            break
                    }
            }
            switch ("$equip_type") {
                case "Air Conditioning only":
                case "Furnace and Air Conditioning":
                    switch ("$cool_type") {
                        case "Single stage":
                            input "capY1", "number", required: false, title: "Air conditioner output (kbtu/hr)", submitOnChange: true
                            break
                        case "Two stage":
                            input "capY1", "number", required: false, title: "Air conditioner stage 1 output (kbtu/hr)", submitOnChange: true
                            input "capY2", "number", required: false, title: "Air conditioner stage 2 output (kbtu/hr)", submitOnChange: true
                            break
                    }
            }
        }
        section ("Blower Data") {
            switch ("$equip_type") {
                case "Heat Pump only":
                case "Heat Pump with Electric Backup":
                case "Heat Pump with Backup Furnace":
                    switch ("$cool_type") {
                        case "Single stage":
                            if (capY1) {
                                est = capY1 * 200 / 6
                                input "cfmY1", "number", required: true, title: "Airflow for heat pump (cfm) (suggest $est based on output)"
                            } else {
                                input "cfmY1", "number", required: true, title: "Airflow for heat pump (cfm)"
                            }
                            break
                        case "Two stage":
                            if (capY1) {
                                est = capY1 * 200 / 6
                                input "cfmY1", "number", required: true, title: "Airflow for heat pump stage 1 (cfm) (suggest $est based on output)"
                            } else {
                                input "cfmY1", "number", required: true, title: "Airflow for heat pump stage 1 (cfm)"
                            }
                            if (capY2) {
                                est = capY2 * 200 / 6
                                input "cfmY2", "number", required: true, title: "Airflow for heat pump stage 2 (cfm) (suggest $est based on output)"
                            } else {
                                input "cfmY2", "number", required: true, title: "Airflow for heat pump stage 2 (cfm)"
                            }
                            break
                    }
                    break
            }
            switch ("$equip_type") {
                case "Furnace only":
                case "Furnace and Air Conditioning":
                case "Heat Pump with Backup Furnace":
                    switch ("$heat_type") {
                        case "Single stage":
                            if (capW1) {
                                est = capW1 * 20
                                input "cfmW1", "number", required: true, title: "Airflow for furnace (cfm) (suggest $est based on output)"
                            } else {
                                input "cfmW1", "number", required: true, title: "Airflow for furnace (cfm)"
                            }
                            break
                        case "Two stage":
                            if (capW1) {
                                est = capW1 * 20
                                input "cfmW1", "number", required: true, title: "Airflow for furnace stage 1 (cfm) (suggest $est based on output)"
                            } else {
                                input "cfmW1", "number", required: true, title: "Airflow for furnace stage 1 (cfm)"
                            }
                            if (capW2) {
                                est = capW2 * 20
                                input "cfmW2", "number", required: true, title: "Airflow for furnace stage 2 (cfm) (suggest $est based on output)"
                            } else {
                                input "cfmW2", "number", required: true, title: "Airflow for furnace stage 2 (cfm)"
                            }
                            break
                    }
            }
            switch ("$equip_type") {
                case "Air Conditioning only":
                case "Furnace and Air Conditioning":
                    switch ("$cool_type") {
                        case "Single stage":
                            if (capY1) {
                                est = capY1 * 200 / 6
                                input "cfmY1", "number", required: true, title: "Airflow for air conditioner (cfm) (suggest $est based on output)"
                            } else {
                                input "cfmY1", "number", required: true, title: "Airflow for air conditioner (cfm)"
                            }
                            break
                        case "Two stage":
                            if (capY1) {
                                est = capY1 * 200 / 6
                                input "cfmY1", "number", required: true, title: "Airflow for air conditioner stage 1 (cfm) (suggest $est based on output)"
                            } else {
                                input "cfmY1", "number", required: true, title: "Airflow for air conditioner stage 1 (cfm)"
                            }
                            if (capY2) {
                                est = capY2 * 200 / 6
                                input "cfmY2", "number", required: true, title: "Airflow for air conditioner stage 2 (cfm) (suggest $est based on output)"
                            } else {
                                input "cfmY2", "number", required: true, title: "Airflow for air conditioner stage 2 (cfm)"
                            }
                            break
                    }
                    break
            }
            input "cfmG", "number", required: true, title: "Airflow for fan only (cfm)"
            switch ("$dehumidifer_type") {
                case "Outputs to Supply Plenum":
                    input "cfmDeHum", "number", required: true, title: "Airflow for dehumidifier (cfm)"
            }
        }
        humidity_required = false
        section ("Equipment Control Switches") {
            switch ("$equip_type") {
                case "Air Conditioning only":
                case "Furnace and Air Conditioning":
                case "Heat Pump only":
                case "Heat Pump with Electric Backup":
                case "Heat Pump with Backup Furnace":
                    switch ("$cool_type") {
                        case "Single stage":
                            input "Y1", "capability.switch", required: true, title: "Command Compressor (probably labeled Y/Y2 on air handler)"
                            break
                        case "Two stage":
                            input "Y1", "capability.switch", required: true, title: "Command Compressor stage 1 (probably labeled Y1 on air handler)"
                            input "Y2", "capability.switch", required: true, title: "Command Compressor stage 2 (probably laeled Y/Y2 on air handler)"
                            break
                    }
                    if (cool_dehum_mode) {
                        input "cool_dehum", "capability.switch", required: true, title: "Command Dehumidify mode"
                    }
            }
            switch ("$equip_type") {
                case "Heat Pump only":
                case "Heat Pump with Electric Backup":
                case "Heat Pump with Backup Furnace":
                    if (rv_heat) {
                        input "OB", "capability.switch", required: true, title: "Reversing Valve (probably labeled B or O/B on air handler)"
                    } else {
                        input "OB", "capability.switch", required: true, title: "Reversing Valve (probably labeled O or O/B on air handler)"
                    }
            }
            switch ("$equip_type") {
                case "Heat Pump with Electric Backup":
                    input "W1", "capability.switch", required: true, title: "Command Electric Backup Heat (probably labeled W/W1 on air handler)"
                    break
                case "Heat Pump with Backup Furnace":
                case "Furnace only":
                case "Furnace and Air Conditioning":
                    switch ("$heat_type") {
                        case "Single stage":
                            input "W1", "capability.switch", required: true, title: "Command Furnace (probably labeled W/W1 on air handler)"
                            break
                        case "Two stage":
                            input "W1", "capability.switch", required: true, title: "Command Furnace stage 1 (probably labeled W/W1 on air handler)"
                            input "W2", "capability.switch", required: true, title: "Command Furnace stage 2 (probably labeled W2 on air handler)"
                            break
                    }
            }
            input "G", "capability.switch", required:true, title: "Command Fan (probably labeled G on furnace)"
            switch ("$vent_type") {
                case "Requires Blower":
                case "Doesn't Require Blower":
                    input "V", "capability.switch", required:true, title: "Command Ventilation Equipment"
            }
            switch ("$humidifer_type") {
                case "Separate from HVAC ductwork":
                case "Requires Heat":
                case "Requires Fan":
                    input "Hum", "capability.switch", required:true, title: "Command Humidifier"
                    humidity_required = true
            }
            switch ("$dehumidifer_type") {
                case "Separate from HVAC ductwork":
                case "Outputs to Supply Plenum":
                case "Outputs to Return Plenum":
                    input "Dehum", "capability.switch", required:true, title: "Command Dehumidifier"
                    humidity_required = true
            }
        }
        section ("Sensors") {
            input "outdoor_temp", "capability.temperatureMeasurement", required: false, title: "Outdoor temperature sensor (optional)"
            if (humidity_required) {
                input "indoor_humidity", "capability.relativeHumidityMeasurement", required: true, title: "Indoor humidity sensor"
            } else {
                input "indoor_humidity", "capability.relativeHumidityMeasurement", required: false, title: "Indoor humidity sensor (optional)"
            }
            input "over_pressure", "capability.switch", required:false, title: "Excessive Pressure Indicator (optional)"
            input "wired_tstat", "capability.thermostat", required: false, title: "Thermostat wired to Equipment (optional)"
        }
    }
}

def pageThree() {
    dynamicPage(name: "pageThree") {
        section ("Control Parameters") {
            switch ("$equip_type") {
                case "Furnace and Air Conditioning":
                case "Heat Pump only":
                case "Heat Pump with Electric Backup":
                case "Heat Pump with Backup Furnace":
                    input "mode_change_delay", "number", required: true, title: "Minimum time between heating and cooling (minutes)"
            }
            switch ("$equip_type") {
                case "Heat Pump only":
                case "Heat Pump with Electric Backup":
                case "Heat Pump with Backup Furnace":
                    switch ("$cool_type") {
                        case "Two stage":
                            if (outdoor_temp) {
                                input (name:"cool_stage2_criteria", type: "enum", required: false, title: "Criteria for using high stage of heat pump",
                                    options: ["Runtime in low stage","Setpoint/Temp difference","Total demand threshold","When a switch is on","Outdoor temp"],
                                    submitOnChange: true, multiple: true)
                            } else {
                                input (name:"cool_stage2_criteria", type: "enum", required: false, title: "Criteria for using high stage of heat pump",
                                    options: ["Runtime in low stage","Setpoint/Temp difference","Total demand threshold","When a switch is on"],
                                    submitOnChange: true, multiple: true)
                            }
                            if (cool_stage2_criteria) {
                                if (cool_stage2_criteria.findAll { it == "Runtime in low stage" }) {
                                    input "cool_stage2_delay", "number", required: true, title: "Time in stage 1 to trigger stage 2 (minutes)"
                                }
                                if (cool_stage2_criteria.findAll { it == "Setpoint/Temp difference" }) {
                                    input "cool_stage2_delta", "number", required: true, title: "Setpoint/Temp difference to trigger stage 2"
                                }
                                if (cool_stage2_criteria.findAll { it == "Total demand threshold" }) {
                                    input "cool_stage2_threshold", "number", required: true, title: "Demand to trigger stage 2 (cfm)"
                                }
                                if (cool_stage2_criteria.findAll { it == "When a switch is on" }) {
                                    input "cool_stage2_switch", "capability.switch", required: true, title: "Switch for using high stage"
                                }
                                if (cool_stage2_criteria.findAll { it == "Outdoor temp" }) {
                                    input "cool_stage2_outdoor_threshold", "number", required: true, title: "Temperature above which high stage is used for cooling"
                                }
                                if (cool_stage2_criteria.size() > 1) {
                                    input "cool_stage2_and", "bool", required: true, title: "Require ALL conditions?", default: false
                                }
                            }
                        case "Single stage":
                            input "cool_min_runtime", "number", required: true, title: "Minimum Cooling Runtime (minutes)"
                            input "cool_min_idletime", "number", required: true, title: "Minimum Cooling Idle Time (minutes)"
                            input "heat_min_runtime", "number", required: true, title: "Minimum Heating Runtime (minutes)"
                            input "heat_min_idletime", "number", required: true, title: "Minimum Heating Idle Time (minutes)"
                            break
                    }
            }
            switch ("$equip_type") {
                case "Heat Pump with Electric Backup":
                case "Heat Pump with Backup Furnace":
                    if (outdoor_temp) {
                        input (name:"backup_heat_criteria", type: "enum", required: false, title: "Criteria for using backup heat",
                            options: ["Runtime in heat pump","Setpoint/Temp difference","When a switch is on","Outdoor temp"],
                            submitOnChange: true, multiple: true)
                    } else {
                        input (name:"backup_heat_criteria", type: "enum", required: false, title: "Criteria for using backup heat",
                            options: ["Runtime in heat pump","Setpoint/Temp difference","When a switch is on"],
                            submitOnChange: true, multiple: true)
                    }
                    if (backup_heat_criteria) {
                        if (backup_heat_criteria.findAll { it == "Runtime in heat pump" }) {
                            input "backup_heat_delay", "number", required: true, title: "Heat pump runtime to trigger backup heat (minutes)"
                        }
                        if (backup_heat_criteria.findAll { it == "Setpoint/Temp difference" }) {
                            input "backup_heat_delta", "number", required: true, title: "Setpoint/Temp difference to trigger backup heat"
                        }
                        if (backup_heat_criteria.findAll { it == "When a switch is on" }) {
                            input "backup_heat_switch", "capability.switch", required: true, title: "Switch for using backup heat"
                        }
                        if (backup_heat_criteria.findAll { it == "Outdoor temp" }) {
                            input "backup_heat_outdoor_threshold", "number", required: true, title: "Temperature below which backup heat is used"
                        }
                        if (backup_heat_criteria.size() > 1) {
                            input "backup_heat_and", "bool", required: true, title: "Require ALL conditions?", default: false
                        }
                    }
                    input "emergency_heat_stats", "capability.thermostat", required: false, multiple: true, title: "Thermostats permitted to call for emergency heat"
            }
            switch ("$equip_type") {
                case "Furnace only":
                case "Furnace and Air Conditioning":
                case "Heat Pump with Backup Furnace":
                    switch ("$heat_type") {
                        case "Two stage":
                            if (outdoor_temp) {
                                input (name:"heat_stage2_criteria", type: "enum", required: false, title: "Criteria for using high stage of furnace",
                                    options: ["Runtime in low stage","Setpoint/Temp difference","Total heat demand threshold","When a switch is on","Outdoor temp"],
                                    submitOnChange: true, multiple: true)
                            } else {
                                input (name:"heat_stage2_criteria", type: "enum", required: false, title: "Criteria for using high stage of furnace",
                                    options: ["Runtime in low stage","Setpoint/Temp difference","Total heat demand threshold","When a switch is on"],
                                    submitOnChange: true, multiple: true)
                            }
                            if (heat_stage2_criteria) {
                                if (heat_stage2_criteria.findAll { it == "Runtime in low stage" }) {
                                    input "heat_stage2_delay", "number", required: true, title: "Time in stage 1 heating to trigger stage 2 (minutes)"
                                }
                                if (heat_stage2_criteria.findAll { it == "Setpoint/Temp difference" }) {
                                    input "heat_stage2_delta", "number", required: true, title: "Setpoint/Temp difference to trigger stage 2 heating"
                                }
                                if (heat_stage2_criteria.findAll { it == "Total heat demand threshold" }) {
                                    input "heat_stage2_threshold", "number", required: true, title: "Heat demand to trigger stage 2 heating (cfm)"
                                }
                                if (heat_stage2_criteria.findAll { it == "When a switch is on" }) {
                                    input "heat_stage2_switch", "capability.switch", required: true, title: "Switch for using high stage heat"
                                }
                                if (heat_stage2_criteria.findAll { it == "Outdoor temp" }) {
                                    input "heat_stage2_outdoor_threshold", "number", required: true, title: "Temperature below which high stage heat is used"
                                }
                                if (heat_stage2_criteria.size() > 1) {
                                    input "heat_stage2_and", "bool", required: true, title: "Require ALL conditions?", default: false
                                }
                            }
                            if ("$equip_type" == "Heat Pump with Backup Furnace") { break }
                        case "Single stage":
                            input "heat_min_runtime", "number", required: true, title: "Minimum Heating Runtime (minutes)"
                            input "heat_min_idletime", "number", required: true, title: "Minimum Heating Idle Time (minutes)"
                            break
                    }
            }
            switch ("$equip_type") {
                case "Air Conditioning only":
                case "Furnace and Air Conditioning":
                    switch ("$cool_type") {
                        case "Two stage":
                            if (outdoor_temp) {
                                input (name:"cool_stage2_criteria", type: "enum", required: false, title: "Criteria for using high stage of air conditioner",
                                    options: ["Runtime in low stage","Setpoint/Temp difference","Total cool demand threshold","When a switch is on","Outdoor temp"],
                                    submitOnChange: true, multiple: true)
                            } else {
                                input (name:"cool_stage2_criteria", type: "enum", required: false, title: "Criteria for using high stage of air conditioner",
                                    options: ["Runtime in low stage","Setpoint/Temp difference","Total cool demand threshold","When a switch is on"],
                                    submitOnChange: true, multiple: true)
                            }
                            if (cool_stage2_criteria) {
                                if (cool_stage2_criteria.findAll { it == "Runtime in low stage" }) {
                                    input "cool_stage2_delay", "number", required: true, title: "Time in stage 1 cooling to trigger stage 2 (minutes)"
                                }
                                if (cool_stage2_criteria.findAll { it == "Setpoint/Temp difference" }) {
                                    input "cool_stage2_delta", "number", required: true, title: "Setpoint/Temp difference to trigger stage 2 cooling"
                                }
                                if (cool_stage2_criteria.findAll { it == "Total cool demand threshold" }) {
                                    input "cool_stage2_threshold", "number", required: true, title: "Cooling demand to trigger stage 2 cooling (cfm)"
                                }
                                if (cool_stage2_criteria.findAll { it == "When a switch is on" }) {
                                    input "cool_stage2_switch", "capability.switch", required: true, title: "Switch for using high stage cooling"
                                }
                                if (cool_stage2_criteria.findAll { it == "Outdoor temp" }) {
                                    input "cool_stage2_outdoor_threshold", "number", required: true, title: "Temperature above which high stage cooling is used"
                                }
                                if (cool_stage2_criteria.size() > 1) {
                                    input "cool_stage2_and", "bool", required: true, title: "Require ALL conditions?", default: false
                                }
                            }
                        case "Single stage":
                            input "cool_min_runtime", "number", required: true, title: "Minimum Cooling Runtime (minutes)"
                            input "cool_min_idletime", "number", required: true, title: "Minimum Cooling Idle Time (minutes)"
                            break
                    }
            }
            switch ("$vent_type") {
                case "Requires Blower":
                case "Doesn't Require Blower":
                    input "vent_control", "capability.switchLevel", required:true, title: "Ventilation Control - Use dimmer to set percent runtime and on/off"
                    input "vent_force", "capability.switch", required:false, title: "Spot Ventilation Control - turn on to temporarily force ventilation on (e.g. bathroom vent)"
            }
            switch ("$humidifer_type") {
                case "Separate from HVAC ductwork":
                case "Requires Heat":
                case "Requires Fan":
                    input "humidifier_target", "capability.switchLevel", required:true, title: "Minimum Relative Humidity Target (generally Heating Season)"
            }
            switch ("$dehumidifer_type") {
                case "Separate from HVAC ductwork":
                case "Outputs to Supply Plenum":
                case "Outputs to Return Plenum":
                    input "dehumidifier_target", "capability.switchLevel", required:true, title: "Maximum Relative Humidity Target (generally Cooling Season)"
            }
        }
        section ("Refresh intervals to recover from missed signals") {
            // time interval for polling in case any signals missed
            input(name:"output_refresh_interval", type:"enum", required: true, title: "Output refresh", options: ["None","Every 5 minutes","Every 10 minutes",
                                                                                                                 "Every 15 minutes","Every 30 minutes"]) 
            input(name:"input_refresh_interval", type:"enum", required: true, title: "Input refresh", options: ["None","Every 5 minutes","Every 10 minutes",
                                                                                                                 "Every 15 minutes","Every 30 minutes"]) 
        }
    }
}

def pageFour() {
    dynamicPage(name: "pageFour") {
        section ("Zones") {
            app(name: "zones", appName: "HVAC Zone", namespace: "rbaldwi3", title: "Create New Zone", multiple: true, submitOnChange: true)
        }
    }
}

def installed() {
    // log.debug("In installed()")
    addChildDevice("rbaldwi3", "HVAC Zone Status", "HVACZoning_${app.id}", [isComponent: true, label: app.label])
    atomicState.equip_state = "Idle"
    atomicState.vent_state = "Off"
    atomicState.fan_state = "Off"
    // These variables indicate the timing of the last cooling and heating equipment runs.
    // When equipment is running start time > stop time. Otherwise, stop time > start time.
    atomicState.last_cooling_start = now() - 1000*60*30
    atomicState.last_cooling_stop = now() - 1000*60*30
    atomicState.last_heating_start = now() - 1000*60*30
    atomicState.last_heating_stop = now() - 1000*60*30
    // These two variables indicate the beginning and ending of the current ventilation interval.
    // When ventilation is on, start time is in the past and end time is in the future.
    atomicState.vent_interval_start = now() - 1000*60*30
    atomicState.vent_interval_end = now() - 1000*60*20
    initialize()
}

def uninstalled() {
    // log.debug("In uninstalled()")
}

def updated() {
    // log.debug("In updated()")
    unsubscribe()
    unschedule()
    // reschedule equipment_state_timeout if necessary
    switch ("$atomicState.equip_state") {
        case "IdleH":
            Integer next_transition = (mode_change_delay+heat_min_idletime)*60 - (now()-atomicState.last_heating_stop)/1000
            runIn(next_transition, equipment_state_timeout, [misfire: "ignore"])
            break
        case "IdleC":
            Integer next_transition = (mode_change_delay+cool_min_idletime)*60 - (now()-atomicState.last_cooling_stop)/1000
            runIn(next_transition, equipment_state_timeout, [misfire: "ignore"])
            break
        case "PauseH":
            Integer next_transition = heat_min_idletime*60 - (now()-atomicState.last_heating_stop)/1000
            runIn(next_transition, equipment_state_timeout, [misfire: "ignore"])
            break
        case "PauseC":
            Integer next_transition = cool_min_idletime*60 - (now()-atomicState.last_cooling_stop)/1000
            runIn(next_transition, equipment_state_timeout, [misfire: "ignore"])
            break
        case "HeatingL":
            Integer next_transition = heat_min_runtime*60 - (now()-atomicState.last_heating_start)/1000
            runIn(next_transition, equipment_state_timeout, [misfire: "ignore"])
            break
        case "CoolingL":
            Integer next_transition = cool_min_runtime*60 - (now()-atomicState.last_cooling_start)/1000
            runIn(next_transition, equipment_state_timeout, [misfire: "ignore"])
            break
    }
    initialize()
}

def initialize() {
    log.debug("In initialize()")
    // Subscribe to state changes for inputs and set state variables to reflect their current values
    switch ("$equip_type") {
        case "Heat Pump with Electric Backup":
        case "Heat Pump with Backup Furnace":
            if (emergency_heat_stats) {
                emergency_heat_stats.each { ehs ->
                    subscribe(ehs, "thermostatMode", set_emergency_heat)
                }
            }
            set_emergency_heat()
    }
    switch ("$vent_type") {
        case "Requires Blower":
        case "Doesn't Require Blower":
            subscribe(vent_control, "switch.on", vent_control_activated)
            subscribe(vent_control, "switch.off", vent_control_deactivated)
            def currentvalue = vent_control.currentValue("switch")
            switch ("$currentvalue") {
                case "on":
                    set_vent_state("Complete")
                    runEvery1Hour(start_vent_interval)
                    break;
                case "off":
                    set_vent_state("Off")
                    break;
            }
            V.off()
            if (vent_force) {
                subscribe(vent_force, "switch.on", vent_force_activated)
                subscribe(vent_force, "switch.off", vent_force_deactivated)
                currentvalue = vent_force.currentValue("switch")
                switch ("$currentvalue") {
                    case "on":
                        set_vent_state("Forced")
                        V.on()
                        break;
                }
            }
    }
    switch ("$humidifer_type") {
        case "Separate from HVAC ductwork":
        case "Requires Heat":
        case "Requires Fan":
            subscribe(humidifier_target, "switch", update_humid_targets)
            subscribe(humidifier_target, "level", update_humid_targets)
    }
    switch ("$dehumidifer_type") {
        case "Separate from HVAC ductwork":
        case "Outputs to Supply Plenum":
        case "Outputs to Return Plenum":
            subscribe(dehumidifier_target, "switch", update_humid_targets)
            subscribe(dehumidifier_target, "level", update_humid_targets)
    }
    update_humid_targets()
    if (over_pressure) {
        subscribe(over_pressure, "switch.on", over_pressure_stage1)
        atomicState.over_pressure_time = now() - 5*60*1000
    }
    // schedule any periodic refreshes
    runEvery30Minutes(periodic_check)
    switch ("$output_refresh_interval") {
        case "None":
            break
        case "Every 5 minutes":
            runEvery5Minutes(refresh_outputs)
            break
        case "Every 10 minutes":
            runEvery10Minutes(refresh_outputs)
            break
        case "Every 15 minutes":
            runEvery15Minutes(refresh_outputs)
            break
        case "Every 30 minutes":
            runEvery30Minutes(refresh_outputs)
            break
    }
    switch ("$input_refresh_interval") {
        case "None":
            break
        case "Every 5 minutes":
            runEvery5Minutes(refresh_inputs)
            break
        case "Every 10 minutes":
            runEvery10Minutes(refresh_inputs)
            break
        case "Every 15 minutes":
            runEvery15Minutes(refresh_inputs)
            break
        case "Every 30 minutes":
            runEvery30Minutes(refresh_inputs)
            break
    }
    atomicState.off_capacity = 0
    atomicState.heat_demand = 0
    atomicState.heat_accept = 0
    atomicState.cool_demand = 0
    atomicState.cool_accept = 0
    atomicState.fan_demand = 0
    atomicState.fan_accept = 0
    atomicState.dehum_accept = 0
    atomicState.humid_accept = 0
    tmplist = []
    def zones = getChildApps()
    zones.each { z ->
        if (subzone_ok("", z.label)) {
            tmplist << z.label
            status_device = z.get_status_device()
            def off_capacity_value = status_device.currentValue("off_capacity")
            atomicState.off_capacity += off_capacity_value
            def heat_demand_value = status_device.currentValue("heat_demand")
            atomicState.heat_demand += heat_demand_value
            subscribe(status_device, "heat_demand", child_heat_demand_Handler)
            def heat_accept_value = status_device.currentValue("heat_accept")
            atomicState.heat_accept += heat_accept_value
            subscribe(status_device, "heat_accept", child_heat_accept_Handler)
            def cool_demand_value = status_device.currentValue("cool_demand")
            atomicState.cool_demand += cool_demand_value
            subscribe(status_device, "cool_demand", child_cool_demand_Handler)
            def cool_accept_value = status_device.currentValue("cool_accept")
            atomicState.cool_accept += cool_accept_value
            subscribe(status_device, "cool_accept", child_cool_accept_Handler)
            def fan_demand_value = status_device.currentValue("fan_demand")
            atomicState.fan_demand += fan_demand_value
            subscribe(status_device, "fan_demand", child_fan_demand_Handler)
            def fan_accept_value = status_device.currentValue("fan_accept")
            atomicState.fan_accept += fan_accept_value
            subscribe(status_device, "fan_accept", child_fan_accept_Handler)
            def dehum_accept_value = status_device.currentValue("dehum_accept")
            atomicState.dehum_accept += dehum_accept_value
            subscribe(status_device, "dehum_accept", child_dehum_accept_Handler)
            def humid_accept_value = status_device.currentValue("humid_accept")
            atomicState.humid_accept += humid_accept_value
            subscribe(status_device, "humid_accept", child_humid_accept_Handler)
        }
    }
    atomicState.main_zones = tmplist
    status_device = getChildDevice("HVACZoning_${app.id}")
    if (status_device) {
        status_device.set_off_capacity(atomicState.off_capacity)
        status_device.set_heat_demand(atomicState.heat_demand)
        status_device.set_heat_accept(atomicState.heat_accept)
        status_device.set_cool_demand(atomicState.cool_demand)
        status_device.set_cool_accept(atomicState.cool_accept)
        status_device.set_fan_demand(atomicState.fan_demand)
        status_device.set_fan_accept(atomicState.fan_accept)
        status_device.set_dehum_accept(atomicState.dehum_accept)
        status_device.set_humid_accept(atomicState.humid_accept)
    }
    if (wired_tstat) {
        subscribe(wired_tstat, "thermostatOperatingState", wired_tstatHandler)
        wired_tstatHandler() // sets wired mode and calls zone_call_changed()
    } else {
        atomicState.wired_mode = "none"
        // call update_equipment_state to put outputs in a state consistent with the current inputs
        update_equipment_state()
    }
    if (backup_heat_criteria) {
        if (backup_heat_criteria.findAll { it == "When a switch is on" }) {
            subscribe(backup_heat_switch, "switch", backup_heat)
        }
        if (backup_heat_criteria.findAll { it == "Outdoor temp" }) {
            subscribe(outdoor_temp, "temperature", backup_heat)
        }
    }
    if (heat_stage2_criteria) {
        if (heat_stage2_criteria.findAll { it == "When a switch is on" }) {
            subscribe(heat_stage2_switch, "switch", stage2_heat)
        }
        if (heat_stage2_criteria.findAll { it == "Outdoor temp" }) {
            subscribe(outdoor_temp, "temperature", stage2_heat)
        }
    }
    if (cool_stage2_criteria) {
        if (cool_stage2_criteria.findAll { it == "When a switch is on" }) {
            subscribe(cool_stage2_switch, "switch", stage2_cool)
        }
        if (cool_stage2_criteria.findAll { it == "Outdoor temp" }) {
            subscribe(outdoor_temp, "temperature", stage2_cool)
        }
    }
}

def refresh_outputs() {
    log.debug("In refresh_outputs() state is $atomicState.equip_state")
    switch ("$atomicState.equip_state") {
        case "Heating": 
        case "HeatingL": 
            switch ("$equip_type") {
                case "Furnace and Air Conditioning":
                    switch ("$cool_type") {
                        case "Two stage":
                            stage2_cool_off()
                        case "Single stage":
                            Y1.off()
                    }
                case "Furnace only":
                    switch ("$heat_type") {
                        case "Two stage":
                            stage2_heat()
                        case "Single stage":
                            W1.on()
                    }
            }
            break
        case "Cooling": 
        case "CoolingL": 
            switch ("$equip_type") {
                case "Furnace and Air Conditioning":
                    switch ("$heat_type") {
                        case "Two stage":
                            stage2_heat_off()
                        case "Single stage":
                            W1.off()
                    }
                case "Air Conditioning only":
                    switch ("$cool_type") {
                        case "Two stage":
                            stage2_cool()
                        case "Single stage":
                            Y1.on()
                    }
            }
            break
        case "Idle": 
        case "IdleH": 
        case "PauseH": 
        case "IdleC": 
        case "PauseC": 
            switch ("$equip_type") {
                case "Furnace only":
                case "Furnace and Air Conditioning":
                    switch ("$heat_type") {
                        case "Two stage":
                            stage2_heat_off()
                        case "Single stage":
                            W1.off()
                    }
            }
            switch ("$equip_type") {
                case "Air Conditioning only":
                case "Furnace and Air Conditioning":
                    switch ("$heat_type") {
                        case "Two stage":
                            stage2_cool_off()
                        case "Single stage":
                            Y1.off()
                    }
            }
            break
    }
    if (V) {
        switch ("$atomicState.vent_state") {
            case "End_Phase":
            case "Running":
            case "Forced":
                V.on()
                break
            case "Complete":
            case "Off":
            case "Waiting":
                V.off()
                break
        }
    }
    switch ("$atomicState.humid_state") {
        case "Off": 
            if (Hum) { Hum.off() }
            if (Dehum) { Dehum.off() }
            break
        case "Humidify": 
            Hum.on()
            if (Dehum) { Dehum.off() }
            break
        case "Dehumidify": 
            if (Hum) { Hum.off() }
            Dehum.on()
            break
    }
    update_fan_state()
    update_zones()
}

def refresh_inputs() {
    // log.debug("In refresh_inputs()")
    switch ("$vent_type") {
        case "Requires Blower":
        case "Doesn't Require Blower":
            if (vent_control.hasCapability("Refresh")) {
                vent_control.refresh()
            }
            if (vent_force) {
                if (vent_force.hasCapability("Refresh")) {
                    vent_force.refresh()
                }
            }
            def vent_value = vent_control.currentValue("switch")
            def force_value = "off"
            if (vent_force) {
                force_value = vent_force.currentValue("switch")
            }
            // log.debug("vent_state is $atomicState.vent_state, vent_value is $vent_value, force_value is $force_value")
            switch ("$atomicState.vent_state") {
                case "Forced":
                    if ("$force_value" != "on") {
                        vent_force_deactivated()
                    }
                    break
                case "Off":
                    if ("$force_value" == "on") {
                        vent_force_activated()
                        break
                    }
                    if ("$vent_value" != "off") {
                        vent_control_activated()
                    }
                    break
                case "Complete":
                case "Waiting":
                case "End_Phase":
                case "Running":
                    if ("$force_value" == "on") {
                        vent_force_activated()
                        break
                    }
                    if ("$vent_value" != "on") {
                        vent_control_deactivated()
                    }
            }
    }
    update_humid_targets()
    new_heat_demand = 0
    new_heat_accept = 0
    new_cool_demand = 0
    new_cool_accept = 0
    new_fan_demand = 0
    new_fan_accept = 0
    new_dehum_accept = 0
    new_humid_accept = 0
    def zones = getChildApps()
    zones.each { z ->
        if (atomicState.main_zones.findAll { it == z.label }) {
            status_device = z.get_status_device()
            def zone_value = status_device.currentValue("heat_demand")
            new_heat_demand += zone_value
            zone_value = status_device.currentValue("heat_accept")
            new_heat_accept += zone_value
            zone_value = status_device.currentValue("cool_demand")
            new_cool_demand += zone_value
            zone_value = status_device.currentValue("cool_accept")
            new_cool_accept += zone_value
            zone_value = status_device.currentValue("fan_demand")
            new_fan_demand += zone_value
            zone_value = status_device.currentValue("fan_accept")
            new_fan_accept += zone_value
            zone_value = status_device.currentValue("dehum_accept")
            new_dehum_accept += zone_value
            zone_value = status_device.currentValue("humid_accept")
            new_humid_accept += zone_value
        }
    }
    change = false
    if ((new_heat_demand != atomicState.heat_demand) || (new_heat_accept != atomicState.heat_accept) ||
        (new_cool_demand != atomicState.cool_demand) || (new_cool_accept != atomicState.cool_accept) || (new_fan_demand != atomicState.fan_demand)) {
        status_device = getChildDevice("HVACZoning_${app.id}")
        atomicState.heat_demand = new_heat_demand
        status_device.set_heat_demand(atomicState.heat_demand)
        atomicState.heat_accept = new_heat_accept
        status_device.set_heat_accept(atomicState.heat_accept)
        atomicState.cool_demand = new_cool_demand
        status_device.set_cool_demand(atomicState.cool_demand)
        atomicState.cool_accept = new_cool_accept
        status_device.set_cool_accept(atomicState.cool_accept)
        atomicState.fan_demand = new_fan_demand
        status_device.set_fan_demand(atomicState.fan_demand)
        update_equipment_state()
        change = true
    }
    if (new_fan_accept != atomicState.fan_accept) {
        atomicState.fan_accept = new_fan_accept
        status_device = getChildDevice("HVACZoning_${app.id}")
        status_device.set_fan_accept(atomicState.fan_accept)
        change = true
    }
    if (new_dehum_accept != atomicState.dehum_accept) {
        atomicState.dehum_accept = new_dehum_accept
        status_device = getChildDevice("HVACZoning_${app.id}")
        status_device.set_dehum_accept(atomicState.dehum_accept)
        change = true
    }
    if (new_humid_accept != atomicState.humid_accept) {
        atomicState.humid_accept = new_humid_accept
        status_device = getChildDevice("HVACZoning_${app.id}")
        status_device.set_humid_accept(atomicState.humid_accept)
        change = true
    }
    if (change) {
        update_fan_state()
        update_zones()
    }
}

def child_heat_demand_Handler(evt=NULL) {
    // log.debug("In child_heat_demand_Handler()")
    new_value = 0
    def zones = getChildApps()
    zones.each { z ->
        if (atomicState.main_zones.findAll { it == z.label }) {
            status_device = z.get_status_device()
            def zone_value = status_device.currentValue("heat_demand")
            new_value += zone_value
        }
    }
    if (new_value != atomicState.heat_demand) {
        atomicState.heat_demand = new_value
        status_device = getChildDevice("HVACZoning_${app.id}")
        status_device.set_heat_demand(atomicState.heat_demand)
        runIn(5, update_equipment_state, [misfire: "ignore"])
    }
}

def child_heat_accept_Handler(evt=NULL) {
    // log.debug("In child_heat_accept_Handler()")
    new_value = 0
    def zones = getChildApps()
    zones.each { z ->
        if (atomicState.main_zones.findAll { it == z.label }) {
            status_device = z.get_status_device()
            def zone_value = status_device.currentValue("heat_accept")
            new_value += zone_value
        }
    }
    if (new_value != atomicState.heat_accept) {
        atomicState.heat_accept = new_value
        status_device = getChildDevice("HVACZoning_${app.id}")
        status_device.set_heat_accept(atomicState.heat_accept)
        runIn(5, update_equipment_state, [misfire: "ignore"])
    }
}

def child_cool_demand_Handler(evt=NULL) {
    // log.debug("In child_cool_demand_Handler()")
    new_value = 0
    def zones = getChildApps()
    zones.each { z ->
        if (atomicState.main_zones.findAll { it == z.label }) {
            status_device = z.get_status_device()
            def zone_value = status_device.currentValue("cool_demand")
            new_value += zone_value
        }
    }
    if (new_value != atomicState.cool_demand) {
        atomicState.cool_demand = new_value
        status_device = getChildDevice("HVACZoning_${app.id}")
        status_device.set_cool_demand(atomicState.cool_demand)
        runIn(5, update_equipment_state, [misfire: "ignore"])
    }
}

def child_cool_accept_Handler(evt=NULL) {
    // log.debug("In child_cool_accept_Handler()")
    new_value = 0
    def zones = getChildApps()
    zones.each { z ->
        if (atomicState.main_zones.findAll { it == z.label }) {
            status_device = z.get_status_device()
            def zone_value = status_device.currentValue("cool_accept")
            new_value += zone_value
        }
    }
    if (new_value != atomicState.cool_accept) {
        atomicState.cool_accept = new_value
        status_device = getChildDevice("HVACZoning_${app.id}")
        status_device.set_cool_accept(atomicState.cool_accept)
        runIn(5, update_equipment_state, [misfire: "ignore"])
    }
}

def child_fan_demand_Handler(evt=NULL) {
    // log.debug("In child_fan_demand_Handler()")
    new_value = 0
    def zones = getChildApps()
    zones.each { z ->
        if (atomicState.main_zones.findAll { it == z.label }) {
            status_device = z.get_status_device()
            def zone_value = status_device.currentValue("fan_demand")
            new_value += zone_value
        }
    }
    if (new_value != atomicState.fan_demand) {
        atomicState.fan_demand = new_value
        status_device = getChildDevice("HVACZoning_${app.id}")
        status_device.set_fan_demand(atomicState.fan_demand)
        runIn(5, update_equipment_state, [misfire: "ignore"])
    }
}

def child_fan_accept_Handler(evt=NULL) {
    // log.debug("In child_fan_accept_Handler()")
    new_value = 0
    def zones = getChildApps()
    zones.each { z ->
        if (atomicState.main_zones.findAll { it == z.label }) {
            status_device = z.get_status_device()
            def zone_value = status_device.currentValue("fan_accept")
            new_value += zone_value
        }
    }
    if (new_value != atomicState.fan_accept) {
        atomicState.fan_accept = new_value
        status_device = getChildDevice("HVACZoning_${app.id}")
        status_device.set_fan_accept(atomicState.fan_accept)
        current_state = status_device.currentValue("current_state")
        if ("$current_state.value" == "Vent") {
            runIn(5, update_zones, [misfire: "ignore"])
        }
    }
}

def child_dehum_accept_Handler(evt=NULL) {
    // log.debug("In child_dehum_accept_Handler()")
    new_value = 0
    def zones = getChildApps()
    zones.each { z ->
        if (atomicState.main_zones.findAll { it == z.label }) {
            status_device = z.get_status_device()
            def zone_value = status_device.currentValue("dehum_accept")
            new_value += zone_value
        }
    }
    if (new_value != atomicState.dehum_accept) {
        atomicState.dehum_accept = new_value
        status_device = getChildDevice("HVACZoning_${app.id}")
        status_device.set_dehum_accept(atomicState.dehum_accept)
        current_state = status_device.currentValue("current_state")
        if ("$current_state.value" == "Dehum") {
            runIn(5, update_zones, [misfire: "ignore"])
        }
    }
}

def child_humid_accept_Handler(evt=NULL) {
    // log.debug("In child_humid_accept_Handler()")
    new_value = 0
    def zones = getChildApps()
    zones.each { z ->
        if (atomicState.main_zones.findAll { it == z.label }) {
            status_device = z.get_status_device()
            def zone_value = status_device.currentValue("humid_accept")
            new_value += zone_value
        }
    }
    if (new_value != atomicState.humid_accept) {
        atomicState.humid_accept = new_value
        status_device = getChildDevice("HVACZoning_${app.id}")
        status_device.set_humid_accept(atomicState.humid_accept)
        current_state = status_device.currentValue("current_state")
        if ("$current_state.value" == "Humid") {
            runIn(5, update_zones, [misfire: "ignore"])
        }
    }
}

// Equipment Control Routines
// The variable atomicState.equip_state indicates the current state of heating and cooling equipment.  Possible values are:
// "Idle": which means that both heating and cooling equipment has been off for long enough that either heating or cooling calls could be served if they occur
// "IdleH": which means that both heating and cooling equipment is off and the heating equipment turned off long enough ago to serve a heat call (but not a cooling call)
// "PauseH": which means that both heating and cooling equipment is off, but the heating equipment turned off recently enough that new calls should not be served yet
// "Heating": which means that heating equipment is running and has been running long enough to turn if off when heating calls end
// "HeatingL": which means that heating equipment is running but has turn on recently enough that it needs to remain on even if a heating call ends
// "IdleC": which means that both heating and cooling equipment is off and the cooling equipment turned off long enough ago to serve a cooling call (but not a heat call)
// "PauseC": which means that both heating and cooling equipment is off, but the cooling equipment turned off recently enough that new calls should not be served yet
// "Cooling": which means that coolinging equipment is running and has been running long enough to turn if off when cooling calls end
// "CoolingL": which means that cooling equipment is running but has turn on recently enough that it needs to remain on even if a cooling call ends

def set_equip_state(String new_state) {
    atomicState.equip_state = new_state
    status_device = getChildDevice("HVACZoning_${app.id}")
    status_device.set_status_msg("$atomicState.equip_state, $atomicState.vent_state, $atomicState.fan_state")
}

// The variable atomicState.fan_state indicates the current state of the blower.  Possible values are:
// "Off": blower is off
// "On_for_equip": blower is commanded on because cooling or heating is being commanded (user may also be requesting fan and ventilation and/or dehumidification may also be underway)
// "On_for_vent": blower is commanded on because ventilation is being commanded (heating and cooling off, user may also be requesting fan and dehumidification may be underway)
// "On_for_dehum": blower is commanded on because dehumidification is being commanded (heating and cooling off, user may also be requesting fan)
// "On_for_humid": blower is commanded on because humidification is being commanded (heating and cooling off, user may also be requesting fan)
// "On_by_request": blower is commanded on due to fan call from a zone (ventilation, heating, and cooling off)

def set_fan_state(String new_state) {
    atomicState.fan_state = new_state
    status_device = getChildDevice("HVACZoning_${app.id}")
    status_device.set_status_msg("$atomicState.equip_state, $atomicState.vent_state, $atomicState.fan_state")
}

// The variable atomicState.vent_state indicates the current state of the ventilation.  Possible values are:
// "Off": which means that the user has turned ventilation off
// "Forced": which means that the user has forced ventilation to run, regardless of equipment state of how much it has already run recently
// "Waiting": which means ventilation has not yet run enough during the present interval, but the app is waiting for equipment to run so
//   that ventilation can be done during a cooling or heating call.  In this state, a call to vent_deadline_reached() is scheduled for when
//   ventilation should start even if there is no heating or cooling call.
// "Complete": which means ventilation has already run enough during the present interval
// "Running": which means ventilation is running during a heating or cooling call.  In this state, a call to vent_runtime_reached() is scheduled
//   for when ventilation will have run enough in this interval and should be stopped even if the heating or cooling call continues.
// "End_Phase": which means that ventilation needs to run for the remainder of the interval in order to run enough during the present interval

def set_vent_state(String new_state) {
    atomicState.vent_state = new_state
    status_device = getChildDevice("HVACZoning_${app.id}")
    status_device.set_status_msg("$atomicState.equip_state, $atomicState.vent_state, $atomicState.fan_state")
}

def wired_tstatHandler(evt=NULL) {
    // this routine is called if a zone which is hardwired to the equipment gets updated.
    // the purpose of the routine is to ensure that the app does not issue equipment calls inconsistent with the hardwired thermostat
    // this call will likely be followed in approximately 5 seconds by a call to zone_call_changed()
    // log.debug("In wired_tstatHandler")
    def opstate = wired_tstat.currentValue("thermostatOperatingState")
    switch ("$opstate") {
        case "cooling":
            switch ("$atomicState.equip_state") {
                case "Idle": 
                case "Cooling": 
                case "CoolingL": 
                case "IdleC": 
                    // safe state – zone_call_changed() will handle correctly
                    break
                case "Heating": 
                case "HeatingL": 
                    end_heat_run()
                    unschedule(equipment_off_adjust_vent)
                case "PauseH": 
                case "PauseC": 
                case "IdleH": 
                    unschedule(equipment_state_timeout) 
                    set_equip_state("Idle")
                    break
            }
            break
        case "heating":
            switch ("$atomicState.equip_state") {
                case "Idle": 
                case "Heating": 
                case "HeatingL": 
                case "IdleH": 
                    // safe state – zone_call_changed() will handle correctly
                    break
                case "Cooling": 
                case "CoolingL": 
                    end_cool_run()
                    unschedule(equipment_off_adjust_vent)
                case "PauseH": 
                case "PauseC": 
                case "IdleC": 
                    unschedule(equipment_state_timeout) 
                    set_equip_state("Idle")
                    break
            }
            break
    }
}

def set_emergency_heat(evt=NULL) {
    atomicState.emergency_heat = false
    if (emergency_heat_stats) {
        emergency_heat_stats.each { ehs ->
            mode_value = ehs.currentValue("thermostatMode")
            if (mode_value == "emergency heat") { atomicState.emergency_heat = true }
        }
    }
}

def update_equipment_state() {
    log.debug("In update_equipment_state() - current state is $atomicState.equip_state")  
    // this section updates the state and, in heating and cooling modes, selects the zones and equipment
    switch ("$atomicState.equip_state") {
        case "Idle":
            if (servable_heat_call()) {
                start_heat_run()
            } else if (servable_cool_call()) {
                start_cool_run()
            } else {
                update_fan_state()
            }
            break
        case "IdleH":
            if (servable_heat_call()) {
                start_heat_run()
            } else {
                update_fan_state()
            }
            break
        case "PauseH":
        case "PauseC":
            update_fan_state()
            break
        case "Heating":
            if (servable_heat_call()) {
                update_heat_run()
            } else {
                end_heat_run()
            }
            break
        case "HeatingL":
            update_heat_run()
            return
        case "IdleC":
            if (servable_cool_call()) {
                start_cool_run()
            } else {
                update_fan_state()
            }
            break
        case "Cooling":
            if (servable_cool_call()) {
                update_cool_run()
            } else {
                end_cool_run()
            }
            break
        case "CoolingL":
            update_cool_run()
    }
    update_zones()
}

def equipment_state_timeout() {
    log.debug("In equipment_state_timeout() - state is $atomicState.equip_state")
    switch ("$atomicState.equip_state") {
        case "Idle":
            status_device = getChildDevice("HVACZoning_${app.id}")
            status_device.debug("timeout in Idle mode shouldn't happen")
            log.debug("timeout in Idle mode shouldn't happen")
            break
        case "IdleH":
        case "IdleC":
            set_equip_state("Idle")
            update_equipment_state()
            break
        case "PauseH":
            if (mode_change_delay > heat_min_idletime) {
                set_equip_state("IdleH")
                runIn((mode_change_delay-heat_min_idletime)*60, equipment_state_timeout, [misfire: "ignore"]) // this will cause a transition to Idle state at the right time 
            } else {
                set_equip_state("Idle")
            }
            update_equipment_state()
            break
        case "PauseC":
            if (mode_change_delay > cool_min_idletime) {
                set_equip_state("IdleC")
                runIn((mode_change_delay-cool_min_idletime)*60, equipment_state_timeout, [misfire: "ignore"]) // this will cause a transition to Idle state at the right time 
            } else {
                set_equip_state("Idle")
            }
            update_equipment_state()
            break
        case "Heating":
            status_device = getChildDevice("HVACZoning_${app.id}")
            status_device.debug("timeout in Heating mode shouldn't happen")
            log.debug("timeout in Heating mode shouldn't happen")
            break
        case "HeatingL":
            set_equip_state("Heating")
            update_equipment_state()
            break
        case "Cooling":
            status_device = getChildDevice("HVACZoning_${app.id}")
            status_device.debug("timeout in Cooling mode shouldn't happen")
            log.debug("timeout in Cooling mode shouldn't happen")
            break
        case "CoolingL":
            set_equip_state("Cooling")
            update_equipment_state()
            break
    }
}

def periodic_check() {
    log.debug("In periodic_check()")
    // check whether we are stuck in a state
    switch ("$atomicState.equip_state") {
        case "PauseH":
            if ((now() - atomicState.last_heating_stop) > heat_min_idletime*60*1000) {
                status_device = getChildDevice("HVACZoning_${app.id}")
                status_device.debug("Appears to be stuck in state $atomicState.equip_state")
                log.debug("Appears to be stuck in state $atomicState.equip_state")
                runIn(5, "equipment_state_timeout", [misfire: "ignore"])
            }
            break
        case "IdleH":
            if ((now() - atomicState.last_heating_stop) > mode_change_delay*60*1000) {
                status_device = getChildDevice("HVACZoning_${app.id}")
                status_device.debug("Appears to be stuck in state $atomicState.equip_state")
                log.debug("Appears to be stuck in state $atomicState.equip_state")
                runIn(5, "equipment_state_timeout", [misfire: "ignore"])
            }
            break
        case "HeatingL":
            if ((now() - atomicState.last_heating_start) > heat_min_runtime*60*1000) {
                status_device = getChildDevice("HVACZoning_${app.id}")
                status_device.debug("Appears to be stuck in state $atomicState.equip_state")
                log.debug("Appears to be stuck in state $atomicState.equip_state")
                runIn(5, "equipment_state_timeout", [misfire: "ignore"])
            }
            break
        case "PauseC":
            if ((now() - atomicState.last_cooling_stop) > cool_min_idletime*60*1000) {
                status_device = getChildDevice("HVACZoning_${app.id}")
                status_device.debug("Appears to be stuck in state $atomicState.equip_state")
                log.debug("Appears to be stuck in state $atomicState.equip_state")
                runIn(5, "equipment_state_timeout", [misfire: "ignore"])
            }
            break
        case "IdleC":
            if ((now() - atomicState.last_cooling_stop) > mode_change_delay*60*1000) {
                status_device = getChildDevice("HVACZoning_${app.id}")
                status_device.debug("Appears to be stuck in state $atomicState.equip_state")
                log.debug("Appears to be stuck in state $atomicState.equip_state")
                runIn(5, "equipment_state_timeout", [misfire: "ignore"])
            }
            break
        case "CoolingL":
            if ((now() - atomicState.last_cooling_start) > cool_min_runtime*60*1000) {
                status_device = getChildDevice("HVACZoning_${app.id}")
                status_device.debug("Appears to be stuck in state $atomicState.equip_state")
                log.debug("Appears to be stuck in state $atomicState.equip_state")
                runIn(5, "equipment_state_timeout", [misfire: "ignore"])
            }
            break
    }
    // start or stop humidifier and/or dehumidifier if necessary
    update_humidity_equip()
    // check whether heating outputs are in correct state
    W1_value = "TBD"
    W2_value = "TBD"
    Y1_value = "TBD"
    Y2_value = "TBD"
    G_value = G.currentValue("switch")
    switch ("$equip_type") {
        case "Heat Pump with Electric Backup":
            W1_value = W1.currentValue("switch")
            break
        case "Furnace only":
        case "Furnace and Air Conditioning":
        case "Heat Pump with Backup Furnace":
            switch ("$heat_type") {
                case "Two stage":
                    W2_value = W2.currentValue("switch")
                case "Single stage":
                    W1_value = W1.currentValue("switch")
            }
    }
    switch ("$equip_type") {
        case "Air Conditioning only":
        case "Furnace and Air Conditioning":
        case "Heat Pump only":
        case "Heat Pump with Electric Backup":
        case "Heat Pump with Backup Furnace":
            switch ("$cool_type") {
                case "Two stage":
                    Y2_value = Y2.currentValue("switch")
                case "Single stage":
                    Y1_value = Y1.currentValue("switch")
            }
    }
    switch ("$atomicState.equip_state") {
        case "Cooling":
        case "CoolingL":
            switch("$Y1_value") {
                case "off":
                    status_device = getChildDevice("HVACZoning_${app.id}")
                    status_device.debug("Y1 is off in state $state.equip_state")
                    log.debug("Y1 is off in state $state.equip_state")
                    Y1.on()
                    update_cool_run()
            }
            switch("$G_value") {
                case "off":
                    status_device = getChildDevice("HVACZoning_${app.id}")
                    status_device.debug("G is off in state $state.equip_state")
                    log.debug("G is off in state $state.equip_state")
                    G.on()
            }
            switch("$W1_value") {
                case "on":
                    status_device = getChildDevice("HVACZoning_${app.id}")
                    status_device.debug("W1 is on in state $atomicState.equip_state")
                    log.debug("1 - W1 is on in state $atomicState.equip_state")
                    W1.off()
            }
            switch("$W2_value") {
                case "on":
                    status_device = getChildDevice("HVACZoning_${app.id}")
                    status_device.debug("W2 is on in state $atomicState.equip_state")
                    log.debug("1 - W2 is on in state $atomicState.equip_state")
                    W2.off()
            }
            break
        case "Heating":
        case "HeatingL":
            switch ("$equip_type") {
                case "Heat Pump with Backup Furnace":
                    if (!atomicState.backup_heat) { break }
                case "Furnace only":
                case "Furnace and Air Conditioning":
                    switch("$W1_value") {
                        case "off":
                            status_device = getChildDevice("HVACZoning_${app.id}")
                            status_device.debug("W1 is off in state $state.equip_state")
                            log.debug("W1 is off in state $state.equip_state")
                            W1.on()
                            update_heat_run()
                    }
                    switch("$Y1_value") {
                        case "on":
                            status_device = getChildDevice("HVACZoning_${app.id}")
                            status_device.debug("Y1 is on in state $atomicState.equip_state")
                            log.debug("Y1 is on in state $atomicState.equip_state")
                            Y1.off()
                    }
                    switch("$Y2_value") {
                        case "on":
                            status_device = getChildDevice("HVACZoning_${app.id}")
                            status_device.debug("Y2 is on in state $atomicState.equip_state")
                            log.debug("Y2 is on in state $atomicState.equip_state")
                            Y2.off()
                    }
            }
            switch ("$equip_type") {
                case "Heat Pump with Backup Furnace":
                    if (atomicState.backup_heat) { break }
                    switch("$W1_value") {
                        case "on":
                            status_device = getChildDevice("HVACZoning_${app.id}")
                            status_device.debug("W1 is on in state $atomicState.equip_state")
                            log.debug("2 - W1 is on in state $atomicState.equip_state")
                            W1.off()
                    }
                    switch("$W2_value") {
                        case "on":
                            status_device = getChildDevice("HVACZoning_${app.id}")
                            status_device.debug("W2 is on in state $atomicState.equip_state")
                            log.debug("2 - W2 is on in state $atomicState.equip_state")
                            W2.off()
                    }
                case "Heat Pump only":
                case "Heat Pump with Electric Backup":
                    switch("$Y1_value") {
                        case "off":
                            status_device = getChildDevice("HVACZoning_${app.id}")
                            status_device.debug("Y1 is off in state $state.equip_state")
                            log.debug("Y1 is off in state $state.equip_state")
                            Y1.on()
                            update_heat_run()
                    }
            }
            break
        case "Idle":
        case "IdleH":
        case "IdleC":
        case "PauseH":
        case "PauseC":
            switch("$W1_value") {
                case "on":
                    status_device = getChildDevice("HVACZoning_${app.id}")
                    status_device.debug("W1 is on in state $atomicState.equip_state")
                    log.debug("3 - W1 is on in state $atomicState.equip_state")
                    W1.off()
            }
            switch("$W2_value") {
                case "on":
                    status_device = getChildDevice("HVACZoning_${app.id}")
                    status_device.debug("W2 is on in state $atomicState.equip_state")
                    log.debug("3 - W2 is on in state $atomicState.equip_state")
                    W2.off()
            }
            switch("$Y1_value") {
                case "on":
                    status_device = getChildDevice("HVACZoning_${app.id}")
                    status_device.debug("Y1 is on in state $atomicState.equip_state")
                    log.debug("Y1 is on in state $atomicState.equip_state")
                    Y1.off()
            }
            switch("$Y2_value") {
                case "on":
                    status_device = getChildDevice("HVACZoning_${app.id}")
                    status_device.debug("Y2 is on in state $atomicState.equip_state")
                    log.debug("Y2 is on in state $atomicState.equip_state")
                    Y2.off()
            }
            break
    }
    // check whether vent outputs are in correct state
    V_value = "TBD"
    switch ("$equip_type") {
        case "Requires Blower":
        case "Doesn't Require Blower":
            V_value = V.currentValue("switch")
            switch ("$atomicState.vent_state") {
                case "Forced":
                case "Running":
                case "End_Phase":
                    switch("$V_value") {
                        case "off":
                            status_device = getChildDevice("HVACZoning_${app.id}")
                            status_device.debug("V is off in state $atomicState.vent_state")
                            log.debug("V is off in state $atomicState.vent_state")
                            V.on()
                    }
                    switch("$G_value") {
                        case "off":
                            status_device = getChildDevice("HVACZoning_${app.id}")
                            status_device.debug("G is off in state $atomicState.vent_state")
                            log.debug("G is off in state $atomicState.vent_state")
                            G.on()
                    }
                    break
                 case "Off":
                case "Waiting":
                case "Complete":
                    switch("$V_value") {
                        case "on":
                            status_device = getChildDevice("HVACZoning_${app.id}")
                            status_device.debug("V is on in state $atomicState.vent_state")
                            log.debug("V is on in state $atomicState.vent_state")
                            V.off()
                    }
                    break
        }
    }
    update_zones()
}

// Routines for handling humidiciation / dehumidification

// The variable atomicState.humid_sw_state indicates the current state of humidification / dehumidification controls.  Possible values are:
// "Off": which means neither humidification nor dehumidification is on (both are either off or don't exist)
// "Humidify": which means humidification is on and dehumidification is off, atomicState.min_humidity_target is the humidity target
// "Dehumidify": which means humidification is off and dehumidification is on, atomicState.max_humidity_target is the humidity target
// "Auto": which means both humidification and dehumidification are on, target range is between atomicState.min_humidity_target and atomicState.max_humidity_target

// The variable atomicState.humid_state indicates the current state of humidification / dehumidification equipment.  Possible values are:
// "Off": which means neither humidification nor dehumidification is running
// "Humidify": which means humidification is currently running
// "Dehumidify": which means dehumidification is currently running

def update_humidity_equip() {
    // log.debug("In update_humidity_equip()")
    // adjust humidification and dehumidification
    Integer current_humidity
    switch (atomicState.humid_sw_state) {
        case "Humidify":
        case "Dehumidify":
        case "Auto":
            def levelstate = indoor_humidity.currentState("humidity")
            current_humidity = levelstate.value as Integer
    }
    atomicState.humid_state = "Off"
    switch (atomicState.humid_sw_state) {
        case "Humidify":
        case "Auto":
            Boolean state_ok
            switch ("$humidifer_type") {
                case "Separate from HVAC ductwork":
                    state_ok = true
                    break
                case "Requires Heat":
                    switch ("$atomicState.equip_state") {
                        case "HeatingL":
                        case "Heating":
                            state_ok = true
                            break;
                        default:
                            state_ok = false
                    }
                    break
                case "Requires Fan":
                    switch ("$atomicState.equip_state") {
                        case "CoolingL":
                        case "Cooling":
                            state_ok = false
                            break;
                        default:
                            state_ok = true
                    }
                    break
            }
            if (state_ok && (current_humidity < atomicState.min_humidity_target)) {
                atomicState.humid_state = "Humidify"
                update_fan_state()
                Hum.on()
            } else {
                Hum.off()
                update_fan_state()
            }
            break
        default:
            if (Hum) { Hum.off() }
    }
    switch (atomicState.humid_sw_state) {
        case "Dehumidify":
        case "Auto":
            Boolean state_ok
            switch ("$dehumidifer_type") {
                case "Separate from HVAC ductwork":
                    state_ok = true
                    break
                case "Outputs to Supply Plenum":
                    switch ("$atomicState.equip_state") {
                        case "Idle":
                        case "IdleC":
                        case "PauseC":
                            state_ok = true
                            break;
                        default:
                            state_ok = false
                    }
                    break
                case "Outputs to Return Plenum":
                    switch ("$atomicState.equip_state") {
                        case "Idle":
                        case "IdleC":
                        case "PauseC":
                        case "Cooling":
                        case "CoolingL":
                            state_ok = true
                            break;
                        default:
                            state_ok = false
                    }
                    break
            }
            // log.debug("state_ok = $state_ok, current_humidity = $current_humidity, atomicState.max_humidity_target = $atomicState.max_humidity_target")
            if (state_ok && (current_humidity > atomicState.max_humidity_target)) {
                atomicState.humid_state = "Dehumidify"
                Dehum.on()
            } else {
                Dehum.off()
            }
            // set dehumidify mode which is engaged by turning the switch off 
            if (cool_dehum_mode) {
                if (current_humidity > atomicState.max_humidity_target) {
                    switch ("$atomicState.equip_state") {
                        case "Cooling":
                        case "CoolingL":
                            cool_dehum.off()
                            break
                        default:
                            cool_dehum.on()
                    }
                } else {
                    cool_dehum.on()
                }
            } else if (cool_dehum) { cool_dehum.on() }
            update_fan_state()
            break
        default:
            if (Dehum) { Dehum.off() }
            if (cool_dehum) { cool_dehum.on() }
            atomicState.dehum_flow = 0
    }
}

def update_humid_targets(evt=NULL) {
    // log.debug("In update_humid_targets()")
    humidifier_on = false
    if (humidifier_target) {
        def levelstate = humidifier_target.currentState("switch")
        switch ("$levelstate.value") {
            case "on":
                levelstate = humidifier_target.currentState("level")
                atomicState.min_humidity_target = levelstate.value as Integer
                humidifier_on = true
        }
    }
    dehumidifier_on = false
    if (dehumidifier_target) {
        def levelstate = dehumidifier_target.currentState("switch")
        switch ("$levelstate.value") {
            case "on":
                levelstate = dehumidifier_target.currentState("level")
                atomicState.max_humidity_target = levelstate.value as Integer
                dehumidifier_on = true
        }
    }
    if (humidifier_on) {
        if (dehumidifier_on) {
            atomicState.humid_sw_state = "Auto"
            if (atomicState.max_humidity_target - atomicState.min_humidity_target < 10) {
                Integer avg = (atomicState.min_humidity_target + atomicState.max_humidity_target) / 2 
                atomicState.min_humidity_target = avg - 5
                atomicState.max_humidity_target = avg + 5
                humidifier_target.setLevel(atomicState.min_humidity_target)
                dehumidifier_target.setLevel(atomicState.max_humidity_target)
            }
        } else {
            atomicState.humid_sw_state = "Humidify"
        }
    } else {
        if (dehumidifier_on) {
            atomicState.humid_sw_state = "Dehumidify"
        } else {
            atomicState.humid_sw_state = "Off"
        }
    }
}

// Routines for handling heating

Boolean servable_heat_call() {
    // check for servable heat call
    // log.debug("In servable_heat_call()")
    if (atomicState.heat_demand > 0) {
        switch ("$equip_type") {
            case "Furnace only":
            case "Furnace and Air Conditioning":
                if (atomicState.heat_demand+atomicState.heat_accept+atomicState.off_capacity >= cfmW1) { return true }
                break
            case "Heat Pump only":
            case "Heat Pump with Electric Backup":
            case "Heat Pump with Backup Furnace":
                if (atomicState.heat_demand+atomicState.heat_accept+atomicState.off_capacity >= cfmY1) { return true }
                break
        }
    }
    return false;
}

def start_heat_run() {
    log.debug("In start_heat_run()")
    set_equip_state("HeatingL")
    atomicState.last_heating_start = now()
    atomicState.backup_heat=false
    switch ("$equip_type") {
        case "Furnace only":
        case "Furnace and Air Conditioning":
            W1.on()
            break
        case "Heat Pump only":
            Y1.on()
            if (rv_heat) { OB.on() } else { OB.off() }
            break
        case "Heat Pump with Electric Backup":
            if (atomicState.emergency_heat) {
                W1.on() // Electric emergency heat is run without heat pump
            } else {
                Y1.on()
                if (rv_heat) { OB.on() } else { OB.off() }
                backup_heat()
                if (atomicState.backup_heat) { W1.on() } // Electric backup heat is run in addition to heat pump
            }
            break
        case "Heat Pump with Backup Furnace":
            backup_heat()
            if (atomicState.backup_heat || atomicState.emergency_heat) { // Furnace backup heat is run instead of heat pump
                W1.on()
            } else {
                Y1.on()
                if (rv_heat) { OB.on() } else { OB.off() }
            }
            break
    }
    // If two stage furnace (including backup to heat pump), then run stage2_heat check
    switch ("$equip_type") {
        case "Heat Pump with Backup Furnace":
            if (!atomicState.backup_heat || atomicState.emergency_heat) { break }
        case "Furnace only":
        case "Furnace and Air Conditioning":
            switch ("$heat_type") {
                case "Two stage":
                    if (heat_stage2_delay) { runIn(heat_stage2_delay*60, stage2_heat, [misfire: "ignore"]) }
                    stage2_heat()
            }
    }
    // If two stage heat_pump, then run stage2_cool check
    switch ("$equip_type") {
        case "Heat Pump with Backup Furnace":
            if (atomicState.backup_heat || atomicState.emergency_heat) { break }
        case "Heat Pump only":
        case "Heat Pump with Electric Backup":
            switch ("$cool_type") {
                case "Two stage":
                    if (cool_stage2_delay) { runIn(cool_stage2_delay*60, stage2_cool, [misfire: "ignore"]) }
                    stage2_cool()
            }
    }
    update_humidity_equip()
    equipment_on_adjust_vent()
    update_fan_state()
    runIn(heat_min_runtime*60, equipment_state_timeout, [misfire: "ignore"]) // this will cause a transition to Heating state at the right time 
}

// This routine checks whether backup heat should be used and sets boolean variable atomicState.backup_heat accordingly.
// This routine is called at the beginning of a heat run or periodic check (with evt == NULL) and when a variable used in a criteria changes (with evt != NULL)
def backup_heat(evt=NULL) {
    log.debug("In backup_heat()")
    if (atomicState.backup_heat) { return } // If already in a heating run with backup heat on, keep backup heat on for the remainder of the run.
    switch (atomicState.equip_state) {
        case "Heating":
        case "HeatingL":
            if (backup_heat_criteria) {
                num_satisfied = 0
                backup_heat_criteria.each { crit ->
                    switch ("$crit") {
                        case "Runtime in heat pump":
                            if (now() - atomicState.last_heating_start > backup_heat_delay * 60 * 1000) { num_satisfied++ }
                            break
                        case "Setpoint/Temp difference":
                            def zones = getChildApps()
                            BigDecimal max_delta = 0
                            zones.each { z ->
                                if (atomicState.main_zones.findAll { it == z.label }) {
                                    BigDecimal delta = z.heat_delta_temp()
                                    if (delta > max_delta) { max_delta = delta }
                                 }
                            }
                            if (max_delta >= backup_heat_delta) { num_satisfied++ }
                            break
                        case "When a switch is on":
                            if (backup_heat_switch) {
                                switch_value = backup_heat_switch.currentValue("switch")
                                if (switch_value == "on") { num_satisfied++ }
                            }
                            break
                        case "Outdoor temp":
                            if (outdoor_temp) {
                                temp_value = outdoor_temp.currentValue("temperature")
                                if (temp_value < backup_heat_outdoor_threshold) { num_satisfied++ }
                            }
                            break
                        default:
                            log.debug("Unknown backup_heat_criteria $crit")
                    }
                }
                log.debug("num_satisfied = $num_satisfied")
                if (backup_heat_criteria.size() > 0) {
                    if (backup_heat_and) {
                        if (num_satisfied == backup_heat_criteria.size()) {
                            atomicState.backup_heat = true
                            if (evt) {
                                update_heat_run()
                                update_zones()
                            }
                        }
                    } else {
                        if (num_satisfied > 0) {
                            atomicState.backup_heat = true
                            if (evt) {
                                update_heat_run()
                                update_zones()
                            }
                        }
                    }
                }
            }
    }
}

def stage2_heat(evt=NULL) {
    log.debug("In stage2_heat() - state is $atomicState.equip_state")
    old_second_stage = atomicState.second_stage
    switch ("$atomicState.equip_state") {
        case "Heating":
        case "HeatingL":
            switch ("$heat_type") {
                case "Two stage":
                    if (atomicState.heat_demand + atomicState.off_capacity < cfmW2) {
                        stage2_heat_off()
                    } else {
                        if (heat_stage2_criteria) {
                            num_satisfied = 0
                            heat_stage2_criteria.each { crit ->
                                switch ("$crit") {
                                    case "Runtime in low stage":
                                        if (now() - atomicState.last_heating_start > heat_stage2_delay * 60 * 1000) { num_satisfied++ }
                                        break
                                    case "Setpoint/Temp difference":
                                        def zones = getChildApps()
                                        BigDecimal max_delta = 0
                                        zones.each { z ->
                                            if (atomicState.main_zones.findAll { it == z.label }) {
                                                BigDecimal delta = z.heat_delta_temp()
                                                if (delta > max_delta) { max_delta = delta }
                                            }
                                        }
                                        if (max_delta >= heat_stage2_delta) { num_satisfied++ }
                                        break
                                    case "Total heat demand threshold":
                                        if (atomicState.heat_demand >= heat_stage2_threshold) { num_satisfied++ }
                                        break
                                    case "When a switch is on":
                                        if (heat_stage2_switch) {
                                            switch_value = heat_stage2_switch.currentValue("switch")
                                            if (switch_value == "on") { num_satisfied++ }
                                        }
                                        break
                                    case "Outdoor temp":
                                        if (outdoor_temp) {
                                            temp_value = outdoor_temp.currentValue("temperature")
                                            if (temp_value < heat_stage2_outdoor_threshold) { num_satisfied++ }
                                        }
                                        break
                                    default:
                                        log.debug("Unknown heat_stage2_criteria $crit")
                                }
                            }
                            if (heat_stage2_criteria.size() == 0) {
                                stage2_heat_off()
                            } else {
                                if (heat_stage2_and) {
                                    if (num_satisfied == heat_stage2_criteria.size()) {
                                        stage2_heat_on()
                                    } else {
                                        stage2_heat_off()
                                    }
                                } else {
                                    if (num_satisfied > 0) {
                                        stage2_heat_on()
                                    } else {
                                        stage2_heat_off()
                                    }
                                }
                            }
                        } else {
                            stage2_heat_off()
                        }
                   }
            }
            break
    }
    if (old_second_stage != atomicState.second_stage) {
        update_zones()
    }
}

def stage2_heat_on() {
    log.debug("In stage2_heat_on()")
    switch ("$heat_type") {
        case "Two stage":
            switch ("$equip_type") {
                case "Heat Pump with Backup Furnace":
                  if (!atomicState.backup_heat && !atomicState.emergency_heat) { break }
                case "Furnace only":
                case "Furnace and Air Conditioning":
                    W2.on()
                    if (!atomicState.second_stage) { atomicState.second_stage = true }
            }
    }
}

def stage2_heat_off() {
    log.debug("In stage2_heat_off()")
    switch ("$heat_type") {
        case "Two stage":
            W2.off()
    }
    if (atomicState.second_stage) { atomicState.second_stage = false }
}

// this routine may be called from update_equipment_state or from backup_heat()
def update_heat_run() {
    log.debug("In update_heat_run()")
    switch ("$equip_type") {
        case "Heat Pump with Electric Backup":
            if (atomicState.emergency_heat) {
                W1.on()
                Y1.off()
            } else if (atomicState.backup_heat) {
                Y1.on()
                W1.on()
            } else {
                Y1.on()
                W1.off()
            }
            break
        case "Heat Pump with Backup Furnace":
            if (atomicState.backup_heat || atomicState.emergency_heat) {
                W1.on()
                Y1.off()
            } else {
                Y1.on()
                W1.off()
            }
            break
    }
    // If two stage furnace (including backup to heat pump), then run stage2_heat check
    switch ("$equip_type") {
        case "Heat Pump with Backup Furnace":
            if (!atomicState.backup_heat && !atomicState.emergency_heat) { break }
        case "Furnace only":
        case "Furnace and Air Conditioning":
            switch ("$heat_type") {
                case "Two stage":
                    stage2_heat()
                case "Single stage":
                    if (Y2) { Y2.off() }
            }
    }
    // If two stage heat_pump, then run stage2_cool check
    switch ("$equip_type") {
        case "Heat Pump with Backup Furnace":
            if (atomicState.backup_heat || atomicState.emergency_heat) { break }
        case "Heat Pump only":
        case "Heat Pump with Electric Backup":
            switch ("$cool_type") {
                case "Two stage":
                    stage2_cool()
            }
    }
}

def end_heat_run() {
    // log.debug("In end_heat_run()")
    set_equip_state("PauseH")
    atomicState.last_heating_stop = now()
    stage2_heat_off()
    stage2_cool_off()
    unschedule(stage2_heat)
    unschedule(stage2_cool)
    if (W1) { W1.off() }
    if (Y1) { Y1.off() }
    runIn(heat_min_idletime*60, equipment_state_timeout, [misfire: "ignore"]) // this will cause a transition to IdleH state at the right time 
    update_humidity_equip()
    equipment_off_adjust_vent()
    update_fan_state()
}

// Routines for handling cooling

Boolean servable_cool_call() {
    // check for servable cooling call
    // log.debug("In servable_cool_call()")
    switch ("$equip_type") {
        case "Air Conditioning only":
        case "Furnace and Air Conditioning":
        case "Heat Pump only":
        case "Heat Pump with Electric Backup":
        case "Heat Pump with Backup Furnace":
            if (atomicState.cool_demand > 0) {
                 if (atomicState.cool_demand+atomicState.cool_accept+atomicState.off_capacity >= cfmY1) {
                     return true;
                 }
            }
    }
    return false;
}

def start_cool_run() {
    // log.debug("In start_cool_run()")
    set_equip_state("CoolingL")
    atomicState.last_cooling_start = now()
    atomicState.backup_heat=false
    Y1.on()
    switch ("$equip_type") {
        case "Heat Pump only":
        case "Heat Pump with Electric Backup":
        case "Heat Pump with Backup Furnace":
            if (rv_heat) { OB.off() } else { OB.on() }
    }
    update_fan_state()
    runIn(cool_min_runtime*60, equipment_state_timeout, [misfire: "ignore"]) // this will cause a transition to Cooling state at the right time 
    switch ("$cool_type") {
        case "Two stage":
            if (cool_stage2_delay) { runIn(cool_stage2_delay*60, stage2_cool, [misfire: "ignore"])  }
            stage2_cool()
    }
    update_humidity_equip()
    equipment_on_adjust_vent()
    update_fan_state()
}

// This routine may be called during a cooling run or during a heat pump heating run (with heat pump, same staging criteria are used for heating and cooling)
def stage2_cool(evt=NULL) {
    log.debug("In stage2_cool() - state is $atomicState.equip_state")
    old_second_stage = atomicState.second_stage
    num_satisfied = 0
    switch ("$atomicState.equip_state") {
        case "Cooling":
        case "CoolingL":
            switch ("$cool_type") {
                case "Two stage":
                    if (atomicState.cool_demand + atomicState.off_capacity < cfmY2) {
                        stage2_cool_off()
                    } else {
                        if (cool_stage2_criteria) {
                            cool_stage2_criteria.each { crit ->
                                switch ("$crit") {
                                    case "Runtime in low stage":
                                        if (now() - atomicState.last_cooling_start > cool_stage2_delay * 60 * 1000) { num_satisfied++ }
                                        break
                                    case "Setpoint/Temp difference":
                                        def zones = getChildApps()
                                        BigDecimal max_delta = 0
                                        zones.each { z ->
                                            if (atomicState.main_zones.findAll { it == z.label }) {
                                                BigDecimal delta = z.cool_delta_temp()
                                                if (delta > max_delta) { max_delta = delta }
                                            }
                                        }
                                        if (max_delta >= cool_stage2_delta) { num_satisfied++ }
                                        break
                                    case "Total cool demand threshold":
                                        if (atomicState.cool_demand >= cool_stage2_threshold) { num_satisfied++ }
                                        break
                                    case "When a switch is on":
                                        if (cool_stage2_switch) {
                                            switch_value = cool_stage2_switch.currentValue("switch")
                                            if (switch_value == "on") { num_satisfied++ }
                                        }
                                        break
                                    case "Outdoor temp":
                                        if (outdoor_temp) {
                                            temp_value = outdoor_temp.currentValue("temperature")
                                            if (temp_value > cool_stage2_outdoor_threshold) { num_satisfied++ }
                                        }
                                        break
                                    default:
                                        log.debug("Unknown cool_stage2_criteria $crit")
                                }
                            }
                        } else {
                            stage2_cool_off()
                        }
                   }
            }
            break
        case "Heating":
        case "HeatingL":
            switch ("$cool_type") {
                case "Two stage":
                    if (atomicState.heat_demand + atomicState.off_capacity < cfmY2) {
                        stage2_cool_off()
                    } else {
                        if (cool_stage2_criteria) {
                            cool_stage2_criteria.each { crit ->
                                switch ("$crit") {
                                    case "Runtime in low stage":
                                        if (now() - atomicState.last_heating_start > cool_stage2_delay * 60 * 1000) { num_satisfied++ }
                                        break
                                    case "Setpoint/Temp difference":
                                        def zones = getChildApps()
                                        BigDecimal max_delta = 0
                                        zones.each { z ->
                                            if (atomicState.main_zones.findAll { it == z.label }) {
                                                BigDecimal delta = z.heat_delta_temp()
                                                if (delta > max_delta) { max_delta = delta }
                                            }
                                        }
                                        if (max_delta >= cool_stage2_delta) { num_satisfied++ }
                                        break
                                    case "When a switch is on":
                                        if (cool_stage2_switch) {
                                            switch_value = cool_stage2_switch.currentValue("switch")
                                            if (switch_value == "on") { num_satisfied++ }
                                        }
                                        break
                                   default:
                                        log.debug("Unknown cool_stage2_criteria $crit")
                                }
                            }
                        } else {
                            stage2_cool_off()
                        }
                   }
            }
            break
    }
    switch ("$atomicState.equip_state") {
        case "Cooling":
        case "CoolingL":
        case "Heating":
        case "HeatingL":
            if (cool_stage2_criteria) {
                if (cool_stage2_criteria.size() == 0) {
                   stage2_cool_off()
                } else {
                    if (cool_stage2_and) {
                        if (num_satisfied == cool_stage2_criteria.size()) {
                            stage2_cool_on()
                        } else {
                            stage2_cool_off()
                        }
                    } else {
                        if (num_satisfied > 0) {
                            stage2_cool_on()
                        } else {
                            stage2_cool_off()
                        }
                    }
                }
            }
    }
    if (old_second_stage != atomicState.second_stage) {
        update_zones()
    }
}

def stage2_cool_on() {
    log.debug("In stage2_cool_on()")
    switch ("$cool_type") {
        case "Two stage":
            switch ("$equip_type") {
                case "Heat Pump with Backup Furnace":
                  if (atomicState.backup_heat || atomicState.emergency_heat) { break }
                case "Air Conditioning only":
                case "Furnace and Air Conditioning":
                    Y2.on()
                    if (!atomicState.second_stage) { atomicState.second_stage = true }
            }
    }
}

def stage2_cool_off() {
    log.debug("In stage2_cool_off()")
    switch ("$cool_type") {
        case "Two stage":
            Y2.off()
    }
    if (atomicState.second_stage) { atomicState.second_stage = false }
}

def update_cool_run() {
    // log.debug("In update_cool_run()")
    switch ("$cool_type") {
        case "Two stage":
            stage2_cool()
    }
}

def end_cool_run() {
    // log.debug("In end_cool_run()")
    set_equip_state("PauseC")
    atomicState.last_cooling_stop = now()
    switch ("$cool_type") {
        case "Two stage":
            stage2_cool_off()
            unschedule(stage2_cool)
        case "Single stage":
            Y1.off()
    }
    runIn(cool_min_idletime*60, equipment_state_timeout, [misfire: "ignore"]) // this will cause a transition to IdleC state at the right time 
    update_humidity_equip()
    equipment_off_adjust_vent()
    update_fan_state()
}

// Routines for handling fan only commands

def update_fan_state() {
    // log.debug("In update_fan_state()")
    switch ("$atomicState.equip_state") {
        case "Idle":
        case "IdleH":
        case "PauseH":
        case "IdleC":
        case "PauseC":
            if ("$vent_type" == "Requires Blower") {
                switch ("$atomicState.vent_state") {
                    case "Forced":
                    case "End_Phase":
                    case "Running":
                    set_fan_state("On_for_vent")
                    G.on()
                    return
                }
            }
            if (("$atomicState.humid_state" == "Dehumidify") && ("$dehumidifer_type" == "Outputs to Return Plenum")) {
                set_fan_state("On_for_dehum")
                G.on()
                return
            }
            if (("$atomicState.humid_state" == "Humidify") && ("$humidifer_type" == "Requires Fan")) {
                set_fan_state("On_for_humid")
                G.on()
                return
            }
            if (atomicState.fan_demand > 0) {
                if (atomicState.fan_demand+atomicState.off_capacity >= cfmG) {
                    set_fan_state("On_by_request")
                    G.on()
                    return
                }
            }
            set_fan_state("Off")
            G.off()
            return
        case "Heating":
        case "HeatingL":
        case "Cooling":
        case "CoolingL":
            set_fan_state("On_for_equip")
            G.on()
            return
    }
}

def update_zones() {
    log.debug("In update_zones()")
    def flow = 0
    def accept_flow = 0
    def force_flow = 0
    def current_state = "TBD"
    Number heat_output = 0
    Number cool_output = 0
    Number vent_output = 0
    switch ("$atomicState.vent_state") {
        case "Forced": 
        case "Running":
        case "End_Phase":
            vent_output = 100
    }
    status_device = getChildDevice("HVACZoning_${app.id}")
    switch ("$atomicState.equip_state") {
        case "Idle":
        case "IdleH":
        case "IdleC":
        case "PauseH":
        case "PauseC":
            switch ("$atomicState.fan_state") {
                case "Off":
                    if (("$atomicState.humid_state" == "Dehumidify") && ("$dehumidifer_type" == "Outputs to Supply Plenum")) {
                        current_state = "Dehum"
                        flow = cfmDeHum 
                        if (flow > atomicState.dehum_accept) {
                            force_flow = flow - atomicState.dehum_accept
                        }
                    } else {
                        current_state = "Idle"
                    }
                    break
                case "On_for_equip":
                    log.debug("On_for_equip is incompatible with equipment state $atomicState.equip_state")
                    status_device.debug("On_for_equip is incompatible with equipment state $atomicState.equip_state")
                    break
                case "On_for_vent":
                    current_state = "Vent"
                    flow = cfmG - atomicState.off_capacity
                    if (("$atomicState.humid_state" == "Dehumidify") && ("$dehumidifer_type" == "Outputs to Supply Plenum")) {
                        flow += cfmDeHum 
                    }
                    if (flow > atomicState.fan_accept) {
                        force_flow = flow - atomicState.fan_accept
                    }
                    break
                case "On_for_dehum":
                    current_state = "Dehum"
                    flow = cfmG - atomicState.off_capacity
                    if (flow > atomicState.dehum_accept) {
                       force_flow = flow - atomicState.dehum_accept
                    }
                    break
                case "On_for_humid":
                    current_state = "Humid"
                    flow = cfmG - atomicState.off_capacity
                    if (flow > atomicState.humid_accept) {
                       force_flow = flow - atomicState.humid_accept
                    }
                    break
                case "On_by_request":
                    current_state = "Fan"
                    flow = cfmG - atomicState.off_capacity
                    if (("$atomicState.humid_state" == "Dehumidify") && ("$dehumidifer_type" == "Outputs to Supply Plenum")) {
                        flow += cfmDeHum 
                    }
                    if (flow > atomicState.fan_demand) {
                        force_flow = flow - atomicState.fan_demand
                    }
                    break
            }
            break
        case "Heating":
        case "HeatingL":
            current_state = "Heating"
            switch ("$equip_type") {
                case "Furnace only":
                case "Furnace and Air Conditioning":
                    if (atomicState.second_stage) {
                        flow = cfmW2 - atomicState.off_capacity
                        if (capW2) {
                            heat_output = capW2
                        }
                    } else {
                        flow = cfmW1 - atomicState.off_capacity
                        if (capW1) {
                            heat_output = capW1
                        }
                    }
                    break
                case "Heat Pump with Backup Furnace":
                    if (atomicState.backup_heat) {
                        if (atomicState.second_stage) {
                            flow = cfmW2 - atomicState.off_capacity
                            if (capW2) {
                                heat_output = capW2
                            }
                        } else {
                            flow = cfmW1 - atomicState.off_capacity
                            if (capW1) {
                                heat_output = capW1
                            }
                        }
                        break
                    }
                case "Heat Pump only":
                case "Heat Pump with Electric Backup":
                    if (atomicState.second_stage) {
                        flow = cfmY2 - atomicState.off_capacity
                        if (capY2) {
                            heat_output = capY2
                        }
                    } else {
                        flow = cfmY1 - atomicState.off_capacity
                        if (capY1) {
                            heat_output = capY1
                        }
                    }
                    break
            }
            if (flow > atomicState.heat_demand) {
                accept_flow = flow - atomicState.heat_demand
                if (accept_flow > atomicState.heat_accept) {
                    force_flow = accept_flow - atomicState.heat_accept
                }
            }
            break
        case "Cooling":
        case "CoolingL":
            current_state = "Cooling"
            if (atomicState.second_stage) {
                flow = cfmY2 - atomicState.off_capacity
                if (capY2) {
                    cool_output = capY2
                }
            } else {
                flow = cfmY1 - atomicState.off_capacity
                if (capY1) {
                    cool_output = capY1
                }
            }
            if (flow > atomicState.cool_demand) {
                accept_flow = flow - atomicState.cool_demand
                if (accept_flow > atomicState.cool_accept) {
                    force_flow = accept_flow - atomicState.cool_accept
                }
            }
            break
    }
    // log.debug("state = $current_state, flow = $flow, accept_flow = $accept_flow, force_flow = $force_flow")
    if (status_device) {
        status_device.setState(current_state, flow)
        status_device.set_heat_output(heat_output)
        status_device.set_cool_output(cool_output)
        status_device.set_vent_output(vent_output)
    }
    def total_flow = atomicState.off_capacity
    def zones = getChildApps()
    zones.each { z ->
        // log.debug("checking zone $z.label")
        if (atomicState.main_zones.findAll { it == z.label }) {
            // log.debug("updating state and flow for zone $z.label")
            zone_status_device = z.get_status_device()
            switch ("$current_state") {
                case "Heating":
                    demand = zone_status_device.currentValue("heat_demand")
                    if (demand) {
                        zone_status_device.setState("Heating", demand + force_flow)
                        total_flow += demand + force_flow
                    } else {
                        accept = zone_status_device.currentValue("heat_accept")
                        if (force_flow) {
                            zone_status_device.setState("Heating", accept + force_flow)
                            total_flow += accept + force_flow
                        } else if (atomicState.heat_accept) {
                            Integer sub_flow = accept * accept_flow / atomicState.heat_accept
                            if (sub_flow) {
                                zone_status_device.setState("Heating", sub_flow)
                                total_flow += sub_flow
                            } else {
                                zone_status_device.setState("Off", 0)
                            }
                        } else {
                            zone_status_device.setState("Off", 0)
                        }
                    }
                    break
                case "Cooling":
                    demand = zone_status_device.currentValue("cool_demand")
                    if (demand) {
                        zone_status_device.setState("Cooling", demand + force_flow)
                        total_flow += demand + force_flow
                    } else {
                        accept = zone_status_device.currentValue("cool_accept")
                        if (force_flow) {
                            zone_status_device.setState("Cooling", accept + force_flow)
                            total_flow += accept + force_flow
                        } else if (atomicState.cool_accept) {
                            Integer sub_flow = accept * accept_flow / atomicState.cool_accept
                            if (sub_flow) {
                                zone_status_device.setState("Cooling", sub_flow)
                                total_flow += sub_flow
                            } else {
                                zone_status_device.setState("Off", 0)
                            }
                        } else {
                            zone_status_device.setState("Off", 0)
                        }
                    }
                    break
                case "Fan":
                    demand = zone_status_device.currentValue("fan_demand")
                    if (demand) {
                        zone_status_device.setState("Fan", demand + force_flow)
                        total_flow += demand + force_flow
                    } else {
                        if (force_flow) {
                            zone_status_device.setState("Fan", force_flow)
                            total_flow += force_flow
                        } else {
                            zone_status_device.setState("Off", 0)
                        }
                    }
                    break
                case "Vent":
                    demand = zone_status_device.currentValue("fan_accept")
                    if (demand) {
                        zone_status_device.setState("Vent", demand + force_flow)
                        total_flow += demand + force_flow
                    } else {
                        if (force_flow) {
                            zone_status_device.setState("Vent", force_flow)
                            total_flow += force_flow
                        } else {
                            zone_status_device.setState("Off", 0)
                        }
                    }
                    break
                case "Dehum":
                    demand = zone_status_device.currentValue("dehum_accept")
                    // log.debug("zone dehum demand is $demand")
                    if (demand) {
                        zone_status_device.setState("Dehum", demand + force_flow)
                        total_flow += demand + force_flow
                    } else {
                        if (force_flow) {
                            zone_status_device.setState("Dehum", force_flow)
                            total_flow += force_flow
                        } else {
                            zone_status_device.setState("Off", 0)
                        }
                    }
                    break
                case "Humid":
                    demand = zone_status_device.currentValue("humid_accept")
                    // log.debug("zone humid demand is $demand")
                    if (demand) {
                        zone_status_device.setState("Humid", demand + force_flow)
                        total_flow += demand + force_flow
                    } else {
                        if (force_flow) {
                            zone_status_device.setState("Humid", force_flow)
                            total_flow += force_flow
                        } else {
                            zone_status_device.setState("Off", 0)
                        }
                    }
                    break
                case "Idle":
                    zone_status_device.setState("Idle", 0)
                    break
            }
        }
    }
    // log.debug("total_flow = $total_flow")
    zones.each { z1 ->
        // log.debug("checking zone $z1.label")
        if (atomicState.main_zones.findAll { it == z1.label }) {
            // log.debug("updating output for zone $z1.label")
            zone_status_device = z1.get_status_device()
            zone_flow = zone_status_device.currentValue("flow") + zone_status_device.currentValue("off_capacity")
            if (total_flow) {
                zone_status_device.set_heat_output(heat_output * zone_flow / total_flow)
                zone_status_device.set_cool_output(cool_output * zone_flow / total_flow)
                zone_status_device.set_vent_output(vent_output * zone_flow / total_flow)
            } else {
                // log.debug("zone_flow = $zone_flow")
                zone_status_device.set_heat_output(0)
                zone_status_device.set_cool_output(0)
                zone_status_device.set_vent_output(0)
            }
        }
    }
}

// Ventilation Control Routines
// The ventilation functionality runs ventilation for a specified fraction of each hour.  The function start_vent_interval gets called at the beginning
// of each interval.  Ventilation can be turned off.  Ventilation can also be forced to run.
// The variable atomicState.vent_runtime represents the number of seconds that are still needed in the current interval.  atomicState.vent_runtime is
// updated during changes (not continuously).

def start_vent_interval() {
    // log.debug("In start_vent_interval()")
    unschedule(vent_runtime_reached)
    unschedule(vent_deadline_reached)
    atomicState.vent_interval_start = now()
    atomicState.vent_interval_end = now() + 60*60*1000
    // determine how many minutes ventilation should run per interval, based on dimmer setting
    // any change to the dimmer during the interval has no effect until the next interval
    def levelstate = vent_control.currentState("level")
    Integer percent = levelstate.value as Integer
    Integer runtime = 60 * 60 * percent / 100
    atomicState.vent_runtime = runtime
    if ("$atomicState.vent_state" == "Forced") {
        return;
    }
    switch ("$vent_type") {
        case "Doesn't Require Blower":
            set_vent_state("Running")
            V.on()
            runIn(atomicState.vent_runtime, vent_runtime_reached, [misfire: "ignore"])
            break;
        case "Requires Blower":
            switch ("$atomicState.equip_state") {
                case "Idle":
                case "IdleH":
                case "PauseH":
                case "IdleC":
                case "PauseC":
                    set_vent_state("Waiting")
                    V.off()
                    update_fan_state()
                    update_zones()
                    runIn(60 * 60 - atomicState.vent_runtime, vent_deadline_reached, [misfire: "ignore"])
                    break
                case "Heating":
                case "HeatingL":
                case "Cooling":
                case "CoolingL":
                    atomicState.vent_started = now()
                    set_vent_state("Running")
                    V.on()
                    runIn(atomicState.vent_runtime, vent_runtime_reached, [misfire: "ignore"])
                    break
            }
    }
}

def vent_force_activated(evt=NULL) {
    // log.debug("In vent_force_activated()")
    unschedule(vent_runtime_reached)
    unschedule(vent_deadline_reached)
    // update runtime so far for use when forced ventilation stops
    Integer runtime
    switch ("$atomicState.vent_state") {
        case "End_Phase":
        case "Running":
            runtime = atomicState.vent_runtime - (now() - atomicState.vent_started) / 1000
            break
        case "Complete":
            runtime = 60*60 // actually less but this works
            break
        case "Off":
            runtime = 0
            break
        case "Forced":
        case "Waiting":
            runtime = atomicState.vent_runtime
            break
    }
    atomicState.vent_runtime = runtime
    set_vent_state("Forced")
    atomicState.vent_started = now()
    V.on()
    switch ("$vent_type") {
        case "Requires Blower":
            update_fan_state()
            update_zones()
            break
    }
}

def vent_force_deactivated(evt=NULL) {
    // log.debug("In vent_force_deactivated()")
    def switchstate = vent_control.currentState("switch")
    if (switchstate.value == "on") {
        if (atomicState.vent_started > atomicState.vent_interval_start) {
            // still in same interval as when force ventilation started
            Integer runtime = atomicState.vent_runtime
            runtime -= (now() - atomicState.vent_started) / 1000
            atomicState.vent_runtime = runtime
        } else {
            // we have been in forced ventilation for the entire interval up to this point
            def levelstate = vent_control.currentState("level")
            Integer percent = levelstate.value as Integer
            Integer runtime = 60 * 60 * percent / 100
            runtime -= (now() - atomicState.vent_interval_start) / 1000
            atomicState.vent_runtime = runtime
        }
        if (atomicState.vent_runtime <= 0) {
            set_vent_state("Complete")
            V.off()
            switch ("$vent_type") {
                case "Requires Blower":
                    update_fan_state()
                    update_zones()
            }
        } else {
            switch ("$vent_type") {
                case "Doesn't Require Blower":
                    V.on()
                    set_vent_state("Running")
                    runIn(atomicState.vent_runtime, vent_runtime_reached, [misfire: "ignore"])
                    break;
                case "Requires Blower":
                    switch ("$atomicState.equip_state") {
                        case "Idle":
                        case "IdleH":
                        case "PauseH":
                        case "IdleC":
                        case "PauseC":
                            log.debug("vent off - Waiting")
                            set_vent_state("Waiting")
                            V.off()
                            update_fan_state()
                            update_zones()
                            Integer deadline = (atomicState.vent_interval_end - now()) / 1000 - atomicState.vent_runtime
                            runIn(deadline, vent_deadline_reached, [misfire: "ignore"])
                            break
                        case "Heating":
                        case "HeatingL":
                        case "Cooling":
                        case "CoolingL":
                            log.debug("vent on - Running")
                            atomicState.vent_started = now()
                            set_vent_state("Running")
                            V.on()
                            runIn(atomicState.vent_runtime, vent_runtime_reached, [misfire: "ignore"])
                    }
                    break;
            }
        }
    } else {
        set_vent_state("Off")
        V.off()
        switch ("$vent_type") {
            case "Requires Blower":
                update_fan_state()
                update_zones()
        }
    }
}

def vent_control_activated(evt=NULL) {
    // log.debug("In vent_control_activated()")
    // schedule vent intervals every hour with the first one starting 5 seconds from now
    Long time = now() + 5000
    Long seconds = time / 1000
    Long minutes = seconds / 60
    Long hours = minutes / 60
    seconds = seconds - (minutes * 60)
    minutes = minutes - (hours * 60)
    schedule_str = "$seconds $minutes * ? * *"
    schedule(schedule_str, start_vent_interval)
    // In 5 seconds, start_vent_interval() will do the work of putting everything into the correct state
}

def vent_control_deactivated(evt=NULL) {
    // log.debug("In vent_control_deactivated()")
    unschedule(vent_runtime_reached)
    unschedule(vent_deadline_reached)
    unschedule(start_vent_interval)
    if ("$atomicState.vent_state" == "Forced") {
        // log.debug("Still in forced vent state")
    } else {
        set_vent_state("Off")
        V.off()
        switch ("$vent_type") {
            case "Requires Blower":
                update_fan_state()
                update_zones()
        }
    }
}

def vent_runtime_reached() {
    // log.debug("In vent_runtime_reached()")
    set_vent_state("Complete")
    V.off()
    switch ("$vent_type") {
        case "Requires Blower":
            update_fan_state()
            update_zones()
    }
}

def vent_deadline_reached() {
    // log.debug("In vent_deadline_reached()")
    set_vent_state("End_Phase")
    V.on()
    switch ("$vent_type") {
        case "Requires Blower":
            update_fan_state()
            update_zones()
    }
}

def equipment_off_adjust_vent() {
    // this is called any time that the heating and cooling equipment transitions from on to off
    // it may also be called while equipment is off, such as extraneous call from mode_change_delay
    // it should not be called with equipment on, although the blower may be on serving a fan only call
    // log.debug("In equipment_off_adjust_vent()")
    switch ("$atomicState.equip_state") {
        case "Heating":
        case "HeatingL":
        case "Cooling":
        case "CoolingL":
            status_device = getChildDevice("HVACZoning_${app.id}")
            status_device.debug("equipment_off_adjust_vent() should not be called with equipment running")
            log.debug("equipment_off_adjust_vent() should not be called with equipment running")
            return
    }
    switch ("$vent_type") {
        case "Doesn't Require Blower":
        case "None":
            break
        case "Requires Blower":
            switch ("$atomicState.vent_state") {
                case "Complete":
                case "Waiting":
                case "Off":
                case "End_Phase":
                case "Forced":
                    break;
                case "Running":
                    set_vent_state("Waiting")
                    unschedule(vent_runtime_reached)
                    V.off()
                    Integer runtime = atomicState.vent_runtime - (now() - atomicState.vent_started) / 1000
                    atomicState.vent_runtime = runtime
                    Integer deadline = (atomicState.vent_interval_end - now()) / 1000 - runtime
                    runIn(deadline, vent_deadline_reached, [misfire: "ignore"])
                    break;
            }
    }
}

def equipment_on_adjust_vent() {
    // this is called any time that the heating and cooling equipment transitions from off to on
    // log.debug("In equipment_on_adjust_vent()")
    switch ("$vent_type") {
        case "Doesn't Require Blower":
        case "None":
            break;
        case "Requires Blower":
            switch ("$atomicState.vent_state") {
                case "End_Phase":
                case "Complete":
                case "Running":
                case "Off":
                case "Forced":
                    break;
                case "Waiting":
                    set_vent_state("Running")
                    atomicState.vent_started = now()
                    unschedule(vent_deadline_reached)
                    V.on()
                    update_fan_state()
                    runIn(atomicState.vent_runtime, vent_runtime_reached, [misfire: "ignore"])
                    break;
            }
    }
}

// These routeins handle an over-pressure signal.  The zones are each instructed to adapt their capacity to avoid getting into over-pressure in the future
// If stage 2 is operating, it is turned off and over_pressure_stage2() is scheduled for 60 seconds later in case that is not sufficient. If stage 1
// only is operating, over_pressure_stage2() is called right away.  over_pressure_stage2() opens all of the zones, regardless of which ones are calling.

def over_pressure_stage1(evt=NULL) {
    // log.debug("In over_pressure_stage1()")
    // Handle over-pressure signal
    if (now() - atomicState.over_pressure_time < 2*60*1000) { // don't respond to more than one over pressure per two minutes
        return
    }
    atomicState.over_pressure_time = now()
    def zones = getChildApps()
    zones.each { z ->
        z.handle_overpressure()
    }
    child_updated()
    switch ("$atomicState.equip_state") {
        case "Heating":
        case "HeatingL":
            switch ("$heat_type") {
                case "Two stage":
                    def currentvalue = W2.currentValue("switch")
                    switch ("$currentvalue") {
                        case "on":
                        stage2_heat_off()
                        runIn(60, over_pressure_stage2, [misfire: "ignore"])
                        return
                    }
            }
            break
        case "Cooling":
        case "CoolingL":
            switch ("$cool_type") {
                case "Two stage":
                    def currentvalue = Y2.currentValue("switch")
                    switch ("$currentvalue") {
                        case "on":
                        stage2_cool_off()
                        runIn(60, over_pressure_stage2, [misfire: "ignore"])
                        return
                    }
            }
            break
    }
    over_pressure_stage2()
}

def over_pressure_stage2() {
    // log.debug("In over_pressure_stage2()")
    // if overpressure still persists, open all zones
    def currentvalue = over_pressure.currentValue("switch")
    switch ("$currentvalue") {
        case "on":
        def zones = getChildApps()
        zones.each { z ->
            z.turn_on()
        }
    }
}

def get_equipment_status() {
    switch ("$atomicState.equip_state") {
        case "Idle":
        case "IdleH":
        case "PauseH":
        case "IdleC":
        case "PauseC":
            switch ("$atomicState.fan_state") {
                case "On_for_cooling":
                case "On_for_vent": 
                case "On_for_dehum": 
                case "On_by_request":
                    return "fan only"
                case "Off":
                    return "idle"
            }
        case "Heating":
        case "HeatingL":
            return "heating"
        case "Cooling":
        case "CoolingL":
            return "cooling"
    }
}

Boolean subzone_ok(String parent_zone, String child_zone) {
    // log.debug("checking whether $child_zone can be a subzone of $parent_zone")
    if (child_zone == parent_zone) { return false }
    if (child_zone == app.label) { return false }
    Boolean result = true
    def zones = getChildApps()
    zones.each { z ->
        if (z.label != parent_zone) {
            if (z.has_subzone(child_zone)) { result = false }
        }
    }
    return result
}

Number get_min_heat_flow() {
    switch ("$equip_type") {
        case "Furnace only":
        case "Furnace and Air Conditioning":
            if (atomicState.off_capacity) {
                return cfmW1 - atomicState.off_capacity
            } else {
                return cfmW1
            }
            break
        default:
            return 0
    }
}

Number get_min_cool_flow() {
    switch ("$equip_type") {
        case "Air Conditioning only":
        case "Furnace and Air Conditioning":
            if (atomicState.off_capacity) {
                return cfmY1 - atomicState.off_capacity
            } else {
                return cfmY1
            }
            break
        default:
            return 0
    }
}
