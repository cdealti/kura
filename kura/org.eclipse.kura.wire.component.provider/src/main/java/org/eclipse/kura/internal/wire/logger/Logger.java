/*******************************************************************************
 * Copyright (c) 2016, 2017 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Eurotech
 *  Amit Kumar Mondal
 *
 *******************************************************************************/
package org.eclipse.kura.internal.wire.logger;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static org.eclipse.kura.internal.wire.logger.LoggingVerbosity.QUIET;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.configuration.ConfigurationService;
import org.eclipse.kura.localization.LocalizationAdapter;
import org.eclipse.kura.localization.resources.WireMessages;
import org.eclipse.kura.type.TypedValue;
import org.eclipse.kura.wire.WireEnvelope;
import org.eclipse.kura.wire.WireHelperService;
import org.eclipse.kura.wire.WireReceiver;
import org.eclipse.kura.wire.WireRecord;
import org.eclipse.kura.wire.WireSupport;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.wireadmin.Wire;
import org.slf4j.LoggerFactory;

/**
 * The Class Logger is the specific Wire Component to log a list of {@link WireRecord}s
 * as received in {@link WireEnvelope}
 */
public final class Logger implements WireReceiver, ConfigurableComponent {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(Logger.class);

    private static final WireMessages message = LocalizationAdapter.adapt(WireMessages.class);

    private static final String DEFAULT_LOG_LEVEL = QUIET.name();

    private static final String PROP_LOG_LEVEL = "log.verbosity";

    private static final String PROP_FILE_MAX_ROWS = "log.max.rows";

    private static final int DEFAULT_FILE_MAX_ROWS = 40000;

    private static final String PROP_CSV_SEPARATOR = "log.csv.separator";

    private static final String DEFAULT_CSV_SEPARATOR = ",";

    private static final String PROP_LOG_DIRECTORY = "log.directory";

    private static final String DEFAULT_LOG_DIRECTORY = "/var/log/";

    private static final String PROP_LOG_FILE_PREFIX = "log.file.prefix";

    private static final String DEFAULT_LOG_FILE_PREFIX = "wirelog";

    private static final String PROP_MAX_LOGS = "log.max.count";

    private static final int DEFAULT_MAX_LOGS = 10;

    private volatile WireHelperService wireHelperService;

    private WireSupport wireSupport;

    private Map<String, Object> properties;

    private List<WireEnvelope> envelopes;

    private Set<String> columns;

    /**
     * Binds the Wire Helper Service.
     *
     * @param wireHelperService
     *            the new Wire Helper Service
     */
    public void bindWireHelperService(final WireHelperService wireHelperService) {
        if (isNull(this.wireHelperService)) {
            this.wireHelperService = wireHelperService;
        }
    }

    /**
     * Unbinds the Wire Helper Service.
     *
     * @param wireHelperService
     *            the new Wire Helper Service
     */
    public void unbindWireHelperService(final WireHelperService wireHelperService) {
        if (this.wireHelperService == wireHelperService) {
            this.wireHelperService = null;
        }
    }

    /**
     * OSGi Service Component callback for activation.
     *
     * @param componentContext
     *            the component context
     * @param properties
     *            the properties
     */
    protected void activate(final ComponentContext componentContext, final Map<String, Object> properties) {
        this.properties = properties;
        this.wireSupport = this.wireHelperService.newWireSupport(this);
        this.envelopes = new LinkedList<>();
        this.columns = new HashSet<>();
        logger.debug(message.activatingLoggerDone());
    }

    /**
     * OSGi Service Component callback for updating.
     *
     * @param properties
     *            the updated properties
     */
    public void updated(final Map<String, Object> properties) {
        logger.debug(message.updatingLogger());
        this.properties = properties;
        logger.debug(message.updatingLoggerDone());
    }

    /**
     * OSGi Service Component callback for deactivation.
     *
     * @param componentContext
     *            the component context
     */
    protected void deactivate(final ComponentContext componentContext) {
        logger.debug(message.deactivatingLogger());
        // remained for debugging purposes
        logger.debug(message.deactivatingLoggerDone());
    }

    /** {@inheritDoc} */
    @Override
    public void onWireReceive(final WireEnvelope wireEnvelope) {
        if (wireEnvelope == null) {
            return;
        }
        updateColumns(wireEnvelope);
        addEnvelope(wireEnvelope);
    }

    private synchronized void addEnvelope(final WireEnvelope envelope) {
        envelopes.add(envelope);
        if (envelopes.size() >= getFileMaxRows()) {
            writeEnvelopes();
            envelopes.clear();
            garbageCollectionOldLogs();
        }
    }

    private void writeEnvelopes() {
        long lid = newLogID();

        // FIXME: encoding
        try (final PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(getLogFile(lid))))) {
            writeHeader(out);
            writeLines(out);
        } catch (IOException e) {
            // FIXME:internationalization
            logger.error("Failed to write log file", e);
        }
    }

    private void writeLines(final PrintWriter out) {
        for (WireEnvelope envelope : envelopes) {
            writeCsvLines(out, envelope);
        }
    }

    private void writeCsvLines(final PrintWriter out, final WireEnvelope envelope) {
        final List<WireRecord> records = envelope.getRecords();
        if (records != null) {
            for (WireRecord record : records) { // Typically there's only one record per envelope
                final Map<String, TypedValue<?>> props = record.getProperties();
                if (props != null) {
                    StringBuilder sb = new StringBuilder();
                    // we assume that the iteration order is the same if the set is not modified in between
                    for (String column : columns) {
                        final TypedValue<?> propValue = props.get(column);
                        if (propValue != null) {
                            sb.append(propValue.getValue());
                        }
                        sb.append(getCsvSeparator());
                    }
                    sb.setLength(sb.length() - 1); // remove last separator
                    out.println(sb.toString());
                }
            }
        }
    }

    private void writeHeader(final PrintWriter out) {
        StringBuilder sb = new StringBuilder();
        // we assume that the iteration order is the same as the one above
        // FIXME: code duplicated from above (use lambda)
        for (String column : columns) {
            sb.append(column);
            sb.append(getCsvSeparator());
        }
        sb.setLength(sb.length() - 1); // remove last separator
        out.println(sb.toString());
    }

    private synchronized void updateColumns(final WireEnvelope envelopes) {
        final List<WireRecord> records = envelopes.getRecords();
        if (records != null) {
            for (WireRecord record : records) { // typically there's only one record
                final Set<String> keys = record.getProperties().keySet();
                columns.addAll(keys);
            }
        }
    }

    private void garbageCollectionOldLogs() {
        // get the current logs and compared with the maximum number we
        // need to keep
        TreeSet<Long> lids = getLogs();

        int currCount = lids.size();
        int maxCount = getMaxLogs();
        while (currCount > maxCount && !lids.isEmpty()) { // stop if count reached or no more snapshots remain

            // preserve log ID 0 as this will be considered the seeding
            // one.
            long lid = lids.pollFirst();
            if (lid != 0) {
                File fLog = getLogFile(lid);
                if (fLog != null && fLog.exists()) {
                    logger.info("Logs Garbage Collector. Deleting {}", fLog.getAbsolutePath());
                    fLog.delete();
                    currCount--;
                }
            }
        }
    }

    private long newLogID() {
        long lid = new Date().getTime();

        // Do not save the log in the past
        Set<Long> logIDs = getLogs();
        if (!logIDs.isEmpty()) {
            Long[] logs = logIDs.toArray(new Long[] {});
            Long lastestID = logs[logIDs.size() - 1];

            if (lastestID != null && lid <= lastestID) {
                logger.warn("Log ID: {} is in the past. Adjusting ID to: {} + 1", lid, lastestID);
                lid = lastestID + 1;
            }
        }
        return lid;
    }

    private int getFileMaxRows() {
        int result = DEFAULT_FILE_MAX_ROWS;
        final Object o = this.properties.get(PROP_FILE_MAX_ROWS);
        if (o instanceof Integer) {
            result = (int) o;
        }
        return result;
    }

    private String getCsvSeparator() {
        String result = DEFAULT_CSV_SEPARATOR;
        final Object o = this.properties.get(PROP_CSV_SEPARATOR);
        if (o instanceof Character) {
            result = (String) o;
        }
        return result;
    }

    private String getLogDirectory() {
        String result = DEFAULT_LOG_DIRECTORY;
        final Object o = this.properties.get(PROP_LOG_DIRECTORY);
        if (o instanceof String) {
            result = (String) o;
        }
        return result;
    }

    private String getLogFilePrefix() {
        String result = DEFAULT_LOG_FILE_PREFIX;
        final String pid = getOwnPid();
        if (pid != null && !pid.isEmpty()) {
            result = pid;
        }
        final Object o = this.properties.get(PROP_LOG_FILE_PREFIX);
        if (o instanceof String) {
            final String configuredPrefix = (String) o;
            if (!configuredPrefix.isEmpty()) {
                result = configuredPrefix;
            }
        }
        return result;
    }

    private int getMaxLogs() {
        int result = DEFAULT_MAX_LOGS;
        final Object o = this.properties.get(PROP_MAX_LOGS);
        if (o instanceof Integer) {
            result = (Integer) o;
        }
        return result;
    }

    private String getOwnPid() {
        String result = null;
        final Object o = this.properties.get(ConfigurationService.KURA_SERVICE_PID);
        if (o instanceof String) {
            result = (String) o;
        }
        return result;
    }

    private TreeSet<Long> getLogs() {
        // keeps the list of logs ordered
        TreeSet<Long> ids = new TreeSet<>();
        String logDir = getLogDirectory();
        if (logDir != null) {
            File fLogDir = new File(logDir);
            File[] files = fLogDir.listFiles();
            if (files != null) {
                Pattern p = Pattern.compile(getLogFilePrefix() + "_([0-9]+)\\.csv");
                for (File file : files) {
                    Matcher m = p.matcher(file.getName());
                    if (m.matches()) {
                        ids.add(Long.parseLong(m.group(1)));
                    }
                }
            }
        }
        return ids;
    }

    private File getLogFile(long id) {
        String logDir = getLogDirectory();

        if (logDir == null) {
            return null;
        }

        StringBuilder sbDir = new StringBuilder(logDir);
        sbDir.append(File.separator).append(getLogFilePrefix()).append("_").append(id).append(".csv");

        String log = sbDir.toString();
        return new File(log);
    }

    private String getLoggingLevel() {
        String logLevel = DEFAULT_LOG_LEVEL;
        final Object configuredLogLevel = this.properties.get(PROP_LOG_LEVEL);
        if (nonNull(configuredLogLevel) && configuredLogLevel instanceof String) {
            logLevel = String.valueOf(configuredLogLevel);
        }
        return logLevel;
    }

    /** {@inheritDoc} */
    @Override
    public void producersConnected(final Wire[] wires) {
        requireNonNull(wires, message.wiresNonNull());
        this.wireSupport.producersConnected(wires);
    }

    /** {@inheritDoc} */
    @Override
    public void updated(final Wire wire, final Object value) {
        this.wireSupport.updated(wire, value);
    }
}