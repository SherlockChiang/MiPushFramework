package com.xiaomi.xmsf.push.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Pair;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.nihility.Global;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.trumeet.common.utils.Utils;

public class ConfigurationsLoader {
    private static final Logger logger = XLog.tag(ConfigurationsLoader.class.getSimpleName()).build();

    private volatile PublishedConfigurationState publishedState =
            PublishedConfigurationState.notConfigured();

    private Context mContext = null;
    private Uri mTreeUri = null;
    private DocumentFile mDocumentFile = null;
    private long mLastLoadTime = 0;


    public ConfigurationsLoader() {
    }

    public Map<String, List<Object>> getConfigs() {
        return publishedState.packageConfigs;
    }

    public ConfigurationDiagnosticsSnapshot getDiagnosticsSnapshot() {
        return publishedState.diagnosticsSnapshot;
    }

    /**
     * Returns a deterministic snapshot of configuration references that do not resolve to a
     * currently loaded config key. Diagnostics contain config metadata only; notification data is
     * never inspected or retained.
     */
    public List<ConfigurationReferenceDiagnostics.UnresolvedReference> getUnresolvedReferences() {
        return getDiagnosticsSnapshot().getUnresolvedReferences();
    }

    public synchronized boolean init(Context context, Uri treeUri) {
        mLastLoadTime = System.currentTimeMillis();
        if (context == null || treeUri == null) {
            clearDirectoryTracking();
            publishedState = PublishedConfigurationState.notConfigured();
            return false;
        }

        mContext = context;
        mTreeUri = treeUri;
        mDocumentFile = null;

        DocumentFile documentFile;
        try {
            documentFile = DocumentFile.fromTreeUri(context, treeUri);
            if (documentFile == null
                    || !documentFile.exists()
                    || !documentFile.isDirectory()
                    || !documentFile.canRead()) {
                logger.e("configuration_directory_load_failed stage=[validate_directory]");
                publishedState = PublishedConfigurationState.failed();
                return false;
            }
        } catch (RuntimeException exception) {
            logDirectoryFailure("open_directory", exception);
            publishedState = PublishedConfigurationState.failed();
            return false;
        }

        mDocumentFile = documentFile;
        MutableConfigurationState candidate = MutableConfigurationState.empty();
        List<Pair<DocumentFile, JSONException>> exceptions = new ArrayList<>();
        List<DocumentFile> loadedFiles = new ArrayList<>();
        boolean directoryParsed;
        try {
            directoryParsed = parseDirectory(
                    context, treeUri, documentFile, candidate, exceptions, loadedFiles);
        } catch (RuntimeException exception) {
            logDirectoryFailure("parse_directory", exception);
            directoryParsed = false;
        }

        boolean successful = directoryParsed && exceptions.isEmpty();
        if (successful) {
            publish(candidate);
        } else {
            publishedState = PublishedConfigurationState.failed();
        }

        reportDirectoryLoadResult(context, loadedFiles, exceptions);
        return successful;
    }

    private void clearDirectoryTracking() {
        mContext = null;
        mTreeUri = null;
        mDocumentFile = null;
    }

    private static void logDirectoryFailure(String stage, RuntimeException exception) {
        logger.e("configuration_directory_load_failed stage=[%s] exception_type=[%s]",
                stage, exception.getClass().getName());
    }

    private static void reportDirectoryLoadResult(
            Context context,
            List<DocumentFile> loadedFiles,
            List<Pair<DocumentFile, JSONException>> exceptions) {
        try {
            if (!loadedFiles.isEmpty()
                    && Global.ConfigCenter().isShowConfigurationListOnLoaded(context)) {
                StringBuilder loadedList = new StringBuilder("loaded configuration list:");
                for (DocumentFile file : loadedFiles) {
                    loadedList.append('\n');
                    loadedList.append(file.getName());
                }
                Utils.makeText(context, loadedList, Toast.LENGTH_SHORT);
            }
        } catch (RuntimeException exception) {
            logDirectoryFailure("report_loaded_files", exception);
        }
        for (Pair<DocumentFile, JSONException> pair : exceptions) {
            try {
                StringBuilder errmsg = getJsonExceptionMessage(context, pair);
                logger.e(errmsg);
                Utils.makeText(context, errmsg.toString(), Toast.LENGTH_LONG);
            } catch (RuntimeException exception) {
                logDirectoryFailure("report_json_error", exception);
            }
        }
    }

    @NonNull
    public static StringBuilder getJsonExceptionMessage(Context context, Pair<DocumentFile, JSONException> pair) {
        DocumentFile file = pair.first;
        JSONException e = pair.second;
        e.printStackTrace();

        StringBuilder errmsg = new StringBuilder(e.toString());
        Pattern pattern = Pattern.compile(" character (\\d+) of ");
        Matcher matcher = pattern.matcher(errmsg.toString());
        if (matcher.find()) {
            int pos = Integer.parseInt(matcher.group(1));
            String json = readTextFromUri(context, file.getUri());
            String[] beforeErr = json.substring(0, pos).split("\n");
            int errorLine = beforeErr.length;
            int errorColumn = beforeErr[beforeErr.length - 1].length();
            String exceptionMessage = errmsg.substring(0, matcher.start())
                    .replace("org.json.JSONException: ", "")
                    .replaceFirst("(after )(.*)( at)", "$1\"$2\"$3");
            errmsg = new StringBuilder(String.format("%s line %d column %d", exceptionMessage, errorLine, errorColumn));

            String[] jsonLine = json.split("\n");
            jsonLine[errorLine - 1] = jsonLine[errorLine - 1].substring(0, errorColumn - 1) +
                    "┋" +
                    jsonLine[errorLine - 1].substring(errorColumn - 1);
            for (int i = Math.max(0, errorLine - 2); i <= Math.min(jsonLine.length - 1, errorLine); ++i) {
                errmsg.append('\n');
                errmsg.append(i + 1);
                errmsg.append(": ");
                errmsg.append(jsonLine[i]);
            }
        }
        errmsg.insert(0, file.getName() + "\n");
        return errmsg;
    }

    private boolean parseDirectory(
            Context context,
            Uri treeUri,
            DocumentFile documentFile,
            MutableConfigurationState candidate,
            List<Pair<DocumentFile, JSONException>> exceptions,
            List<DocumentFile> loadedFiles) {
        logger.i("parseDirectory uri: [%s]", treeUri.getPath());
        DocumentFile[] files;
        try {
            files = documentFile.listFiles();
        } catch (RuntimeException exception) {
            logDirectoryFailure("list_files", exception);
            return false;
        }
        if (files == null) {
            logger.e("configuration_directory_load_failed stage=[list_files_null]");
            return false;
        }
        for (DocumentFile file : files) {
            String fileName = file.getName();
            logger.i("file: [%s], type: [%s]", fileName, file.getType());
            if (fileName == null || !fileName.toLowerCase().endsWith(".json")) {
                continue;
            }
            String json;
            try {
                json = readConfigurationText(context, file.getUri());
            } catch (ConfigurationReadException exception) {
                logger.e(
                        "configuration_file_load_failed file=[%s] exception_type=[%s]",
                        fileName, exception.exceptionType);
                return false;
            }
            try {
                parse(json, fileName, candidate);
                loadedFiles.add(file);
            } catch (JSONException e) {
                exceptions.add(new Pair<>(file, e));
            }
        }
        return exceptions.isEmpty();
    }

    private static String readConfigurationText(Context context, Uri uri)
            throws ConfigurationReadException {
        StringBuilder stringBuilder = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(Objects.requireNonNull(inputStream)))) {
            char[] buffer = new char[1024];
            int len;
            while ((len = reader.read(buffer)) != -1) {
                stringBuilder.append(buffer, 0, len);
            }
            return stringBuilder.toString();
        } catch (Exception exception) {
            exception.printStackTrace();
            Utils.makeText(context, exception.toString(), Toast.LENGTH_LONG);
            throw new ConfigurationReadException(exception.getClass().getName());
        }
    }

    private static final class ConfigurationReadException extends Exception {
        private final String exceptionType;

        private ConfigurationReadException(String exceptionType) {
            super(null, null, false, false);
            this.exceptionType = exceptionType;
        }
    }

    public void load(String json) throws JSONException {
        load("<memory>", json);
    }

    /** Loads an in-memory configuration while retaining a caller-provided diagnostic source name. */
    public synchronized void load(String sourceName, String json) throws JSONException {
        MutableConfigurationState candidate =
                MutableConfigurationState.copyOf(publishedState);
        parse(json, sourceName, candidate);
        publish(candidate);
    }

    private void parse(
            String json, String sourceName, MutableConfigurationState candidate)
            throws JSONException {
        JSONObject jsonObject = new JSONObject(json);
        candidate.version = jsonObject.getString("version");
        JSONObject packageConfigsObj = jsonObject.getJSONObject("configs");
        Iterator<String> packageNames = packageConfigsObj.keys();
        while (packageNames.hasNext()) {
            String packageName = packageNames.next();
            JSONArray configsObj = packageConfigsObj.getJSONArray(packageName);
            candidate.packageConfigs.put(packageName, parseConfigs(configsObj));
            candidate.referenceSites.put(
                    packageName, findReferenceSites(sourceName, packageName, configsObj));
        }
    }

    @NonNull
    private static List<ConfigurationReferenceDiagnostics.UnresolvedReference> findReferenceSites(
            String sourceName, String ownerKey, JSONArray configsObj) throws JSONException {
        List<ConfigurationReferenceDiagnostics.UnresolvedReference> sites = new ArrayList<>();
        for (int i = 0; i < configsObj.length(); ++i) {
            Object config = configsObj.get(i);
            if (config instanceof String) {
                sites.add(ConfigurationReferenceDiagnostics.referenceSite(
                        sourceName, ownerKey, (String) config));
            }
        }
        return sites;
    }

    private void publish(MutableConfigurationState candidate) {
        List<ConfigurationReferenceDiagnostics.UnresolvedReference> sites = new ArrayList<>();
        for (List<ConfigurationReferenceDiagnostics.UnresolvedReference> ownerSites
                : candidate.referenceSites.values()) {
            sites.addAll(ownerSites);
        }
        List<ConfigurationReferenceDiagnostics.UnresolvedReference> unresolvedReferences =
                ConfigurationReferenceDiagnostics.resolve(
                        candidate.packageConfigs.keySet(), sites);
        ConfigurationDiagnosticsSnapshot diagnosticsSnapshot =
                ConfigurationDiagnosticsSnapshot.ready(unresolvedReferences);
        publishedState = PublishedConfigurationState.ready(
                candidate.version, candidate.packageConfigs, candidate.referenceSites,
                diagnosticsSnapshot);
        for (ConfigurationReferenceDiagnostics.UnresolvedReference diagnostic
                : unresolvedReferences) {
            logger.w("unresolved_configuration_reference source=[%s] owner=[%s] reference=[%s]",
                    diagnostic.getSourceName(), diagnostic.getOwnerKey(), diagnostic.getReference());
        }
    }

    @NonNull
    private ArrayList<Object> parseConfigs(JSONArray configsObj) throws JSONException {
        ArrayList<Object> configs = new ArrayList<>();
        for (int i = 0; i < configsObj.length(); ++i) {
            Object config = configsObj.get(i);
            if (config instanceof JSONArray) {
                configs.add(config);
            } else if (config instanceof String) {
                configs.add(config);
            } else {
                configs.add(parseConfig(configsObj.getJSONObject(i)));
            }
        }
        return configs;
    }

    @NonNull
    PackageConfig parseConfig(JSONObject configObj) throws JSONException {
        PackageConfig config = new PackageConfig(Configurations.getInstance());
        if (!configObj.isNull(PackageConfig.KEY_META_INFO)) {
            JSONObject obj = new JSONObject();
            obj.put(PackageConfig.KEY_META_INFO, configObj.getJSONObject(PackageConfig.KEY_META_INFO));
            config.cfgMatch = obj;
        }
        if (!configObj.isNull(PackageConfig.KEY_NEW_META_INFO)) {
            JSONObject obj = new JSONObject();
            obj.put(PackageConfig.KEY_META_INFO, configObj.getJSONObject(PackageConfig.KEY_NEW_META_INFO));
            config.cfgReplace = obj;
        }
        if (!configObj.isNull(PackageConfig.KEY_MATCH)) {
            config.cfgMatch = configObj.getJSONObject(PackageConfig.KEY_MATCH);
        }
        if (!configObj.isNull(PackageConfig.KEY_REPLACE)) {
            config.cfgReplace = configObj.getJSONObject(PackageConfig.KEY_REPLACE);;
        }
        if (!configObj.isNull(PackageConfig.KEY_OPERATION)) {
            String operations = configObj.getString(PackageConfig.KEY_OPERATION);
            config.operation = new HashSet<>(Arrays.asList(operations.split("[\\s|]+")));
        }
        if (!configObj.isNull(PackageConfig.KEY_STOP)) {
            config.stop = configObj.getBoolean(PackageConfig.KEY_STOP);
        }
        return config;
    }

    public synchronized void reInitIfDirectoryUpdated() {
        if (mContext == null || mTreeUri == null || mDocumentFile == null) {
            return;
        }
        if (mDocumentFile.lastModified() > mLastLoadTime) {
            init(mContext, mTreeUri);
        }
    }

    private static final class MutableConfigurationState {
        private String version;
        private final Map<String, List<Object>> packageConfigs;
        private final Map<String, List<ConfigurationReferenceDiagnostics.UnresolvedReference>>
                referenceSites;

        private MutableConfigurationState(
                String version,
                Map<String, List<Object>> packageConfigs,
                Map<String, List<ConfigurationReferenceDiagnostics.UnresolvedReference>>
                        referenceSites) {
            this.version = version;
            this.packageConfigs = packageConfigs;
            this.referenceSites = referenceSites;
        }

        private static MutableConfigurationState empty() {
            return new MutableConfigurationState(null, new HashMap<>(), new HashMap<>());
        }

        private static MutableConfigurationState copyOf(PublishedConfigurationState state) {
            return new MutableConfigurationState(
                    state.version,
                    new HashMap<>(state.packageConfigs),
                    new HashMap<>(state.referenceSites));
        }
    }

    private static final class PublishedConfigurationState {
        private final String version;
        private final Map<String, List<Object>> packageConfigs;
        private final Map<String, List<ConfigurationReferenceDiagnostics.UnresolvedReference>>
                referenceSites;
        private final ConfigurationDiagnosticsSnapshot diagnosticsSnapshot;

        private PublishedConfigurationState(
                String version,
                Map<String, List<Object>> packageConfigs,
                Map<String, List<ConfigurationReferenceDiagnostics.UnresolvedReference>>
                        referenceSites,
                ConfigurationDiagnosticsSnapshot diagnosticsSnapshot) {
            this.version = version;
            this.packageConfigs = Collections.unmodifiableMap(
                    new HashMap<>(packageConfigs));
            this.referenceSites = Collections.unmodifiableMap(
                    new HashMap<>(referenceSites));
            this.diagnosticsSnapshot = diagnosticsSnapshot;
        }

        private static PublishedConfigurationState notConfigured() {
            return empty(ConfigurationDiagnosticsSnapshot.notConfigured());
        }

        private static PublishedConfigurationState failed() {
            return empty(ConfigurationDiagnosticsSnapshot.failed());
        }

        private static PublishedConfigurationState empty(
                ConfigurationDiagnosticsSnapshot diagnosticsSnapshot) {
            return new PublishedConfigurationState(
                    null, Collections.emptyMap(), Collections.emptyMap(), diagnosticsSnapshot);
        }

        private static PublishedConfigurationState ready(
                String version,
                Map<String, List<Object>> packageConfigs,
                Map<String, List<ConfigurationReferenceDiagnostics.UnresolvedReference>>
                        referenceSites,
                ConfigurationDiagnosticsSnapshot diagnosticsSnapshot) {
            return new PublishedConfigurationState(
                    version, packageConfigs, referenceSites, diagnosticsSnapshot);
        }
    }


    public static String readTextFromUri(Context context, Uri uri) {
        StringBuilder stringBuilder = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(Objects.requireNonNull(inputStream)))) {
            char[] buffer = new char[1024];
            int len;
            while ((len = reader.read(buffer)) != -1) {
                stringBuilder.append(buffer, 0, len);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Utils.makeText(context, e.toString(), Toast.LENGTH_LONG);
        }
        return stringBuilder.toString();
    }

}
