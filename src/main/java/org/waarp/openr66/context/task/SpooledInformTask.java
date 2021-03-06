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
package org.waarp.openr66.context.task;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Set;
import java.util.TreeMap;

import org.waarp.common.digest.FilesystemBasedDigest;
import org.waarp.common.filemonitor.FileMonitor.FileItem;
import org.waarp.common.filemonitor.FileMonitor.FileMonitorInformation;
import org.waarp.common.json.JsonHandler;
import org.waarp.common.logging.WaarpLogger;
import org.waarp.common.logging.WaarpLoggerFactory;
import org.waarp.openr66.client.SpooledDirectoryTransfer;
import org.waarp.openr66.protocol.configuration.Configuration;
import org.waarp.openr66.protocol.configuration.Messages;
import org.waarp.openr66.protocol.exception.OpenR66ProtocolPacketException;
import org.waarp.openr66.protocol.localhandler.packet.BusinessRequestPacket;
import org.waarp.openr66.protocol.utils.ChannelUtils;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

/**
 * Java Task for SpooledDirectory information to the Waarp Server
 * 
 * @author Frederic Bregier
 * 
 */
public class SpooledInformTask extends AbstractExecJavaTask {

    /**
     * Internal Logger
     */
    private static final WaarpLogger logger = WaarpLoggerFactory
            .getLogger(SpooledInformTask.class);

    public static final TreeMap<String, SpooledInformation> spooledInformationMap = new TreeMap<String, SpooledInformTask.SpooledInformation>();

    public static class SpooledInformation {
        public String host;
        public FileMonitorInformation fileMonitorInformation;
        public Date lastUpdate = new Date();

        /**
         * @param host
         * @param fileItemHashMap
         */
        private SpooledInformation(String host, FileMonitorInformation fileMonitorInformation) {
            this.host = host;
            this.fileMonitorInformation = fileMonitorInformation;
        }
    }

    @Override
    public void run() {
        if (callFromBusiness) {
            // Business Request to validate?
            String validated = SpooledDirectoryTransfer.PARTIALOK;
            if (isToValidate) {
                try {
                    FileMonitorInformation fileMonitorInformation =
                            JsonHandler.mapper.readValue(fullarg, FileMonitorInformation.class);
                    logger.info("Receive SpooledInform of size: "
                            + fullarg.length()
                            + " ("
                            + fileMonitorInformation.fileItems.size()
                            + ", "
                            +
                            (fileMonitorInformation.removedFileItems != null ? fileMonitorInformation.removedFileItems
                                    .size() : -1) + ")");
                    String host = this.session.getAuth().getUser();
                    synchronized (spooledInformationMap) {
                        if (fileMonitorInformation.removedFileItems == null
                                || fileMonitorInformation.removedFileItems.isEmpty()) {
                            SpooledInformation old = spooledInformationMap.put(fileMonitorInformation.name,
                                    new SpooledInformation(host, fileMonitorInformation));
                            if (old != null && old.fileMonitorInformation != null) {
                                if (old.fileMonitorInformation.directories != null) {
                                    old.fileMonitorInformation.directories.clear();
                                }
                                if (old.fileMonitorInformation.fileItems != null) {
                                    old.fileMonitorInformation.fileItems.clear();
                                }
                                old.fileMonitorInformation = null;
                            }
                            old = null;
                        } else {
                            // partial update
                            SpooledInformation update = spooledInformationMap.get(fileMonitorInformation.name);
                            if (update == null) {
                                // Issue since update is not existing so full update is needed next time
                                spooledInformationMap.put(fileMonitorInformation.name, new SpooledInformation(host,
                                        fileMonitorInformation));
                                validated = SpooledDirectoryTransfer.NEEDFULL;
                            } else {
                                for (String item : fileMonitorInformation.removedFileItems) {
                                    update.fileMonitorInformation.fileItems.remove(item);
                                }
                                update.fileMonitorInformation.fileItems.putAll(fileMonitorInformation.fileItems);
                                update.lastUpdate = new Date();
                            }
                        }
                    }
                } catch (JsonParseException e1) {
                    logger.warn("Cannot parse SpooledInformation: " + fullarg + " " + e1.getMessage());
                } catch (JsonMappingException e1) {
                    logger.warn("Cannot parse SpooledInformation: " + fullarg + " " + e1.getMessage());
                } catch (IOException e1) {
                    logger.warn("Cannot parse SpooledInformation: " + e1.getMessage());
                }
                BusinessRequestPacket packet =
                        new BusinessRequestPacket(this.getClass().getName() + " informed", 0);
                validate(packet);
                try {
                    ChannelUtils.writeAbstractLocalPacket(session.getLocalChannelReference(),
                            packet, true);
                } catch (OpenR66ProtocolPacketException e) {
                }
                this.status = 0;
            }
            finalValidate(validated);
        } else {
            // unallowed
            logger.warn("SpooledInformTask not allowed as Java Task: " + fullarg);
            invalid();
        }
    }

    /**
     * @param detailed
     * @param status
     *            1 for ok, -1 for ko, 0 for all
     * @param uri
     * @return the StringBuilder containing the HTML format as a Table of the current Spooled information
     */
    public static StringBuilder buildSpooledTable(boolean detailed, int status, String uri) {
        StringBuilder builder = beginSpooledTable(detailed, uri);
        // get current information
        synchronized (spooledInformationMap) {
            Set<String> names = spooledInformationMap.keySet();
            for (String name : names) {
                // per Name
                buildSpooledTableElement(detailed, status, builder, name);
            }
        }
        endSpooledTable(builder);
        return builder;
    }

    /**
     * @param name
     * @param uri
     * @return the StringBuilder containing the HTML format as a Table of the current Spooled information
     */
    public static StringBuilder buildSpooledUniqueTable(String uri, String name) {
        StringBuilder builder = beginSpooledTable(false, uri);
        // get current information
        synchronized (spooledInformationMap) {
            // per Name
            SpooledInformation inform = buildSpooledTableElement(false, 0, builder, name);
            endSpooledTable(builder);
            builder.append("<BR>");
            if (inform != null) {
                buildSpooledTableFiles(builder, inform);
            }
        }
        return builder;
    }

    /**
     * @param builder
     */
    private static void endSpooledTable(StringBuilder builder) {
        builder.append("</TABLE>");
    }

    /**
     * @param detailed
     * @param uri
     * @return the associated StringBuilder as temporary result
     */
    private static StringBuilder beginSpooledTable(boolean detailed, String uri) {
        StringBuilder builder = new StringBuilder();
        builder.append("<TABLE BORDER=1><CAPTION><A HREF=");
        builder.append(uri);
        if (detailed) {
            builder.append(Messages.getString("SpooledInformTask.TitleDetailed")); //$NON-NLS-1$
        } else {
            builder.append(Messages.getString("SpooledInformTask.TitleNormal")); //$NON-NLS-1$
        }
        // title first
        builder.append("<TR><TH>").append(Messages.getString("SpooledInformTask.0")) //$NON-NLS-1$
                .append("</TH><TH>").append(Messages.getString("SpooledInformTask.1")) //$NON-NLS-1$
                .append("</TH><TH>").append(Messages.getString("SpooledInformTask.2")) //$NON-NLS-1$
                .append("</TH><TH>").append(Messages.getString("SpooledInformTask.3")) //$NON-NLS-1$
                .append("</TH><TH>").append(Messages.getString("SpooledInformTask.4")) //$NON-NLS-1$
                .append("</TH><TH>").append(Messages.getString("SpooledInformTask.5")) //$NON-NLS-1$
                .append("</TH><TH>").append(Messages.getString("SpooledInformTask.6")) //$NON-NLS-1$
                .append("</TH><TH>").append(Messages.getString("SpooledInformTask.7")) //$NON-NLS-1$
                .append("</TH><TH>").append(Messages.getString("SpooledInformTask.8")) //$NON-NLS-1$
                .append("</TH><TH>").append(Messages.getString("SpooledInformTask.9")) //$NON-NLS-1$
                .append("</TH></TR>");
        return builder;
    }

    /**
     * @param detailed
     * @param status
     * @param builder
     * @param name
     */
    private static SpooledInformation buildSpooledTableElement(boolean detailed, int status, StringBuilder builder,
            String name) {
        SpooledInformation inform = spooledInformationMap.get(name);
        if (inform == null) {
            return null;
        }
        long time = inform.lastUpdate.getTime() + Configuration.configuration.TIMEOUTCON;
        long curtime = System.currentTimeMillis();
        if (time + Configuration.configuration.TIMEOUTCON < curtime) {
            if (status > 0) {
                return inform;
            }
        } else {
            if (status < 0) {
                return inform;
            }
        }
        builder.append("<TR><TH>").append(name.replace(',', ' ')).append("</TH><TD>").append(inform.host)
                .append("</TD>");
        if (time + Configuration.configuration.TIMEOUTCON < curtime) {
            builder.append("<TD bgcolor=Red>");
        } else if (time < curtime) {
            builder.append("<TD bgcolor=Orange>");
        } else {
            builder.append("<TD bgcolor=LightGreen>");
        }
        builder.append(inform.lastUpdate).append("</TD>");
        if (inform.fileMonitorInformation != null) {
            builder.append(Messages.getString("SpooledInformTask.AllOk")) //$NON-NLS-1$
                    .append(inform.fileMonitorInformation.globalok)
                    .append(Messages.getString("SpooledInformTask.AllError")) //$NON-NLS-1$
                    .append(inform.fileMonitorInformation.globalerror)
                    .append(Messages.getString("SpooledInformTask.TodayOk")) //$NON-NLS-1$
                    .append(inform.fileMonitorInformation.todayok)
                    .append(Messages.getString("SpooledInformTask.TodayError")) //$NON-NLS-1$
                    .append(inform.fileMonitorInformation.todayerror)
                    .append("</TD><TD>")
                    .append(inform.fileMonitorInformation.elapseTime)
                    .append("</TD><TD>")
                    .append(inform.fileMonitorInformation.stopFile)
                    .append("</TD><TD>")
                    .append(inform.fileMonitorInformation.statusFile)
                    .append("</TD><TD>")
                    .append(inform.fileMonitorInformation.scanSubDir)
                    .append("</TD>");
            String dirs = "";
            for (File dir : inform.fileMonitorInformation.directories) {
                dirs += dir + "<br>";
            }
            builder.append("<TD>").append(dirs).append("</TD><TD>");
            if (detailed && inform.fileMonitorInformation.fileItems != null) {
                buildSpooledTableFiles(builder, inform);
            } else {
                // simply print number of files
                if (inform.fileMonitorInformation.fileItems != null) {
                    builder.append(inform.fileMonitorInformation.fileItems.size());
                } else {
                    builder.append(0);
                }
                // Form GET to ensure encoding
                builder.append(
                        "<FORM name='DETAIL' method='GET' action='/SpooledDetailed.html'><input type=hidden name='name' value='")
                        .append(name).append("'/><INPUT type='submit' value='DETAIL'/></FORM>");
            }
        }
        builder.append("</TD></TR>");
        return inform;
    }

    /**
     * @param builder
     * @param inform
     */
    private static void buildSpooledTableFiles(StringBuilder builder, SpooledInformation inform) {
        builder.append("<TABLE BORDER=1><TR><TH>").append(Messages.getString("SpooledInformTask.10")) //$NON-NLS-1$
                .append("</TH><TH>").append(Messages.getString("SpooledInformTask.11")) //$NON-NLS-1$
                .append("</TH><TH>").append(Messages.getString("SpooledInformTask.12")) //$NON-NLS-1$
                .append("</TH><TH>").append(Messages.getString("SpooledInformTask.13")) //$NON-NLS-1$
                .append("</TH><TH>").append(Messages.getString("SpooledInformTask.14")) //$NON-NLS-1$
                .append("</TH><TH>").append(Messages.getString("SpooledInformTask.15")) //$NON-NLS-1$
                .append("</TH></TR>");
        for (FileItem fileItem : inform.fileMonitorInformation.fileItems.values()) {
            builder.append("<TR><TD>").append(fileItem.file).append("</TD><TD>");
            if (fileItem.hash != null) {
                builder.append(FilesystemBasedDigest.getHex(fileItem.hash));
            }
            builder.append("</TD><TD>");
            if (fileItem.lastTime > 0) {
                builder.append(new Date(fileItem.lastTime));
            }
            builder.append("</TD><TD>");
            if (fileItem.timeUsed > 0) {
                builder.append(new Date(fileItem.timeUsed));
            }
            builder.append("</TD><TD>").append(fileItem.used).append("</TD><TD>")
                    .append(fileItem.specialId).append("</TD></TR>");
        }
        builder.append("</TABLE>");
    }
}
