/*
   Virtual Thermostat for Evaporative Coolers 
   Copyright 2016 Dale Coffing
   
   This smartapp provides automatic control for Evaporative Coolers (single or two-speed) using 
   any temperature sensor. On a call for cooling the water pump is turned on and given two minutes
   to wet pads before fan low speed is enabled. The fan high speed is turned on if the temperature 
   continues to rise above the adjustable differential. There is an optional motion override.
   
   It requires these hardware devices; any temperature sensor, a switch for Fan On-Off, a switch
   for pump. For two speed control is desired another switch will be necessary.
   I suggest a Remotec ZFM-80 15amp relay for fan motor on-off, if you desired both pump and fan speed
   then Enerwave ZWN-RSM2 dual 10amp relays to control pump and the second relay to control hi-lo speed
   via Omoron LY1F SPDT 15amp relay. For only pump control any switch could work like the Enerwave ZWN-RSM1S
   or Monoprice #11989 Z-Wave In-Wall On/Off module
    
  Change Log
  2016-06-28 added submitOnChange for motion so to skip minutes input next if no motion selected
 			changed order of inputs for better logic flow
            added separate input page for Configuring Settings to reduce clutter on required inputs
            change to other mode techinque to see if it will force a reevaluate of methods
            renamed fanHiSpeed to fanSpeed for more generic use, added 0.0 on timer selection
            changed motion detector minutes input only if motion selected submitOnChange
  2016-06-22e added single speed default
  2016-06-22d change user guide content
  2016-06-22c modified icon to fan style, breeze style for comparison
  2016-06-22b moved pump input to first position (required), made other selections not required	for those with single speed motor
  2016-06-22 added icons
  2016-06-21 modify 3-speed-ceiling-fan-thermostat code for outlets

  
  Known Behavior from original Virtual Thermostat code
  -(fixed) when SP is updated, temp control isn't evaluated immediately, an event must trigger like change in temp, motion
  - if load is previously running when smartapp is loaded, it isn't evaluated immediately to turn off when SP>CT
 
   Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
   in compliance with the License. You may obtain a copy of the License at: www.apache.org/licenses/LICENSE-2.0
   Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
   for the specific language governing permissions and limitations under the License.

*/

definition(
    name: "Evap Cooler Thermostat",
    namespace: "dcoffing",
    author: "Dale Coffing",
    description: "Automatic control for an Evaporative Cooler with a 2-speed motor, water pump and any temp sensor.",
    category: "My Apps",
	iconUrl: "https://raw.githubusercontent.com/dcoffing/SmartThingsPublic/master/smartapps/dcoffing/evap-cooler-thermostat.src/ect125x125.png", 
   	iconX2Url: "https://raw.githubusercontent.com/dcoffing/SmartThingsPublic/master/smartapps/dcoffing/evap-cooler-thermostat.src/ect250x250.png",
	iconX3Url: "https://raw.githubusercontent.com/dcoffing/SmartThingsPublic/master/smartapps/dcoffing/evap-cooler-thermostat.src/ect250x250.png",
)

preferences {
	page(name: "mainPage")
    page(name: "optionsPage")
    page(name: "aboutPage")
}

def mainPage() {
	dynamicPage(name: "mainPage", title: "Select your devices and settings", install: true, uninstall: true) {
   	
    	section("Select a room temperature sensor to control the Evap Cooler..."){
			input "tempSensor", "capability.temperatureMeasurement",
        	multiple:false, title: "Temperature Sensor", required: true 
		}
    	section("Enter the desired room temperature (ie 72.5)..."){
			input "setpoint", "decimal", title: "Room Setpoint Temp", required: true
		}
    	section("Select the Evap Cooler fan motor switch hardware..."){
			input "fanMotor", "capability.switch", 
	    	multiple:false, title: "Fan Motor On-Off Control device", required: true
		}
		section("Optional Settings (Diff Temp, Timers, Motion, etc)") {
			href (name: "optionsPage", 
        	title: "Configure Optional settings", 
        	description: none,
        	image: "https://raw.githubusercontent.com/dcoffing/SmartThingsPublic/master/smartapps/dcoffing/evap-cooler-thermostat.src/settings250x250.png",
        	required: false,
        	page: "optionsPage"
        	)
        }
		section("Version Info, User's Guide") {
// VERSION
			href (name: "aboutPage", 
            title: "Evap Cooler Thermostat \n"+"Version: 1.0.160628 \n"+"Copyright © 2016 Dale Coffing",
            description: "Tap to get user's guide.",
            image: "https://raw.githubusercontent.com/dcoffing/SmartThingsPublic/master/smartapps/dcoffing/evap-cooler-thermostat.src/ect250x250.png",
            required: false,
            page: "aboutPage"
			)
   		}	
  	}
}

def optionsPage() {
	dynamicPage(name: "optionsPage", title: "Configure Optional Settings", install: false, uninstall: false) {
    
		section("Select the Evap Cooler Fan Speed switch control hardware (optional, leave blank for single speed)...."){
			input "fanSpeed", "capability.switch", 
	    	multiple:false, title: "Fan Hi-Lo Speed Control device", required: false
		}

		section("Enter the desired differential temp between fan speeds (default=1.0)..."){
			input "fanDiffTempString", "enum", title: "Fan Differential Temp", options: ["0.5","1.0","1.5","2.0"], required: false
		}
        
        section("Select the Evap Cooler pump switch hardware..."){
			input "fanPump", "capability.switch", 
	    	multiple:false, title: "Fan Pump On-Off Control device", required: false
		}
    	section("Enter the desired minutes to delay start of fan to allow for wetting of pads. (default=2.0)..."){
			input "fanDelayOnString", "enum", title: "Fan Delay On Timer", options: ["0.0","0.5","1.0","1.5","2.0","2.5"], required: false
		}
		section("Enable Evap Cooler thermostat only if motion is detected at (optional, leave blank to not require motion)..."){
			input "motionSensor", "capability.motionSensor", title: "Select Motion device", required: false, submitOnChange: true
		}
        
        if (motionSensor) {
			section("Turn off Evap Cooler when there's been no motion detected for..."){
				input "minutesNoMotion", "number", title: "Minutes?", required: true
			}
        }
		
        section("Select Evap Cooler operating mode desired (default to 'YES-Auto'..."){
			input "autoMode", "enum", title: "Enable Evap Cooler Thermostat?", options: ["NO-Manual","YES-Auto"], required: false
		}
        
        section ("Change SmartApp name, Mode selector") {
			label title: "Assign a name", required: false
			mode title: "Set for specific mode(s)", required: false
		}
        	
 	}
  }