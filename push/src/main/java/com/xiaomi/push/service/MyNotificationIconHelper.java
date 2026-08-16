package com.xiaomi.push.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.xiaomi.channel.commonutils.file.IOUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/* loaded from: classes.dex */
public class MyNotificationIconHelper {
    public static final int KiB = 1024;
    public static final int MiB = 1024 * KiB;

    private static final int CONNECT_TIMEOUT = 8000;
    private static final int READ_TIMEOUT = 20000;
    private static final int FOCUS_CONNECT_TIMEOUT = 2000;
    private static final int FOCUS_READ_TIMEOUT = 3000;
    private static final int READ_UNIT = 1024;
    private static final int STANDARD_DENSITY = 160;
    private static final int STANDARD_ICON_SIZE = 48;
    private static final int MAX_DECODED_DIMENSION = 2048;
    private static final long MAX_DECODED_PIXELS = 1024L * 1024L;

    /* loaded from: classes.dex */
    public static class GetIconResult {
        public Bitmap bitmap;
        public long downloadSize;

        public GetIconResult(Bitmap bitmap, long downloadSize) {
            this.bitmap = bitmap;
            this.downloadSize = downloadSize;
        }
    }

    public static GetIconResult getIconFromUrl(Context context, String urlStr, int maxDownloadBytes) {
        return getIconFromUrl(context, urlStr, maxDownloadBytes, CONNECT_TIMEOUT, READ_TIMEOUT);
    }

    public static GetIconResult getFocusIconFromUrl(Context context, String urlStr, int maxDownloadBytes) {
        return getIconFromUrl(context, urlStr, maxDownloadBytes, FOCUS_CONNECT_TIMEOUT, FOCUS_READ_TIMEOUT);
    }

    /** Resolve the content/resource URI form used by HyperOS focus notifications. */
    public static GetIconResult getFocusIconFromUri(Context context, String uriStr, int maxDownloadBytes) {
        InputStream inputStream = null;
        GetIconResult result = new GetIconResult(null, 0L);
        try {
            inputStream = context.getContentResolver().openInputStream(Uri.parse(uriStr));
            byte[] data = readAtMost(inputStream, maxDownloadBytes);
            if (data == null) {
                result.downloadSize = maxDownloadBytes + 1L;
                return result;
            }
            result.downloadSize = data.length;
            if (data.length > 0) {
                int sampleSize = getSampleSize(context, new ByteArrayInputStream(data));
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sampleSize;
                result.bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
            }
        } catch (Throwable e) {
            MyLog.e(e);
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
        return result;
    }

    public static GetIconResult getIconFromUrl(Context context, String urlStr, int maxDownloadBytes, int connectTimeout, int readTimeout) {
        InputStream isForBitmapSize = null;
        GetIconResult result = new GetIconResult(null, 0L);
        try {
            GetDataResult getDataResult = getDataFromUrl(urlStr, maxDownloadBytes, connectTimeout, readTimeout);
            if (getDataResult != null) {
                result.downloadSize = getDataResult.downloadSize;
                byte[] data = getDataResult.data;
                if (data != null && data.length > 0) {
                    isForBitmapSize = new ByteArrayInputStream(data);
                    int sampleSize = getSampleSize(context, isForBitmapSize);
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = sampleSize;
                    result.bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                }
            }
        } catch (Throwable e) {
            MyLog.e(e);
        } finally {
            IOUtils.closeQuietly(isForBitmapSize);
        }
        return result;
    }

    /* loaded from: classes.dex */
    public static class GetDataResult {
        byte[] data;
        int downloadSize;

        public GetDataResult(byte[] data, int downloadSize) {
            this.data = data;
            this.downloadSize = downloadSize;
        }
    }

    private static GetDataResult getDataFromUrl(String urlStr, int maxDownloadBytes, int connectTimeout, int readTimeout) {
        HttpURLConnection conn = null;
        InputStream inputStream = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.connect();
            int contentLen = conn.getContentLength();
            if (contentLen > maxDownloadBytes) {
                MyLog.w("Bitmap size is too big, max size is " + maxDownloadBytes + " contentLen size is " + contentLen + " from url " + urlStr);
                return null;
            }
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                MyLog.w("Invalid Http Response Code " + responseCode + " received");
                return null;
            }
            inputStream = conn.getInputStream();
            ByteArrayOutputStream tempOutStream = new ByteArrayOutputStream();
            int availableSpace = maxDownloadBytes;
            byte[] dataUnit = new byte[READ_UNIT];
            while (availableSpace > 0) {
                int readCount = inputStream.read(dataUnit, 0, Math.min(READ_UNIT, availableSpace));
                if (readCount == -1) {
                    break;
                }
                availableSpace -= readCount;
                tempOutStream.write(dataUnit, 0, readCount);
            }
            if (availableSpace <= 0 && inputStream.read() != -1) {
                MyLog.w("length " + maxDownloadBytes + " exhausted.");
                return new GetDataResult(null, maxDownloadBytes);
            }
            byte[] data = tempOutStream.toByteArray();
            return new GetDataResult(data, data.length);
        } catch (Throwable e) {
            MyLog.e(e);
            return null;
        } finally {
            IOUtils.closeQuietly(inputStream);
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** Read one extra byte so callers can distinguish an exact limit from overflow. */
    private static byte[] readAtMost(InputStream inputStream, int maxDownloadBytes) throws IOException {
        if (inputStream == null || maxDownloadBytes < 0) {
            return null;
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxDownloadBytes, 8192));
        byte[] buffer = new byte[READ_UNIT];
        int total = 0;
        while (total <= maxDownloadBytes) {
            int read = inputStream.read(buffer, 0,
                    Math.min(buffer.length, maxDownloadBytes + 1 - total));
            if (read < 0) {
                return output.toByteArray();
            }
            output.write(buffer, 0, read);
            total += read;
            if (total > maxDownloadBytes) {
                return null;
            }
        }
        return null;
    }

    public static Bitmap getIconFromUri(Context context, String uriStr) {
        Bitmap bitmap = null;
        Uri uri = Uri.parse(uriStr);
        InputStream is = null;
        InputStream isForBitmapSize = null;
        try {
            isForBitmapSize = context.getContentResolver().openInputStream(uri);
            int sampleSize = getSampleSize(context, isForBitmapSize);
            is = context.getContentResolver().openInputStream(uri);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize;
            bitmap = BitmapFactory.decodeStream(is, null, options);
            return bitmap;
        } catch (Throwable th) {
            MyLog.e(th);
            return null;
        } finally {
            IOUtils.closeQuietly(is);
            IOUtils.closeQuietly(isForBitmapSize);
        }
    }

    private static int getSampleSize(Context context, InputStream inputStream) {
        if (inputStream == null) {
            return 1;
        }
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(inputStream, null, opt);
        if (opt.outWidth == -1 || opt.outHeight == -1) {
            MyLog.w("decode dimension failed for bitmap.");
            return 1;
        }
        int screenDensity = context.getResources().getDisplayMetrics().densityDpi;
        int targetWidth = Math.max(1,
                Math.round((screenDensity / (float) STANDARD_DENSITY) * STANDARD_ICON_SIZE));
        return calculateSampleSize(opt.outWidth, opt.outHeight, targetWidth);
    }

    /**
     * Preserve Xiaomi's 48dp target while bounding pathological panoramic or
     * highly-compressed images before BitmapFactory allocates their pixels.
     */
    static int calculateSampleSize(int width, int height, int targetWidth) {
        if (width <= 0 || height <= 0 || targetWidth <= 0) {
            return 1;
        }
        int sampleSize = 1;
        if (width > targetWidth && height > targetWidth) {
            int requested = Math.max(1,
                    Math.min(width / targetWidth, height / targetWidth));
            // BitmapFactory rounds non-power-of-two values down on supported
            // Android releases, so model the effective value explicitly.
            sampleSize = Integer.highestOneBit(requested);
        }
        while (decodedDimension(width, sampleSize) > MAX_DECODED_DIMENSION
                || decodedDimension(height, sampleSize) > MAX_DECODED_DIMENSION
                || decodedPixels(width, height, sampleSize) > MAX_DECODED_PIXELS) {
            if (sampleSize > Integer.MAX_VALUE / 2) return Integer.MAX_VALUE;
            sampleSize *= 2;
        }
        return sampleSize;
    }

    private static long decodedPixels(int width, int height, int sampleSize) {
        return (long) decodedDimension(width, sampleSize)
                * decodedDimension(height, sampleSize);
    }

    private static int decodedDimension(int value, int sampleSize) {
        return (int) (((long) value + sampleSize - 1L) / sampleSize);
    }
}
