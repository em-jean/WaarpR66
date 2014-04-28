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

import org.jboss.netty.handler.codec.http.HttpResponseStatus;
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
import org.waarp.openr66.protocol.exception.OpenR66ProtocolNoDataException;
import org.waarp.openr66.protocol.exception.OpenR66ProtocolNotAuthenticatedException;
import org.waarp.openr66.protocol.exception.OpenR66ProtocolPacketException;
import org.waarp.openr66.protocol.localhandler.ServerActions;
import org.waarp.openr66.protocol.localhandler.packet.ValidPacket;
import org.waarp.openr66.protocol.localhandler.packet.json.InformationJsonPacket;
import org.waarp.openr66.protocol.localhandler.packet.json.JsonPacket;
import org.waarp.openr66.protocol.localhandler.rest.HttpRestR66Handler;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Info Http REST interface: http://host/info?... + InformationJsonPacket as GET
 * @author "Frederic Bregier"
 *
 */
public class HttpRestInformationR66Handler extends HttpRestAbstractR66Handler {
	
	public static final String BASEURI = "info";
	/**
     * Internal Logger
     */
    private static final WaarpInternalLogger logger = WaarpInternalLoggerFactory
            .getLogger(HttpRestInformationR66Handler.class);
   
	/**
	 * @param path
	 * @param method
	 */
	public HttpRestInformationR66Handler() {
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
			if (json instanceof InformationJsonPacket) {//
				InformationJsonPacket node = (InformationJsonPacket) json;
				ValidPacket validPacket = serverHandler.information(node.isIdRequest(), node.getId(), node.isTo(), node.getRequest(), node.getRulename(), node.getFilename(), true);
				if (validPacket != null) {
					// will not use default setOk
					ObjectNode resp = JsonHandler.getFromString(validPacket.getSheader());
					handler.setStatus(HttpResponseStatus.OK);
					result.addItem(JSON_MESSAGE, ErrorCode.CompleteOk.mesg);
					result.addItem(JSON_MESSAGECODE, ""+ErrorCode.CompleteOk.code);
					result.getAnswer().put(JSON_RESULT, resp);
				} else {
					result.addItem(JSON_DETAIL, "Error during information request");
					setError(handler, result, ErrorCode.TransferError);
				}
			} else {
				logger.info("Validation is ignored: " + json);
				result.addItem(JSON_DETAIL, "Unknown command");
				setError(handler, result, json, ErrorCode.Unknown);
			}
		} catch (OpenR66ProtocolNotAuthenticatedException e) {
			throw new HttpInvalidAuthenticationException(e);
		} catch (OpenR66ProtocolPacketException e) {
			throw new HttpIncorrectRequestException(e);
		} catch (OpenR66ProtocolNoDataException e) {
			throw new HttpIncorrectRequestException(e);
		}
	}

	protected ArrayNode getDetailedAllow() {
		ArrayNode node = JsonHandler.createArrayNode();
		
		ObjectNode node2 = node.addObject().putObject(METHOD.GET.name());
		node2.put(RestArgument.JSON_PATH, "/"+this.path);
		node2.put(RestArgument.JSON_COMMAND, "GetInformation");
		InformationJsonPacket node3 = new InformationJsonPacket();
		node3.setComment("Information request (GET)");
		node3.setFilename("The filename to look for if any");
		node3.setRulename("The rule name associated with the remote repository");
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
