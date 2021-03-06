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
package org.waarp.openr66.protocol.configuration;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.traffic.AbstractTrafficShapingHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.handler.traffic.GlobalChannelTrafficShapingHandler;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;

import org.waarp.common.crypto.Des;
import org.waarp.common.crypto.ssl.WaarpSecureKeyStore;
import org.waarp.common.crypto.ssl.WaarpSslContextFactory;
import org.waarp.common.crypto.ssl.WaarpSslUtility;
import org.waarp.common.database.DbSession;
import org.waarp.common.database.exception.WaarpDatabaseException;
import org.waarp.common.database.exception.WaarpDatabaseNoConnectionException;
import org.waarp.common.database.exception.WaarpDatabaseSqlException;
import org.waarp.common.digest.FilesystemBasedDigest;
import org.waarp.common.digest.FilesystemBasedDigest.DigestAlgo;
import org.waarp.common.file.filesystembased.FilesystemBasedFileParameterImpl;
import org.waarp.common.future.WaarpFuture;
import org.waarp.common.logging.WaarpLogger;
import org.waarp.common.logging.WaarpLoggerFactory;
import org.waarp.common.role.RoleDefault;
import org.waarp.common.utility.SystemPropertyUtil;
import org.waarp.common.utility.WaarpNettyUtil;
import org.waarp.common.utility.WaarpShutdownHook.ShutdownConfiguration;
import org.waarp.common.utility.WaarpThreadFactory;
import org.waarp.gateway.kernel.rest.RestConfiguration;
import org.waarp.openr66.commander.ClientRunner;
import org.waarp.openr66.commander.InternalRunner;
import org.waarp.openr66.configuration.FileBasedConfiguration;
import org.waarp.openr66.context.R66BusinessFactoryInterface;
import org.waarp.openr66.context.R66DefaultBusinessFactory;
import org.waarp.openr66.context.R66FiniteDualStates;
import org.waarp.openr66.context.task.localexec.LocalExecClient;
import org.waarp.openr66.database.DbConstant;
import org.waarp.openr66.database.data.DbHostAuth;
import org.waarp.openr66.database.data.DbTaskRunner;
import org.waarp.openr66.protocol.exception.OpenR66ProtocolNoDataException;
import org.waarp.openr66.protocol.exception.OpenR66ProtocolNoSslException;
import org.waarp.openr66.protocol.http.HttpInitializer;
import org.waarp.openr66.protocol.http.adminssl.HttpSslHandler;
import org.waarp.openr66.protocol.http.adminssl.HttpSslInitializer;
import org.waarp.openr66.protocol.http.rest.HttpRestR66Handler;
import org.waarp.openr66.protocol.localhandler.LocalTransaction;
import org.waarp.openr66.protocol.localhandler.Monitoring;
import org.waarp.openr66.protocol.networkhandler.ChannelTrafficHandler;
import org.waarp.openr66.protocol.networkhandler.GlobalTrafficHandler;
import org.waarp.openr66.protocol.networkhandler.NetworkServerInitializer;
import org.waarp.openr66.protocol.networkhandler.NetworkTransaction;
import org.waarp.openr66.protocol.networkhandler.R66ConstraintLimitHandler;
import org.waarp.openr66.protocol.networkhandler.ssl.NetworkSslServerInitializer;
import org.waarp.openr66.protocol.snmp.R66PrivateMib;
import org.waarp.openr66.protocol.snmp.R66VariableFactory;
import org.waarp.openr66.protocol.utils.ChannelUtils;
import org.waarp.openr66.protocol.utils.R66ShutdownHook;
import org.waarp.openr66.protocol.utils.Version;
import org.waarp.openr66.thrift.R66ThriftServerService;
import org.waarp.snmp.WaarpMOFactory;
import org.waarp.snmp.WaarpSnmpAgent;

/**
 * Configuration class
 * 
 * @author Frederic Bregier
 */
public class Configuration {
    /**
     * Internal Logger
     */
    private static final WaarpLogger logger = WaarpLoggerFactory.getLogger(Configuration.class);

    // Static values
    /**
     * General Configuration object
     */
    public static Configuration configuration = new Configuration();

    public static final String SnmpName = "Waarp OpenR66 SNMP";
    public static final int SnmpPrivateId = 66666;
    public static final int SnmpR66Id = 66;
    public static final String SnmpDefaultAuthor = "Frederic Bregier";
    public static final String SnmpVersion = "Waarp OpenR66 "
            + Version.ID;
    public static final String SnmpDefaultLocalization = "Paris, France";
    public static final int SnmpService = 72;
    /**
     * Time elapse for retry in ms
     */
    public static final long RETRYINMS = 10;

    /**
     * Number of retry before error
     */
    public static final int RETRYNB = 3;

    /**
     * Hack to say Windows or Unix (USR1 not OK on Windows)
     */
    public static boolean ISUNIX;

    /**
     * Default size for buffers (NIO)
     */
    public static final int BUFFERSIZEDEFAULT = 0x10000; // 64K

    /**
     * Time elapse for WRITE OR CLOSE WAIT elaps in ms
     */
    public static final long WAITFORNETOP = 200;

    /**
     * Extension of file during transfer
     */
    public static final String EXT_R66 = ".r66";

    /**
     * Rank to redo when a restart occurs
     */
    public static int RANKRESTART = 30;

    /**
     * FileParameter
     */
    private static final FilesystemBasedFileParameterImpl fileParameter = new FilesystemBasedFileParameterImpl();

    public final R66BusinessFactoryInterface r66BusinessFactory = new R66DefaultBusinessFactory();
    // Global Dynamic values
    /**
     * Version validation
     */
    public boolean extendedProtocol = true;
    /**
     * Global digest
     */
    public boolean globalDigest = true;
    /**
     * White List of allowed Partners to use Business Requests
     */
    public final HashSet<String> businessWhiteSet = new HashSet<String>();
    /**
     * Roles list for identified partners
     */
    public final HashMap<String, RoleDefault> roles = new HashMap<String, RoleDefault>();
    /**
     * Aliases list for identified partners
     */
    public final HashMap<String, String> aliases = new HashMap<String, String>();
    /**
     * reverse Aliases list for identified partners
     */
    public final HashMap<String, String[]> reverseAliases = new HashMap<String, String[]>();
    /**
     * Versions for each HostID
     */
    public final ConcurrentHashMap<String, PartnerConfiguration> versions = new ConcurrentHashMap<String, PartnerConfiguration>();
    /**
     * Actual Host ID
     */
    public String HOST_ID;
    /**
     * Actual SSL Host ID
     */
    public String HOST_SSLID;

    /**
     * Server Administration user name
     */
    public String ADMINNAME = null;
    /**
     * Server Administration Key
     */
    private byte[] SERVERADMINKEY = null;
    /**
     * Server Administration Key file
     */
    public String serverKeyFile = null;
    /**
     * Server Actual Authentication
     */
    public DbHostAuth HOST_AUTH;
    /**
     * Server Actual SSL Authentication
     */
    public DbHostAuth HOST_SSLAUTH;

    /**
     * Default number of threads in pool for Server (true network listeners). Server will change
     * this value on startup if not set. The value should be closed to the number of CPU.
     */
    public int SERVER_THREAD = 8;

    /**
     * Default number of threads in pool for Client. The value is for true client for Executor in
     * the Pipeline for Business logic. The value does not indicate a limit of concurrent clients,
     * but a limit on truly packet concurrent actions.
     */
    public int CLIENT_THREAD = 80;

    /**
     * Default session limit 64Mbit, so up to 16 full simultaneous clients
     */
    public final long DEFAULT_SESSION_LIMIT = 0x800000L;

    /**
     * Default global limit 1024Mbit
     */
    public final long DEFAULT_GLOBAL_LIMIT = 0x8000000L;

    /**
     * Default server port
     */
    public int SERVER_PORT = 6666;

    /**
     * Default SSL server port
     */
    public int SERVER_SSLPORT = 6667;

    /**
     * Default HTTP server port
     */
    public int SERVER_HTTPPORT = 8066;

    /**
     * Default HTTP server port
     */
    public int SERVER_HTTPSPORT = 8067;

    /**
     * Nb of milliseconds after connection is in timeout
     */
    public long TIMEOUTCON = 30000;

    /**
     * Size by default of block size for receive/sending files. Should be a multiple of 8192
     * (maximum = 2^30K due to block limitation to 4 bytes)
     */
    public int BLOCKSIZE = 0x10000; // 64K

    /**
     * Max global memory limit: default is 4GB
     */
    public long maxGlobalMemory = 0x100000000L;

    /**
     * Rest configuration list
     */
    public final List<RestConfiguration> restConfigurations = new ArrayList<RestConfiguration>();

    /**
     * Base Directory
     */
    public String baseDirectory;

    /**
     * In path (receive)
     */
    public String inPath = null;

    /**
     * Out path (send, copy, pending)
     */
    public String outPath = null;

    /**
     * Archive path
     */
    public String archivePath = null;

    /**
     * Working path
     */
    public String workingPath = null;

    /**
     * Config path
     */
    public String configPath = null;

    /**
     * Http Admin base
     */
    public String httpBasePath = "src/main/admin/";

    /**
     * True if the service is going to shutdown
     */
    public volatile boolean isShutdown = false;

    /**
     * Limit in Write byte/s to apply globally to the FTP Server
     */
    public long serverGlobalWriteLimit = DEFAULT_GLOBAL_LIMIT;

    /**
     * Limit in Read byte/s to apply globally to the FTP Server
     */
    public long serverGlobalReadLimit = DEFAULT_GLOBAL_LIMIT;

    /**
     * Limit in Write byte/s to apply by session to the FTP Server
     */
    public long serverChannelWriteLimit = DEFAULT_SESSION_LIMIT;

    /**
     * Limit in Read byte/s to apply by session to the FTP Server
     */
    public long serverChannelReadLimit = DEFAULT_SESSION_LIMIT;

    /**
     * Any limitation on bandwidth active?
     */
    public boolean anyBandwidthLimitation = false;
    /**
     * Delay in ms between two checks
     */
    public long delayLimit = AbstractTrafficShapingHandler.DEFAULT_CHECK_INTERVAL;

    /**
     * Does this OpenR66 server will use and accept SSL connections
     */
    public boolean useSSL = false;
    /**
     * Does this OpenR66 server will use and accept non SSL connections
     */
    public boolean useNOSSL = true;
    /**
     * Algorithm to use for Digest
     */
    public FilesystemBasedDigest.DigestAlgo digest = DigestAlgo.MD5;

    /**
     * Does this OpenR66 server will try to compress HTTP connections
     */
    public boolean useHttpCompression = false;

    /**
     * Does this OpenR66 server will use Waarp LocalExec Daemon for ExecTask and ExecMoveTask
     */
    public boolean useLocalExec = false;

    /**
     * Crypto Key
     */
    public Des cryptoKey = null;
    /**
     * Associated file for CryptoKey
     */
    public String cryptoFile = null;

    /**
     * List of all Server Channels to enable the close call on them using Netty ChannelGroup
     */
    protected ChannelGroup serverChannelGroup = null;
    /**
     * Main bind address in no ssl mode
     */
    protected Channel bindNoSSL = null;
    /**
     * Main bind address in ssl mode
     */
    protected Channel bindSSL = null;

    /**
     * Does the current program running as Server
     */
    public boolean isServer = false;

    /**
     * ExecutorService Other Worker
     */
    protected final ExecutorService execOtherWorker = Executors.newCachedThreadPool(new WaarpThreadFactory(
            "OtherWorker"));

    protected EventLoopGroup bossGroup;
    protected EventLoopGroup workerGroup;
    protected EventLoopGroup handlerGroup;
    protected EventLoopGroup subTaskGroup;
    protected EventLoopGroup localBossGroup;
    protected EventLoopGroup localWorkerGroup;
    protected EventLoopGroup httpBossGroup;
    protected EventLoopGroup httpWorkerGroup;

    /**
     * ExecutorService Scheduled tasks
     */
    protected final ScheduledExecutorService scheduledExecutorService;

    /**
     * Bootstrap for server
     */
    protected ServerBootstrap serverBootstrap = null;

    /**
     * Bootstrap for SSL server
     */
    protected ServerBootstrap serverSslBootstrap = null;
    /**
     * Factory for NON SSL Server
     */
    protected NetworkServerInitializer networkServerInitializer;
    /**
     * Factory for SSL Server
     */
    protected NetworkSslServerInitializer networkSslServerInitializer;

    /**
     * Bootstrap for Http server
     */
    protected ServerBootstrap httpBootstrap = null;
    /**
     * Bootstrap for Https server
     */
    protected ServerBootstrap httpsBootstrap = null;
    /**
     * List of all Http Channels to enable the close call on them using Netty ChannelGroup
     */
    protected ChannelGroup httpChannelGroup = null;

    /**
     * Timer for CloseOpertations
     */
    private final Timer timerCloseOperations =
            new HashedWheelTimer(
                    new WaarpThreadFactory(
                            "TimerClose"),
                    50,
                    TimeUnit.MILLISECONDS,
                    1024);
    /**
     * Global TrafficCounter (set from global configuration)
     */
    protected GlobalTrafficHandler globalTrafficShapingHandler = null;

    /**
     * LocalTransaction
     */
    protected LocalTransaction localTransaction;
    /**
     * InternalRunner
     */
    private InternalRunner internalRunner;
    /**
     * Maximum number of concurrent active transfer by submission.
     */
    public int RUNNER_THREAD = 1000;
    /**
     * Delay in ms between two steps of Commander
     */
    public long delayCommander = 5000;
    /**
     * Delay in ms between two retries
     */
    public long delayRetry = 30000;
    /**
     * Constraint Limit Handler on CPU usage and Connection limitation
     */
    public R66ConstraintLimitHandler constraintLimitHandler = new R66ConstraintLimitHandler();
    /**
     * Do we check Remote Address from DbHost
     */
    public boolean checkRemoteAddress = false;
    /**
     * Do we check address even for Client
     */
    public boolean checkClientAddress = false;
    /**
     * For No Db client, do we saved TaskRunner in a XML
     */
    public boolean saveTaskRunnerWithNoDb = false;
    /**
     * In case of Multiple OpenR66 monitor servers behing a load balancer (HA solution)
     */
    public int multipleMonitors = 1;
    /**
     * Monitoring object
     */
    public Monitoring monitoring = null;
    /**
     * Monitoring: how long in ms to get back in monitoring
     */
    public long pastLimit = 86400000; // 24H
    /**
     * Monitoring: minimal interval in ms before redo real monitoring
     */
    public long minimalDelay = 5000; // 5 seconds
    /**
     * Monitoring: snmp configuration file (empty means no snmp support)
     */
    public String snmpConfig = null;
    /**
     * SNMP Agent (if any)
     */
    public WaarpSnmpAgent agentSnmp = null;
    /**
     * Associated MIB
     */
    public R66PrivateMib r66Mib = null;

    protected volatile boolean configured = false;

    public static WaarpSecureKeyStore waarpSecureKeyStore;

    public static WaarpSslContextFactory waarpSslContextFactory;
    /**
     * Thrift support
     */
    public R66ThriftServerService thriftService;
    public int thriftport = -1;

    public boolean isExecuteErrorBeforeTransferAllowed = true;

    public final ShutdownConfiguration shutdownConfiguration = new ShutdownConfiguration();

    public boolean isHostProxyfied = false;

    public boolean warnOnStartup = true;

    public boolean chrootChecked = true;

    public boolean blacklistBadAuthent = false;

    public int maxfilenamelength = 255;

    public int timeStat = 0;

    public int limitCache = 20000;

    public long timeLimitCache = 180000;

    public Configuration() {
        // Init signal handler
        shutdownConfiguration.timeout = TIMEOUTCON;
        new R66ShutdownHook(shutdownConfiguration);
        computeNbThreads();
        scheduledExecutorService = Executors.newScheduledThreadPool(this.SERVER_THREAD, new WaarpThreadFactory(
                "ScheduledTask"));
        // Init FiniteStates
        R66FiniteDualStates.initR66FiniteStates();
        if (!SystemPropertyUtil.isFileEncodingCorrect()) {
            logger.error("Issue while trying to set UTF-8 as default file encoding: use -Dfile.encoding=UTF-8 as java command argument");
            logger.warn("Currently file.encoding is: " + SystemPropertyUtil.get(SystemPropertyUtil.FILE_ENCODING));
        }
        isExecuteErrorBeforeTransferAllowed = SystemPropertyUtil.getBoolean(
                R66SystemProperties.OPENR66_EXECUTEBEFORETRANSFERRED, true);
        boolean useSpaceSeparator = SystemPropertyUtil.getBoolean(R66SystemProperties.OPENR66_USESPACESEPARATOR, false);
        if (useSpaceSeparator) {
            PartnerConfiguration.SEPARATOR_FIELD = PartnerConfiguration.BLANK_SEPARATOR_FIELD;
        }
        isHostProxyfied = SystemPropertyUtil.getBoolean(R66SystemProperties.OPENR66_ISHOSTPROXYFIED, false);
        warnOnStartup = SystemPropertyUtil.getBoolean(R66SystemProperties.OPENR66_STARTUP_WARNING, true);
        FileBasedConfiguration.checkDatabase = SystemPropertyUtil.getBoolean(R66SystemProperties.OPENR66_STARTUP_DATABASE_CHECK, true);
        chrootChecked = SystemPropertyUtil.getBoolean(R66SystemProperties.OPENR66_CHROOT_CHECKED, true);
        blacklistBadAuthent = SystemPropertyUtil.getBoolean(R66SystemProperties.OPENR66_BLACKLIST_BADAUTHENT, true);
        maxfilenamelength = SystemPropertyUtil.getInt(R66SystemProperties.OPENR66_FILENAME_MAXLENGTH, 255);
        timeStat = SystemPropertyUtil.getInt(R66SystemProperties.OPENR66_TRACE_STATS, 0);
        limitCache = SystemPropertyUtil.getInt(R66SystemProperties.OPENR66_CACHE_LIMIT, 20000);
        if (limitCache <= 100) {
            limitCache = 100;
        }
        timeLimitCache = SystemPropertyUtil.getLong(R66SystemProperties.OPENR66_CACHE_TIMELIMIT, 180000);
        if (timeLimitCache < 1000) {
            timeLimitCache = 1000;
        }
        DbTaskRunner.createLruCache(limitCache, timeLimitCache);
        if (limitCache > 0 && timeLimitCache > 1000) {
            launchInFixedDelay(new CleanLruCache(), timeLimitCache, TimeUnit.MILLISECONDS);
        }
        if (isHostProxyfied) {
            blacklistBadAuthent = false;
        }
    }

    public String toString() {
        String rest = null;
        for (RestConfiguration config : restConfigurations) {
            if (rest == null) {
                rest = (config.REST_ADDRESS != null ? "'" + config.REST_ADDRESS + ":" : "'All:") + config.REST_PORT
                        + "'";
            } else {
                rest += ", " + (config.REST_ADDRESS != null ? "'" + config.REST_ADDRESS + ":" : "'All:")
                        + config.REST_PORT + "'";
            }
        }
        return "Config: { ServerPort: " + SERVER_PORT + ", ServerSslPort: " + SERVER_SSLPORT + ", ServerView: "
                + SERVER_HTTPPORT + ", ServerAdmin: " + SERVER_HTTPSPORT +
                ", ThriftPort: " + (thriftport > 0 ? thriftport : "'NoThriftSupport'") + ", RestAddress: ["
                + (rest != null ? rest : "'NoRestSupport'") + "]" +
                ", TimeOut: " + TIMEOUTCON + ", BaseDir: '" + baseDirectory + "', DigestAlgo: '" + digest.name
                + "', checkRemote: " + checkRemoteAddress +
                ", checkClient: " + checkClientAddress + ", snmpActive: " + (agentSnmp != null) + ", chrootChecked: "
                + chrootChecked +
                ", blacklist: " + blacklistBadAuthent + ", isHostProxified: " + isHostProxyfied + "}";
    }

    /**
     * Configure the pipeline for client (to be called only once)
     */
    public void pipelineInit() {
        if (configured) {
            return;
        }
        workerGroup = new NioEventLoopGroup(CLIENT_THREAD * 4, new WaarpThreadFactory("Worker"));
        handlerGroup = new NioEventLoopGroup(CLIENT_THREAD * 2, new WaarpThreadFactory("Handler"));
        subTaskGroup = new NioEventLoopGroup(CLIENT_THREAD, new WaarpThreadFactory("SubTask"));
        localBossGroup = new NioEventLoopGroup(CLIENT_THREAD, new WaarpThreadFactory("LocalBoss"));
        localWorkerGroup = new NioEventLoopGroup(CLIENT_THREAD, new WaarpThreadFactory("LocalWorker"));
        localTransaction = new LocalTransaction();
        WaarpLoggerFactory.setDefaultFactory(WaarpLoggerFactory.getDefaultFactory());
        if (warnOnStartup) {
            logger.warn("Server Thread: " + SERVER_THREAD + " Client Thread: " + CLIENT_THREAD
                    + " Runner Thread: " + RUNNER_THREAD);
        } else {
            logger.info("Server Thread: " + SERVER_THREAD + " Client Thread: " + CLIENT_THREAD
                    + " Runner Thread: " + RUNNER_THREAD);
        }
        logger.info("Current launched threads: " + ManagementFactory.getThreadMXBean().getThreadCount());
        if (useLocalExec) {
            LocalExecClient.initialize();
        }
        configured = true;
    }

    public void serverPipelineInit() {
        bossGroup = new NioEventLoopGroup(SERVER_THREAD * 2, new WaarpThreadFactory("Boss", false));
        httpBossGroup = new NioEventLoopGroup(SERVER_THREAD * 3, new WaarpThreadFactory("HttpBoss"));
        httpWorkerGroup = new NioEventLoopGroup(SERVER_THREAD * 10, new WaarpThreadFactory("HttpWorker"));
    }

    /**
     * Startup the server
     * 
     * @throws WaarpDatabaseSqlException
     * @throws WaarpDatabaseNoConnectionException
     */
    public void serverStartup() throws WaarpDatabaseNoConnectionException,
            WaarpDatabaseSqlException {
        isServer = true;
        if (blacklistBadAuthent) {
            blacklistBadAuthent = !DbHostAuth.hasProxifiedHosts(DbConstant.admin.session);
        }
        shutdownConfiguration.timeout = TIMEOUTCON;
        if (timeLimitCache < TIMEOUTCON * 10) {
            timeLimitCache = TIMEOUTCON * 10;
            DbTaskRunner.updateLruCacheTimeout(timeLimitCache);
        }
        R66ShutdownHook.addShutdownHook();
        logger.debug("Use NoSSL: " + useNOSSL + " Use SSL: " + useSSL);
        if ((!useNOSSL) && (!useSSL)) {
            logger.error(Messages.getString("Configuration.NoSSL")); //$NON-NLS-1$
            System.exit(-1);
        }
        pipelineInit();
        serverPipelineInit();
        r66Startup();
        startHttpSupport();
        startMonitoring();
        launchStatistics();
        startRestSupport();

        logger.info("Current launched threads: " + ManagementFactory.getThreadMXBean().getThreadCount());
    }

    /**
     * Used to log statistics information regularly
     */
    public void launchStatistics() {
        if (timeStat > 0) {
            launchInFixedDelay(new UsageStatistic(), timeStat, TimeUnit.SECONDS);
        }
    }

    public void r66Startup() throws WaarpDatabaseNoConnectionException, WaarpDatabaseSqlException {
        logger.info(Messages.getString("Configuration.Start") + SERVER_PORT + ":" + useNOSSL + ":" + HOST_ID + //$NON-NLS-1$
                " " + SERVER_SSLPORT + ":" + useSSL + ":" + HOST_SSLID);
        // add into configuration
        this.constraintLimitHandler.setServer(true);
        // Global Server
        serverChannelGroup = new DefaultChannelGroup("OpenR66", subTaskGroup.next());
        if (useNOSSL) {
            serverBootstrap = new ServerBootstrap();
            WaarpNettyUtil.setServerBootstrap(serverBootstrap, bossGroup, workerGroup, (int) TIMEOUTCON);
            networkServerInitializer = new NetworkServerInitializer(true);
            serverBootstrap.childHandler(networkServerInitializer);
            ChannelFuture future = serverBootstrap.bind(new InetSocketAddress(SERVER_PORT)).awaitUninterruptibly();
            if (future.isSuccess()) {
                bindNoSSL = future.channel();
                serverChannelGroup.add(bindNoSSL);
            } else {
                logger.warn(Messages.getString("Configuration.NOSSLDeactivated")); //$NON-NLS-1$
            }
        } else {
            networkServerInitializer = null;
            logger.warn(Messages.getString("Configuration.NOSSLDeactivated")); //$NON-NLS-1$
        }

        if (useSSL && HOST_SSLID != null) {
            serverSslBootstrap = new ServerBootstrap();
            WaarpNettyUtil.setServerBootstrap(serverSslBootstrap, bossGroup, workerGroup, (int) TIMEOUTCON);
            networkSslServerInitializer = new NetworkSslServerInitializer(false);
            serverSslBootstrap.childHandler(networkSslServerInitializer);
            ChannelFuture future = serverSslBootstrap.bind(new InetSocketAddress(SERVER_SSLPORT))
                    .awaitUninterruptibly();
            if (future.isSuccess()) {
                bindSSL = future.channel();
                serverChannelGroup.add(bindSSL);
            } else {
                logger.warn(Messages.getString("Configuration.SSLMODEDeactivated")); //$NON-NLS-1$
            }
        } else {
            networkSslServerInitializer = null;
            logger.warn(Messages.getString("Configuration.SSLMODEDeactivated")); //$NON-NLS-1$
        }

        // Factory for TrafficShapingHandler
        globalTrafficShapingHandler = new GlobalTrafficHandler(subTaskGroup, serverGlobalWriteLimit,
                serverGlobalReadLimit, serverChannelWriteLimit, serverChannelReadLimit, delayLimit);
        this.constraintLimitHandler.setHandler(globalTrafficShapingHandler);

        // Now start the InternalRunner
        internalRunner = new InternalRunner();

        if (thriftport > 0) {
            thriftService = new R66ThriftServerService(new WaarpFuture(true), thriftport);
            execOtherWorker.execute(thriftService);
            thriftService.awaitInitialization();
        } else {
            thriftService = null;
        }
    }

    public void startHttpSupport() {
        // Now start the HTTP support
        logger.info(Messages.getString("Configuration.HTTPStart") + SERVER_HTTPPORT + //$NON-NLS-1$
                " HTTPS: " + SERVER_HTTPSPORT);
        httpChannelGroup = new DefaultChannelGroup("HttpOpenR66", subTaskGroup.next());
        // Configure the server.
        httpBootstrap = new ServerBootstrap();
        WaarpNettyUtil.setServerBootstrap(httpBootstrap, httpBossGroup, httpWorkerGroup, (int) TIMEOUTCON);
        // Set up the event pipeline factory.
        httpBootstrap.childHandler(new HttpInitializer(useHttpCompression));
        // Bind and start to accept incoming connections.
        if (SERVER_HTTPPORT > 0) {
            ChannelFuture future = httpBootstrap.bind(new InetSocketAddress(SERVER_HTTPPORT)).awaitUninterruptibly();
            if (future.isSuccess()) {
                httpChannelGroup.add(future.channel());
            }
        }
        // Now start the HTTPS support
        // Configure the server.
        httpsBootstrap = new ServerBootstrap();
        // Set up the event pipeline factory.
        WaarpNettyUtil.setServerBootstrap(httpsBootstrap, httpBossGroup, httpWorkerGroup, (int) TIMEOUTCON);
        httpsBootstrap.childHandler(new HttpSslInitializer(useHttpCompression, false));
        // Bind and start to accept incoming connections.
        if (SERVER_HTTPSPORT > 0) {
            ChannelFuture future = httpsBootstrap.bind(new InetSocketAddress(SERVER_HTTPSPORT)).awaitUninterruptibly();
            if (future.isSuccess()) {
                httpChannelGroup.add(future.channel());
            }
        }
    }

    public void startRestSupport() {
        HttpRestR66Handler.initialize(baseDirectory + "/" + workingPath + "/httptemp");
        for (RestConfiguration config : restConfigurations) {
            HttpRestR66Handler.initializeService(config);
            logger.info(Messages.getString("Configuration.HTTPStart") + " (REST Support) " + config.toString());
        }
    }

    public void startMonitoring() throws WaarpDatabaseSqlException {
        monitoring = new Monitoring(pastLimit, minimalDelay, null);
        if (snmpConfig != null) {
            int snmpPortShow = (useNOSSL ? SERVER_PORT : SERVER_SSLPORT);
            R66PrivateMib r66Mib =
                    new R66PrivateMib(SnmpName,
                            snmpPortShow,
                            SnmpPrivateId,
                            SnmpR66Id,
                            SnmpDefaultAuthor,
                            SnmpVersion,
                            SnmpDefaultLocalization,
                            SnmpService);
            WaarpMOFactory.factory = new R66VariableFactory();
            agentSnmp = new WaarpSnmpAgent(new File(snmpConfig), monitoring, r66Mib);
            try {
                agentSnmp.start();
            } catch (IOException e) {
                throw new WaarpDatabaseSqlException(Messages.getString("Configuration.SNMPError"), e); //$NON-NLS-1$
            }
            this.r66Mib = r66Mib;
        }
    }

    public InternalRunner getInternalRunner() {
        return internalRunner;
    }

    /**
     * Prepare the server to stop
     * 
     * To be called early before other stuff will be closed
     */
    public void prepareServerStop() {
        if (thriftService != null) {
            thriftService.releaseResources();
        }
        if (internalRunner != null) {
            internalRunner.prepareStopInternalRunner();
        }
    }

    /**
     * Unbind network connectors
     */
    public void unbindServer() {
        if (bindNoSSL != null) {
            bindNoSSL.close();
            bindNoSSL = null;
        }
        if (bindSSL != null) {
            bindSSL.close();
            bindSSL = null;
        }
    }

    public void shutdownGracefully() {
        if (bossGroup != null && !bossGroup.isShuttingDown()) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null && !workerGroup.isShuttingDown()) {
            workerGroup.shutdownGracefully();
        }
        if (handlerGroup != null && !handlerGroup.isShuttingDown()) {
            handlerGroup.shutdownGracefully();
        }
        if (httpBossGroup != null && !httpBossGroup.isShuttingDown()) {
            httpBossGroup.shutdownGracefully();
        }
        if (httpWorkerGroup != null && !httpWorkerGroup.isShuttingDown()) {
            httpWorkerGroup.shutdownGracefully();
        }
        if (handlerGroup != null && !handlerGroup.isShuttingDown()) {
            handlerGroup.shutdownGracefully();
        }
        if (subTaskGroup != null && !subTaskGroup.isShuttingDown()) {
            subTaskGroup.shutdownGracefully();
        }
        if (localBossGroup != null && !localBossGroup.isShuttingDown()) {
            localBossGroup.shutdownGracefully();
        }
        if (localWorkerGroup != null && !localWorkerGroup.isShuttingDown()) {
            localWorkerGroup.shutdownGracefully();
        }
    }

    public void shutdownQuickly() {
        if (bossGroup != null && !bossGroup.isShuttingDown()) {
            bossGroup.shutdownGracefully(10, 10, TimeUnit.MILLISECONDS);
        }
        if (workerGroup != null && !workerGroup.isShuttingDown()) {
            workerGroup.shutdownGracefully(10, 10, TimeUnit.MILLISECONDS);
        }
        if (handlerGroup != null && !handlerGroup.isShuttingDown()) {
            handlerGroup.shutdownGracefully(10, 10, TimeUnit.MILLISECONDS);
        }
        if (httpBossGroup != null && !httpBossGroup.isShuttingDown()) {
            httpBossGroup.shutdownGracefully(10, 10, TimeUnit.MILLISECONDS);
        }
        if (httpWorkerGroup != null && !httpWorkerGroup.isShuttingDown()) {
            httpWorkerGroup.shutdownGracefully(10, 10, TimeUnit.MILLISECONDS);
        }
        if (handlerGroup != null && !handlerGroup.isShuttingDown()) {
            handlerGroup.shutdownGracefully(10, 10, TimeUnit.MILLISECONDS);
        }
        if (subTaskGroup != null && !subTaskGroup.isShuttingDown()) {
            subTaskGroup.shutdownGracefully(10, 10, TimeUnit.MILLISECONDS);
        }
        if (localBossGroup != null && !localBossGroup.isShuttingDown()) {
            localBossGroup.shutdownGracefully(10, 10, TimeUnit.MILLISECONDS);
        }
        if (localWorkerGroup != null && !localWorkerGroup.isShuttingDown()) {
            localWorkerGroup.shutdownGracefully(10, 10, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Stops the server
     * 
     * To be called after all other stuff are closed (channels, connections)
     */
    public void serverStop() {
        WaarpSslUtility.forceCloseAllSslChannels();
        if (internalRunner != null) {
            internalRunner.stopInternalRunner();
        }
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdown();
        }
        if (agentSnmp != null) {
            agentSnmp.stop();
        } else if (monitoring != null) {
            monitoring.releaseResources();
            monitoring = null;
        }
        shutdownGracefully();
        if (execOtherWorker != null) {
            execOtherWorker.shutdownNow();
        }
        if (timerCloseOperations != null) {
            timerCloseOperations.stop();
        }
    }

    /**
     * To be called after all other stuff are closed for Client
     */
    public void clientStop() {
        clientStop(true);
    }
    /**
     * To be called after all other stuff are closed for Client
     * @param shutdownQuickly For client only, shall be true to speedup the end of the process
     */
    public void clientStop(boolean shutdownQuickly) {
        WaarpSslUtility.forceCloseAllSslChannels();
        if (!Configuration.configuration.isServer) {
            ChannelUtils.stopLogger();
        }
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdown();
        }
        if (localTransaction != null) {
            localTransaction.closeAll();
            localTransaction = null;
        }
        if (shutdownQuickly) {
            
        } else {
            shutdownGracefully();
        }
        if (useLocalExec) {
            LocalExecClient.releaseResources();
        }
        if (timerCloseOperations != null) {
            timerCloseOperations.stop();
        }
        r66BusinessFactory.releaseResources();
    }

    /**
     * Try to reload the Commander
     * 
     * @return True if reloaded, else in error
     */
    public boolean reloadCommanderDelay() {
        if (internalRunner != null) {
            try {
                internalRunner.reloadInternalRunner();
                return true;
            } catch (WaarpDatabaseNoConnectionException e) {
            } catch (WaarpDatabaseSqlException e) {
            }
        }
        return false;
    }

    /**
     * submit a task in a fixed delay
     * 
     * @param thread
     * @param delay
     * @param unit
     */
    public void launchInFixedDelay(Thread thread, long delay, TimeUnit unit) {
        scheduledExecutorService.schedule(thread, delay, unit);
    }

    /**
     * Reset the global monitor for bandwidth limitation and change future channel monitors
     * 
     * @param writeGlobalLimit
     * @param readGlobalLimit
     * @param writeSessionLimit
     * @param readSessionLimit
     * @param delayLimit
     */
    public void changeNetworkLimit(long writeGlobalLimit, long readGlobalLimit,
            long writeSessionLimit, long readSessionLimit, long delayLimit) {
        long newWriteLimit = writeGlobalLimit > 1024 ? writeGlobalLimit
                : serverGlobalWriteLimit;
        if (writeGlobalLimit <= 0) {
            newWriteLimit = 0;
        }
        long newReadLimit = readGlobalLimit > 1024 ? readGlobalLimit : serverGlobalReadLimit;
        if (readGlobalLimit <= 0) {
            newReadLimit = 0;
        }
        serverGlobalReadLimit = newReadLimit;
        serverGlobalWriteLimit = newWriteLimit;
        this.delayLimit = delayLimit;
        if (globalTrafficShapingHandler != null) {
            globalTrafficShapingHandler.configure(serverGlobalWriteLimit, serverGlobalReadLimit, delayLimit);
            logger.warn(Messages.getString("Configuration.BandwidthChange"), globalTrafficShapingHandler); //$NON-NLS-1$
        }
        newWriteLimit = writeSessionLimit > 1024 ? writeSessionLimit
                : serverChannelWriteLimit;
        if (writeSessionLimit <= 0) {
            newWriteLimit = 0;
        }
        newReadLimit = readSessionLimit > 1024 ? readSessionLimit
                : serverChannelReadLimit;
        if (readSessionLimit <= 0) {
            newReadLimit = 0;
        }
        serverChannelReadLimit = newReadLimit;
        serverChannelWriteLimit = newWriteLimit;
        if (globalTrafficShapingHandler != null && globalTrafficShapingHandler instanceof GlobalChannelTrafficShapingHandler) {
            ((GlobalChannelTrafficShapingHandler) globalTrafficShapingHandler).configureChannel(serverChannelWriteLimit, serverChannelReadLimit);
        }
        anyBandwidthLimitation = (serverGlobalReadLimit > 0 || serverGlobalWriteLimit > 0 ||
                serverChannelReadLimit > 0 || serverChannelWriteLimit > 0);
    }

    /**
     * Compute number of threads for both client and server from the real number of available
     * processors (double + 1) if the value is less than 32 threads else (available +1).
     */
    public void computeNbThreads() {
        int nb = Runtime.getRuntime().availableProcessors() * 2 + 1;
        if (nb > 32) {
            nb = Runtime.getRuntime().availableProcessors() + 1;
        }
        if (SERVER_THREAD < nb) {
            logger.info(Messages.getString("Configuration.ThreadNumberChange") + nb); //$NON-NLS-1$
            SERVER_THREAD = nb;
            CLIENT_THREAD = SERVER_THREAD * 10;
        } else if (CLIENT_THREAD < nb) {
            CLIENT_THREAD = nb;
        }
    }

    /**
     * @return a new ChannelTrafficShapingHandler
     * @throws OpenR66ProtocolNoDataException
     */
    public ChannelTrafficShapingHandler newChannelTrafficShapingHandler()
            throws OpenR66ProtocolNoDataException {
        if (serverChannelReadLimit == 0 && serverChannelWriteLimit == 0) {
            throw new OpenR66ProtocolNoDataException(Messages.getString("Configuration.ExcNoLimit")); //$NON-NLS-1$
        }
        if (globalTrafficShapingHandler instanceof GlobalChannelTrafficShapingHandler) {
            throw new OpenR66ProtocolNoDataException("Already included through GlobalChannelTSH");
        }
        return new ChannelTrafficHandler(serverChannelWriteLimit, serverChannelReadLimit, delayLimit);
    }

    /**
     * 
     * @return an executorService to be used for any thread
     */
    public ExecutorService getExecutorService() {
        return execOtherWorker;
    }

    public Timer getTimerClose() {
        return timerCloseOperations;
    }

    /**
     * @return the globalTrafficShapingHandler
     */
    public GlobalTrafficHandler getGlobalTrafficShapingHandler() {
        return globalTrafficShapingHandler;
    }

    /**
     * @return the serverChannelGroup
     */
    public ChannelGroup getServerChannelGroup() {
        return serverChannelGroup;
    }

    /**
     * @return the httpChannelGroup
     */
    public ChannelGroup getHttpChannelGroup() {
        return httpChannelGroup;
    }

    /**
     * @return the serverPipelineExecutor
     */
    public EventLoopGroup getNetworkWorkerGroup() {
        return workerGroup;
    }

    /**
     * @return the localBossGroup
     */
    public EventLoopGroup getLocalBossGroup() {
        return localBossGroup;
    }

    /**
     * @return the localWorkerGroup
     */
    public EventLoopGroup getLocalWorkerGroup() {
        return localWorkerGroup;
    }

    /**
     * @return the serverPipelineExecutor
     */
    public EventLoopGroup getHandlerGroup() {
        return handlerGroup;
    }

    /**
     * @return the subTaskGroup
     */
    public EventLoopGroup getSubTaskGroup() {
        return subTaskGroup;
    }

    /**
     * @return the httpBossGroup
     */
    public EventLoopGroup getHttpBossGroup() {
        return httpBossGroup;
    }

    /**
     * @return the httpWorkerGroup
     */
    public EventLoopGroup getHttpWorkerGroup() {
        return httpWorkerGroup;
    }

    /**
     * @return the localTransaction
     */
    public LocalTransaction getLocalTransaction() {
        return localTransaction;
    }

    /**
     * 
     * @return the FilesystemBasedFileParameterImpl
     */
    public static FilesystemBasedFileParameterImpl getFileParameter() {
        return fileParameter;
    }

    /**
     * @return the SERVERADMINKEY
     */
    public byte[] getSERVERADMINKEY() {
        return SERVERADMINKEY;
    }

    /**
     * Is the given key a valid one
     * 
     * @param newkey
     * @return True if the key is valid (or any key is valid)
     */
    public boolean isKeyValid(byte[] newkey) {
        if (newkey == null) {
            return false;
        }
        return FilesystemBasedDigest.equalPasswd(SERVERADMINKEY, newkey);
    }

    /**
     * @param serverkey
     *            the SERVERADMINKEY to set
     */
    public void setSERVERKEY(byte[] serverkey) {
        SERVERADMINKEY = serverkey;
    }

    /**
     * 
     * @param isSSL
     * @return the HostId according to SSL
     * @throws OpenR66ProtocolNoSslException
     */
    public String getHostId(boolean isSSL) throws OpenR66ProtocolNoSslException {
        if (isSSL) {
            if (HOST_SSLID == null) {
                throw new OpenR66ProtocolNoSslException(Messages.getString("Configuration.ExcNoSSL")); //$NON-NLS-1$
            }
            return HOST_SSLID;
        } else {
            return HOST_ID;
        }
    }

    /**
     * 
     * @param dbSession
     * @param remoteHost
     * @return the HostId according to remoteHost (and its SSL status)
     * @throws WaarpDatabaseException
     */
    public String getHostId(DbSession dbSession, String remoteHost) throws WaarpDatabaseException {
        DbHostAuth hostAuth = new DbHostAuth(dbSession, remoteHost);
        try {
            return Configuration.configuration.getHostId(hostAuth.isSsl());
        } catch (OpenR66ProtocolNoSslException e) {
            throw new WaarpDatabaseException(e);
        }
    }

    private static class UsageStatistic extends Thread {

        @Override
        public void run() {
            logger.warn(hashStatus());
            Configuration.configuration.launchInFixedDelay(this, 10, TimeUnit.SECONDS);
        }

    }

    public static String hashStatus() {
        String result = "\n";
        try {
            result += configuration.localTransaction.hashStatus() + "\n";
        } catch (Exception e) {
            logger.warn("Issue while debugging", e);
        }
        try {
            result += ClientRunner.hashStatus() + "\n";
        } catch (Exception e) {
            logger.warn("Issue while debugging", e);
        }
        try {
            result += DbTaskRunner.hashStatus() + "\n";
        } catch (Exception e) {
            logger.warn("Issue while debugging", e);
        }
        try {
            result += HttpSslHandler.hashStatus() + "\n";
        } catch (Exception e) {
            logger.warn("Issue while debugging", e);
        }
        try {
            result += NetworkTransaction.hashStatus();
        } catch (Exception e) {
            logger.warn("Issue while debugging", e);
        }
        return result;
    }

    private static class CleanLruCache extends Thread {

        @Override
        public void run() {
            int nb = DbTaskRunner.clearCache();
            logger.info("Clear Cache: " + nb);
            Configuration.configuration.launchInFixedDelay(this, Configuration.configuration.timeLimitCache,
                    TimeUnit.MILLISECONDS);
        }

    }
}
