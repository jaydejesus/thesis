/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */

package applications;

import java.util.List;
import java.util.Map;
import java.awt.geom.Line2D;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Random;

import report.TrafficAppReporter;
import core.Application;
import core.Coord;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import core.SimScenario;
import core.World;

import gui.playfield.MapGraphic;
import movement.map.SimMap;
import movement.map.MapNode;
import java.util.HashMap;

/**
 * Simple ping application to demonstrate the application support. The 
 * application can be configured to send pings with a fixed interval or to only
 * answer to pings it receives. When the application receives a ping it sends
 * a pong message in response.
 * 
 * The corresponding <code>TrafficAppReporter</code> class can be used to record
 * information about the application behavior.
 * 
 * @see TrafficAppReporter
 * @author teemuk
 */
public class TrafficApp extends Application{
	/** Ping generation interval */
	public static final String TRAFFIC_INTERVAL = "interval";
	/** Ping interval offset - avoids synchronization of ping sending */
	public static final String TRAFFIC_OFFSET = "offset";
	/** Destination address range - inclusive lower, exclusive upper */
	public static final String TRAFFIC_DEST_RANGE = "destinationRange";
	/** Seed for the app's random number generator */
	public static final String TRAFFIC_SEED = "seed";
	/** Size of the ping message */
	public static final String TRAFFIC_MESSAGE_SIZE = "pingSize";
	
	/** Application ID */
	public static final String APP_ID = "fi.tkk.netlab.TrafficApp";
	
	private static boolean doneRoadSegmentation = false;
	// Private vars
	private double	lastAppUpdate = 0;
	private double	appUpdateInterval = 100;
	private int		seed = 0;
	private int		destMin=0;
	private int		destMax=1;
	private int		appMsgSize=1;
	private Random	rng;
	private List<Message> msgs_list;
	
	private Line2D myRoadSegment;
	private static List<Line2D> roadSegments;
	private List<Coord> road_segments;
	private static HashMap<Coord, List<Coord>> segmentsHashMap;
	
	
	/** 
	 * Creates a new ping application with the given settings.
	 * 
	 * @param s	Settings to use for initializing the application.
	 */
	public TrafficApp(Settings s) {
		if (s.contains(TRAFFIC_INTERVAL)){
			this.appUpdateInterval = s.getDouble(TRAFFIC_INTERVAL);
		}
		if (s.contains(TRAFFIC_OFFSET)){
			this.lastAppUpdate = s.getDouble(TRAFFIC_OFFSET);
		}
		if (s.contains(TRAFFIC_SEED)){
			this.seed = s.getInt(TRAFFIC_SEED);
		}
		if (s.contains(TRAFFIC_MESSAGE_SIZE)) {
			this.appMsgSize = s.getInt(TRAFFIC_MESSAGE_SIZE);
		}
		if (s.contains(TRAFFIC_DEST_RANGE)){
			int[] destination = s.getCsvInts(TRAFFIC_DEST_RANGE,2);
			this.destMin = destination[0];
			this.destMax = destination[1];
		}
		
		rng = new Random(this.seed);
		super.setAppID(APP_ID);
	}
	
	/** 
	 * Copy-constructor
	 * 
	 * @param a
	 */
	public TrafficApp(TrafficApp a) {
		super(a);
		this.lastAppUpdate = a.getLastPing();
		this.appUpdateInterval = a.getInterval();
		this.destMax = a.getDestMax();
		this.destMin = a.getDestMin();
		this.seed = a.getSeed();
		this.appMsgSize = a.getAppMsgSize();
		this.rng = new Random(this.seed);
		this.msgs_list = new ArrayList<Message>();
		this.roadSegments = new ArrayList<Line2D>();
		this.segmentsHashMap = new HashMap<Coord, List<Coord>>();
		this.road_segments = new ArrayList<Coord>();
	}
	
	/** 
	 * Handles an incoming message. If the message is a ping message replies
	 * with a pong message. Generates events for ping and pong messages.
	 * 
	 * @param msg	message received by the router
	 * @param host	host to which the application instance is attached
	 */
	@Override
	public Message handle(Message msg, DTNHost host) {
		String type = (String)msg.getProperty("type");

		try {
			 if (type==null) return msg;
				if (msg.getTo()==host && type.equalsIgnoreCase("traffic")) {
					if(msgs_list!=null) {
						try {
							for(Message mm : msgs_list) {
								if(msg.getFrom().equals(mm.getFrom()))
									msgs_list.remove(mm);
								
							}
						}catch(Exception e) {
							
						}
					}
					msgs_list.add(msg);
					deleteExpiredMsgs(msgs_list);
//					System.out.println(SimClock.getIntTime() + " I am " + host + " " + this.myRoadSegment + ". "+ host.getPath().getSpeed() 
//							+ "Msg list contain/s " + msgs_list.size() + ". ");
					for(int i = 0; i < msgs_list.size(); i++) {
						Message m = msgs_list.get(i);
						Line2D l = (Line2D) m.getProperty("myRoad");
						Line2D mine = getMyRoadSegment();
						
						if(m.getTtl()>0) {
							if(isInSameRoad(mine, l)) {
								//compute average on that road
								System.out.println("I am " + host + ". " + m.getFrom() + " " + l + " is on my road");
								System.out.println("L: " + l.getX1() + ", " + l.getY1() + ", " + l.getX2() + ", " + l.getY2());
								System.out.println(host + ": " + mine.getX1() + ", " + mine.getY1() + ", " + mine.getX2() + ", " + mine.getY2());
							}
						}
//							System.out.println("A msg from " + m.getFrom() + "-ttl: "+ m.getTtl()+ ": " + m.getProperty("location") + ", " + m.getProperty("speed") + " " 
//						+ "road: (" + l.getX1() + ", " + l.getY1() + ") , (" + l.getX2() + ", " + l.getY2() + ")");
					}
					
					super.sendEventToListeners("GotPing", null, host);
				}				
		 }catch(Exception e) {			 
		 }		
		return msg;
	}

	private boolean isInSameRoad(Line2D mine, Line2D l) {
		if(round(l.getX1()) == round(mine.getX1()) && round(l.getY1()) == round(mine.getY1()) 
				&& round(l.getX2()) == round(mine.getX2())&& round(l.getY2()) == round(mine.getY2())) {
			return true;
		}
		else
			return false;
	}

	private void deleteExpiredMsgs(List<Message> msgs_list) {
		for(Message m : msgs_list) {
			if(m.getTtl() <= 0)
				msgs_list.remove(m);
		}
	}

	/** 
	 * Draws a random host from the destination range
	 * 
	 * @return host
	 */
	private DTNHost randomHost() {
		int destaddr = 0;
		if (destMax == destMin) {
			destaddr = destMin;
		}
		destaddr = destMin + rng.nextInt(destMax - destMin);
		World w = SimScenario.getInstance().getWorld();
		return w.getNodeByAddress(destaddr);
	}
	
	@Override
	public Application replicate() {
		return new TrafficApp(this);
	}

	@Override
	public void update(DTNHost host) {
		
		double curTime = SimClock.getTime();
		Line2D myroad;

		if(!this.doneRoadSegmentation) {
			try {
				initRoadSegments();
			} catch (IOException e1) {

			}
		}
		
		try {
			getMyRoad(host, host.getLocation(), host.getCurrentDestination());
			if (host.getConnections().get(0).isUp()) {
				
				if (curTime - this.lastAppUpdate >= this.appUpdateInterval) {
					
					// Time to send a new ping
					String id = "traffic";
					String idd = SimClock.getIntTime() + "-" + host.getAddress() + "Host"+ host+" "+host.getLocation()+": " + host.getPath().getSpeed();
					
					Message m = new Message(host, randomHost(), id+idd, getAppMsgSize());
					m.addProperty("type", id);
					m.addProperty("location", host.getLocation());
					m.addProperty("speed", host.getPath().getSpeed());
					m.addProperty("myRoad", this.myRoadSegment);
					
					
					m.setAppID(APP_ID);
					host.createNewMessage(m);

					super.sendEventToListeners("SentPing", null, host);
					
					this.lastAppUpdate = curTime;
				}
			}
		}catch(Exception e) {

		}
	}
		
	public boolean isOnTheLine(Line2D l, Coord c) {
		double x1, x2, y1, y2;

		x1 = l.getX1();
		x2 = l.getX2();
		y1 = l.getY1();
		y2 = l.getY2();

		double m = (y2-y1)/(x2-x1);
		double b = y1 - (m * x1);
		//System.out.println(round(c.getY()) + " and " + round((m * c.getX())+b));
		if(round(c.getY()) == round((m * c.getX())+b)) {
//			System.out.println("Found on : (" + l.getX1() + ", " + l.getY1() + ") , (" + l.getX2() + ", " + l.getY2() + ")");
//			System.out.println();
			return true;
		}
		else {
//			System.out.println("NOT FOUND on : (" + l.getX1() + ", " + l.getY1() + ") , (" + l.getX2() + ", " + l.getY2() + ")");
//			System.out.println();
			return false;
		}
	}
	
	public Line2D getMyRoadSegment() {
		return this.myRoadSegment;
	}
	
	public Line2D getMyRoad(DTNHost h, Coord location, Coord waypoint) {
		Line2D myRoad = null;
		
		if(!this.segmentsHashMap.get(waypoint).equals(null)) {
			{
				for(Coord c : this.segmentsHashMap.get(waypoint)) {
					//myRoad = new Line2D.Double(round(waypoint.getX()), round(waypoint.getY()), round(c.getX()), round(c.getY()));
					myRoad = new Line2D.Double(waypoint.getX(), waypoint.getY(), c.getX(), c.getY());
					//System.out.println("Line : " + waypoint + " ,, " + c);
					if(isOnTheLine(myRoad, location)) {
						break;
					}
				}
			}
			
		}
		this.myRoadSegment = myRoad;
		return this.myRoadSegment;
	}
	
	
	public void initRoadSegments() throws IOException{
		Coord c, c2;
		
		for(MapNode n : SimScenario.getInstance().getMap().getNodes()) {
			c = n.getLocation();
			this.road_segments = new ArrayList<Coord>();
			for(MapNode n2 : n.getNeighbors()) {
				
				c2 = n2.getLocation();
				this.road_segments.add(c2);
			}
			this.segmentsHashMap.put(c, this.road_segments);
		}
		this.doneRoadSegmentation = true;
	}
	
	public double round(double value) {
		return (double)Math.round(value * 100)/100;
	}

	
	/**
	 * @return the lastPing
	 */
	public double getLastPing() {
		return lastAppUpdate;
	}

	/**
	 * @param lastPing the lastPing to set
	 */
	public void setLastPing(double lastPing) {
		this.lastAppUpdate = lastPing;
	}

	/**
	 * @return the interval
	 */
	public double getInterval() {
		return appUpdateInterval;
	}

	/**
	 * @param interval the interval to set
	 */
	public void setInterval(double interval) {
		this.appUpdateInterval = interval;
	}

	/**
	 * @return the destMin
	 */
	public int getDestMin() {
		return destMin;
	}

	/**
	 * @param destMin the destMin to set
	 */
	public void setDestMin(int destMin) {
		this.destMin = destMin;
	}

	/**
	 * @return the destMax
	 */
	public int getDestMax() {
		return destMax;
	}

	/**
	 * @param destMax the destMax to set
	 */
	public void setDestMax(int destMax) {
		this.destMax = destMax;
	}

	/**
	 * @return the seed
	 */
	public int getSeed() {
		return seed;
	}

	/**
	 * @param seed the seed to set
	 */
	public void setSeed(int seed) {
		this.seed = seed;
	}

	/**
	 * @return the pingSize
	 */
	public int getAppMsgSize() {
		return this.appMsgSize;
	}

	/**
	 * @param pingSize the pingSize to set
	 */
	public void setPingSize(int size) {
		this.appMsgSize = size;
	}

}
