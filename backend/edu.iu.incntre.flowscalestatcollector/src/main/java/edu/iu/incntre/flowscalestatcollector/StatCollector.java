/**
 *Copyright 2012, InCNTRE. This file is licensed under Apache 2.0 *
 **/

package edu.iu.incntre.flowscalestatcollector;

import java.util.Calendar;
import java.util.List;

import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.util.HexString;

import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.iu.incntre.flowscale.FlowscaleController;
import edu.iu.incntre.flowscale.SwitchDevice;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import edu.iu.incntre.flowscale.exception.NoSwitchException;
import edu.iu.incntre.flowscale.util.JSONConverter;

import java.io.IOException;
import java.sql.*;

/**
 * The class responsible for polling all statistics from the switch and storing
 * them in a database
 * 
 * 
 * @author Ali Khalfan (akhalfan@indiana.edu)
 * 
 * **/

public class StatCollector {

	private boolean isQuery;
	private long intervalTime;
	private FlowscaleController flowscaleController;
	protected static Logger logger = LoggerFactory
			.getLogger(StatCollector.class);

	private String datapathIdStrings;

	private Connection conn;
	private Statement stat;
	private String databaseDriver;
	private String databaseClass;
	protected Thread statThread;


	
	private HashMap<Long,HashMap<Long,Long>> tempPortStatTransmittedHashMap = new HashMap<Long,HashMap<Long,Long>>();
	private HashMap<Long,HashMap<Long,Long>> tempPortStatReceivedHashMap = new HashMap<Long, HashMap<Long,Long>>();
	private HashMap<Long,HashMap<String,Long>> tempFlowStatHashMap = new HashMap<Long,HashMap<String,Long>>();
	
	private Calendar calendar;
	
	
	public void setIsQuery(boolean isQuery) {

		this.isQuery = isQuery;
	}

	public void setIntervalTime(long intervalTime) {
		this.intervalTime = intervalTime;
	}

	public void setFlowscaleController(FlowscaleController flowscaleController) {
		this.flowscaleController = flowscaleController;
	}

	public void setDatapathIdStrings(String datapathIdStrings) {

		this.datapathIdStrings = datapathIdStrings;

	}

	public void setDatabaseDriver(String databaseDriver) {
		this.databaseDriver = databaseDriver;
	}

	public void setDatabaseClass(String databaseClass) {
		this.databaseClass = databaseClass;
	}

	public void killThread() {

		this.statThread = null;
		//startUp();
	}

	public void startUp() {

		logger.trace("Startup of StatCollector");

		if (isQuery) {

			// initiate sqlite database

			try {

				Class.forName(databaseClass);
				conn = DriverManager.getConnection(databaseDriver, "root",
						"password");
				stat = conn.createStatement();
			} catch (ClassNotFoundException e2) {

				logger.error("{}", e2);
			} catch (SQLException e1) {
				// TODO Auto-generated catch block
				logger.error("{}", e1);
			}

			// end initiate database

			// start up thread

			statThread = new Thread(new Runnable() {

				@Override
				public void run() {

					logger.trace("Starting Thread ..");
					logger.trace("Getting flows from switch every {} seconds",
							intervalTime);

					List<OFStatistics> portStats;
					List<OFStatistics> flowStats;
					List<OFPhysicalPort> portStatus;
					SwitchDevice swd = null;
					String[] datapathIdStringElements = datapathIdStrings.split(",");
					try{
					
					while (statThread != null) {
						 calendar = Calendar.getInstance();
						logger.trace("getting flows from switches");
						for (String datapathIdString : datapathIdStringElements) {
							
							try {
								
							
							logger.info("Getting flows from switch {}",
									datapathIdString);

							swd = flowscaleController.getSwitchDevices().get(
									HexString.toLong(datapathIdString));
							if (swd == null) {
								logger.info("switch does not exist, is it connected?");
								continue;
							}

							try {
								portStats = flowscaleController
										.getSwitchStatisticsFromInterface(
												datapathIdString, "port");

								flowStats = flowscaleController
										.getSwitchStatisticsFromInterface(
												datapathIdString, "flow");

								portStatus = flowscaleController
										.getSwitchDevices()
										.get(HexString.toLong(datapathIdString))
										.getPortStates();

								if (flowStats != null && portStats != null) {

									String flowStatsJSON = JSONConverter
											.toStat(flowStats, "flow")
											.toJSONString();
									String portStatsJSON = JSONConverter
											.toStat(portStats, "port")
											.toJSONString();
									String portStatusJSON = JSONConverter
											.toPortStatus(portStatus)
											.toJSONString();

									
									//initialize or set hashmaps
									
									HashMap<Long,Long> tempPortStatTransmitted;
									HashMap<Long,Long> tempPortStatReceived;
									HashMap<String,Long> tempFlowStat;
									
									long datapathId = HexString.toLong(datapathIdString);
									if(tempPortStatTransmittedHashMap.get(datapathId) == null){
									
										tempPortStatTransmitted = new HashMap<Long,Long>();
										tempPortStatTransmittedHashMap.put(datapathId, tempPortStatTransmitted);
									}else{
										tempPortStatTransmitted = tempPortStatTransmittedHashMap.get(datapathId);
										
									}
									
									
									if(tempPortStatReceivedHashMap.get(datapathId) == null){
										tempPortStatReceived = new HashMap<Long,Long>();
										tempPortStatReceivedHashMap.put(datapathId, tempPortStatReceived);
									}else{
										tempPortStatReceived = tempPortStatReceivedHashMap.get(datapathId);
									}
									if(tempFlowStatHashMap.get(datapathId) == null){
										tempFlowStat = new HashMap<String,Long>();
										tempFlowStatHashMap.put(datapathId,tempFlowStat);
									}else{
										
										tempFlowStat = tempFlowStatHashMap.get(datapathId);
									}
									
									
									
									
									
									
									
									storeSwitchDetails(
											HexString.toLong(datapathIdString),
											portStatsJSON, flowStatsJSON,
											portStatusJSON, tempPortStatTransmitted,tempPortStatReceived,tempFlowStat );
								} else {
									logger.error(
											"Switch {} returned a null result possibility because the switch is not connected to the controller",
											datapathIdString);
								}
							} catch (NoSwitchException e1) {
								// TODO Auto-generated catch block
								logger.error(
										"Switch {} is not connected aborting",
										datapathIdString);
							} catch (IOException e1) {
								logger.error("IOException {}", e1);

							} catch (InterruptedException e1) {
								logger.error("Thread Interrupted {}", e1);
								killThread();
							} catch (ExecutionException e1) {
								logger.error("Execution Exception {}", e1);
							} catch (TimeoutException e1) {
								logger.error("Switch Timeout Exception {}", e1);
								killThread();

							}

						

							}catch(Exception e){
								logger.error("unchecked exception here {}",e);
								
								killThread();
								shutDown();
								Thread.yield();
								
						
							}
							
							
							
						}
						
						
						try {		

							Thread.sleep(intervalTime);

						} catch (InterruptedException e) {

							logger.error("{}", e);

							break;
						}

					}
					}catch(Exception e){
						logger.error("exception in while {}",e);
						shutDown();
						
					}
					
					try {
						conn.close();
					} catch (SQLException e) {
						// TODO Auto-generated catch block
						logger.error("{}", e);
					}
				}

				private void storeSwitchDetails(long datapathId,
						String portStats, String flowStats, String portStatus  , HashMap<Long,Long> tempPortStatTransmitted, HashMap<Long,Long> tempPortStatReceived, HashMap<String,Long> tempFlowStat) {

					Object obj = JSONValue.parse(portStats);
					JSONArray jsonArray = (JSONArray) obj;

					
					for (int i = 0; i < jsonArray.size(); i++) {

						JSONObject jsonObject = (JSONObject) jsonArray.get(i);
						long transmittedPackets = (Long) jsonObject
								.get("transmit_packets");
						long receivedPackets = (Long) jsonObject
								.get("receive_packets");
						
						
						
						long portId = (Long) jsonObject.get("port_id");
						
						
						//logger.info("the port is {}", portId);
						//logger.info("{} packets transmitted and {} packets received", receivedPackets,transmittedPackets);

						
						PreparedStatement prep = null;
						try {
							prep = null;
							if (conn != null) {
								prep = conn
										.prepareStatement("insert into port_stats values (?,?,?,?,?);");

							} else {

								logger.error("no connection object instantiated aborting .. ");
								return;
							}

							prep.setLong(1, datapathId);
							prep.setLong(2, calendar.getTimeInMillis());

							if (tempPortStatTransmitted.get(portId) != null) {

								long currentTransmittedPackets = transmittedPackets
										- tempPortStatTransmitted.get(portId);

								if (currentTransmittedPackets < 0) {

									prep.setLong(5, transmittedPackets);
								} else {

									prep.setLong(5, currentTransmittedPackets);
								}
							} else {

								prep.setLong(5, transmittedPackets);
							}

							tempPortStatTransmitted.put(portId,
									transmittedPackets);

							// take care of port received

							if (tempPortStatReceived.get(portId) != null) {

								long currentReceivedPackets = receivedPackets
										- tempPortStatReceived.get(portId);

								if (currentReceivedPackets < 0) {

									prep.setLong(4, receivedPackets);
								} else {

									prep.setLong(4, currentReceivedPackets);
								}
							} else {

								prep.setLong(4, receivedPackets);
							}

							tempPortStatReceived.put(portId, receivedPackets);

							prep.setLong(3, portId);
							prep.addBatch();

							conn.setAutoCommit(false);
							prep.executeBatch();
							conn.setAutoCommit(true);
						} catch (SQLException e) {

							logger.error("{}", e);
						} finally {
							if (prep != null) {
								try {
									prep.close();
								} catch (SQLException e) {
									// TODO Auto-generated catch block
									logger.error("{}", e);
								}
							}
						}
					}

					Object flowJSONobj = JSONValue.parse(flowStats);
					JSONArray flowJsonArray = (JSONArray) flowJSONobj;

					for (int i = 0; i < flowJsonArray.size(); i++) {

						JSONObject jsonObject = (JSONObject) flowJsonArray
								.get(i);
						long packets = (Long) jsonObject.get("packet_count");
						String matchString = (String) jsonObject.get("match");
						String action = (String) jsonObject.get("actions");
						PreparedStatement prep = null;

						try {
							prep = conn
									.prepareStatement("insert into flow_stats values (?,?,?,?,?);");
							prep.setLong(1, datapathId);
							prep.setLong(2, calendar.getTimeInMillis());

							if (tempFlowStat.get(matchString) != null) {

								long packetsReceived = packets
										- tempFlowStat.get(matchString);

								if (packetsReceived < 0) {

									prep.setLong(5, packets);
								} else {

									prep.setLong(5, packetsReceived);
								}
							} else {

								prep.setLong(5, packets);
							}

							tempFlowStat.put(matchString, packets);

							prep.setString(3, matchString);
							prep.setString(4, action);
							prep.addBatch();

							conn.setAutoCommit(false);
							prep.executeBatch();
							conn.setAutoCommit(true);
						} catch (SQLException e) {
							// TODO Auto-generated catch block
							logger.error("{}", e);
						} finally {
							if (prep != null) {
								try {
									prep.close();
								} catch (SQLException e) {
									// TODO Auto-generated catch block
									logger.error("{}", e);
								}
							}
						}

					}

					Object portStatusJSONobj = JSONValue.parse(portStatus);
					JSONArray portStatusJsonArray = (JSONArray) portStatusJSONobj;

					for (int i = 0; i < portStatusJsonArray.size(); i++) {
						byte portStatusValue = 0;
						JSONObject jsonObject = (JSONObject) portStatusJsonArray
								.get(i);
						long portId = (Long) jsonObject.get("port_id");
						String portAddress = (String) jsonObject
								.get("port_address");
						try{
						 portStatusValue = (byte) (Integer
								.parseInt(jsonObject.get("state").toString()) % 2);
						}catch(NumberFormatException nfe ){
							logger.error("{}",nfe);
							continue;
						}
						PreparedStatement prep = null;
						try {
							prep = conn
									.prepareStatement("insert into port_status	 values (?,?,?,?,?);");
							prep.setLong(1, datapathId);
							prep.setLong(2, calendar.getTimeInMillis());
							prep.setLong(3, portId);

							prep.setString(4, portAddress);

							prep.setByte(5, portStatusValue);
							prep.addBatch();

							conn.setAutoCommit(false);
							prep.executeBatch();
							conn.setAutoCommit(true);
						} catch (SQLException e) {
							// TODO Auto-generated catch block
							logger.error("{}", e);
						} finally {
							if (prep != null) {
								try {
									prep.close();
								} catch (SQLException e) {
									// TODO Auto-generated catch block
									logger.error("{}", e);
								}
							}
						}

					}

				}

			}, "Switch Stat Collector");
			statThread.start();

		}

	}

	public void shutDown() {

		statThread.interrupt();
		statThread =null;
		this.notify();
		
	}

	public String getName() {
		// TODO Auto-generated method stub
		return "statCollector";
	}
}