/**
 * Virtual Thermostat for 3 Speed Ceiling Fan Control 
 *  This smartapp provides automatic control of Low, Medium, High speeds of a ceiling fan using 
 *  any temperature sensor with optional motion override. 
 *  It requires two hardware devices, any temperature sensor and 3-speed smart fan controller
 *  such as the GE 12730 Z-Wave Smart Fan Control hardware.
 *  It works well with @ChadCK custom device handler Z-Wave Smart Fan Control located here
 *  https://community.smartthings.com/t/z-wave-smart-fan-control-custom-device-type/25558
 *  This smartapp was modified from the SmartThings Virtual Thermostat code which only allowed
 *  for simple on/off control and not multiple fan stages. 
 *  Thanks to @krlaframboise for his patient help and knowledge in solving issues for a first time coder.
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
 *  Version: 0.9h
 *
 *   * Change Log
 * 2016-5-14 (h)Fan temperature differential variable
 * 2016-5-13 (g)replace ELSE IF for SWITCH statements on fan speeds
 * 2016-5-12 added new icons for 3SFC, colored text in 3SFC125x125.png and 3sfc250x250.png
 * 2016-5-6  (e)minor changes to text, labels, for clarity, (^^^e)default to NO-Manual for thermostat mode 
 * 2016-5-5c clean code, added current ver section header, allow for multiple fan controllers,
 *           replace icons to ceiling fan, modify name from Control to Thermostat
 * 2016-5-5b @krlaframboise change to bypasses the temperatureHandler method and calls the evaluate method
 *           with the current temperature and setpoint setting
 * 2016-5-5  autoMode added for manual override of auto control
 * 2016-5-4b cleaned debug logs, removed heat-cool selection, removed multiple stages
 * 2016-5-3  fixed error on not shutting down, huge shout out to my bro Stephen Coffing in the logic formation 
 * 
 * Consider/Feature Requests:
 *	-User adjustable temperature differential from setpoint for Low, Med, High speeds
 *	-Global Mode check and/or virtual switch to disable app 
 *
 */
definition(
    name: "3 Speed Ceiling Fan Thermostat",
    namespace: "dcoffing",
    author: "Dale Coffing",
    description: "Automatic control of a 3 Speed Ceiling Fan using Low, Medium, High speeds with any temperature sensor.",
    category: "My Apps",
	iconUrl: "https://raw.githubusercontent.com/dcoffing/SmartThingsPublic/master/smartapps/dcoffing/3-Speed-Fan-Control.src/3scft125x125.png", 
   	iconX2Url: "https://raw.githubusercontent.com/dcoffing/SmartThingsPublic/master/smartapps/dcoffing/3-Speed-Fan-Control.src/3scft250x250.png",
)

preferences {
	section("Select a temperature sensor to control the fan..."){
		input "sensor", "capability.temperatureMeasurement",
        	multiple:false, title: "Temperature Sensor", required: true 
	}
    section("Select the fan control hardware..."){
		input "fanDimmer", "capability.switchLevel", 
	    	multiple:false, title: "Fan Control device", required: true
	}
	section("Enter the desired room temperature (ie 72.5)..."){
		input "setpoint", "decimal", title: "Room Setpoint Temp", required: true
	}
    section("Enter the desired differential temp between fan speeds (default=1)..."){
		input "fanDiffTemp", "decimal", title: "Fan Differential Temp", required: false
	}
	section("When there's been movement from (optional, leave blank to not require motion)..."){
		input "motion", "capability.motionSensor", title: "Select Motion device", required: false
	}
	section("Within this number of minutes..."){
		input "minutes", "number", title: "Minutes", required: false
	}
	section("Select operating mode desired (defaulted 'Manual')..."){
		input "autoMode", "enum", title: "Enable Ceiling Fan Thermostat?", options: ["NO-Manual","YES-Auto"], required: true
	}
    section ("3 Speed Ceiling Fan Thermostat - Ver 0.9h") { }
}
def installed(){
	subscribe(sensor, "temperature", temperatureHandler)
	if (motion) {
		subscribe(motion, "motion", motionHandler)
	}
}

def updated(){
	unsubscribe()
	subscribe(sensor, "temperature", temperatureHandler)
	if (motion) {
		subscribe(motion, "motion", motionHandler)
	}					// @krlaframboise fix for setpoint changes to immediately act.
    handleTemperature(sensor.currentTemperature) //The change I recommended bypasses the temperatureHandler method 											
} 						 //and calls the evaluate method with the current temperature and
						//setpoint setting. If you want to execute the same code that the                                                
def temperatureHandler(evt){			//temperatureHandler method calls, you should break that code into					
    handleTemperature(evt.doubleValue)		 //a separate method.
}	//

def handleTemperature(temp) {			// method named handleTemperature 
	def isActive = hasBeenRecentMotion()
	if (isActive) {
		evaluate(temp, setpoint)
	}
	else {
     	fanDimmer.off()
        fanDimmer.setLevel(0)
	}
}

def motionHandler(evt){
	if (evt.value == "active") {
		def lastTemp = sensor.currentTemperature
		if (lastTemp != null) {
			evaluate(lastTemp, setpoint)
		}
	} else if (evt.value == "inactive") {  //testing to see if evt.value is indeed equal to "inactive" (vs setting evt.value to inactive
		def isActive = hasBeenRecentMotion() //define isActive local variable to returned true or false
		log.debug "INACTIVE($isActive)"
		if (isActive) {
			def lastTemp = sensor.currentTemperature
			if (lastTemp != null) {  // lastTemp not equal to null (value never been set) 
				evaluate(lastTemp, setpoint)
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
log.debug "EVALUATE($currentTemp, $desiredTemp, $fanDimmer.currentSwitch, $fanDimmer.currentLevel, $autoMode)"
   // these are temp differentials desired from setpoint for Low, Medium, High fan speeds
    def LowDiff = 1.0 
    def MedDiff = 2.0
    def HighDiff = 3.0
	if (autoMode == "YES-Auto") {
    	switch (currentTemp - desiredTemp) {
        	case { it  >= HighDiff }:
        		// turn on fan high speed
       			fanDimmer.setLevel(90) 
            	log.debug "HIGH speed($currentTemp, $desiredTemp, $fanDimmer.currentLevel)"
                break  //exit switch statement 
			case { it >= MedDiff }:
            	// turn on fan medium speed
            	fanDimmer.setLevel(60)
            	log.debug "MED speed($currentTemp, $desiredTemp, $fanDimmer.currentLevel)"
                break
       		case { it >= LowDiff }:
            	// turn on fan low speed
            	if (fanDimmer.currentSwitch == "off") { // if fan is OFF protect motor by  
            		fanDimmer.setLevel(90)                	// starting fan in High speed temporarily then 
                	fanDimmer.setLevel(30, [delay: 3000]) 	// change to Low speed after 3 seconds
                	log.debug "LOW speed after HI3secs($currentTemp, $desiredTemp, $fanDimmer.currentLevel)"
          		} else {
                	fanDimmer.setLevel(30) 	//fan is already running, not necessary to protect motor
            	}			           //set Low speed immediately
            	log.debug "LOW speed immediately($currentTemp, $desiredTemp, $fanDimmer.currentLevel)"
                break
			default:
            	// check to see if fan should be turned off
            	if (desiredTemp - currentTemp >= 0 ) {   //below or equal to setpoint, turn off fan, zero level
            		fanDimmer.off()  // fanDimmer.off required since .setLevel(0) doesn't execute
            		log.debug "below SP+Diff ($currentTemp, $desiredTemp, $fanDimmer.currentLevel)"
				} 
                log.debug "autoMode YES-MANUAL? else OFF($currentTemp, $desiredTemp,$fanDimmer.currentLevel, $autoMode)"
        } //end of switch statement
	} // end of if (automode...
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

