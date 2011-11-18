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

package org.apache.james.protocols.pop3.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.POP3StreamResponse;
import org.apache.james.protocols.pop3.mailbox.MessageMetaData;

/**
 * Handles TOP command
 */
public class TopCmdHandler extends RetrCmdHandler implements CapaCapability {
    private static final Collection<String> COMMANDS = Collections.unmodifiableCollection(Arrays.asList("TOP"));
    private static final List<String> CAPS = Collections.unmodifiableList(Arrays.asList("TOP"));

    
    /**
     * Handler method called upon receipt of a TOP command. This command
     * retrieves the top N lines of a specified message in the mailbox.
     * 
     * The expected command format is TOP [mail message number] [number of lines
     * to return]
     */
    @SuppressWarnings("unchecked")
    @Override
    public Response onCommand(POP3Session session, Request request) {
        POP3Response response = null;
        String parameters = request.getArgument();
        if (parameters == null) {
            response = new POP3Response(POP3Response.ERR_RESPONSE, "Usage: TOP [mail number] [Line number]");
            return response;
        }

        String argument = "";
        String argument1 = "";
        int pos = parameters.indexOf(" ");
        if (pos > 0) {
            argument = parameters.substring(0, pos);
            argument1 = parameters.substring(pos + 1);
        }

        if (session.getHandlerState() == POP3Session.TRANSACTION) {
            int num = 0;
            int lines = -1;
            try {
                num = Integer.parseInt(argument);
                lines = Integer.parseInt(argument1);
            } catch (NumberFormatException nfe) {
                response = new POP3Response(POP3Response.ERR_RESPONSE, "Usage: TOP [mail number] [Line number]");
                return response;
            }
            try {
                List<MessageMetaData> uidList = (List<MessageMetaData>) session.getState().get(POP3Session.UID_LIST);
                List<Long> deletedUidList = (List<Long>) session.getState().get(POP3Session.DELETED_UID_LIST);

                Long uid = uidList.get(num - 1).getUid();
                if (deletedUidList.contains(uid) == false) {

                    InputStream body = new CountingBodyInputStream(new ExtraDotInputStream(new CRLFTerminatedInputStream(session.getUserMailbox().getMessageBody(uid))), lines);
                    InputStream headers = session.getUserMailbox().getMessageHeaders(uid);
                    if (body != null && headers != null) {
                        response = new POP3StreamResponse(POP3Response.OK_RESPONSE, "Message follows", new SequenceInputStream(headers, body));
                        return response;

                    } else {
                        StringBuilder exceptionBuffer = new StringBuilder(64).append("Message (").append(num).append(") does not exist.");
                        response = new POP3Response(POP3Response.ERR_RESPONSE, exceptionBuffer.toString());
                    }

                } else {
                    StringBuilder responseBuffer = new StringBuilder(64).append("Message (").append(num).append(") already deleted.");
                    response = new POP3Response(POP3Response.ERR_RESPONSE, responseBuffer.toString());
                }
            } catch (IOException ioe) {
                response = new POP3Response(POP3Response.ERR_RESPONSE, "Error while retrieving message.");
            
            } catch (IndexOutOfBoundsException iob) {
                StringBuilder exceptionBuffer = new StringBuilder(64).append("Message (").append(num).append(") does not exist.");
                response = new POP3Response(POP3Response.ERR_RESPONSE, exceptionBuffer.toString());
            } catch (NoSuchElementException iob) {
                StringBuilder exceptionBuffer = new StringBuilder(64).append("Message (").append(num).append(") does not exist.");
                response = new POP3Response(POP3Response.ERR_RESPONSE, exceptionBuffer.toString());
            }
        } else {
            response = new POP3Response(POP3Response.ERR_RESPONSE);
        }
        return response;

    }

    /**
     * @see org.apache.james.pop3server.core.CapaCapability#getImplementedCapabilities(org.apache.james.pop3server.POP3Session)
     */
    @SuppressWarnings("unchecked")
	public List<String> getImplementedCapabilities(POP3Session session) {
        if (session.getHandlerState() == POP3Session.TRANSACTION) {
        	return CAPS;
        } else {
        	return Collections.EMPTY_LIST;
        }
    }

    /**
     * @see org.apache.james.protocols.api.handler.CommandHandler#getImplCommands()
     */
    public Collection<String> getImplCommands() {
    	return COMMANDS;
    }

    /**
     * This {@link InputStream} implementation can be used to limit the body
     * lines which will be read from the wrapped {@link InputStream}
     */
    // TODO: Fix me!
    private final class CountingBodyInputStream extends InputStream {

        private int count = 0;
        private int limit = -1;
        private int lastChar;
		private InputStream in;

        /**
         * 
         * @param in
         *            InputStream to read from
         * @param limit
         *            the lines to read. -1 is used for no limits
         */
        public CountingBodyInputStream(InputStream in, int limit) {
            this.in = in;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            if (limit != -1) {
                if (count <= limit) {
                    int a = in.read();

                    if (lastChar == '\r' && a == '\n') {
                        count++;
                    }
                    lastChar = a;

                    return a;
                } else {
                    return -1;
                }
            } else {
                return in.read();
            }

        }

		@Override
		public long skip(long n) throws IOException {
			return in.skip(n);
		}

		@Override
		public int available() throws IOException {
			return in.available();
		}

		@Override
		public void close() throws IOException {
			in.close();
		}

		@Override
		public void mark(int readlimit) {
			// not supported
		}

		@Override
		public synchronized void reset() throws IOException {
			// do nothing as mark is not supported
		}

		@Override
		public boolean markSupported() {
			return false;
		}
        
        
    }
}