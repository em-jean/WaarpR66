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
package org.waarp.openr66.protocol.localhandler.packet.json;

import java.util.Date;

import org.waarp.openr66.database.data.DbTaskRunner;
import org.waarp.openr66.protocol.configuration.PartnerConfiguration;
import org.waarp.openr66.protocol.localhandler.packet.LocalPacketFactory;

/**
 * Transfer request JSON packet
 * 
 * @author "Frederic Bregier"
 *
 */
public class TransferRequestJsonPacket extends JsonPacket {

    protected static final byte REQVALIDATE = 0;

    protected static final byte REQANSWERVALIDATE = 1;

    protected String rulename;

    protected int mode;

    protected String filename;

    protected String requested;

    protected int blocksize;

    protected int rank;

    protected long specialId;

    protected byte validate;

    protected long originalSize;

    protected String fileInformation;

    protected String separator = PartnerConfiguration.BAR_JSON_FIELD;

    protected Date start;

    protected boolean isAdditionalDelay;

    protected long delay;

    /**
     * @return the isAdditionalDelay
     */
    public boolean isAdditionalDelay() {
        return isAdditionalDelay;
    }

    /**
     * @param isAdditionalDelay
     *            the isAdditionalDelay to set
     */
    public void setAdditionalDelay(boolean isAdditionalDelay) {
        this.isAdditionalDelay = isAdditionalDelay;
    }

    /**
     * @return the delay
     */
    public long getDelay() {
        return delay;
    }

    /**
     * @param delay
     *            the delay to set
     */
    public void setDelay(long delay) {
        this.delay = delay;
    }

    /**
     * @return the start
     */
    public Date getStart() {
        return start;
    }

    /**
     * @param start
     *            the start to set
     */
    public void setStart(Date start) {
        this.start = start;
    }

    /**
     * @return the requested
     */
    public String getRequested() {
        return requested;
    }

    /**
     * @param requested
     *            the requested to set
     */
    public void setRequested(String requested) {
        this.requested = requested;
    }

    /**
     * @return the rulename
     */
    public String getRulename() {
        return rulename;
    }

    /**
     * @param rulename
     *            the rulename to set
     */
    public void setRulename(String rulename) {
        this.rulename = rulename;
    }

    /**
     * @return the mode
     */
    public int getMode() {
        return mode;
    }

    /**
     * @param mode
     *            the mode to set
     */
    public void setMode(int mode) {
        this.mode = mode;
    }

    /**
     * @return the filename
     */
    public String getFilename() {
        return filename;
    }

    /**
     * @param filename
     *            the filename to set
     */
    public void setFilename(String filename) {
        this.filename = filename;
    }

    /**
     * @return the blocksize
     */
    public int getBlocksize() {
        return blocksize;
    }

    /**
     * @param blocksize
     *            the blocksize to set
     */
    public void setBlocksize(int blocksize) {
        this.blocksize = blocksize;
    }

    /**
     * @return the rank
     */
    public int getRank() {
        return rank;
    }

    /**
     * @param rank
     *            the rank to set
     */
    public void setRank(int rank) {
        this.rank = rank;
    }

    /**
     * @return the specialId
     */
    public long getSpecialId() {
        return specialId;
    }

    /**
     * @param specialId
     *            the specialId to set
     */
    public void setSpecialId(long specialId) {
        this.specialId = specialId;
    }

    /**
     * @return the validate
     */
    public byte getValidate() {
        return validate;
    }

    /**
     * @return True if is to validate
     */
    public boolean isToValidate() {
        return validate == REQVALIDATE;
    }

    /**
     * @param validate
     *            the validate to set
     */
    public void setValidate(byte validate) {
        this.validate = validate;
    }

    /**
     * Validate the request
     */
    public void validate() {
        this.validate = REQANSWERVALIDATE;
    }

    /**
     * @return the originalSize
     */
    public long getOriginalSize() {
        return originalSize;
    }

    /**
     * @param originalSize
     *            the originalSize to set
     */
    public void setOriginalSize(long originalSize) {
        this.originalSize = originalSize;
    }

    /**
     * @return the fileInformation
     */
    public String getFileInformation() {
        return fileInformation;
    }

    /**
     * @param fileInformation
     *            the fileInformation to set
     */
    public void setFileInformation(String fileInformation) {
        this.fileInformation = fileInformation;
    }

    /**
     * @return the separator
     */
    public String getSeparator() {
        return separator;
    }

    /**
     * @param separator
     *            the separator to set
     */
    public void setSeparator(String separator) {
        this.separator = separator;
    }

    /**
     * Update the JsonPacket from runner (blocksize, rank, specialid)
     * 
     * @param runner
     */
    public void setFromDbTaskRunner(DbTaskRunner runner) {
        this.blocksize = runner.getBlocksize();
        this.rank = runner.getRank();
        this.specialId = runner.getSpecialId();
        this.originalSize = runner.getOriginalSize();
    }

    public void setRequestUserPacket() {
        super.setRequestUserPacket(LocalPacketFactory.REQUESTPACKET);
    }
}
