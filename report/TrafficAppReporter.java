/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */

package report;

import applications.PingApplication;
import applications.TrafficApplication;
import core.Application;
import core.ApplicationListener;
import core.DTNHost;

/**
 * Reporter for the <code>PingApplication</code>. Counts the number of pings
 * and pongs sent and received. Calculates success probabilities.
 * 
 * @author teemuk
 */
public class TrafficAppReporter extends Report implements ApplicationListener {
	
	private int requestSent=0, requestReceived=0;
	private int replySent=0, replyReceived=0;
	
	public void gotEvent(String event, Object params, Application app,
			DTNHost host) {
		// Check that the event is sent by correct application type
		if (!(app instanceof TrafficApplication)) return;
		
		// Increment the counters based on the event type
		if (event.equalsIgnoreCase("GotPing")) {
			requestReceived++;
		}
		if (event.equalsIgnoreCase("SentPong")) {
			replySent++;
		}
		if (event.equalsIgnoreCase("GotPong")) {
			replyReceived++;
		}
		if (event.equalsIgnoreCase("SentPing")) {
			requestSent++;
		}
		
	}

	
	@Override
	public void done() {
		write("Ping stats for scenario " + getScenarioName() + 
				"\nsim_time: " + format(getSimTime()));
		double pingProb = 0; // ping probability
		double pongProb = 0; // pong probability
		double successProb = 0;	// success probability
		
		if (this.requestSent > 0) {
			pingProb = (1.0 * this.requestReceived) / this.requestSent;
		}
		if (this.replySent > 0) {
			pongProb = (1.0 * this.replyReceived) / this.replySent;
		}
		if (this.requestSent > 0) {
			successProb = (1.0 * this.replyReceived) / this.requestSent;
		}
		
		String statsText = "pings sent: " + this.requestSent + 
			"\npings received: " + this.requestReceived + 
			"\npongs sent: " + this.replySent +
			"\npongs received: " + this.replyReceived +
			"\nping delivery prob: " + format(pingProb) +
			"\npong delivery prob: " + format(pongProb) + 
			"\nping/pong success prob: " + format(successProb)
			;
		
		write(statsText);
		super.done();
	}
}
