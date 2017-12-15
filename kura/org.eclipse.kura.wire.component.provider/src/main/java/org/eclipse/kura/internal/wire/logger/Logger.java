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
import static java.util.Objects.requireNonNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.kura.configuration.ConfigurableComponent;
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

    private volatile WireHelperService wireHelperService;

    private WireSupport wireSupport;

    private LoggerOptions options;

    private PrintWriter printWriter;

    private List<WireEnvelope> envelopes;

    private int lineCount;

    private Set<String> columns;

    private boolean columnsChanged;

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
        this.wireSupport = this.wireHelperService.newWireSupport(this);
        this.options = new LoggerOptions(properties);
        this.envelopes = new LinkedList<>();
        this.columns = new LinkedHashSet<>();
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
        writeRemainingEnvelopes();
        // updating the component also resets the CSV column set learnt so far
        clearColumns();
        this.options = new LoggerOptions(properties);
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
        writeRemainingEnvelopes();
        logger.debug(message.deactivatingLoggerDone());
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void onWireReceive(final WireEnvelope wireEnvelope) {
        if (wireEnvelope == null) {
            return;
        }
        processEnvelope(wireEnvelope);
    }

    private void processEnvelope(final WireEnvelope envelope) {

        columnsChanged = updateColumns(envelope) || columnsChanged;

        envelopes.add(envelope);

        if (envelopes.size() >= options.getMaxInMemoryEnvelopes()) {
            if (columnsChanged) {
                closeWriter();
                columnsChanged = false;
            }

            writeEnvelopes();
            if (lineCount >= options.getFileMaxRows()) {
                closeWriter();
            }
        }
    }

    private void writeEnvelopes() {
        if (envelopes.isEmpty()) {
            return;
        }

        if (printWriter == null) {
            FileOutputStream fos;
            try {
                long lid = newLogID();
                fos = new FileOutputStream(getLogFile(lid));
            } catch (IOException e) {
                logger.error("Failed to create CSV file", e);
                return;
            }

            printWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8),
                    options.getStreamBufferSize()));
            lineCount = 0;
        }

        // write the header once
        if (lineCount == 0) {
            writeHeader();
        }

        writeLines();

        printWriter.flush();

        envelopes.clear();
    }

    private synchronized void writeRemainingEnvelopes() {
        writeEnvelopes();
        closeWriter();
    }

    private void closeWriter() {
        if (printWriter != null) {
            printWriter.close();
            printWriter = null;
            garbageCollectionOldLogs();
        }
    }

    private synchronized void clearColumns() {
        columns.clear();
        columnsChanged = false;
    }

    private void writeLines() {
        for (WireEnvelope envelope : envelopes) {
            writeCsvLines(envelope);
        }
    }

    private void writeCsvLines(final WireEnvelope envelope) {
        final List<WireRecord> records = envelope.getRecords();
        final String separator = options.getCsvSeparator();
        if (records != null) {
            for (WireRecord record : records) { // Typically there's only one record per envelope
                final Map<String, TypedValue<?>> props = record.getProperties();
                if (props != null) {
                    StringBuilder sb = new StringBuilder();
                    for (String column : columns) {
                        final TypedValue<?> propValue = props.get(column);
                        if (propValue != null) {
                            sb.append(propValue.getValue());
                        }
                        sb.append(separator);
                    }
                    sb.setLength(sb.length() - separator.length()); // remove last separator
                    printWriter.println(sb.toString());
                    lineCount++;
                }
            }
        }
    }

    private void writeHeader() {
        StringBuilder sb = new StringBuilder();
        final String separator = options.getCsvSeparator();
        // FIXME: code duplicated from above (use lambda)
        for (String column : columns) {
            sb.append(column);
            sb.append(separator);
        }
        sb.setLength(sb.length() - separator.length()); // remove last separator
        printWriter.println(sb.toString());
    }

    private boolean updateColumns(final WireEnvelope envelopes) {
        boolean changed = false;
        final List<WireRecord> records = envelopes.getRecords();
        if (records != null) {
            for (WireRecord record : records) { // typically there's only one record
                final Set<String> keys = record.getProperties().keySet();
                changed = columns.addAll(keys) || changed;
            }
        }
        return changed;
    }

    private void garbageCollectionOldLogs() {
        // get the current logs and compared with the maximum number we
        // need to keep
        TreeSet<Long> lids = getLogs();

        int currCount = lids.size();
        int maxCount = options.getMaxLogs();
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

    private TreeSet<Long> getLogs() {
        // keeps the list of logs ordered
        TreeSet<Long> ids = new TreeSet<>();
        String logDir = options.getLogDirectory();
        if (logDir != null) {
            File fLogDir = new File(logDir);
            File[] files = fLogDir.listFiles();
            if (files != null) {
                Pattern p = Pattern.compile(options.getLogFilePrefix() + "_([0-9]+)\\.csv");
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
        String logDir = options.getLogDirectory();

        if (logDir == null) {
            return null;
        }

        StringBuilder sbDir = new StringBuilder(logDir);
        sbDir.append(File.separator).append(options.getLogFilePrefix()).append("_").append(id).append(".csv");

        String log = sbDir.toString();
        return new File(log);
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