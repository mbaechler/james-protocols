/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.protocols.api;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;



/**
 * Abstract base class which CommandDispatcher implementations should extend. A CommandDispatcher is responsible
 * to call the right {@link CommandHandler} for a given Command
 *
 */
public abstract class AbstractCommandDispatcher<Session extends ProtocolSession> implements ExtensibleHandler, LineHandler<Session> {
    /**
     * The list of available command handlers
     */
    private HashMap<String, List<CommandHandler<Session>>> commandHandlerMap = new HashMap<String, List<CommandHandler<Session>>>();

    private List<CommandHandlerResultHandler<Response, Session>> rHandlers = new ArrayList<CommandHandlerResultHandler<Response, Session>>();
    /**
     * Add it to map (key as command name, value is an array list of CommandHandlers)
     *
     * @param commandName the command name which will be key
     * @param cmdHandler The CommandHandler object
     */
    protected void addToMap(String commandName, CommandHandler<Session> cmdHandler) {
        List<CommandHandler<Session>> handlers = commandHandlerMap.get(commandName);
        if(handlers == null) {
            handlers = new ArrayList<CommandHandler<Session>>();
            commandHandlerMap.put(commandName, handlers);
        }
        handlers.add(cmdHandler);
    }


    /**
     * Returns all the configured CommandHandlers for the specified command
     *
     * @param command the command name which will be key
     * @param session not null
     * @return List of CommandHandlers
     */
    protected List<CommandHandler<Session>> getCommandHandlers(String command, ProtocolSession session) {
        if (command == null) {
            return null;
        }
        if (session.getLogger().isDebugEnabled()) {
            session.getLogger().debug("Lookup command handler for command: " + command);
        }
        List<CommandHandler<Session>> handlers =  commandHandlerMap.get(command);
        if(handlers == null) {
            handlers = commandHandlerMap.get(getUnknownCommandHandlerIdentifier());
        }

        return handlers;
    }

    /**
     * @throws WiringException 
     * @see org.apache.james.protocols.api.ExtensibleHandler#wireExtensions(java.lang.Class, java.util.List)
     */
    @SuppressWarnings("unchecked")
    public void wireExtensions(Class interfaceName, List extension) throws WiringException {
        if (interfaceName.equals(CommandHandlerResultHandler.class)) {
            rHandlers.addAll(extension);
        }
        if (interfaceName.equals(CommandHandler.class)) {
            for (Iterator it = extension.iterator(); it.hasNext();) {
                CommandHandler handler = (CommandHandler) it.next();
                Collection implCmds = handler.getImplCommands();

                for (Iterator i = implCmds.iterator(); i.hasNext();) {
                    String commandName = ((String) i.next()).trim().toUpperCase(Locale.US);
                    addToMap(commandName, (CommandHandler) handler);
                }
            }

            addToMap(getUnknownCommandHandlerIdentifier(), getUnknownCommandHandler());

            if (commandHandlerMap.size() < 2) {
                throw new WiringException("No commandhandlers configured");
            } else {
                List<String> mandatoryCommands = getMandatoryCommands();
                for (int i = 0; i < mandatoryCommands.size(); i++) {
                    String cmd = mandatoryCommands.get(i);
                    if (!commandHandlerMap.containsKey(mandatoryCommands.get(i))) {
                        throw new WiringException("No commandhandlers configured for mandatory command " + cmd);
                    }
                }
            }
        }

    }
    
    /*
     * (non-Javadoc)
     * @see org.apache.james.api.protocol.LineHandler#onLine(org.apache.james.api.protocol.ProtocolSession, byte[])
     */
    public boolean onLine(Session session, byte[] line) {
        String curCommandName = null;
        String curCommandArgument = null;
        String cmdString;

        try {
            cmdString = new String(line, getLineDecodingCharset()).trim(); 
            int spaceIndex = cmdString.indexOf(" ");
            if (spaceIndex > 0) {
                curCommandName = cmdString.substring(0, spaceIndex);
                curCommandArgument = cmdString.substring(spaceIndex + 1);
            } else {
                curCommandName = cmdString;
            }
            curCommandName = curCommandName.toUpperCase(Locale.US);

            if (session.getLogger().isDebugEnabled()) {
                session.getLogger().debug(getClass().getName()+" received: " + cmdString);
            }
            List<CommandHandler<Session>> commandHandlers = getCommandHandlers(curCommandName, session);
            // fetch the command handlers registered to the command
            int count = commandHandlers.size();
            for (int i = 0; i < count; i++) {
                CommandHandler<Session> cHandler = commandHandlers.get(i);

                long start = System.currentTimeMillis();
                Response response = cHandler.onCommand(session, new BaseRequest(curCommandName, curCommandArgument));
                long executionTime = System.currentTimeMillis() - start;

                // if the response is received, stop processing of command
                // handlers
                if (response != null) {
                    
                    // now process the result handlers
                    for (int a = 0; a < rHandlers.size(); a++) {
                        response = rHandlers.get(a).onResponse(session, response, executionTime, (CommandHandler<Session>) cHandler);
                    }
                    session.writeResponse(response);

                    break;
                }
            }

        } catch (UnsupportedEncodingException e) {
            // Should never happen
           session.getLogger().error("Unable to handle encoding" ,e );
        }
        
        return false;
       
    }

    protected String getLineDecodingCharset() {
        return "US-ASCII";
    }

    /**
     * @see org.apache.james.protocols.api.ExtensibleHandler#getMarkerInterfaces()
     */
    @SuppressWarnings("unchecked")
    public List<Class<?>> getMarkerInterfaces() {
        List res = new LinkedList();
        res.add(CommandHandler.class);
        res.add(CommandHandlerResultHandler.class);
        return res;
    }

    /**
     * Return a List which holds all mandatory commands
     * 
     * @return mCommands
     */
    protected abstract List<String> getMandatoryCommands();
    
    /**
     * Return the identifier to lookup the UnknownCmdHandler in the handler map
     * 
     * @return identifier
     */
    protected abstract String getUnknownCommandHandlerIdentifier();
    
    /**
     * Return the CommandHandler which should use to handle unknown commands
     * 
     * @return unknownCmdHandler
     */
    protected abstract CommandHandler<Session> getUnknownCommandHandler();
}
