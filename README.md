# HVAC
Zoning and Ventilation Control via Hubitat

The HVAC Zoning App for Hubitat controls a Heating, Ventilation, and Air Conditioning system. 
•	Single and Two-stage Forced Air Heating and Cooling Equipment (Heat pumps not presently supported)
•	Zoned and Non-zoned duct systems, including systems with controllable registers and duct fans
•	Ventilation equipment, including ventilation equipment that utilizes the air handler blower
•	Humidifiers and Dehumidifiers

Why control the HVAC system from Hubitat?
•	Zone control hardware does not have access to as much information as a home automation system and therefore cannot take advantage of that information to control the HVAC system.
•	It is expensive to include a lot of options in zone control hardware, so available hardware solutions provide only a few configuration options. Software based solutions can offer much more flexibility and customization.

Requirements
•	A Hubitat-connected thermostat in each zone
•	Hubitat-connected switch devices to control the equipment (Zooz ZEN16 is recommended)

Will I freeze if something fails?
The software has a number of features to limit the impact of device failures or missed messages. Even if Hubitat goes completely offline, the system may be configured such that it reverts to operating as a single zone system.
