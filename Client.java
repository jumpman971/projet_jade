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
	//constante du client
	public final static int MAX_WAIT_TIME = 60;	//temps d'attente max pour envoyer une demande de taxi
	public final static int MAX_WAIT_FOR_TAXI = 15;	//temps d'attente max pour les taxis répondant à la demande de taxi du client
	public final static int MIN_TAXI_SCORE = 0;	//note minimum qu'un taxi peut avoir par un client
	public final static int MAX_TAXI_SCORE = 10;	//note maximal qu'un taxi peut avoir par un client

	private Position currPos;	//position actuel du client
	private Position destPos;	//position où le client souhaite aller
	private HashMap myTaxis; //les taxis que l'on a pris
	private HashMap myTaxi; //le taxi dans lequel on se trouve (ou que l'on sera)
	
	private int waitTime;	//variable qui contient la valeur d'attente avant d'envoyer une demande de taxi
	private boolean waitingForResponse = false; //variable qui indique qu'on attend la réponse à notre demande de taxi et bloque certaine action dans ce cas
	private boolean waitingForTaxi = false;	//variable qui indique qu'on attend que des taxis répondent à notre demande de taxi
	private Timer timer;	//variable utilisé pour stocker un timer
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
            
            //initialisation des variables de notre client
            myTaxis = new HashMap();
            currPos = NuberHost.getRandomPosition(); //on positionne le client à une position aléatoire
            timer = null;
            //on choisie un temps d'attente aléatoire avant d'envoyer une demande de taxi (enfaite, ça ne sert à rien de le faire ici)
            waitTime = (int) (Math.random() * (MAX_WAIT_TIME - 1));
			waitTime *= 1000;
			
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
                                    		//on relance une demande de taxi (après un temps d'attente x)
                                    		restartSendingMsg();
                                    	}
                                    } else if (NuberHost.END_OF_THE_DRIVE.equals(msg.getContent())) { //notre taxi nous signal que c'est la fin du trajet
                                    	//on set notre position actuel à celle de destination
                                    	currPos = destPos;
                                    	destPos = null;
                                    	System.out.println("( "+ getAID().getName() +" ) Hey " + msg.getSender().getName() + ", thanks for the travel!");
                                    	//System.out.println(myTaxis);
                                    	restartSendingMsg();
                                    } else if (NuberHost.STARTING_THE_DRIVE.equals(msg.getContent())) { //notre taxi nous signal qu'on démarre le trajet
                                    	//noter ses taxis et attendre la fin du trajet via un message du taxi
                                    	if (myTaxis.get(msg.getSender()) == null) { //on set le taxi avec lequel on effectue le trajet
                                    		myTaxis.put(msg.getSender(), myTaxi);
                                    	}
                                    	//on note tous nos taxis
                                    	for (Iterator<HashMap> it = myTaxis.values().iterator(); it.hasNext();) {
                                    		HashMap taxi = it.next();
                                    		
                                    		taxi.put("score", (int) (Math.random() * (MAX_TAXI_SCORE - MIN_TAXI_SCORE)));
                                    	}
                                    } else { //on vérifie si le message contient un objet
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
										
                                    	if (NuberHost.IM_AVAILABLE.equals(message)) { //un taxi nous dit qu'il est dispo
                                    		if (waitingForResponse) {
                                    			waitingForResponse = false;
                                    			waitingForTaxi = true;
                                    			timer.cancel();
                                    			timer.purge();
                                    			timer = null;
                                    		}
                                    		
                                    		//on l'inscrie dans a liste des taxi qui ont répondu et on attend la fin du timer
                                    		chooseATaxi(content);
    									} else {
    										System.out.println( "Client received unexpected message: " + msg );
    										System.out.println(msg.getContent());
    										try {
												System.out.println(msg.getContentObject());
											} catch (UnreadableException e) {
												// TODO Auto-generated catch block
												e.printStackTrace();
											}
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
									//block();
									return;
								
								//System.out.println("test");
								int waitTime = (int) (Math.random() * (MAX_WAIT_TIME - 1));
								waitTime *= 1000;
								//System.out.println("test2");
								try {
									Thread.sleep(waitTime);
									//System.out.println("test3");
								} catch(InterruptedException ex) {
									Thread.currentThread().interrupt();
								}
								waitingForResponse = true;
								iNeedATaxi(); 
								
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
            /*addBehaviour(new TickerBehaviour(this, waitTime) {
                protected void onTick() {
                	if (waitingForResponse || waitingForTaxi)
						block();
					waitingForResponse = true;
					iNeedATaxi();
					
					timer = new Timer();
					timer.schedule(new TimerTask() {
						@Override
						public void run() {
							waitingForResponse = false;
							waitingForTaxi = false;
						}
					}, MAX_WAIT_FOR_TAXI * 1000);
                } 
              });*/
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
					if (myTaxis.isEmpty() || !oneTaxiIsInMyList) {//si on a jamais pris de taxi ou qu'aucun de ceux dispo est dans ma liste
						int index = (int) (Math.random() * tmpTaxi.size());
						choosenTaxi = tmpTaxi.get(index);
						tmpTaxi.remove(index);
					} else { //on choisi le taxi qui a le plus de score parmi la liste
						HashMap highest = tmpTaxi.get(0);
						for (int i = 1; i < tmpTaxi.size(); ++i) {
							HashMap thisTaxi = tmpTaxi.get(i);
							if (((int) thisTaxi.get("score")) > ((int) highest.get("score")))
								highest = thisTaxi;
						}
						choosenTaxi = highest;
					}
					
					if (choosenTaxi == null) {//si aucun taxi n'a pu être choisi???
						restartSendingMsg();
						return;
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
		            
		            oneTaxiIsInMyList = false;
				}
			}, MAX_WAIT_FOR_TAXI * 1000);
			
			tmpTaxi = new ArrayList();
		}
		//System.out.println("test2");
		HashMap tmp;
		AID taxiId = (AID) content.get("id");
		tmp = (HashMap) myTaxis.get(taxiId); 
		if (tmp == null) {	//on check si on a jamais voyagé avec ce taxi et dans ce cas, on le créé
			tmp = new HashMap();
			tmp.put("id", taxiId);
			tmp.put("score", 0);
		} else //on indique que notre list de taxi contient un taxi avec lequel on a déja voyagé
			oneTaxiIsInMyList = true; //ne pas oublié de réinit la var au bon moment
			
		tmpTaxi.add(tmp);
		//System.out.println(tmp);
		//System.out.println("test3");
	}
	
	/*
	 * Réinitialise plusieurs variables permettant au client de recommencer à envoyer des messages
	 * pour demander un taxi. 
	 */
	private void restartSendingMsg() {
		waitingForResponse = false;
		waitingForTaxi = false;
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
