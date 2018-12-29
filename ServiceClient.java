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
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

public class ServiceClient extends Agent {

  // Put agent initializations here
	protected void setup() {	
		try {
            // create the agent descrption of itself
            ServiceDescription sd = new ServiceDescription();
            sd.setType( "NuberServiceClient" );
            sd.setName( "ServiceClientServiceDescription" );
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName( getAID() );
            dfd.addServices( sd );

            // register the description with the DF
            DFService.register( this, dfd );

            // notify the host that we have arrived
            ACLMessage hello = new ACLMessage( ACLMessage.INFORM );
            hello.setContent( NuberHost.SERVICE_CLIENT);
            hello.addReceiver( new AID( "host", AID.ISLOCALNAME ) );
            send( hello );

            // add a Behaviour to process incoming messages
            addBehaviour( new CyclicBehaviour( this ) {
                            public void action() {
                                // listen if a greetings message arrives
                                ACLMessage msg = receive( MessageTemplate.MatchPerformative( ACLMessage.INFORM ) );

                                if (msg != null) {
                                    if (NuberHost.GOODBYE.equals( msg.getContent() )) {
                                        // time to go
                                        leaveParty();
                                    } else if (NuberHost.NEED_A_TAXI.equals( msg.getContent())) {
										System.out.println("someone need a taxi...");
										Position clientPos = ???;
										
										for (int i = 0; i < "list_taxi"; ++i) {	
										thisTaxi = ???;
											if (isPositionInTaxiArea(clientPos, thisTaxi)) {
												sendMessageToTaxi
											}
										}
									} else {
                                        System.out.println( "ServiceClient received unexpected message: " + msg );
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
            System.out.println( "Saw exception in ServiceClientAgent: " + e );
            e.printStackTrace();
        }
	}

	// Put agent clean-up operations here
	protected void takeDown() {

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
	
	protected boolean isPositionInTaxiArea(Position pos, Taxi aTaxi) {
		//if ()
		return true;
	}
}
