/*****************************************************************
JADE - Java Agent DEvelopment Framework is a framework to develop
multi-agent systems in compliance with the FIPA specifications.
Jade is Copyright (C) 2000 CSELT S.p.A.
This file copyright (c) 2001 Hewlett-Packard Corp.

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

/*****************************************************************************
 * Source code information
 * -----------------------
 * Original author    Ian Dickinson, HP Labs Bristol
 * Author email       Ian_Dickinson@hp.com
 * Package
 * Created            1 Oct 2001
 * Filename           $RCSfile$
 * Revision           $Revision: 5373 $
 * Release status     Experimental. $State$
 *
 * Last modified on   $Date: 2004-09-22 15:07:26 +0200 (mer, 22 set 2004) $
 *               by   $Author: dominic $
 *
 * See foot of file for terms of use.
 *****************************************************************************/

// Package
///////////////
package projet_jade;



// Imports
///////////////
import jade.core.AID;
import jade.core.Agent;
import jade.core.ProfileImpl;
import jade.core.Profile;

import jade.wrapper.PlatformController;
import jade.wrapper.AgentController;

import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;

import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.DFService;
import jade.domain.FIPAException;

import javax.swing.*;
import java.util.*;
import java.text.NumberFormat;


/**
 * <p>
 * Agent representing the host for a party, to which a user-controlled number of guests is invited.  The sequence is
 * as follows: the user selects a number guests to attend the party from 0 to 1000, using the
 * slider on the UI.  When the party starts, the host creates N guest agents, each of which registers
 * with the DF, and sends the host a message to say that they have arrived.  When all the guests
 * have arrived, the party starts.  The host selects one guest at random, and tells them a rumour.
 * The host then selects two other guests at random, and introduces them to each other.  The party
 * then proceeds as follows: each guest that is introduced to someone asks the host to introduce them
 * to another guest (at random).  If a guest has someone introduce themselves, and the guest knows
 * the rumour, they tell the other guest.  When a guest hears the rumour for the first time, they
 * notify the host.  When all the guests have heard the rumour, the party ends and the guests leave.
 * </p>
 * <p>
 * Note: to start the host agent, it must be named 'host'.  Thus:
 * <code><pre>
 *     java jade.Boot -gui host:examples.party.HostAgent()
 * </pre></code>
 * </p>
 *
 * @author Ian Dickinson, HP Labs (<a href="mailto:Ian_Dickinson@hp.com">email</a>)
 * @version CVS info: $Id: HostAgent.java 5373 2004-09-22 13:07:26Z dominic $
 */
public class NuberHost
    extends Agent {
	// Constants
    //////////////////////////////////

    
	
    // Static variables
    //////////////////////////////////
	
	public final static String CLIENT = "CLIENT";
	public final static String I_CHOOSE_YOU = "I CHOOSE YOU";
	public final static String I_DONT_CHOOSE_YOU = "I DONT CHOOSE YOU";
	
    public final static String TAXI = "TAXI";
    public final static String REGISTER_TAXI = "REGISTER TAXI";
    public final static String IM_AVAILABLE = "HEY I'M AVAILABLE";
    public final static String IM_NOT_AVAILABLE = "HEY SORRY I'M NOT AVAILABLE";
    public final static String I_HAVE_MOVE = "I'VE MOVE FROM PREVIOUS POSITION";
    //public final static String I_HAVE_A_CLIENT = "I HAVE A CLIENT";
    public final static String STARTING_THE_DRIVE = "STARTING THE DRIVE";
    public final static String END_OF_THE_DRIVE = "END OF THE DRIVE";
	
	public final static String SERVICE_CLIENT = "SERVICE CLIENT";
	public final static String NEED_A_TAXI = "NEED A TAXI";
	
	public final static String GOODBYE = "GOODBYE";
	
	//taille de la "carte"
	public final static int MAX_X_MAP_AREA = 80;
	public final static int MAX_Y_MAP_AREA = 80;

    // Instance variables
    //////////////////////////////////
    protected JFrame m_frame = null;
    protected Vector m_clientList = new Vector();    // les clients
    protected int m_clientCount = 0;                 // arrivals
	protected Vector m_taxiList = new Vector();    // les clients
    protected int m_taxiCount = 0;                 // arrivals
	protected Vector m_serviceClientList = new Vector();  
    protected NumberFormat m_avgFormat = NumberFormat.getInstance();
    protected long m_startTime = 0L;


    // Constructors
    //////////////////////////////////

    /**
     * Construct the host agent.  Some tweaking of the UI parameters.
     */
    public NuberHost() {
        m_avgFormat.setMaximumFractionDigits( 2 );
        m_avgFormat.setMinimumFractionDigits( 2 );
    }



    // External signature methods
    //////////////////////////////////

    /**
     * Setup the agent.  Registers with the DF, and adds a behaviour to
     * process incoming messages.
     */
    protected void setup() {
        try {
            System.out.println( getLocalName() + " setting up");

            // create the agent descrption of itself
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName( getAID() );
            DFService.register( this, dfd );

            // add the GUI
            setupUI();

            // add a Behaviour to handle messages from guests
            addBehaviour( new CyclicBehaviour( this ) {
                            public void action() {
                                ACLMessage msg = receive();
						
                                if (msg != null) {
									if (CLIENT.equals( msg.getContent() )) {
                                        // a client has arrived
                                        m_clientCount++;
									} else if (TAXI.equals( msg.getContent() )) {
                                        // a taxi has arrived
                                        m_taxiCount++;
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
            System.out.println( "Saw exception in HostAgent: " + e );
            e.printStackTrace();
        }

    }


    // Internal implementation methods
    //////////////////////////////////

    /**
     * Setup the UI, which means creating and showing the main frame.
     */
    private void setupUI() {
        m_frame = new HostUIFrame( this );

        m_frame.setSize( 400, 200 );
        m_frame.setLocation( 400, 400 );
        m_frame.setVisible( true );
        m_frame.validate();
    }


	protected void invite(int nClients, int nTaxis) {
		// notice the start time
        m_startTime = System.currentTimeMillis();
		
		PlatformController container = getContainerController(); // get a container controller for creating new agents
		
		try {
			String localName = "serviceClient";
			AgentController serviceClient = container.createNewAgent(localName, "projet_jade.ServiceClient", null);
			serviceClient.start();
            m_serviceClientList.add( new AID(localName, AID.ISLOCALNAME) );
        }
        catch (Exception e) {
            System.err.println( "Exception while adding serviceClients: " + e );
            e.printStackTrace();
        }
		
		inviteClients(nClients, container);
		inviteTaxis(nTaxis, container);
	}
	
    /**
     * Invite a number of guests, as determined by the given parameter.  Clears old
     * state variables, then creates N guest agents.  A list of the agents is maintained,
     * so that the host can tell them all to leave at the end of the party.
     *
     * @param nClients The number of guest agents to invite.
     */
    protected void inviteClients( int nClients, PlatformController container) {
        // remove any old state
        m_clientList.clear();
        m_clientCount = 0;

	
        // create N guest agents
        try {
            for (int i = 0;  i < nClients;  i++) {
                // create a new agent
				String localName = "client_"+i;
				AgentController client = container.createNewAgent(localName, "projet_jade.Client", null);
				client.start();
                //Agent guest = new GuestAgent();
                //guest.doStart( "guest_" + i );

                // keep the guest's ID on a local list
                m_clientList.add( new AID(localName, AID.ISLOCALNAME) );
            }
        }
        catch (Exception e) {
            System.err.println( "Exception while adding clients: " + e );
            e.printStackTrace();
        }
    }
	
	/**
     * Invite a number of guests, as determined by the given parameter.  Clears old
     * state variables, then creates N guest agents.  A list of the agents is maintained,
     * so that the host can tell them all to leave at the end of the party.
     *
     * @param nClients The number of guest agents to invite.
     */
    protected void inviteTaxis( int nTaxis, PlatformController container) {
        // remove any old state
        m_taxiList.clear();
        m_taxiCount = 0;

        // create N guest agents
        try {
            for (int i = 0;  i < nTaxis;  i++) {
                // create a new agent
		String localName = "taxi_"+i;
		AgentController taxi = container.createNewAgent(localName, "projet_jade.Taxi", null);
		taxi.start();
                //Agent guest = new GuestAgent();
                //guest.doStart( "guest_" + i );

                // keep the guest's ID on a local list
                m_taxiList.add( new AID(localName, AID.ISLOCALNAME) );
            }
        }
        catch (Exception e) {
            System.err.println( "Exception while adding taxis: " + e );
            e.printStackTrace();
        }
    }
    
    protected static Position getRandomPosition() {
		return new Position(Math.random() * NuberHost.MAX_X_MAP_AREA, Math.random() * NuberHost.MAX_Y_MAP_AREA);
    }
    
    protected static int calculateTravelDuration(Position from, Position to) {
		int duration = 0;
		double distance = Math.sqrt( Math.pow(to.getX() - from.getX(), 2) + Math.pow(to.getY() - from.getY(), 2));
		
		//(distance_à_parcourir * tempsUnitaire)/distanceUnitaire;
		duration = (int) ((distance * 1)/ 10);
		return duration;
	}

	/**
     * End the party: set the state variables, and tell all the guests to leave.
     */
    protected void endParty() {
        //setPartyState( "Party over" );
        //m_partyOver = true;

        // log the duration of the run

        // send a message to all guests to tell them to leave
        for (Iterator i = m_clientList.iterator();  i.hasNext();  ) {
            ACLMessage msg = new ACLMessage( ACLMessage.INFORM );
            msg.setContent( GOODBYE );

            msg.addReceiver( (AID) i.next() );

            send(msg);
        }

        m_clientList.clear();
		
		for (Iterator i = m_taxiList.iterator();  i.hasNext();  ) {
            ACLMessage msg = new ACLMessage( ACLMessage.INFORM );
            msg.setContent( GOODBYE );

            msg.addReceiver( (AID) i.next() );

            send(msg);
        }

        m_taxiList.clear();
		
		for (Iterator i = m_serviceClientList.iterator();  i.hasNext();  ) {
            ACLMessage msg = new ACLMessage( ACLMessage.INFORM );
            msg.setContent( GOODBYE );

            msg.addReceiver( (AID) i.next() );

            send(msg);
        }

        m_serviceClientList.clear();
    }
	
    /**
     * Shut down the host agent, including removing the UI and deregistering
     * from the DF.
     */
    protected void terminateHost() {
        try {
            if (!m_clientList.isEmpty() || !m_taxiList.isEmpty()) {
                endParty();
            }

            DFService.deregister( this );
            m_frame.dispose();
            doDelete();
        }
        catch (Exception e) {
            System.err.println( "Saw FIPAException while terminating: " + e );
            e.printStackTrace();
        }
    }

}


