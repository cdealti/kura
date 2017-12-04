package org.eclipse.kura.internal.wire.logger;

import static org.eclipse.kura.internal.wire.logger.LoggingVerbosity.QUIET;

import java.util.Map;

import org.eclipse.kura.configuration.ConfigurationService;

public class LoggerOptions {

    private static final String DEFAULT_LOG_LEVEL = QUIET.name();

    private static final String PROP_LOG_LEVEL = "log.verbosity";

    private static final String PROP_FILE_MAX_ROWS = "log.max.rows";

    private static final int DEFAULT_FILE_MAX_ROWS = 40000;

    private static final String PROP_MAX_IN_MEMORY = "log.max.in.memory.envelopes";

    private static final int DEFAULT_MAX_IN_MEMORY = 10;

    private static final String PROP_STREAM_BUFFER_SIZE = "log.stream.buffer.size";

    private static final int DEFAULT_STREAM_BUFFER_SIZE = 8192;

    private static final String PROP_CSV_SEPARATOR = "log.csv.separator";

    private static final String DEFAULT_CSV_SEPARATOR = ",";

    private static final String PROP_LOG_DIRECTORY = "log.directory";

    private static final String DEFAULT_LOG_DIRECTORY = "/var/log/";

    private static final String PROP_LOG_FILE_PREFIX = "log.file.prefix";

    private static final String DEFAULT_LOG_FILE_PREFIX = "wirelog";

    private static final String PROP_MAX_LOGS = "log.max.count";

    private static final int DEFAULT_MAX_LOGS = 10;

    private Map<String, Object> properties;
    private int fileMaxRows;
    private int maxInMemoryEnvelopes;
    private int streamBufferSize;
    private String csvSeparator;
    private String ownPid;
    private String logDirectory;
    private String logFilePrefix;
    private int maxLogs;

    public LoggerOptions(Map<String, Object> properties) {
        this.properties = properties;
        fileMaxRows = _getFileMaxRows();
        maxInMemoryEnvelopes = _getMaxInMemoryEnvelopes();
        streamBufferSize = _getStreamBufferSize();
        csvSeparator = _getCsvSeparator();
        ownPid = _getOwnPid();
        logDirectory = _getLogDirectory();
        logFilePrefix = _getLogFilePrefix();
        maxLogs = _getMaxLogs();
    }

    private int _getFileMaxRows() {
        int result = DEFAULT_FILE_MAX_ROWS;
        final Object o = this.properties.get(PROP_FILE_MAX_ROWS);
        if (o instanceof Integer) {
            result = (int) o;
        }
        return result;
    }

    private int _getMaxInMemoryEnvelopes() {
        int result = DEFAULT_MAX_IN_MEMORY;
        final Object o = this.properties.get(PROP_MAX_IN_MEMORY);
        if (o instanceof Integer) {
            result = (int) o;
        }
        return result;
    }

    private int _getStreamBufferSize() {
        int result = DEFAULT_STREAM_BUFFER_SIZE;
        final Object o = this.properties.get(PROP_STREAM_BUFFER_SIZE);
        if (o instanceof Integer) {
            result = (int) o;
        }
        return result;
    }

    private String _getCsvSeparator() {
        String result = DEFAULT_CSV_SEPARATOR;
        final Object o = this.properties.get(PROP_CSV_SEPARATOR);
        if (o instanceof Character) {
            result = (String) o;
        }
        return result;
    }

    private String _getLogDirectory() {
        String result = DEFAULT_LOG_DIRECTORY;
        final Object o = this.properties.get(PROP_LOG_DIRECTORY);
        if (o instanceof String) {
            result = (String) o;
        }
        return result;
    }

    private String _getLogFilePrefix() {
        String result = DEFAULT_LOG_FILE_PREFIX;
        final String pid = _getOwnPid();
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

    private int _getMaxLogs() {
        int result = DEFAULT_MAX_LOGS;
        final Object o = this.properties.get(PROP_MAX_LOGS);
        if (o instanceof Integer) {
            result = (Integer) o;
        }
        return result;
    }

    private String _getOwnPid() {
        String result = null;
        final Object o = this.properties.get(ConfigurationService.KURA_SERVICE_PID);
        if (o instanceof String) {
            result = (String) o;
        }
        return result;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public int getFileMaxRows() {
        return fileMaxRows;
    }

    public int getMaxInMemoryEnvelopes() {
        return maxInMemoryEnvelopes;
    }

    public int getStreamBufferSize() {
        return streamBufferSize;
    }

    public String getCsvSeparator() {
        return csvSeparator;
    }

    public String getOwnPid() {
        return ownPid;
    }

    public String getLogDirectory() {
        return logDirectory;
    }

    public String getLogFilePrefix() {
        return logFilePrefix;
    }

    public int getMaxLogs() {
        return maxLogs;
    }
}
