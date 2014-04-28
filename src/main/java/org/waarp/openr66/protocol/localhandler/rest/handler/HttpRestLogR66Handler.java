/**
   This file is part of Waarp Project.

   Copyright 2009, Frederic Bregier, and individual contributors by the @author
   tags. See the COPYRIGHT.txt in the distribution for a full listing of
   individual contributors.

   All Waarp Project is free software: you can redistribute it and/or 
   modify it under the terms of the GNU General Public License as published 
   by the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   Waarp is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with Waarp .  If not, see <http://www.gnu.org/licenses/>.
 */
package org.waarp.openr66.protocol.localhandler.rest.handler;

import java.sql.Timestamp;
import java.util.Date;

import org.waarp.common.json.JsonHandler;
import org.waarp.common.logging.WaarpInternalLogger;
import org.waarp.common.logging.WaarpInternalLoggerFactory;
import org.waarp.gateway.kernel.exception.HttpIncorrectRequestException;
import org.waarp.gateway.kernel.exception.HttpInvalidAuthenticationException;
import org.waarp.gateway.kernel.rest.HttpRestHandler;
import org.waarp.gateway.kernel.rest.DataModelRestMethodHandler.COMMAND_TYPE;
import org.waarp.gateway.kernel.rest.HttpRestHandler.METHOD;
import org.waarp.gateway.kernel.rest.RestArgument;
import org.waarp.openr66.context.ErrorCode;
import org.waarp.openr66.protocol.exception.OpenR66ProtocolBusinessException;
import org.waarp.openr66.protocol.exception.OpenR66ProtocolNotAuthenticatedException;
import org.waarp.openr66.protocol.exception.OpenR66ProtocolPacketException;
import org.waarp.openr66.protocol.localhandler.ServerActions;
import org.waarp.openr66.protocol.localhandler.packet.json.JsonPacket;
import org.waarp.openr66.protocol.localhandler.packet.json.LogJsonPacket;
import org.waarp.openr66.protocol.localhandler.packet.json.LogResponseJsonPacket;
import org.waarp.openr66.protocol.localhandler.rest.HttpRestR66Handler;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Log Http REST interface: http://host/log?... + LogJsonPacket as GET
 * @author "Frederic Bregier"
 *
 */
public class HttpRestLogR66Handler extends HttpRestAbstractR66Handler {
	
	public static final String BASEURI = "log";
	/**
     * Internal Logger
     */
    private static final WaarpInternalLogger logger = WaarpInternalLoggerFactory
            .getLogger(HttpRestLogR66Handler.class);
   
	/**
	 * @param path
	 * @param method
	 */
	public HttpRestLogR66Handler() {
		super(BASEURI, METHOD.GET);
	}
	
	@Override
	public void endParsingRequest(HttpRestHandler handler, RestArgument arguments, RestArgument result, Object body)
			throws HttpIncorrectRequestException, HttpInvalidAuthenticationException {
		logger.debug("debug: {} ### {}",arguments,result);
		if (body != null) {
			logger.debug("Obj: {}", body);
		}
		handler.setWillClose(false);
		ServerActions serverHandler = ((HttpRestR66Handler) handler).serverHandler;
		// now action according to body
		JsonPacket json = (JsonPacket) body;
		if (json == null) {
			result.addItem(JSON_DETAIL, "not enough information");
			setError(handler, result, ErrorCode.CommandNotFound);
			return;
		}
		try {
			if (json instanceof LogJsonPacket) {//
				LogJsonPacket node = (LogJsonPacket) json;
				boolean purge = node.isPurge();
				boolean clean = node.isClean();
				Timestamp start = (node.getStart() == null) ? null :
					new Timestamp(node.getStart().getTime());
				Timestamp stop = (node.getStop() == null) ? null :
					new Timestamp(node.getStop().getTime());
				String startid = node.getStartid();
				String stopid = node.getStopid();
				String rule = node.getRule();
				String request = node.getRequest();
				boolean pending = node.isStatuspending();
				boolean transfer = node.isStatustransfer();
				boolean done = node.isStatusdone();
				boolean error = node.isStatuserror();
				boolean isPurge = purge;
				String sresult[] = serverHandler.logPurge(purge, clean, start, stop, startid, stopid, rule, request, pending, transfer, done, error, isPurge);
				LogResponseJsonPacket newjson = new LogResponseJsonPacket();
				newjson.fromJson(node);
				// Now answer
				newjson.setCommand(node.getRequestUserPacket());
				newjson.setFilename(sresult[0]);
				newjson.setExported(Long.parseLong(sresult[1]));
				newjson.setPurged(Long.parseLong(sresult[2]));
				setOk(handler, result, newjson, ErrorCode.CompleteOk);
			} else {
				logger.info("Validation is ignored: " + json);
				result.addItem(JSON_DETAIL, "Unknown command");
				setError(handler, result, json, ErrorCode.Unknown);
			}
		} catch (OpenR66ProtocolNotAuthenticatedException e) {
			throw new HttpInvalidAuthenticationException(e);
		} catch (OpenR66ProtocolBusinessException e) {
			throw new HttpIncorrectRequestException(e);
		}
	}
	
	protected ArrayNode getDetailedAllow() {
		ArrayNode node = JsonHandler.createArrayNode();
		
		ObjectNode node2 = node.addObject().putObject(METHOD.GET.name());
		node2.put(RestArgument.JSON_PATH, "/"+this.path);
		node2.put(RestArgument.JSON_COMMAND, "GetLog");
		LogJsonPacket node3 = new LogJsonPacket();
		node3.setComment("Log export request (GET)");
		node3.setRequest("The requester or requested host name");
		node3.setRule("The rule name");
		node3.setStart(new Date());
		node3.setStop(new Date());
		node3.setStartid("Start id - long -");
		node3.setStopid("Stop id - long -");
		node2 = node2.putObject(RestArgument.JSON_JSON);
		try {
			node2.putAll(node3.createObjectNode());
		} catch (OpenR66ProtocolPacketException e) {
		}
		
		node2 = node.addObject().putObject(METHOD.OPTIONS.name());
		node2.put(RestArgument.JSON_COMMAND, COMMAND_TYPE.OPTIONS.name());
		node2.put(RestArgument.JSON_PATH, "/"+this.path);

		return node;
	}
}
