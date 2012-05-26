/**
   This file is part of GoldenGate Project (named also GoldenGate or GG).

   Copyright 2009, Frederic Bregier, and individual contributors by the @author
   tags. See the COPYRIGHT.txt in the distribution for a full listing of
   individual contributors.

   All GoldenGate Project is free software: you can redistribute it and/or 
   modify it under the terms of the GNU General Public License as published 
   by the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   GoldenGate is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with GoldenGate .  If not, see <http://www.gnu.org/licenses/>.
 */
package openr66.context.task;

import goldengate.common.logging.GgInternalLogger;
import goldengate.common.logging.GgInternalLoggerFactory;
import openr66.client.AbstractBusinessRequest;
import openr66.context.ErrorCode;
import openr66.context.R66FiniteDualStates;
import openr66.context.R66Result;
import openr66.context.R66Session;
import openr66.context.task.R66Runnable;
import openr66.protocol.exception.OpenR66ProtocolPacketException;
import openr66.protocol.localhandler.LocalChannelReference;
import openr66.protocol.localhandler.packet.BusinessRequestPacket;
import openr66.protocol.localhandler.packet.ErrorPacket;
import openr66.protocol.utils.ChannelUtils;

/**
 * Dummy Runnable Task that only logs
 * 
 * @author Frederic Bregier
 *
 */
public class AbstractExecJavaTask implements R66Runnable {
    /**
     * Internal Logger
     */
    private static final GgInternalLogger logger = GgInternalLoggerFactory
            .getLogger(AbstractExecJavaTask.class);
    
    protected int delay;
    protected String[] args = null;
    protected int status = -1;
    protected R66Session session;
    protected boolean waitForValidation;
    protected boolean useLocalExec;

    protected String fullarg;
    protected boolean isToValidate;
    protected boolean callFromBusiness;
    
    /**
     * Server side methode to validate the request
     * @param packet
     */
    public void validate(BusinessRequestPacket packet) {
        this.status = 0;
        packet.validate();
        R66Result result = new R66Result(session, true,
                ErrorCode.CompleteOk, null);
        session.getLocalChannelReference().validateRequest(result);
        try {
            ChannelUtils.writeAbstractLocalPacket(session.getLocalChannelReference(),
                    packet).awaitUninterruptibly();
        } catch (OpenR66ProtocolPacketException e) {
        }
    }
    
    /**
     * To be called by the requester when finished
     */
    public void finalValidate() {
        this.status = 0;
        R66Result result = new R66Result(session, true,
                ErrorCode.CompleteOk, null);
        session.getLocalChannelReference().validateRequest(result);
        ChannelUtils.close(session.getLocalChannelReference().getLocalChannel());
    }

    /**
     * To be used if abnormal usage is made of one Java Method
     */
    public void invalid() {
        this.status = 2;
        if (!callFromBusiness) {
            return;
        }
        R66Result result = new R66Result(null, session, true,
                ErrorCode.Unimplemented, session.getRunner());
        LocalChannelReference localChannelReference = session.getLocalChannelReference();
        if (localChannelReference != null) {
            localChannelReference.sessionNewState(R66FiniteDualStates.ERROR);
            ErrorPacket error = new ErrorPacket("Command Incompatible",
                ErrorCode.ExternalOp.getCode(), ErrorPacket.FORWARDCLOSECODE);
            try {
                ChannelUtils.writeAbstractLocalPacket(localChannelReference, error).awaitUninterruptibly();
            } catch (OpenR66ProtocolPacketException e1) {
            }
            localChannelReference.invalidateRequest(result);
            ChannelUtils.close(localChannelReference.getLocalChannel());
        }
    }

    @Override
    public void run() {
        if (callFromBusiness) {
            // Business Request to validate?
            if (isToValidate) {
                BusinessRequestPacket packet = 
                    new BusinessRequestPacket(this.fullarg, 0);
                validate(packet);
            }
        }
        StringBuilder builder = new StringBuilder(this.getClass().getSimpleName()+":");
        for (int i = 0; i < args.length; i++) {
            builder.append(' ');
            builder.append(args[i]);
        }
        logger.warn(builder.toString());
        this.status = 0;
    }

    @Override
    public void setArgs(R66Session session, boolean waitForValidation, 
            boolean useLocalExec, int delay, String []args) {
        this.session = session;
        this.waitForValidation = waitForValidation;
        this.useLocalExec = useLocalExec;
        this.delay = delay;
        this.args = args;
        if (args.length > 2) {
            callFromBusiness = this.args[this.args.length-2].
                equals(AbstractBusinessRequest.BUSINESSREQUEST);
        }
        if (callFromBusiness) {
            isToValidate = Boolean.parseBoolean(this.args[this.args.length-1]);
            StringBuilder builder = new StringBuilder(args[0]);
            for (int i = 1; i < args.length-2; i++) {
                builder.append(' ');
                builder.append(args[i]);
            }
            fullarg = builder.toString();
        } else {
            StringBuilder builder = new StringBuilder(args[0]);
            for (int i = 1; i < args.length; i++) {
                builder.append(' ');
                builder.append(args[i]);
            }
            fullarg = builder.toString();
        }
    }

    @Override
    public int getFinalStatus() {
        return status;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(this.getClass().getSimpleName()+": [");
        builder.append(args[0]);
        builder.append("]");
        for (int i = 1; i < args.length ; i++) {
            builder.append(' ');
            builder.append(args[i]);
        }
        return builder.toString();
    }

}
