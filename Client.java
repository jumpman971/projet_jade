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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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

public class Client extends Agent {
	public final static int MAX_WAIT_TIME = 60;
	public final static int MAX_WAIT_FOR_TAXI = 15;
	public final static int MIN_TAXI_SCORE = 0;
	public final static int MAX_TAXI_SCORE = 10;

	private Position currPos;
	private Position destPos;
	private HashMap myTaxis; //les taxis que l'on a pris
	private HashMap myTaxi; //le taxi dans lequel on se trouve (ou que l'on sera)
	
	private boolean waitingForResponse = false;
	private boolean waitingForTaxi = false;
	private Timer timer;
	private ArrayList<HashMap> tmpTaxi; //liste de taxi à choisir lors d'une demande de taxi
	private boolean oneTaxiIsInMyList = false; //utile lorsque l'on choisie un taxi (voir le code)
	
  // Put agent initializations here
	protected void setup() {	
		try {
            // create the agent descrption of itself
            ServiceDescription sd = new ServiceDescription();
            sd.setType( "NuberClient" );
            sd.setName( "ClientServiceDescription" );
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName( getAID() );
            dfd.addServices( sd );

            // register the description with the DF
            DFService.register( this, dfd );

            // notify the host that we have arrived
            ACLMessage hello = new ACLMessage( ACLMessage.INFORM );
            hello.setContent( NuberHost.CLIENT);
            hello.addReceiver( new AID( "host", AID.ISLOCALNAME ) );
            send( hello );
            
            myTaxis = new HashMap();
            currPos = NuberHost.getRandomPosition();
            timer = null;
			
            // add a Behaviour to process incoming messages
            addBehaviour( new CyclicBehaviour( this ) {
                            public void action() {
                                // listen if a greetings message arrives
                                ACLMessage msg = receive( MessageTemplate.MatchPerformative( ACLMessage.INFORM ) );

                                if (msg != null) {
                                	//System.out.println(msg.getSender().getName() + " got a message");
                                    if (NuberHost.GOODBYE.equals( msg.getContent() )) {
                                        // time to go
                                        leaveParty();
                                    } else if (NuberHost.IM_NOT_AVAILABLE.equals(msg.getContent())) { //on avait choisie un taxi mais il est plus dispo
                                    	if (myTaxi != null && myTaxi.get("id").equals(msg.getSender())) { //on vérifie que celui qui nous rep est bien le taxi qu'on avait choisie (pas sur que ça soit nécessaire)
                                    		myTaxi = null;
                                    		//on relance une demande de taxi
                                    		restartSendingMsg();
                                    	}
                                    } else if (NuberHost.END_OF_THE_DRIVE.equals(msg.getContent())) {
                                    	currPos = destPos;
                                    	destPos = null;
                                    	
                                    	restartSendingMsg();
                                    } else if (NuberHost.STARTING_THE_DRIVE.equals(msg.getContent())) {
                                    	//noter ses taxis? et attendre la fin du trajet via un message du taxi
                                    	if (myTaxis.get(msg.getSender()) == null) {
                                    		myTaxis.put(msg.getSender(), myTaxi);
                                    	}
                                    	//on note tous nos taxi
                                    	for (Iterator<HashMap> it = myTaxis.values().iterator(); it.hasNext();) {
                                    		HashMap taxi = it.next();
                                    		
                                    		taxi.put("score", (int) (Math.random() * (MAX_TAXI_SCORE - MIN_TAXI_SCORE)));
                                    	}
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
										
                                    	if (NuberHost.IM_AVAILABLE.equals(message)) {
                                    		if (waitingForResponse) {
                                    			waitingForResponse = false;
                                    			waitingForTaxi = true;
                                    			timer.cancel();
                                    			timer.purge();
                                    			timer = null;
                                    		}
                                    		
                                    		chooseATaxi(content);
    									} else {
    										System.out.println( "Client received unexpected message: " + msg );
    									}
                                    }
                                } else {
                                    // if no message is arrived, block the behaviour
                                    block();
                                }
                            }
                        } );
						
			// add a Behaviour for outgoing messages
            addBehaviour( new CyclicBehaviour( this ) {
                            public void action() {
								if (waitingForResponse || waitingForTaxi)
									block();
								
								int waitTime = (int) (Math.random() * (MAX_WAIT_TIME - 1));
								waitTime *= 1000;
								
								try {
									Thread.sleep(waitTime);
								} catch(InterruptedException ex) {
									Thread.currentThread().interrupt();
								}
								
								iNeedATaxi();
								
								waitingForResponse = true;
								timer = new Timer();
								timer.schedule(new TimerTask() {
									@Override
									public void run() {
										waitingForResponse = false;
										waitingForTaxi = false;
									}
								}, MAX_WAIT_FOR_TAXI * 1000);
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
    //////////////////////////////////
	
	private void iNeedATaxi() {
		destPos = NuberHost.getRandomPosition();
		
		ACLMessage msg = new ACLMessage( ACLMessage.INFORM );
		HashMap content = new HashMap();
		content.put("message", NuberHost.NEED_A_TAXI);
		content.put("position", currPos);
		content.put("destination", destPos);
		try {
			msg.setContentObject(content);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		msg.addReceiver( new AID("serviceClient", AID.ISLOCALNAME) );

		send(msg);
	}
	
	private void chooseATaxi(HashMap content) {
		if (timer == null) { //Démarrer compteur pour le choix de taxi dispo
			timer = new Timer();
			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					//System.out.println("test");
					//on choisie un taxi parmi la liste
					HashMap choosenTaxi = null;
					if (myTaxis.isEmpty() || !oneTaxiIsInMyList) {//si on a jamais prix de taxi ou qu'aucun de ceux dispo est dans ma liste
						int index = (int) (Math.random() * tmpTaxi.size());
						choosenTaxi = tmpTaxi.get(index);
						tmpTaxi.remove(index);
					} else { //on choisi le taxi qui a le plus de score parmi la liste
						
					}
					
					if (choosenTaxi == null) {//si aucun taxi n'a pu être choisi???
					
					}
					
					myTaxi = choosenTaxi;
					
					//on envoi un message pour confirmer le choix de taxi mais on attend que le taxi nous confirme que c'est bon
					ACLMessage rep = new ACLMessage( ACLMessage.INFORM );
					HashMap content = new HashMap();
					content.put("message", NuberHost.I_CHOOSE_YOU);
					content.put("position", currPos);
					content.put("destination", destPos);
					try {
						rep.setContentObject(content);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
		            rep.addReceiver((AID) choosenTaxi.get("id"));
		            send(rep);
		            
		            //on envoie des messages au autres taxi pour leur dire qu'on les as pas choisies
		            for (int i = 0; i < tmpTaxi.size(); ++i) {
		            	rep = new ACLMessage( ACLMessage.INFORM );
			            rep.setContent(NuberHost.I_DONT_CHOOSE_YOU);
			            rep.addReceiver((AID) ((HashMap) tmpTaxi.get(i)).get("id"));
			            send(rep);
		            }
				}
			}, MAX_WAIT_FOR_TAXI * 1000);
			
			tmpTaxi = new ArrayList();
		}
		//System.out.println("test2");
		HashMap tmp;
		AID taxiId = (AID) content.get("id");
		tmp = (HashMap) myTaxis.get(taxiId);
		if (tmp == null) {
			tmp = new HashMap();
			tmp.put("id", taxiId);
			tmp.put("score", null);
		} else
			oneTaxiIsInMyList = true; //ne pas oublié de réinit la var au bon moment
			
		tmpTaxi.add(tmp);
		//System.out.println(tmp);
		//System.out.println("test3");
	}
	
	private void restartSendingMsg() {
		waitingForResponse = false;
		waitingForTaxi = true;
		timer.cancel();
		timer.purge();
		timer = null;
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
