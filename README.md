# HVAC
Zoning and Ventilation Control via Hubitat

The Hubitat App is HVAC_Zoning.groovy.
Each Zone is managed by a child App called HVAC_Zone.groovy.
Subzones are managed by a child App of HVAC_Zone.groovy called HVAC_SubZone.groovy.

An accessory program called Indirect_Thermostat.groovy is used to populate a virtual device that implements the Thermostat capability using up to three sensors that detect the 24VAC outputs of a physical thermostat. This accessory program is used when one or more of the thermostats in the system does not reliably and promptly report its thermostat operating state to Hubitat.

HVAC_Zoning_Status.groovy is the device handler for virtual devices which can either be the output of Indirect Thermostat or can be a status reporting object for HVAC_Zoning.groovy.
