/*****************************************************************
JADE - Java Agent DEvelopment Framework is a framework to develop 
multi-agent systems in compliance with the FIPA specifications.
Copyright (C) 2000 CSELT S.p.A. 

GNU Lesser General Public License

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation, 
version 2.1 of the License. 

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the
Free Software Foundation, Inc., 59 Temple Place - Suite 330,
Boston, MA  02111-1307, USA.
*****************************************************************/

package projet_jade;

import jade.core.Agent;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

public class Taxi extends Agent {
	public final static int MIN_WORKING_DISTANCE = 10;
	public final static int MAX_WORKING_DISTANCE = 50;
	public final static int MAX_WAIT_BEFORE_MOVING = 20;
	
	/*private int posX;
	private int posY;*/
	private Position currPos;
	private boolean isAvailable = true;
	private int workingArea;
	private HashMap myClient;
	
	private Timer movingTimer; //timer avant que le taxi ne bouge dans une nouvelle location
	private Timer travelTimer;

  // Put agent initializations here
	protected void setup() {	
		try {
            // create the agent descrption of itself
            ServiceDescription sd = new ServiceDescription();
            sd.setType( "NuberTaxi" );
            sd.setName( "TaxiServiceDescription" );
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName( getAID() );
            dfd.addServices( sd );

            // register the description with the DF
            DFService.register( this, dfd );

            // notify the host that we have arrived
            ACLMessage hello = new ACLMessage( ACLMessage.INFORM );
            hello.setContent( NuberHost.TAXI);
            hello.addReceiver( new AID( "host", AID.ISLOCALNAME ) );
            send( hello );            
			
			currPos = NuberHost.getRandomPosition();
			workingArea = (int) (Math.random() * (MAX_WORKING_DISTANCE - MIN_WORKING_DISTANCE));
			
			hello = new ACLMessage( ACLMessage.INFORM );
            hello.addReceiver( new AID( "serviceClient", AID.ISLOCALNAME ) );
			//send hello to service client to be registred
			HashMap content = new HashMap();
			content.put("id", getAID());
			content.put("message", NuberHost.REGISTER_TAXI);
			content.put("position", currPos);
			content.put("workingArea", workingArea);
			content.put("isAvailable", isAvailable);
			hello.setContentObject(content);
            send( hello );
            waitingForClient();
			
            // add a Behaviour to process incoming messages
            addBehaviour( new CyclicBehaviour( this ) {
                            public void action() {
                                // listen if a greetings message arrives
                                ACLMessage msg = receive( MessageTemplate.MatchPerformative( ACLMessage.INFORM ) );
                                
                                if (msg != null) {
                                    if (NuberHost.GOODBYE.equals(msg.getContent())) {
                                        // time to go
                                        leaveParty();
                                    } else if (NuberHost.I_DONT_CHOOSE_YOU.equals(msg.getContent())) {
										System.out.println(msg.getSender().getName() + " didn't choose me");
										if (isAvailable)
											waitingForClient();
									} else {
                                    	HashMap content = null;
										try {
											content = (HashMap) msg.getContentObject();
										} catch (UnreadableException e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										}
										String message = "";
										if (content != null)
											message = (String) content.get("message");
										
                                    	if (NuberHost.NEED_A_TAXI.equals(message)) {
                                    		if (movingTimer != null) {
                                    			movingTimer.cancel();
                                    			movingTimer.purge();
                                    		}
                                    		imAvailable(content);
    									} else if (NuberHost.I_CHOOSE_YOU.equals(message)) {
    										System.out.println(msg.getSender().getName() + " have choose me");
    										if (isAvailable) {
    											//On accepte la course et on démarre un timer qui correspond au temps de trajet
    											isAvailable = false;
    											
    											myClient = content;
    											myClient.put("id", msg.getSender());
    											myClient.remove("message");
    											
    											//envoie d'un message au service client pour dire qu'on est plus dispo
    											ACLMessage rep = new ACLMessage( ACLMessage.INFORM );
    											System.out.print("( "+ getAID().getName() +" ) I have a client. I'm no longer available.");
    									        rep.setContent(NuberHost.IM_NOT_AVAILABLE);
    											rep.addReceiver(new AID("serviceClient", AID.ISLOCALNAME));
    									        send(rep);
    											//envoi d'un message au client pour dire qu'on démarre la course
    									        rep = new ACLMessage( ACLMessage.INFORM );
    											//System.out.print("( "+ getAID().getName() +" ) I have a client. I'm no longer available.");
    									        rep.setContent(NuberHost.STARTING_THE_DRIVE);
    											rep.addReceiver(msg.getSender());
    									        send(rep);
    									        
    									        //démarrer un timer correspondant au temps de trajet
    									        travelTimer = new Timer();
    									        travelTimer.schedule(new TimerTask() {
    												@Override
    												public void run() {
    													//on set la nouvelle position du taxi
    													currPos = (Position) myClient.get("destination");
    													
    													//on notifie au client qu'on est arrivé
    													AID cliId = (AID) myClient.get("id");
    	    											ACLMessage rep = new ACLMessage( ACLMessage.INFORM );
    	    											System.out.print("( "+ getAID().getName() +" ) Hey " + cliId.getName() + ", we have arrive!");
    	    									        rep.setContent(NuberHost.END_OF_THE_DRIVE);
    	    											rep.addReceiver(cliId);
    	    									        send(rep);
    													
    													//on notifie au service client qu'on est dispo mtn
    	    											rep = new ACLMessage( ACLMessage.INFORM );
    	    									        rep.setContent(NuberHost.IM_AVAILABLE);
    	    											rep.addReceiver(new AID("serviceClient", AID.ISLOCALNAME));
    	    									        send(rep);
    													
    													//trouver le moyen de démarre le movingTimer
    													waitingForClient();
    												}
    											}, 15 * 1000); //A CHANGER: METTRE LE TEMPS DE TRAJET CALCULER?
    										} else {
    											//on renvoie un message au client que l'on est plus dispo
    											//PAS FINI
    											ACLMessage rep = new ACLMessage( ACLMessage.INFORM );
    											//System.out.print("( "+ getAID().getName() +" ) I have a client. I'm no longer available.");
    									        rep.setContent(NuberHost.IM_NOT_AVAILABLE);
    											rep.addReceiver(msg.getSender());
    									        send(rep);
    										}
    											
    									} else {
    										System.out.println( "Taxi received unexpected message: " + msg );
    									}                                        
                                    }
                                }
                                else {
                                    // if no message is arrived, block the behaviour
                                    block();
                                }
                            }
                        } );
        }
        catch (Exception e) {
            System.out.println( "Saw exception in TaxiAgent: " + e );
            e.printStackTrace();
        }
	}

	// Put agent clean-up operations here
	protected void takeDown() {

	}
	
	// Internal implementation methods
    //////////////////////////////////s
	
	private void imAvailable(HashMap content) {
		ACLMessage rep = new ACLMessage( ACLMessage.INFORM );
		HashMap repContent = new HashMap();
		AID id = (AID) content.get("id");
		if (isAvailable) {
			System.out.print("( "+ getAID().getName() +" ) Hey " + id.getName() + ", i'm available!");
			repContent.put("message", NuberHost.IM_AVAILABLE);
		} else {
			System.out.print("( "+ getAID().getName() +" ) Hey " + id.getName() + ", i'm not available!");
			repContent.put("message", NuberHost.IM_NOT_AVAILABLE);
		}
		repContent.put("id", getAID());
        try {
			rep.setContentObject(repContent);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
        //System.out.println("1");
        rep.addReceiver(id);
        send(rep);
	}
	
	private void waitingForClient() { //réinitialise certaines variables
		movingTimer = new Timer();
		movingTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				currPos = NuberHost.getRandomPosition();
			}
		}, 0, MAX_WAIT_BEFORE_MOVING * 1000); 
	}
	
    /**
     * To leave the party, we deregister with the DF and delete the agent from
     * the platform.
     */
    protected void leaveParty() {
        try {
            DFService.deregister( this );
            doDelete();
        }
        catch (FIPAException e) {
            System.err.println( "Saw FIPAException while leaving party: " + e );
            e.printStackTrace();
        }
    }
}
