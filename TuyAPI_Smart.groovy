/*
TuyAPI SmartPlug Device Handler

Derived from
	Dave Gutheinz's TP-Link HS Series Device Handler

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at:
		http://www.apache.org/licenses/LICENSE-2.0
		
Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.


Supported models and functions:  This device supports smart plugs that use the Tuya Smart Life app

Update History
01-04-2018	- Initial creation
*/
metadata {
	definition (name: "TuyAPI", namespace: "bal", author: "Ben Lawson") {
		capability "Switch"
		capability "refresh"
		capability "polling"
		capability "powerMeter"
		capability "Sensor"
		capability "Actuator"
		command "setCurrentDate"
		attribute "monthTotalE", "string"
		attribute "monthAvgE", "string"
		attribute "weekTotalE", "string"
		attribute "weekAvgE", "string"
		attribute "engrToday", "string"
		attribute "dateUpdate", "string"
	}
	tiles(scale: 2) {
		standardTile("switch", "device.switch", width: 6, height: 4, canChangeIcon: true) {
			state "on", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#00a0dc",nextState:"turningOff"
			state "off", label:'${name}', action:"switch.on", icon:"st.switch.off", backgroundColor:"#ffffff",nextState:"waiting"
			state "turningOff", label:'waiting', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#15EE10",nextState:"waiting"
			state "waiting", label:'${name}', action:"switch.on", icon:"st.switches.switch.on", backgroundColor:"#15EE10",nextState:"on"
			state "offline", label:'Comms Error', action:"switch.on", icon:"st.switch.off", backgroundColor:"#e86d13",nextState:"waiting"
		}
		standardTile("refresh", "capability.refresh", width: 3, height: 2,  decoration: "flat") {
			state ("default", label:"Refresh", action:"refresh.refresh", icon:"st.secondary.refresh")
		}		 
		main("switch")
		details("switch", "power", "refresh")
	}
}
preferences {
	input("deviceIP", "text", title: "Device IP", required: true, displayDuringSetup: true)
	input("gatewayIP", "text", title: "Gateway IP", required: true, displayDuringSetup: true)
	input("deviceID", "text", title: "Device ID", required: true, displayDuringSetup: true)
	input("localKey", "text", title: "Local Key", required: true, displayDuringSetup: true)
}

def installed() {
	updated()
}

def updated() {
	unschedule()
	runEvery15Minutes(refresh)
	runIn(2, refresh)
}
//	----- BASIC PLUG COMMANDS ------------------------------------
def on() {
	sendCmdtoServer('{"system":{"set_relay_state":{"state": 1}}}', "deviceCommand", "onOffResponse")
}

def off() {
	sendCmdtoServer('{"system":{"set_relay_state":{"state": 0}}}', "deviceCommand", "onOffResponse")
}

def onOffResponse(response){
	if (response.headers["cmd-response"] == "TcpTimeout") {
		log.error "$device.name $device.label: Communications Error"
		sendEvent(name: "switch", value: "offline", descriptionText: "ERROR - OffLine - mod onOffResponse", isStateChange: true)
	}
	refresh()
}

//	----- REFRESH ------------------------------------------------
def refresh(){
	sendEvent(name: "switch", value: "waiting", isStateChange: true)
	sendCmdtoServer('{"system":{"get_sysinfo":{}}}', "deviceCommand", "refreshResponse")
}
def refreshResponse(response){
	if (response.headers["cmd-response"] == "TcpTimeout") {
		log.error "$device.name $device.label: Communications Error"
		sendEvent(name: "switch", value: "offline", descriptionText: "ERROR - OffLine - mod onOffResponse", isStateChange: true)
	} else {
		def cmdResponse = parseJson(response.headers["cmd-response"])
		def status = cmdResponse.system.get_sysinfo.relay_state
		if (status == 1) {
			status = "on"
		} else {
			status = "off"
		}
		log.info "${device.name} ${device.label}: Power: ${status}"
		sendEvent(name: "switch", value: status, isStateChange: true)
	}
}

//	----- SEND COMMAND DATA TO THE SERVER -------------------------------------
private sendCmdtoServer(command, hubCommand, action){
	def headers = [:] 
	headers.put("HOST", "$gatewayIP:8083")	//	SET TO VALUE IN JAVA SCRIPT PKG.
	headers.put("tuyapi-iot-ip", localKey)
	headers.put("tuyapi-iot-deviceID", deviceID)
	headers.put("tuyapi-iot-localKey", localKey)
	headers.put("tuyapi-command", command)
	headers.put("command", hubCommand)
	sendHubCommand(new physicalgraph.device.HubAction([
		headers: headers],
		device.deviceNetworkId,
		[callback: action]
	))
}