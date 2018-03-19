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
public class TrafficApplicationOrig extends Application{
	/** Run in passive mode - don't generate pings but respond */
	public static final String PING_PASSIVE = "passive";
	/** Ping generation interval */
	public static final String PING_INTERVAL = "interval";
	/** Ping interval offset - avoids synchronization of ping sending */
	public static final String PING_OFFSET = "offset";
	/** Destination address range - inclusive lower, exclusive upper */
	public static final String PING_DEST_RANGE = "destinationRange";
	/** Seed for the app's random number generator */
	public static final String PING_SEED = "seed";
	/** Size of the ping message */
	public static final String PING_PING_SIZE = "pingSize";
	/** Size of the pong message */
	public static final String PING_PONG_SIZE = "pongSize";
	
	/** Application ID */
	public static final String APP_ID = "fi.tkk.netlab.PingApplication";
	
	
	/** Heading FINAL*/
	public static final String TO_NORTH = "heading_to_north"; 
	public static final String TO_SOUTH = "heading_to_south";
	public static final String TO_EAST = "heading_to_east"; 
	public static final String TO_WEST = "heading_to_west";
	public static final String TO_NORTHEAST = "heading_to_northeast"; 
	public static final String TO_SOUTHEAST = "heading_to_southeast";
	public static final String TO_NORTHWEST = "heading_to_northwest"; 
	public static final String TO_SOUTHWEST = "heading_to_southwest";

	protected static double scale = 1;
	private static boolean doneRoadSegmentation = false;
	// Private vars
	private double	lastPing = 0;
	private double	interval = 100;
	private double locInterval = 1;
	private double lastLocUpdate = 0;
	private double ave_N;
	private double ave_S;
	private double ave_W;
	private double ave_E;
	private double ave_NW;
	private double ave_NE;
	private double ave_SW;
	private double ave_SE;
	private boolean passive = false;
	private int		seed = 0;
	private int		destMin=0;
	private int		destMax=1;
	private int		pingSize=1;
	private int		pongSize=1;
	private Random	rng;
	private List<Message> msgs_list;
	
	private Coord previousLocation;
	private Coord currentLocation;
	private String hostIsHeadingto;
	private Line2D myRoadSegment;
	private static List<Line2D> roadSegments;
	private List<Coord> road_segments;
	private static HashMap<Coord, List<Coord>> segmentsHashMap;
	
	
	/** 
	 * Creates a new ping application with the given settings.
	 * 
	 * @param s	Settings to use for initializing the application.
	 */
	public TrafficApplicationOrig(Settings s) {
		if (s.contains(PING_PASSIVE)){
			this.passive = s.getBoolean(PING_PASSIVE);
		}
		if (s.contains(PING_INTERVAL)){
			this.interval = s.getDouble(PING_INTERVAL);
		}
		if (s.contains(PING_OFFSET)){
			this.lastPing = s.getDouble(PING_OFFSET);
		}
		if (s.contains(PING_SEED)){
			this.seed = s.getInt(PING_SEED);
		}
		if (s.contains(PING_PING_SIZE)) {
			this.pingSize = s.getInt(PING_PING_SIZE);
		}
		if (s.contains(PING_PONG_SIZE)) {
			this.pongSize = s.getInt(PING_PONG_SIZE);
		}
		if (s.contains(PING_DEST_RANGE)){
			int[] destination = s.getCsvInts(PING_DEST_RANGE,2);
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
	public TrafficApplicationOrig(TrafficApplicationOrig a) {
		super(a);
		this.lastPing = a.getLastPing();
		this.interval = a.getInterval();
		this.passive = a.isPassive();
		this.destMax = a.getDestMax();
		this.destMin = a.getDestMin();
		this.seed = a.getSeed();
		this.pongSize = a.getPongSize();
		this.pingSize = a.getPingSize();
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
			 if (type==null) return msg; // Not a ping/pong message
				 
				// Respond if we're the recipient
				if (msg.getTo()==host && type.equalsIgnoreCase("ping")) {
					if(msgs_list!=null) {
						try {
							for(Message mm : msgs_list) {
								//add condition para maremove ghap kun it msg tikang mismo haim sarili pero ginforward la haim hin iba na node
								if(msg.getFrom().equals(mm.getFrom()))// && mm.getTtl() <= 0)
									msgs_list.remove(mm);
							}
						}catch(Exception e) {
							
						}
					}
					msgs_list.add(msg);
					
					for(int i = 0; i < msgs_list.size(); i++) {
						Message m = msgs_list.get(i);
						if(m.getTtl()>0)
							System.out.println("A msg from " + m.getFrom() + "-ttl: "+ m.getTtl()+ ": " + m.getProperty("location") + ", " + m.getProperty("speed") + " " + m.getProperty("heading"));
						else
							msgs_list.remove(m);
					}
					System.out.println(SimClock.getIntTime() + " I am " + host + ". "+ host.getPath().getSpeed() + this.hostIsHeadingto + "Msg list contain/s " + msgs_list.size() + ". ");
					getAverageSpeeds(msgs_list, host);
					System.out.println("North: " +this.ave_N + " South: " +this.ave_S + " West: " +this.ave_W + " East: " +this.ave_E);
					System.out.println("NEast: " +this.ave_NE + " NWest: " +this.ave_NW + " SWest: " +this.ave_SW + " SEast: " +this.ave_SE);
					System.out.println();
					// Send event to listeners
					super.sendEventToListeners("GotPing", null, host);
					//super.sendEventToListeners("SentPong", null, host);
				}				
		 }catch(Exception e) {			 
		 }		
		return msg;
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
		return new TrafficApplicationOrig(this);
	}

	/** 
	 * Sends a ping packet if this is an active application instance.
	 * 
	 * @param host to which the application instance is attached
	 */
	@Override
	public void update(DTNHost host) {
		
		double curTime = SimClock.getTime();
		Line2D myroad;
		this.currentLocation = host.getLocation().clone();
		if(!this.doneRoadSegmentation)
			try {
				initRoadSegments();
			} catch (IOException e1) {

			}
		if(this.previousLocation == null) {
			this.previousLocation = currentLocation; 
		}
		if(curTime - this.lastLocUpdate >= this.locInterval) {
			this.hostIsHeadingto = getHostHeading(host, this.currentLocation, this.previousLocation);
			this.lastLocUpdate = curTime;
			this.previousLocation = this.currentLocation;
		}
		getMyRoad(host, host.getLocation(), host.getCurrentDestination());
		try {
			
			if (host.getConnections().get(0).isUp()) {
				
				if (curTime - this.lastPing >= this.interval) {
					
					// Time to send a new ping
					String id = "ping";
					String idd = SimClock.getIntTime() + "-" + host.getAddress() + "Host"+ host+" "+host.getLocation()+": " + host.getPath().getSpeed() + 
							" " + this.hostIsHeadingto;
					
					Message m = new Message(host, randomHost(), id+idd, getPingSize());
					m.addProperty("type", id);
					m.addProperty("location", host.getLocation());
					m.addProperty("speed", host.getPath().getSpeed());
					m.addProperty("heading", this.hostIsHeadingto);
					m.addProperty("myPathCoords", host.getPath().getCoords());
					//m.addProperty("myRoadSegment", getMyRoad(host.getLocation(), host.getCurrentDestination()));
					
					
					m.setAppID(APP_ID);
					host.createNewMessage(m);
					
					// Call listeners
					super.sendEventToListeners("SentPing", null, host);
					
					this.lastPing = curTime;
				}
			}
		}catch(Exception e) {
			//e.printStackTrace();
		}
	}
	
	/**
	 * @return heading of nodes
	 * 
	 */
	public String getHostHeading(DTNHost h, Coord current, Coord previous) {
		String heading = "";
		
		if(((current.getX()-previous.getX()) == 0) && ((previous.getY()-current.getY()) > 0)) {
			heading = TO_NORTH;
		}else if(((current.getX()-previous.getX()) == 0) && ((previous.getY()-current.getY()) < 0)){
			heading = TO_SOUTH;
		}else if(((current.getX()-previous.getX()) < 0) && ((previous.getY()-current.getY()) == 0)) {
			heading = TO_WEST; 
		}else if(((current.getX()-previous.getX()) > 0) && ((previous.getY()-current.getY()) == 0)) {
			heading = TO_EAST; 
		}else if(((current.getX()-previous.getX()) < 0) && ((previous.getY()-current.getY()) > 0)) {
			heading = TO_NORTHWEST; 
		}else if(((current.getX()-previous.getX()) < 0) && ((previous.getY()-current.getY()) < 0)) {
			heading = TO_SOUTHWEST; 
		}else if(((current.getX()-previous.getX()) > 0) && ((previous.getY()-current.getY()) > 0)) {
			heading = TO_NORTHEAST; 
		}else if(((current.getX()-previous.getX()) > 0) && ((previous.getY()-current.getY()) < 0)) {
			heading = TO_SOUTHEAST; 
		}
		
		return heading;
	}
	
	public void getAverageSpeeds(List<Message> m, DTNHost h) {
		this.ave_N = 0;
		this.ave_S = 0;
		this.ave_W = 0;
		this.ave_E = 0;
		this.ave_NW = 0;
		this.ave_NE = 0;
		this.ave_SW = 0;
		this.ave_SE = 0;
		
		int Nctr = 0;
		int Sctr = 0;
		int Wctr = 0;
		int Ectr = 0;
		int NEctr = 0;
		int NWctr = 0;
		int SEctr = 0;
		int SWctr = 0;
		
		for(int i = 0; i < m.size(); i++) {
			//
				if(m.get(i).getProperty("heading").equals(TO_NORTH)) {
					if(Nctr == 0)
						this.ave_N = 0;
					this.ave_N = this.ave_N + (double) m.get(i).getProperty("speed");
					Nctr++;
				}else if(m.get(i).getProperty("heading").equals(TO_SOUTH)) {
					if(Sctr == 0)
						this.ave_S = 0;
					this.ave_S = this.ave_S + (double) m.get(i).getProperty("speed");
					Sctr++;
				}else if(m.get(i).getProperty("heading").equals(TO_WEST)) {
					if(Wctr == 0)
						this.ave_W = 0;
					this.ave_W = this.ave_W + (double) m.get(i).getProperty("speed");
					Wctr++;
				}else if(m.get(i).getProperty("heading").equals(TO_EAST)) {
					if(Ectr == 0)
						this.ave_E = 0;
					this.ave_E = this.ave_E + (double) m.get(i).getProperty("speed");
					Ectr++;
				}else if(m.get(i).getProperty("heading").equals(TO_NORTHEAST)) {
					if(NEctr == 0)
						this.ave_NE = 0;
					this.ave_NE = this.ave_NE + (double) m.get(i).getProperty("speed");
					NEctr++;
				}else if(m.get(i).getProperty("heading").equals(TO_NORTHWEST)) {
					if(NWctr == 0)
						this.ave_NW = 0;
					this.ave_NW = this.ave_NW + (double) m.get(i).getProperty("speed");
					NWctr++;
				}else if(m.get(i).getProperty("heading").equals(TO_SOUTHEAST)) {
					if(SEctr == 0)
						this.ave_SE = 0;
					this.ave_SE = this.ave_SE + (double) m.get(i).getProperty("speed");
					SEctr++;
				}else if(m.get(i).getProperty("heading").equals(TO_SOUTHWEST)) {
					if(SWctr == 0)
						this.ave_SW = 0;
					this.ave_SW = this.ave_SW + (double) m.get(i).getProperty("speed");
					SWctr++;
//				}
			}

			System.out.println("N average: " +this.ave_N + " , " + Nctr);
			System.out.println("S average: " +this.ave_S + " , " + Sctr);
			System.out.println("W average: " +this.ave_W + " , " + Wctr);
			System.out.println("E average: " +this.ave_E + " , " + Ectr);
			System.out.println("NW average: " +this.ave_NW + " , " + NWctr);
			System.out.println("NE average: " +this.ave_NE + " , " + NEctr);
			System.out.println("SW average: " +this.ave_SW + " , " + SWctr);
			System.out.println("SE average: " +this.ave_SE + " , " + SEctr);
			
			
			this.ave_N = this.ave_N/Nctr;
			this.ave_S = this.ave_S/Sctr;
			this.ave_W = this.ave_W/Wctr;
			this.ave_E = this.ave_E/Ectr;
			this.ave_NW = this.ave_NW/NWctr;
			this.ave_NE = this.ave_NE/NEctr;
			this.ave_SW = this.ave_SW/SWctr;
			this.ave_SE = this.ave_SE/SEctr;
		}
		//}
	}
	
	public boolean isOnTheLine(Line2D l, Coord c) {
		double x1, x2, y1, y2;
//		x1 = round(l.getX1());
//		x2 = round(l.getX2());
//		y1 = round(l.getY1());
//		y2 = round(l.getY2());
		x1 = l.getX1();
		x2 = l.getX2();
		y1 = l.getY1();
		y2 = l.getY2();
		//System.out.println("c is: " + c);
		double m = (y2-y1)/(x2-x1);
		double b = y1 - (m * x1);
		//System.out.println(round(c.getY()) + " and " + round((m * c.getX())+b));
		if(round(c.getY()) == round((m * c.getX())+b)) {
			System.out.println("Found on : (" + l.getX1() + ", " + l.getY1() + ") , (" + l.getX2() + ", " + l.getY2() + ")");
			System.out.println();
			return true;
		}
		else {
			System.out.println("NOT FOUND on : (" + l.getX1() + ", " + l.getY1() + ") , (" + l.getX2() + ", " + l.getY2() + ")");
			System.out.println();
			return false;
		}
	}
	
	public Line2D getMyRoad(DTNHost h, Coord location, Coord waypoint) {
		Line2D myRoad = null;
		System.out.println( h + " Finding : " + location + " : getting road segment using waypoint : " +waypoint);
		//System.out.println("hash contains " + waypoint + " ? " + this.segmentsHashMap.containsKey(waypoint));
		//System.out.println(this.segmentsHashMap.get(waypoint));
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
		return myRoad;
	}
	
	
	public void initRoadSegments() throws IOException{
		Coord c, c2;
		Line2D line_segment;
		List<Line2D> road_segments = null;
		int road = 0;
		int ctr = 0;
		
		for(MapNode n : SimScenario.getInstance().getMap().getNodes()) {
			c = n.getLocation();
			this.road_segments = new ArrayList<Coord>();
			for(MapNode n2 : n.getNeighbors()) {
				
				c2 = n2.getLocation();
				this.road_segments.add(c2);
				road++;
			}
			this.segmentsHashMap.put(c, this.road_segments);
			
		}
		FileWriter fileWriter = new FileWriter("segments.txt");
	    PrintWriter printWriter = new PrintWriter(fileWriter);
	    
		for(Map.Entry me : this.segmentsHashMap.entrySet()) {
			printWriter.println("Key: " + me.getKey() + " Values: " + this.segmentsHashMap.get(me.getKey()));
			ctr = ctr + this.segmentsHashMap.get(me.getKey()).size();
		}
		printWriter.close();
		System.out.println("Hash size: " + this.segmentsHashMap.size() + "segments: " + road + "total values: " + ctr);
		this.doneRoadSegmentation = true;
	}
	
	public double round(double value) {
		return (double)Math.round(value * 100)/100;
	}

	
	/**
	 * @return the lastPing
	 */
	public double getLastPing() {
		return lastPing;
	}

	/**
	 * @param lastPing the lastPing to set
	 */
	public void setLastPing(double lastPing) {
		this.lastPing = lastPing;
	}

	/**
	 * @return the interval
	 */
	public double getInterval() {
		return interval;
	}

	/**
	 * @param interval the interval to set
	 */
	public void setInterval(double interval) {
		this.interval = interval;
	}

	/**
	 * @return the passive
	 */
	public boolean isPassive() {
		return passive;
	}

	/**
	 * @param passive the passive to set
	 */
	public void setPassive(boolean passive) {
		this.passive = passive;
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
	 * @return the pongSize
	 */
	public int getPongSize() {
		return pongSize;
	}

	/**
	 * @param pongSize the pongSize to set
	 */
	public void setPongSize(int pongSize) {
		this.pongSize = pongSize;
	}

	/**
	 * @return the pingSize
	 */
	public int getPingSize() {
		return pingSize;
	}

	/**
	 * @param pingSize the pingSize to set
	 */
	public void setPingSize(int pingSize) {
		this.pingSize = pingSize;
	}

}
