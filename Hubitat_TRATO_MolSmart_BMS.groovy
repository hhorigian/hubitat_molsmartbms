/**
 *  Hubitat - BMS MolSmart  Driver by VH - 
 *
 *  Copyright 2025 VH
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
 *        1.0 18/2/2025  - V.BETA 1 - Driver for MolSmart BMS - Modo Local. 
*
 */

metadata {
    definition(name: "MolSmart BMS Board Driver", namespace: "VH", author: "VH") {
        capability "Switch"
        capability "TemperatureMeasurement"
        capability "ContactSensor"
        
        command "Relay1_On"
        command "Relay1_Off"
        command "Relay2_On"
        command "Relay2_Off"
        command "ToggleRelay_1"
        command "ToggleRelay_2"
        
        attribute "Relay1", "string"
        attribute "Relay2", "string"
        attribute "DoorSensor", "string"
        attribute "Temperature", "number"
    }

    preferences {
        input name: "ipAddress", type: "text", title: "IP Address", description: "The IP address of the MolSmart BMS Board", required: true
        input name: "pollingInterval", type: "number", title: "Polling Interval (seconds)", description: "Interval to poll the board for status updates", defaultValue: 60
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
        
    }
}

def installed() {
    log.debug "Installed"
    initialize()
}

def updated() {
    log.debug "Updated"
    initialize()
}

def initialize() {
    log.debug "Initializing"
    unschedule() // Clear any existing schedules
    if (ipAddress) {
        // Schedule the poll method to run every 'pollingInterval' seconds
        if (pollingInterval) {
            def interval = pollingInterval as Integer
            if (interval > 0) {
                // Use runIn to schedule the poll method after the specified interval
                runIn(interval, poll)
            }
        } else {
            // Default to polling every 60 seconds if pollingInterval is not set
            runIn(60, poll)
        }
    }
}

def poll() {
    log.debug "Polling board status"
    getStatus()
    
    // Reschedule the poll method to run again after the pollingInterval
    if (pollingInterval) {
        def interval = pollingInterval as Integer
        if (interval > 0) {
            runIn(interval, poll)
        }
    } else {
        runIn(60, poll) // Default to 60 seconds
    }
}

def getStatus() {
    // Fetch sensor data (door sensor and temperature) from Status 10
    def sensorParams = [
        uri: "http://${ipAddress}/cm?cmnd=Status%2010",
        requestContentType: "application/json",
        contentType: "application/json"
    ]
    
    // Fetch relay statuses from Status 11
    def relayParams = [
        uri: "http://${ipAddress}/cm?cmnd=Status%2011",
        requestContentType: "application/json",
        contentType: "application/json"
    ]
    
    try {
        // Get sensor data
        httpGet(sensorParams) { sensorResp ->
            if (sensorResp.status == 200) {
                def sensorJson = sensorResp.data
                log.debug "Sensor Status Response: ${sensorJson}"
                
                // Update door sensor status
                if (sensorJson.StatusSNS?.Switch1) {
                    def doorStatus = sensorJson.StatusSNS.Switch1
                    sendEvent(name: "DoorSensor", value: doorStatus == "ON" ? "Open" : "Closed")
                } else {
                    log.warn "Door sensor status (Switch1) not found in response"
                }
                
                // Update temperature (from DS18B20)
                if (sensorJson.StatusSNS?.DS18B20?.Temperature) {
                    def temperature = sensorJson.StatusSNS.DS18B20.Temperature
                    sendEvent(name: "Temperature", value: temperature, unit: "C")
                } else {
                    log.warn "Temperature data (DS18B20.Temperature) not found in response"
                }
            } else {
                log.error "Failed to get sensor status: ${sensorResp.status}"
            }
        }
        
        // Get relay statuses
        httpGet(relayParams) { relayResp ->
            if (relayResp.status == 200) {
                def relayJson = relayResp.data
                log.debug "Relay Status Response: ${relayJson}"
                
                // Update relay 1 status (POWER1)
                if (relayJson.StatusSTS?.POWER1) {
                    def relay1Status = relayJson.StatusSTS.POWER1
                    sendEvent(name: "Relay1", value: relay1Status)
                } else {
                    log.warn "Relay 1 status (POWER1) not found in response"
                }
                
                // Update relay 2 status (POWER2)
                if (relayJson.StatusSTS?.POWER2) {
                    def relay2Status = relayJson.StatusSTS.POWER2
                    sendEvent(name: "Relay2", value: relay2Status)
                } else {
                    log.warn "Relay 2 status (POWER2) not found in response"
                }
            } else {
                log.error "Failed to get relay status: ${relayResp.status}"
            }
        }
    } catch (Exception e) {
        log.error "Exception in getStatus: ${e.message}"
    }
}

def relay1On() {
    sendCommand("Power1", "on")
}

def relay1Off() {
    sendCommand("Power1", "off")
}

def relay2On() {
    sendCommand("Power2", "on")
}

def relay2Off() {
    sendCommand("Power2", "off")
}

def toggleRelay1() {
    sendCommand("Power1", "toggle")
}

def toggleRelay2() {
    sendCommand("Power2", "toggle")
}

def sendCommand(command, value) {
    def params = [
        uri: "http://${ipAddress}/cm?cmnd=${command}%20${value}",
        requestContentType: "application/json",
        contentType: "application/json"
    ]
    
    try {
        httpGet(params) { resp ->
            if (resp.status == 200) {
                log.debug "Command ${command} ${value} sent successfully"
                poll() // Refresh status after command
            } else {
                log.error "Failed to send command: ${resp.status}"
            }
        }
    } catch (Exception e) {
        log.error "Exception in sendCommand: ${e.message}"
    }
}
