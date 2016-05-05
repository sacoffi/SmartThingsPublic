/**
 * Virtual Thermostat for 3 Speed Ceiling Fan Control 
 *  This smartapp provides automatic control of Low, Medium, High speeds of smart fan control device using 
 *  any temperature sensor. 
 *  It works best with @ChadCK custom device handler Z-Wave Smart Fan Control located here
 *  https://community.smartthings.com/t/z-wave-smart-fan-control-custom-device-type/25558
 *  along with the GE 12730 Z-Wave Smart Fan Control hardware. This smartapp was modified from the SmartThings
 *  Virtual Thermostat code which only allowed for on/off control of a switch. 
 **Thanks to @krlaframboise for his patient help and knowledge in solving poor coding by a first time coder.
 *
 *  Copyright 2016 Dale Coffing
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
 *
 *  Author: Dale Coffing
 *  Version: 20160505b
 *
 * Change Log
 * 2016-5-5b @krlaframboise change to bypasses the temperatureHandler method and calls the evaluate method
 *           with the current temperature and setpoint setting
 * 2016-5-5  autoMode added for manual override of auto control
 * 2016-5-4b cleaned debug logs, removed heat-cool selection, removed multiple stages
 * 2016-5-3  fixed error on not shutting down, huge shout out to my bro Stephen Coffing in the logic formation 
 * 
 * Consider/Feature Requests:
 *	-Is emergency setpoint function necessary
 *	-User adjustable temperature differentials for Low, Med, High
 *	-Global Mode check and/or virtual switch to disable app 
 *
 */
definition(
    name: "3 Speed Ceiling Fan Control",
    namespace: "dcoffing",
    author: "Dale Coffing",
    description: "Control a 3 Speed Ceiling Fan using Low, Medium, High speeds with any temperature sensor.",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/temp_thermo-switch.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/temp_thermo-switch@2x.png"
)

preferences {
	section("Choose a temperature sensor..."){
		input "sensor", "capability.temperatureMeasurement",
        	multiple:false, title: "Sensor", required: true
	}
    section("Select the Ceiling Fan Control/Dimmer..."){
		input "fanDimmer", "capability.switchLevel", 
	    	multiple: false, title: "Fan Control/Dimmer Switch...", required: true
	}
	section("Set the desired room setpoint temperature..."){
		input "setpoint", "decimal", title: "Set Temp"
	}
	section("When there's been movement from (optional, leave blank to not require motion)..."){
		input "motion", "capability.motionSensor", title: "Motion", required: false
	}
	section("Within this number of minutes..."){
		input "minutes", "number", title: "Minutes", required: false
	}
	section("But never go above this temperature value with or without motion..."){
		input "emergencySetpoint", "decimal", title: "Emer Temp", required: false
	}
	section("Select 'Auto' to enable control ('Off' is default)..."){
		input "autoMode", "enum", title: "Enable Ceiling Fan Control?", options: ["Off","Auto"], required: true
	}
}
def installed()
{
	subscribe(sensor, "temperature", temperatureHandler)
	if (motion) {
		subscribe(motion, "motion", motionHandler)
	}
}

def updated()
{
	unsubscribe()
	subscribe(sensor, "temperature", temperatureHandler)
	if (motion) {
		subscribe(motion, "motion", motionHandler)
	}
    handleTemperature(sensor.currentTemperature)
}

def temperatureHandler(evt){
    handleTemperature(evt.doubleValue)
}
def handleTemperature(temp) {
	def isActive = hasBeenRecentMotion()
	if (isActive || emergencySetpoint) {
		evaluate(temp, isActive ? setpoint : emergencySetpoint)
	}
	else {
     	fanDimmer.off()
        fanDimmer.setLevel(0) 

	}
}

def motionHandler(evt)
{
	if (evt.value == "active") {
		def lastTemp = sensor.currentTemperature
		if (lastTemp != null) {
			evaluate(lastTemp, setpoint)
		}
	} else if (evt.value == "inactive") {
		def isActive = hasBeenRecentMotion()
		log.debug "INACTIVE($isActive)"
		if (isActive || emergencySetpoint) {
			def lastTemp = sensor.currentTemperature
			if (lastTemp != null) {
				evaluate(lastTemp, isActive ? setpoint : emergencySetpoint)
			}
		}
		else {
     	    fanDimmer.off()
            fanDimmer.setLevel(0) 

		}
	}
}

private evaluate(currentTemp, desiredTemp)
{
log.debug "EVALUATE($currentTemp, $desiredTemp, $fanDimmer.currentSwitch)"
   // these are temp differentials desired from setpoint for Low, Medium, High fan speeds
    def LowDiff = 1.0 
    def MedDiff = 2.0
    def HighDiff = 3.0
	if (autoMode == "Auto") {
    		if (currentTemp - desiredTemp >= HighDiff) {
        	// turn on fan high speed
		log.debug "HIGH speed($currentTemp, $desiredTemp)"
       		fanDimmer.setLevel(90) 
      		}
        	    else if  (currentTemp - desiredTemp >= MedDiff) {
            	    // turn on fan medium speed
	      	    log.debug "MED speed($currentTemp, $desiredTemp)"
            	    fanDimmer.setLevel(60)
       		    }
            		 else if  (currentTemp - desiredTemp >= LowDiff) {
              		 // turn on fan low speed
	           	 log.debug "LOW speed($currentTemp, $desiredTemp)"
               			if (fanDimmer.currentSwitch == "off") { // if fan is OFF protect motor by  
               	  		fanDimmer.setLevel(90)                	// starting fan in High speed temporarily then 
                  		fanDimmer.setLevel(30, [delay: 3000]) 	// change to Low speed after 3 seconds 
          			}
               			else {
                  		fanDimmer.setLevel(30) 	//fan is already running, not necessary to protect motor
               			}			//set Low speed immediately
   		}
		else if (desiredTemp - currentTemp >= LowDiff) {   //below setpoint, turn off fan, zero level
			fanDimmer.off()
            fanDimmer.setLevel(0) 
		}
//	else {  //bypassing automatic control due to autoMode in OFF
//		 }
	}
}
private hasBeenRecentMotion()
{
	def isActive = false
	if (motion && minutes) {
		def deltaMinutes = minutes as Long
		if (deltaMinutes) {
			def motionEvents = motion.eventsSince(new Date(now() - (60000 * deltaMinutes)))
			log.trace "Found ${motionEvents?.size() ?: 0} events in the last $deltaMinutes minutes"
			if (motionEvents.find { it.value == "active" }) {
				isActive = true
			}
		}
	}
	else {
		isActive = true
	}
	isActive
}
