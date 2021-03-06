/**
 * This file is part of Waarp Project.
 * 
 * Copyright 2009, Frederic Bregier, and individual contributors by the @author tags. See the
 * COPYRIGHT.txt in the distribution for a full listing of individual contributors.
 * 
 * All Waarp Project is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 * 
 * Waarp is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Waarp . If not, see
 * <http://www.gnu.org/licenses/>.
 */
package org.waarp.openr66.protocol.localhandler.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.waarp.openr66.protocol.exception.OpenR66ProtocolPacketException;
import org.waarp.openr66.protocol.localhandler.LocalChannelReference;

/**
 * Block Request Message class
 * 
 * 1 code (byte as 0: unblock, 1:block): block, 1 string: spassword(or key)
 * 
 * @author frederic bregier
 */
public class BlockRequestPacket extends AbstractLocalPacket {
    private final boolean block;
    private final byte[] key;

    /**
     * @param headerLength
     * @param middleLength
     * @param endLength
     * @param buf
     * @return the new ValidPacket from buffer
     * @throws OpenR66ProtocolPacketException
     */
    public static BlockRequestPacket createFromBuffer(int headerLength,
            int middleLength, int endLength, ByteBuf buf) throws OpenR66ProtocolPacketException {
        if (headerLength - 2 <= 0) {
            throw new OpenR66ProtocolPacketException("Not enough data");
        }
        byte isblock = buf.readByte();
        final byte[] bpassword = new byte[headerLength - 2];
        if (headerLength - 2 > 0) {
            buf.readBytes(bpassword);
        }
        boolean block = (isblock == 1);
        return new BlockRequestPacket(block, bpassword);
    }

    /**
     * @param block
     * @param spassword
     */
    public BlockRequestPacket(boolean block, byte[] spassword) {
        this.block = block;
        key = spassword;
    }

    @Override
    public void createEnd(LocalChannelReference lcr) throws OpenR66ProtocolPacketException {
        end = Unpooled.EMPTY_BUFFER;
    }

    @Override
    public void createHeader(LocalChannelReference lcr) throws OpenR66ProtocolPacketException {
        header = Unpooled.buffer(1 + key.length);
        header.writeByte(block ? 1 : 0);
        header.writeBytes(key);
    }

    @Override
    public void createMiddle(LocalChannelReference lcr) throws OpenR66ProtocolPacketException {
        middle = Unpooled.EMPTY_BUFFER;
    }

    /*
     * (non-Javadoc)
     * @see org.waarp.openr66.protocol.localhandler.packet.AbstractLocalPacket#toString()
     */
    @Override
    public String toString() {
        return "BlockRequestPacket: " + block;
    }

    @Override
    public byte getType() {
        return LocalPacketFactory.BLOCKREQUESTPACKET;
    }

    /**
     * @return True if the request is to block new requests, else false
     */
    public boolean getBlock() {
        return block;
    }

    /**
     * @return the key
     */
    public byte[] getKey() {
        return key;
    }
}
